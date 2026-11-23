/*******************************************************************************
 * Copyright (c) 2000, 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Hitesh Soliwal (Synexia) - GTK scroll damage and pixel regression tests
 *******************************************************************************/
package org.eclipse.swt.tests.junit;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Caret;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Automated Test Suite for class org.eclipse.swt.widgets.Canvas
 *
 * @see org.eclipse.swt.widgets.Canvas
 */
public class Test_org_eclipse_swt_widgets_Canvas extends Test_org_eclipse_swt_widgets_Composite {

Canvas canvas;

@Override
@BeforeEach
public void setUp() {
	super.setUp();
	canvas = new Canvas(shell, 0);
	super.setWidget(canvas);
}

@Override
protected void setWidget(Widget w) {
    if (!canvas.isDisposed()) {
        canvas.dispose();
    }
	canvas = (Canvas)w;
	super.setWidget(w);
}

@Override
@Test
public void test_ConstructorLorg_eclipse_swt_widgets_CompositeI() {
	assertThrows(IllegalArgumentException.class, () -> new Canvas(null, SWT.NONE), "No exception thrown for parent == null");
}

@Test
public void test_scrollIIIIIIZ() {
	canvas.scroll(100, 100, 0, 0, 50, 50, false);
	canvas.scroll(100, 100, 0, 0, 50, 50, true);

	canvas.scroll(10000, 10000, 100, 100, 500, 500, false);
	canvas.scroll(10000, 10000, 100, 100, 500, 500, true);

	canvas.scroll(-100, -100, 10, 10, 30, 30, false);
	canvas.scroll(-100, -100, 10, 10, 30, 30, true);

	canvas.scroll(10, 10, -200, -200, 100, 100, false);
	canvas.scroll(10, 10, -200, -200, 100, 100, true);

	canvas.scroll(100, 100, 50, 50, -50, -50, false);
	canvas.scroll(100, 100, 50, 50, -50, -50, true);
}

@Test
public void test_scroll_withoutOverlayRepaintsOnlyTheExposedBandAndPreservesPixels() throws InterruptedException {
	assumeTrue(SwtTestUtil.isGTK3(), "GTK3 scroll damage regression");
	assertScrollDamageAndPixels(SWT.NONE, false);
	assertScrollDamageAndPixels(SWT.H_SCROLL | SWT.V_SCROLL, false);
}

@Test
public void test_scroll_withOverlayRetainsTheFullRedrawWorkaround() throws InterruptedException {
	assumeTrue(SwtTestUtil.isGTK3(), "GTK3 overlay-scrollbar regression");
	assertScrollDamageAndPixels(SWT.H_SCROLL | SWT.V_SCROLL, true);
}

private void assertScrollDamageAndPixels(int style, boolean overlay) throws InterruptedException {
	setWidget(new Canvas(shell, style));
	if ((style & (SWT.H_SCROLL | SWT.V_SCROLL)) != 0) {
		canvas.setScrollbarsMode(overlay ? SWT.SCROLLBAR_OVERLAY : SWT.NONE);
		if (overlay) {
			assumeTrue((canvas.getScrollbarsMode() & SWT.SCROLLBAR_OVERLAY) != 0,
					"GTK_OVERLAY_SCROLLING=0 disables the overlay case");
		} else {
			assertEquals(SWT.NONE, canvas.getScrollbarsMode());
		}
	}
	canvas.setBounds(10, 10, 160, 128);
	Color[] colors = {
		canvas.getDisplay().getSystemColor(SWT.COLOR_RED),
		canvas.getDisplay().getSystemColor(SWT.COLOR_GREEN),
		canvas.getDisplay().getSystemColor(SWT.COLOR_BLUE),
		canvas.getDisplay().getSystemColor(SWT.COLOR_YELLOW),
		canvas.getDisplay().getSystemColor(SWT.COLOR_CYAN)
	};
	int[] contentOffset = {0};
	List<Rectangle> painted = new ArrayList<>();
	canvas.addPaintListener(event -> {
		painted.add(new Rectangle(event.x, event.y, event.width, event.height));
		for (int y = event.y; y < event.y + event.height; y++) {
			event.gc.setBackground(colors[Math.floorMod(y + contentOffset[0], colors.length)]);
			event.gc.fillRectangle(event.x, y, event.width, 1);
		}
	});
	shell.open();
	canvas.redraw();
	SwtTestUtil.processEvents(200, null);
	assertScrollPixels(contentOffset[0], colors, overlay);
	Rectangle client = canvas.getClientArea();
	for (int deltaY : new int[] {-24, 24}) {
		painted.clear();
		contentOffset[0] -= deltaY;
		canvas.scroll(0, deltaY, 0, 0, client.width, client.height, false);
		canvas.update();
		SwtTestUtil.processEvents(200, null);
		assertFalse(painted.isEmpty(), "the newly exposed band must be painted");
		if (overlay) {
			assertTrue(painted.stream().anyMatch(r -> r.y == 0 && r.height == client.height),
					"overlay-scrollbar artifacts still require the full redraw: " + painted);
		} else {
			int bandTop = deltaY < 0 ? client.height + deltaY : 0;
			for (Rectangle damage : painted) {
				assertTrue(damage.y >= bandTop && damage.y + damage.height <= bandTop + Math.abs(deltaY),
						"scroll without overlays must repaint only the exposed band: " + painted);
			}
		}
		assertScrollPixels(contentOffset[0], colors, overlay);
	}
}

private void assertScrollPixels(int contentOffset, Color[] colors, boolean overlay) {
	Rectangle client = canvas.getClientArea();
	Image capture = new Image(canvas.getDisplay(), client.width, client.height);
	ImageData pixels;
	GC gc = new GC(canvas);
	try {
		gc.copyArea(capture, 0, 0);
		pixels = capture.getImageData();
	} finally {
		gc.dispose();
		capture.dispose();
	}
	// Overlay thumbs are native widgets drawn over the edge pixels. Their
	// workaround is asserted above; validate the unobscured content here.
	int minX = overlay ? pixels.width / 4 : 0;
	int maxX = overlay ? pixels.width * 3 / 4 : pixels.width;
	int maxY = overlay ? pixels.height / 2 : pixels.height;
	for (int y = 0; y < maxY; y++) {
		RGB expected = colors[Math.floorMod(y + contentOffset, colors.length)].getRGB();
		for (int x = minX; x < maxX; x++) {
			assertEquals(expected, pixels.palette.getRGB(pixels.getPixel(x, y)),
					"scrolled pixel at " + x + "," + y);
		}
	}
}

@Test
public void test_setCaretLorg_eclipse_swt_widgets_Caret() {
	int number = 5;
	Caret[] carets = new Caret[number];
	for (int i = 0; i < number; i++) {
		carets[i] = new Caret(canvas, SWT.NONE);
	}
	for (int i = 0; i < number; i++) {
		canvas.setCaret(carets[i]);
		assertEquals(canvas.getCaret(), carets[i]);
	}

	canvas.setCaret(null);
	assertNull(canvas.getCaret());
}

@Override
@Test
public void test_setFontLorg_eclipse_swt_graphics_Font() {
	FontData fontData = canvas.getFont().getFontData()[0];
	Font font = new Font(canvas.getDisplay(), fontData.getName(), 8, fontData.getStyle());
	canvas.setFont(font);
	assertEquals(font, canvas.getFont());
	canvas.setFont(null);
	font.dispose();
}

@Test
public void test_CaretWithDisposedFontDoesNotThrowException_issue2323() {
	Caret caret = new Caret(canvas, SWT.NONE);
	Font font = new Font(canvas.getDisplay(), "Default", 10, SWT.BOLD);
	shell.open();
	caret.setFont(font);
	font.dispose();
	canvas.setFocus();
	canvas.setCaret(caret);
}

/* custom*/
@Test
public void test_consistency_MenuDetect() {
	consistencyEvent(10, 10, 3, 0, ConsistencyUtility.MOUSE_CLICK);
}

@Test
public void test_consistency_DragDetect() {
	consistencyEvent(10, 10, 20, 20, ConsistencyUtility.MOUSE_DRAG);
}

}
