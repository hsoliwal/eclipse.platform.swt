/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.cocoa;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.cocoa.CALayer;
import org.eclipse.swt.internal.cocoa.CATransaction;
import org.eclipse.swt.internal.cocoa.NSColor;
import org.eclipse.swt.internal.cocoa.NSObject;
import org.eclipse.swt.internal.cocoa.NSPoint;
import org.eclipse.swt.internal.cocoa.NSRect;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Native bridge round trips; these do not qualify a viewport compositor. */
@SuppressWarnings("restriction")
public class Test_cocoa_CoreAnimation {

	private Shell shell;

	@BeforeEach
	public void setUp() {
		shell = new Shell(Display.getDefault());
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	@Test
	public void test_layerBackingPreservesCanvasAndNativeChild() {
		Canvas canvas = new Canvas(shell, SWT.BORDER);
		canvas.setBounds(10, 10, 140, 90);
		Button child = new Button(canvas, SWT.PUSH);
		child.setBounds(5, 5, 60, 25);
		shell.open();
		long view = canvas.view.id;
		long childView = child.view.id;
		Rectangle bounds = canvas.getBounds();
		Rectangle client = canvas.getClientArea();
		Rectangle childBounds = child.getBounds();
		boolean original = canvas.view.wantsLayer();
		try {
			canvas.view.setWantsLayer(true);
			assertTrue(canvas.view.wantsLayer());
			assertNotNull(canvas.view.layer());
			assertEquals(view, canvas.view.id);
			assertEquals(childView, child.view.id);
			assertSame(canvas, child.getParent());
			assertEquals(bounds, canvas.getBounds());
			assertEquals(client, canvas.getClientArea());
			assertEquals(childBounds, child.getBounds());
		} finally {
			canvas.view.setWantsLayer(original);
		}
		assertEquals(original, canvas.view.wantsLayer());
	}

	@Test
	public void test_layerGeometryAndDecorationRoundTrip() {
		CALayer layer = newLayer();
		CATransaction.begin();
		try {
			CATransaction.setDisableActions(true);
			NSRect bounds = rect(1.25, 2.5, 30.75, 40.5);
			layer.setBounds(bounds);
			assertRect(bounds, layer.bounds());
			NSPoint anchor = point(0.25, 0.75);
			layer.setAnchorPoint(anchor);
			assertPoint(anchor, layer.anchorPoint());
			NSPoint position = point(15.5, 26.25);
			layer.setPosition(position);
			assertPoint(position, layer.position());
			layer.setFrame(rect(3.5, 5.25, 60.5, 80.25));
			assertRect(rect(3.5, 5.25, 60.5, 80.25), layer.frame());
			layer.setZPosition(-2.75);
			assertEquals(-2.75, layer.zPosition(), 0.0);
			layer.setContentsScale(2.0);
			assertEquals(2.0, layer.contentsScale(), 0.0);
			layer.setBorderWidth(1.5);
			layer.setCornerRadius(4.25);
			assertEquals(1.5, layer.borderWidth(), 0.0);
			assertEquals(4.25, layer.cornerRadius(), 0.0);
			NSColor color = NSColor.separatorColor();
			assertNotNull(color);
			long nativeColor = color.CGColor();
			assertNotEquals(0, nativeColor);
			layer.setBorderColor(nativeColor);
			assertNotEquals(0, layer.borderColor());
			layer.setBorderColor(0);
			assertEquals(0, layer.borderColor());
			layer.setMasksToBounds(true);
			assertTrue(layer.masksToBounds());
			layer.setMasksToBounds(false);
			assertFalse(layer.masksToBounds());
			layer.setHidden(true);
			assertTrue(layer.isHidden());
			layer.setHidden(false);
			assertFalse(layer.isHidden());
		} finally {
			CATransaction.commit();
			layer.release();
		}
	}

	@Test
	public void test_layerRetainsImageAndReleasesSublayerAndMask() {
		CALayer layer = newLayer();
		CALayer child = newLayer();
		Image image = new Image(shell.getDisplay(), 4, 4);
		CATransaction.begin();
		try {
			CATransaction.setDisableActions(true);
			layer.setContents(image.handle);
			assertNotNull(layer.contents());
			image.dispose();
			assertNotNull(layer.contents());
			layer.setContents(null);
			assertNull(layer.contents());
			layer.addSublayer(child);
			assertEquals(layer.id, child.superlayer().id);
			assertEquals(1, layer.sublayers().count());
			assertEquals(child.id, layer.sublayers().objectAtIndex(0).id);
			child.removeFromSuperlayer();
			assertNull(child.superlayer());
			assertTrue(layer.sublayers() == null || layer.sublayers().count() == 0);
			layer.setMask(child);
			assertEquals(child.id, layer.mask().id);
			layer.setMask(null);
			assertNull(layer.mask());
		} finally {
			layer.setContents(null);
			layer.setMask(null);
			child.removeFromSuperlayer();
			CATransaction.commit();
			image.dispose();
			child.release();
			layer.release();
		}
	}

	@Test
	public void test_nestedTransactionsRestoreParentSettings() {
		CATransaction.begin();
		try {
			CATransaction.setDisableActions(true);
			CATransaction.setAnimationDuration(0.125);
			CATransaction.begin();
			try {
				CATransaction.setDisableActions(false);
				CATransaction.setAnimationDuration(0.375);
				assertFalse(CATransaction.disableActions());
				assertEquals(0.375, CATransaction.animationDuration(), 0.0);
			} finally {
				CATransaction.commit();
			}
			assertTrue(CATransaction.disableActions());
			assertEquals(0.125, CATransaction.animationDuration(), 0.0);
		} finally {
			CATransaction.commit();
		}
	}

	private static CALayer newLayer() {
		NSObject allocated = new CALayer().alloc();
		assertNotEquals(0, allocated.id);
		NSObject initialized = allocated.init();
		assertNotNull(initialized);
		return new CALayer(initialized);
	}

	private static NSPoint point(double x, double y) {
		NSPoint point = new NSPoint();
		point.x = x;
		point.y = y;
		return point;
	}

	private static NSRect rect(double x, double y, double width, double height) {
		NSRect rect = new NSRect();
		rect.x = x;
		rect.y = y;
		rect.width = width;
		rect.height = height;
		return rect;
	}

	private static void assertPoint(NSPoint expected, NSPoint actual) {
		assertEquals(expected.x, actual.x, 0.0);
		assertEquals(expected.y, actual.y, 0.0);
	}

	private static void assertRect(NSRect expected, NSRect actual) {
		assertEquals(expected.x, actual.x, 0.0);
		assertEquals(expected.y, actual.y, 0.0);
		assertEquals(expected.width, actual.width, 0.0);
		assertEquals(expected.height, actual.height, 0.0);
	}
}
