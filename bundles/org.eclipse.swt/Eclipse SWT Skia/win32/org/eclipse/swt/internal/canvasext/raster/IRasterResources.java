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

public interface IRasterResources {
	void setBackground(org.eclipse.swt.graphics.Color color);
	void setForeground(org.eclipse.swt.graphics.Color color);
	org.eclipse.swt.graphics.Color getForeground();
	org.eclipse.swt.graphics.Color getBackground();
	void setFont(org.eclipse.swt.graphics.Font font);
	io.github.humbleui.skija.Font getSkiaFont();
	org.eclipse.swt.internal.canvasext.IDpiScaler getScaler();
	org.eclipse.swt.graphics.Font getFont();
	org.eclipse.swt.graphics.FontData getFontData();
	void resetBaseColors();
	void cacheTextImage(String text, org.eclipse.swt.internal.canvasext.FontProperties fontProperties, boolean transparent, int background, int foreground, boolean antiAlias, IRasterImage image);
	IRasterImage getTextImage(String text, org.eclipse.swt.internal.canvasext.FontProperties fontProperties, boolean transparent, int background, int foreground, boolean antialias);
	String[] getTextSplits(String inputText, int flags);
	org.eclipse.swt.graphics.Point textExtent(String text, int flags);
	org.eclipse.swt.graphics.Point textExtent(String string);
}