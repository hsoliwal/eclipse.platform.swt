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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 6)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.render.m3.gc.DpiScalerUtil;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.widgets.Display;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;

/**
 * Leaf 6 fixture: an {@link M3ExternalGC} over the call-logging doubles at a zoom: white on black, a 400 x 300 pixel frame
 * (client area and bounds), Arial at 16 pixels, the default display as the device.
 */
final class M3GcFixture {
	static final int WIDTH = 400, HEIGHT = 300;
	final M3ResourcesDouble resources;
	final M3SurfaceDouble surface;
	final CanvasExtensionDouble ext = new CanvasExtensionDouble();
	final M3ExternalGC gc;

	M3GcFixture(int zoom) {
		resources = new M3ResourcesDouble(zoom);
		resources.setForeground(new Color(null, 255, 255, 255));
		resources.setBackground(new Color(null, 0, 0, 0));
		resources.m3Font = new Font(FontMgr.getDefault().matchFamilyStyle("Arial", FontStyle.NORMAL), 16);
		resources.fontData = new FontData("Arial", 12, SWT.NORMAL);
		surface = new M3SurfaceDouble(WIDTH, HEIGHT, "surface", null);
		ext.surface = surface;
		ext.resources = resources;
		ext.clientArea = new Rectangle(0, 0, WIDTH, HEIGHT);
		ext.bounds = ext.clientArea;
		ext.scaler = new DpiScalerUtil(zoom);
		ext.device = Display.getDefault();
		gc = new M3ExternalGC(ext, SWT.NONE);
	}

	/* the calls of one canvas method, in order */
	List<MethodCall> calls(String method) {
		return surface.calls.stream().filter(c -> method.equals(c.methodName)).toList();
	}

	/* the paint of the last call of a canvas method */
	PaintData paint(String method) {
		List<MethodCall> found = calls(method);
		return Arrays.stream(found.get(found.size() - 1).params).filter(PaintData.class::isInstance).map(PaintData.class::cast)
				.findFirst().orElseThrow();
	}
}
