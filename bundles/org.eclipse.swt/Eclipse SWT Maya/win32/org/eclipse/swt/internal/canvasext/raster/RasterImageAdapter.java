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

import org.eclipse.swt.internal.maya.FilterTileMode;
import org.eclipse.swt.internal.maya.Image;
import org.eclipse.swt.internal.maya.Shader;

public class RasterImageAdapter implements IRasterImage {

	public RasterImageAdapter(Image mayaImage) {
		this.mayaImage = mayaImage;
	}

	private final Image mayaImage;

	@Override
	public boolean isClosed() {
		return mayaImage.isClosed();
	}

	@Override
	public int getHeight() {
		return mayaImage.getHeight();
	}

	@Override
	public void close() {
		mayaImage.close();
	}

	@Override
	public int getWidth() {
		return mayaImage.getWidth();
	}

	@Override
	public Image getMayaImage() {
		return mayaImage;
	}

	@Override
	public Shader makeShader(FilterTileMode repeat) {
		return mayaImage.makeShader(repeat);
	}

}
