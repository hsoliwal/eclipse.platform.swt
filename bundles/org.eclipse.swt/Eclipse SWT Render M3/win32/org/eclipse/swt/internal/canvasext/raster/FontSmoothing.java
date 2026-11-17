/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext.raster;

import org.eclipse.swt.internal.win32.OS;

import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.SurfaceProps;

/**
 * The system's text smoothing for the Skia surfaces, so that drawn text is smoothed as native text is: LCD (ClearType)
 * with the screen's subpixel order when ClearType is on, grayscale otherwise.
 * <p>
 * Read on each call (three <code>SystemParametersInfo</code> calls), so a surface made after the user changed the setting
 * follows it.
 * </p>
 */
public final class FontSmoothing {

	static final int SPI_GETFONTSMOOTHING = 0x004A;
	static final int SPI_GETFONTSMOOTHINGORIENTATION = 0x2012;
	static final int FE_FONTSMOOTHINGORIENTATIONBGR = 0x0000;

	private FontSmoothing() {
	}

	/**
	 * Returns the pixel geometry of text smoothing for the given system values.
	 *
	 * @param smoothing whether font smoothing is on (<code>SPI_GETFONTSMOOTHING</code>)
	 * @param type the smoothing type (<code>SPI_GETFONTSMOOTHINGTYPE</code>)
	 * @param orientation the subpixel orientation (<code>SPI_GETFONTSMOOTHINGORIENTATION</code>)
	 * @return {@link PixelGeometry#RGB_H} or {@link PixelGeometry#BGR_H} for ClearType, {@link PixelGeometry#UNKNOWN}
	 *         (grayscale text) otherwise
	 */
	public static PixelGeometry pixelGeometry(boolean smoothing, int type, int orientation) {
		if (!smoothing || type != OS.FE_FONTSMOOTHINGCLEARTYPE) {
			return PixelGeometry.UNKNOWN;
		}
		return orientation == FE_FONTSMOOTHINGORIENTATIONBGR ? PixelGeometry.BGR_H : PixelGeometry.RGB_H;
	}

	/**
	 * Returns the pixel geometry of the system's text smoothing now.
	 *
	 * @return the pixel geometry
	 */
	public static PixelGeometry pixelGeometry() {
		final int[] value = new int[1];
		final boolean smoothing = OS.SystemParametersInfo(SPI_GETFONTSMOOTHING, 0, value, 0) && value[0] != 0;
		final int type = OS.SystemParametersInfo(OS.SPI_GETFONTSMOOTHINGTYPE, 0, value, 0) ? value[0] : 0;
		final int orientation = OS.SystemParametersInfo(SPI_GETFONTSMOOTHINGORIENTATION, 0, value, 0) ? value[0] : 1;
		return pixelGeometry(smoothing, type, orientation);
	}

	/**
	 * Returns the surface properties for the system's text smoothing now.
	 *
	 * @return the surface properties
	 */
	public static SurfaceProps surfaceProps() {
		return new SurfaceProps(false, pixelGeometry());
	}
}
