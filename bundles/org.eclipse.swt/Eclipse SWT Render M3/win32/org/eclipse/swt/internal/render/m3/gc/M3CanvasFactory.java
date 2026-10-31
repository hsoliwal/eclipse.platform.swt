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
import org.eclipse.swt.widgets.Canvas;

/**
 * Provides a factory for creating M3 canvas extensions. This factory is used by
 * org.eclipse.swt.internal.canvasext.ExternalCanvasHandler (the win32 Display registers it by name when the Canvas
 * startup mode is m3 or gpu; the ServiceLoader path stays for a bundle that ships a META-INF/services entry) to create
 * canvas extensions when the {@link SWT#M3} style is specified.
 * <p>
 * M3 (GC seam, leaf 4): the decision is the widget-mode selector times the style bit, {@link #backend}: a canvas gets a
 * handler only when its mode (<code>swt.widget.mode.Canvas</code>, else <code>swt.widget.mode</code>) is
 * <code>m3</code> (raster, {@link M3CanvasHandler#raster}) or <code>gpu</code> ({@link M3CanvasHandler#gpu}) and it was
 * created with <code>SWT.M3</code>; every other canvas answers null and paints as it always did. The selector and the
 * handler makers are constructor arguments, so the decision is testable without a Display.
 * </p>
 */
public class M3CanvasFactory implements IExternalCanvasFactory {
	/** The widget class whose startup mode decides. */
	public static final String WIDGET = "Canvas"; //$NON-NLS-1$

	private static boolean m3FailedWithErrors = false;

	private final Supplier<WidgetMode> selector;
	private final Function<Canvas, IExternalCanvasHandler> raster, gpu;

	/** The shipped factory (ServiceLoader, Display registration): the startup mode of <code>Canvas</code>, the M3 handlers. */
	public M3CanvasFactory() {
		this(() -> WidgetMode.of(WIDGET, WidgetMode.M3, WidgetMode.GPU), M3CanvasHandler::raster, M3CanvasHandler::gpu);
	}

	/** The decision with a stubbed selector and handler makers (headless tests). */
	public M3CanvasFactory(Supplier<WidgetMode> selector, Function<Canvas, IExternalCanvasHandler> raster,
			Function<Canvas, IExternalCanvasHandler> gpu) {
		this.selector = selector;
		this.raster = raster;
		this.gpu = gpu;
	}

	/**
	 * The backend a mode and a style select: {@link WidgetMode#GPU}, {@link WidgetMode#M3} (raster) or null for the
	 * stock path (no <code>SWT.M3</code> bit, or a mode that is not drawn through the M3 renderer).
	 */
	public static WidgetMode backend(WidgetMode mode, int style) {
		if ((style & SWT.M3) == 0) {
			return null;
		}
		return mode == WidgetMode.M3 || mode == WidgetMode.GPU ? mode : null;
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
			WidgetMode backend = backend(selector.get(), style);
			if (backend == null) {
				return null;
			}
			return (backend == WidgetMode.GPU ? gpu : raster).apply(c);
		} catch (final Throwable t) {
			// M3 initialization failed; log and disable M3 for all future calls
			Logger.logException(t);
			m3FailedWithErrors = true;
			return null;
		}
	}
}
