/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * Contributors:
 *     SAP SE and others - initial API and implementation
 *     Hitesh Soliwal (Synexia) - adoption into this fork, 2026
 *******************************************************************************/

package org.eclipse.swt.internal.canvasext.raster;

import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Shader;

public class RasterImageAdapter implements IRasterImage {

	public RasterImageAdapter(Image skiaImage) {
		this.skiaImage = skiaImage;
	}

	private final Image skiaImage;

	@Override
	public boolean isClosed() {
		return skiaImage.isClosed();
	}

	@Override
	public int getHeight() {
		return skiaImage.getHeight();
	}

	@Override
	public void close() {
		skiaImage.close();
	}

	@Override
	public int getWidth() {
		return skiaImage.getWidth();
	}

	@Override
	public Image getSkiaImage() {
		return skiaImage;
	}

	@Override
	public Shader makeShader(FilterTileMode repeat) {
		return skiaImage.makeShader(repeat);
	}

}
