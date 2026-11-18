/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.BorderPainter;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.Test;

/** Pixel and resource-state contract for the SWT native-GC border painter. */
public class Test_org_eclipse_swt_graphics_BorderPainter {
	private static final int SIZE = 24;

	private static RGB colorAt(ImageData data, int x, int y) {
		int px = (int) ((long) x * data.width / SIZE);
		int py = (int) ((long) y * data.height / SIZE);
		return data.palette.getRGB(data.getPixel(px, py));
	}

	private static ImageData draw(Display display, int style, int depth) {
		Image image = new Image(display, SIZE, SIZE);
		try {
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
				gc.fillRectangle(0, 0, SIZE, SIZE);
				Color before = display.getSystemColor(SWT.COLOR_BLUE);
				gc.setBackground(before);
				int alpha = gc.getAlpha();
				Rectangle clip = gc.getClipping();
				BorderPainter.draw(gc, new Rectangle(4, 4, 16, 16), style, depth);
				assertEquals(before.getRGB(), gc.getBackground().getRGB());
				assertEquals(alpha, gc.getAlpha());
				assertEquals(clip, gc.getClipping());
			} finally {
				gc.dispose();
			}
			return image.getImageData();
		} finally {
			image.dispose();
		}
	}

	@Test
	public void testInsetOutsetUseOppositeNativeThemeEdges() {
		Display display = Display.getDefault();
		ImageData raised = draw(display, SWT.SHADOW_OUT, 2);
		ImageData lowered = draw(display, SWT.SHADOW_IN, 2);
		RGB light = display.getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW).getRGB();
		RGB dark = display.getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW).getRGB();
		assertEquals(light, colorAt(raised, 12, 4));
		assertEquals(dark, colorAt(raised, 12, 19));
		assertEquals(dark, colorAt(lowered, 12, 4));
		assertEquals(light, colorAt(lowered, 12, 19));
		assertEquals(new RGB(255, 255, 255), colorAt(raised, 12, 12));
		assertEquals(new RGB(255, 255, 255), colorAt(lowered, 12, 12));
		assertEquals(new RGB(255, 255, 255), colorAt(raised, 2, 2));
	}

	@Test
	public void testEtchedStylesReverseBothNestedBands() {
		Display display = Display.getDefault();
		ImageData in = draw(display, SWT.SHADOW_ETCHED_IN, 2);
		ImageData out = draw(display, SWT.SHADOW_ETCHED_OUT, 2);
		assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW).getRGB(), colorAt(in, 12, 4));
		assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW).getRGB(), colorAt(in, 12, 5));
		assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW).getRGB(), colorAt(out, 12, 4));
		assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW).getRGB(), colorAt(out, 12, 5));
	}

	@Test
	public void testNoOpClippedSmallAndInvalidContracts() {
		Display display = Display.getDefault();
		ImageData empty = draw(display, SWT.NONE, 20);
		assertEquals(new RGB(255, 255, 255), colorAt(empty, 12, 4));
		ImageData thin = draw(display, SWT.SHADOW_IN, 1000);
		assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW).getRGB(), colorAt(thin, 12, 4));
		Image image = new Image(display, 10, 10);
		try {
			GC gc = new GC(image);
			try {
				assertThrows(IllegalArgumentException.class, () -> BorderPainter.draw(null, 0, 0, 10, 10, SWT.SHADOW_OUT, 1));
				assertThrows(IllegalArgumentException.class, () -> BorderPainter.draw(gc, (Rectangle) null, SWT.SHADOW_OUT, 1));
				assertThrows(IllegalArgumentException.class, () -> BorderPainter.draw(gc, 0, 0, 10, 10, SWT.BORDER, 1));
				assertThrows(IllegalArgumentException.class, () -> BorderPainter.draw(gc, 0, 0, 10, 10, SWT.SHADOW_IN, -1));
				assertThrows(IllegalArgumentException.class, () -> BorderPainter.draw(gc, Integer.MAX_VALUE, 0, 10, 10, SWT.SHADOW_OUT, 1));
				assertThrows(IllegalArgumentException.class, () -> BorderPainter.draw(gc, 0, 0, -1, 10, SWT.SHADOW_OUT, 1));
			} finally {
				gc.dispose();
			}
			assertThrows(SWTException.class, () -> BorderPainter.draw(gc, 0, 0, 10, 10, SWT.SHADOW_OUT, 1));
		} finally {
			image.dispose();
		}
	}
}
