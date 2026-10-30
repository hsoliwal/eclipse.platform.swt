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

import org.eclipse.swt.internal.canvasext.*;


import org.eclipse.swt.widgets.Canvas;

/**
 * Provides a factory for creating Skia-based canvas extensions. This factory is
 * used by org.eclipse.swt.internal.canvasext.ExternalCanvasHandler with the
 * resource loader to create canvas extensions when the SKIA style is specified.
 */
public class M3CanvasFactory implements IExternalCanvasFactory {

	private static boolean m3FailedWithErrors = false;

	@Override
	public IExternalCanvasHandler createCanvasExtension(Canvas c) {

		if (m3FailedWithErrors) {
			return null;
		}

		try {
			/* the M3 planes own their surfaces (M3PlaneCanvasExtension, leaf 3); a per-Canvas handler is a later leaf:
			 * I31's SkiaGlCanvasExtension (GL context and surface per Canvas) is PATTERN_ONLY, see FOSS_REUSE_DECISION.tsv */
			return null;
		} catch (final Throwable t) {
			// Skia initialization failed; log and disable Skia for all future calls
			Logger.logException(t);
			m3FailedWithErrors = true;
			return null;
		}
	}
}
