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
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.canvasext.*;
import org.eclipse.swt.internal.render.m3.*;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.widgets.Canvas;
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
	private final Function<Canvas, IExternalCanvasHandler> raster, gpu;

	/** The shipped factory (ServiceLoader, Display registration): the startup mode of the canvas's class, the M3 handlers. */
	public M3CanvasFactory() {
		this(M3CanvasFactory::mode, M3CanvasHandler::raster, M3CanvasHandler::gpu);
	}

	/** The decision with one stubbed mode for every class and stubbed handler makers (headless tests, leaf 4). */
	public M3CanvasFactory(Supplier<WidgetMode> selector, Function<Canvas, IExternalCanvasHandler> raster,
			Function<Canvas, IExternalCanvasHandler> gpu) {
		this(type -> selector.get(), raster, gpu);
	}

	/** The decision with a stubbed per-class selector and stubbed handler makers (headless tests, leaf 5). */
	public M3CanvasFactory(Function<Class<?>, WidgetMode> selector, Function<Canvas, IExternalCanvasHandler> raster,
			Function<Canvas, IExternalCanvasHandler> gpu) {
		this.selector = selector;
		this.raster = raster;
		this.gpu = gpu;
	}

	/** The startup mode of a canvas class: its own property, a superclass's up to <code>Canvas</code>, else the global one. */
	public static WidgetMode mode(Class<?> type) {
		return WidgetMode.of(type, Canvas.class, WidgetMode.M3, WidgetMode.GPU);
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
		if (m3FailedWithErrors) {
			return null;
		}
		try {
			Class<?> type = type(c);
			WidgetMode backend = backend(selector.apply(type), type, style);
			return backend == null ? null : (backend == WidgetMode.GPU ? gpu : raster).apply(c);
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
}
