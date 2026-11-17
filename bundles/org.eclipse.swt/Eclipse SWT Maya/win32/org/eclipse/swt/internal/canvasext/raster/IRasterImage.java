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
import org.eclipse.swt.internal.maya.Shader;

public interface IRasterImage extends AutoCloseable {

	boolean isClosed();

	int getHeight();

	int getWidth();

	Shader makeShader(FilterTileMode repeat);

	org.eclipse.swt.internal.maya.Image getMayaImage();

	@Override
	void close();
}
