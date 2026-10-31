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
package org.eclipse.swt.internal.canvasext;

import java.util.function.*;

import org.eclipse.swt.widgets.*;

/**
 * Interface with features necessary for external canvas handling.
 */
public interface IExternalCanvasHandler {

	Object paint(Consumer<Event> consumer, long wParam, long lParam);

	/**
	 * M3 (BACKGROUND_PARITY v1, census L16): the paint with the widget's stock background chain, which a handler fills its
	 * frame with instead of a flat colour; the default ignores it.
	 */
	default Object paint(Consumer<Event> consumer, M3BackgroundChain background, long wParam, long lParam) {
		return paint(consumer, wParam, lParam);
	}

	void redrawTriggered();

	void redrawTriggered(int x, int y, int width, int height, boolean all);

	void scroll(int destX, int destY, int x, int y, int width, int height, boolean all);

	/** M3 (MODE_TRUTH v1, census L10): whether frames are drawn on the graphics hardware (M3Mode answers gpu, else m3). */
	default boolean isGpu() {
		return false;
	}

	/** M3 (BACKEND_REPORT v1): the handler's name for reports (M3Mode.backend), for example M3CanvasHandler(raster). */
	default String backend() {
		return getClass().getSimpleName();
	}

}
