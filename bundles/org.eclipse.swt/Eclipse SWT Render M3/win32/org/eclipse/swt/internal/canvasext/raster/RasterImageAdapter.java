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

	public RasterImageAdapter(Image m3Image) {
		this.m3Image = m3Image;
	}

	private final Image m3Image;

	@Override
	public boolean isClosed() {
		return m3Image.isClosed();
	}

	@Override
	public int getHeight() {
		return m3Image.getHeight();
	}

	@Override
	public void close() {
		m3Image.close();
	}

	@Override
	public int getWidth() {
		return m3Image.getWidth();
	}

	@Override
	public Image getM3Image() {
		return m3Image;
	}

	@Override
	public Shader makeShader(FilterTileMode repeat) {
		return m3Image.makeShader(repeat);
	}

}
