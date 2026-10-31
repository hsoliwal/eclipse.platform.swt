/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render.m3.gc;

import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.canvasext.*;
import org.eclipse.swt.internal.render.m3.*;
import org.eclipse.swt.ole.win32.OleClientSite;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Decorations;

/**
 * Provides a factory for creating M3 canvas extensions. This factory is used by
 * org.eclipse.swt.internal.canvasext.ExternalCanvasHandler (the win32 Display registers it by name when the Canvas
 * startup mode is m3 or gpu; the ServiceLoader path stays for a bundle that ships a META-INF/services entry) to create
 * canvas extensions when the {@link SWT#M3} style is specified.
 * <p>
 * M3 (GC seam, leaf 5; pattern M3-CANVAS-ACTIVATION v2, v1 was leaf 4's mode x style bit): the decision is the widget
 * mode of the canvas's class, {@link #backend(WidgetMode, Class, int)}. In the <code>m3</code> (raster,
 * {@link M3CanvasHandler#raster}) and <code>gpu</code> ({@link M3CanvasHandler#gpu}) modes every <code>Canvas</code>
 * subclass gets a handler, with or without <code>SWT.M3</code> (StyledText, CLabel, the table and tree cursors, Nebula's
 * canvases); a class may choose its own mode (<code>swt.widget.mode.StyledText</code>, then a superclass's property up to
 * <code>swt.widget.mode.Canvas</code>, then <code>swt.widget.mode</code>). A canvas whose pixels are not its paint
 * listeners' (a <code>GLCanvas</code>, an <code>SWT.EMBEDDED</code> host) needs the bit, a <code>Decorations</code>
 * (Shell) never gets one, and with no mode set every canvas paints as it always did. The selector and the handler makers
 * are constructor arguments, so the decision is testable without a Display.
 * </p>
 */
public class M3CanvasFactory implements IExternalCanvasFactory {
	/** The widget class whose startup mode decides when no subclass has a property of its own. */
	public static final String WIDGET = "Canvas"; //$NON-NLS-1$

	private static boolean m3FailedWithErrors = false;

	private final Function<Class<?>, WidgetMode> selector;
	private final Function<Composite, IExternalCanvasHandler> raster, gpu;

	/** The shipped factory (ServiceLoader, Display registration): the startup mode of the canvas's class, the M3 handlers. */
	public M3CanvasFactory() {
		this(M3CanvasFactory::mode, M3CanvasHandler::raster, M3CanvasHandler::gpu);
	}

	/** The decision with one stubbed mode for every class and stubbed handler makers (headless tests, leaf 4). */
	public M3CanvasFactory(Supplier<WidgetMode> selector, Function<Composite, IExternalCanvasHandler> raster,
			Function<Composite, IExternalCanvasHandler> gpu) {
		this(type -> selector.get(), raster, gpu);
	}

	/** The decision with a stubbed per-class selector and stubbed handler makers (headless tests, leaf 5; leaf 7: a composite too). */
	public M3CanvasFactory(Function<Class<?>, WidgetMode> selector, Function<Composite, IExternalCanvasHandler> raster,
			Function<Composite, IExternalCanvasHandler> gpu) {
		this.selector = selector;
		this.raster = raster;
		this.gpu = gpu;
	}

	/**
	 * The startup mode of a canvas class: its own property, a superclass's up to <code>Canvas</code>, else the global one.
	 * Leaf 7: a composite that is not a canvas up to <code>Composite</code> (<code>swt.widget.mode.CTabFolder</code>, then
	 * <code>swt.widget.mode.Composite</code>, then <code>swt.widget.mode</code>: the properties the viewport selectors read).
	 */
	public static WidgetMode mode(Class<?> type) {
		Class<?> root = Canvas.class.isAssignableFrom(type) ? Canvas.class : Composite.class;
		return WidgetMode.of(type, root, WidgetMode.M3, WidgetMode.GPU);
	}

	/**
	 * The backend a mode, a canvas class and a style select: {@link WidgetMode#GPU}, {@link WidgetMode#M3} (raster) or null
	 * for the stock path. A mode that is not drawn through the M3 renderer is null (default off); a <code>Decorations</code>
	 * never; a canvas whose pixels come from elsewhere ({@link #foreign}) only with <code>SWT.M3</code>; every other class
	 * with or without the bit.
	 */
	public static WidgetMode backend(WidgetMode mode, Class<?> type, int style) {
		if (mode != WidgetMode.M3 && mode != WidgetMode.GPU || Decorations.class.isAssignableFrom(type)) {
			return null;
		}
		return foreign(type, style) && (style & SWT.M3) == 0 ? null : mode;
	}

	/** {@link #backend(WidgetMode, Class, int)} of a plain <code>Canvas</code>. */
	public static WidgetMode backend(WidgetMode mode, int style) {
		return backend(mode, Canvas.class, style);
	}

	/** A canvas whose pixels come from elsewhere: GL content (<code>GLCanvas</code>) or an embedded foreign window (<code>SWT.EMBEDDED</code>). */
	public static boolean foreign(Class<?> type, int style) {
		return (style & SWT.EMBEDDED) != 0 || GLCanvas.class.isAssignableFrom(type);
	}

	/**
	 * M3 (GC seam, leaf 7; pattern COMPOSITE_PAINT_HOOK v1): the backend of a composite that paints itself, or null for the
	 * stock path. A <code>Canvas</code> is never decided here (its constructor hook decides; <code>Decorations</code> included);
	 * a composite whose pixels come from elsewhere ({@link #foreignComposite}) stays stock, and so does one whose stock paint
	 * reads the pixels behind it or mirrors them (<code>SWT.TRANSPARENT</code>, <code>SWT.RIGHT_TO_LEFT</code>: the retained
	 * planes' rule of <code>Composite.createHandle</code>).
	 */
	public static WidgetMode compositeBackend(WidgetMode mode, Class<?> type, int style) {
		if (mode != WidgetMode.M3 && mode != WidgetMode.GPU || Canvas.class.isAssignableFrom(type)) {
			return null;
		}
		return foreignComposite(type, style) || (style & (SWT.TRANSPARENT | SWT.RIGHT_TO_LEFT)) != 0 ? null : mode;
	}

	/** A composite whose pixels come from elsewhere: an embedded window, an OLE site (its object draws on the device context), a Browser. */
	public static boolean foreignComposite(Class<?> type, int style) {
		return (style & SWT.EMBEDDED) != 0 || OleClientSite.class.isAssignableFrom(type) || Browser.class.isAssignableFrom(type);
	}

	@Override
	public boolean accepts(Canvas canvas, int style) {
		if (m3FailedWithErrors) {
			return false;
		}
		Class<?> type = type(canvas);
		return backend(selector.apply(type), type, style) != null;
	}

	@Override
	public IExternalCanvasHandler createCanvasExtension(Canvas c) {
		return create(c, c.getStyle());
	}

	/** {@link #createCanvasExtension} with the style given; the canvas is only handed to the handler maker. */
	public IExternalCanvasHandler create(Canvas c, int style) {
		return make(c, type -> backend(selector.apply(type), type, style), type(c));
	}

	@Override
	public boolean acceptsComposite(Composite composite, int style) {
		if (m3FailedWithErrors) {
			return false;
		}
		Class<?> type = compositeType(composite);
		return compositeBackend(selector.apply(type), type, style) != null;
	}

	@Override
	public IExternalCanvasHandler createCompositeExtension(Composite composite) {
		return createComposite(composite, composite.getStyle());
	}

	/** {@link #createCompositeExtension} with the style given (leaf 7); the composite is only handed to the handler maker. */
	public IExternalCanvasHandler createComposite(Composite composite, int style) {
		return make(composite, type -> compositeBackend(selector.apply(type), type, style), compositeType(composite));
	}

	/* the backend the decision selects for the widget's class, made by the raster or gpu maker; a failure disables M3 */
	private IExternalCanvasHandler make(Composite widget, Function<Class<?>, WidgetMode> decision, Class<?> type) {
		if (m3FailedWithErrors) {
			return null;
		}
		try {
			WidgetMode backend = decision.apply(type);
			return backend == null ? null : (backend == WidgetMode.GPU ? gpu : raster).apply(widget);
		} catch (final Throwable t) {
			// M3 initialization failed; log and disable M3 for all future calls
			Logger.logException(t);
			m3FailedWithErrors = true;
			return null;
		}
	}

	/* the class that decides: the canvas's runtime class (a null canvas, in the headless tests, is a plain Canvas) */
	static Class<?> type(Canvas canvas) {
		return canvas == null ? Canvas.class : canvas.getClass();
	}

	/* the class that decides for a composite (a null composite, in the headless tests, is a plain Composite) */
	static Class<?> compositeType(Composite composite) {
		return composite == null ? Composite.class : composite.getClass();
	}
}
