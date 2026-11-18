/*******************************************************************************
 * Copyright (c) 2026 Vector Informatik GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Vector Informatik GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.win32.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Automated Tests for class org.eclipse.swt.widgets.Composite
 * for Windows specific behavior
 *
 * @see org.eclipse.swt.widgets.Composite
 */
@SuppressWarnings("restriction")
public class Test_org_eclipse_swt_widgets_Composite {

	private static Display display;
	private Shell shell;

	@BeforeAll
	public static void setUpClass() {
		display = Display.getDefault();
	}

	@AfterAll
	public static void tearDownClass() {
		display.dispose();
	}

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
	}

	@AfterEach
	public void tearDown() {
		display.dispose();
		shell.dispose();
	}

	@Test
	public void test_parentStaysCompositedWhileTransparentChildrenExist() {
		Composite first = new Composite(shell, SWT.TRANSPARENT);
		Composite second = new Composite(shell, SWT.TRANSPARENT);
		assertTrue(isComposited(shell), "parent must be composited for its transparent children");

		first.dispose();
		assertTrue(isComposited(shell), "parent must stay composited for the remaining transparent child");

		second.dispose();
		assertFalse(isComposited(shell), "parent must not stay composited without transparent children");
	}

	@Test
	public void test_parentStopsBeingCompositedWithOpaqueChildrenOnly() {
		Composite transparent = new Composite(shell, SWT.TRANSPARENT);
		new Composite(shell, SWT.NONE);
		assertTrue(isComposited(shell), "parent must be composited for its transparent child");

		transparent.dispose();
		assertFalse(isComposited(shell), "an opaque child must not keep the parent composited");
	}

	/**
	 * A direct Composite/Canvas owns the DrawEdge non-client painter. Raised
	 * Control relief must retain CLIENTEDGE rather than selecting WINDOWEDGE,
	 * which would bypass that painter. The saved #532 bevel precedence remains.
	 */
	@Test
	public void test_nativeOutsetKeepsDirectClientEdgeAndRestoresSavedBevel() {
		Composite[] controls = {
				new Composite(shell, SWT.BORDER),
				new Canvas(shell, SWT.BORDER)
		};
		for (Composite control : controls) {
			try {
				control.setSize(120, 64);
				int initialWidth = control.getBorderWidth();
				int initialExStyle = OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE);
				assertTrue(initialWidth > 0);
				control.setBevelBorder(SWT.SHADOW_IN);
				control.setBorderRelief(SWT.SHADOW_OUT);
				int raisedExStyle = OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE);
				assertTrue((raisedExStyle & OS.WS_EX_CLIENTEDGE) != 0,
						"Direct Composite/Canvas DrawEdge needs CLIENTEDGE native trim");
				assertEquals(0, raisedExStyle & OS.WS_EX_WINDOWEDGE,
						"Direct GDI edge must not be replaced by the WINDOWEDGE frame");
				assertEquals(SWT.SHADOW_IN, control.getBevelBorder(),
						"Control override must not destroy the saved Composite bevel");
				assertEquals(SWT.SHADOW_OUT, control.getBorderRelief());
				assertEquals(initialWidth, control.getBorderWidth());
				control.setBorderRelief(SWT.DEFAULT);
				assertEquals(SWT.SHADOW_IN, control.getBevelBorder(),
						"DEFAULT restores the saved #532 Composite border");
				control.setBevelBorder(SWT.NONE);
				assertEquals(initialWidth, control.getBorderWidth());
				int edgeMask = OS.WS_EX_CLIENTEDGE | OS.WS_EX_STATICEDGE | OS.WS_EX_WINDOWEDGE;
				assertEquals(initialExStyle & edgeMask,
						OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE) & edgeMask);
			} finally {
				control.dispose();
			}
		}
	}

	@Test
	public void test_nativeEtchedTransitionsPreserveFrameAndBevelPrecedence() {
		Composite control = new Composite(shell, SWT.NONE);
		try {
			control.setSize(100, 50);
			control.setBorderRelief(SWT.SHADOW_ETCHED_IN);
			int frameBefore = OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE);
			assertTrue((frameBefore & OS.WS_EX_CLIENTEDGE) != 0);
			control.setBorderRelief(SWT.SHADOW_ETCHED_OUT);
			assertEquals(frameBefore & (OS.WS_EX_CLIENTEDGE | OS.WS_EX_STATICEDGE),
					OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE)
							& (OS.WS_EX_CLIENTEDGE | OS.WS_EX_STATICEDGE),
					"Both etched styles share frame bits; the non-client pixels still require repaint");
			assertEquals(SWT.SHADOW_ETCHED_OUT, control.getBorderRelief());
			control.setBevelBorder(SWT.SHADOW_OUT);
			assertEquals(SWT.DEFAULT, control.getBorderRelief(),
					"Explicit Composite setter is the latest writer");
			assertEquals(SWT.SHADOW_OUT, control.getBevelBorder());
			control.setBorderRelief(SWT.SHADOW_IN);
			control.setBorderRelief(SWT.DEFAULT);
			assertEquals(SWT.SHADOW_OUT, control.getBevelBorder(),
					"Reset restores the last explicitly selected Composite bevel");
		} finally {
			control.dispose();
		}
	}

	@Test
	public void test_nativeOtherControlOutsetKeepsWindowEdge() {
		Button button = new Button(shell, SWT.PUSH);
		try {
			button.setSize(96, 36);
			int originalExStyle = OS.GetWindowLong(button.handle, OS.GWL_EXSTYLE);
			button.setBorderRelief(SWT.SHADOW_OUT);
			int raisedExStyle = OS.GetWindowLong(button.handle, OS.GWL_EXSTYLE);
			assertTrue((raisedExStyle & OS.WS_EX_WINDOWEDGE) != 0,
					"Non-Composite HWND must retain its original WINDOWEDGE path");
			assertEquals(0, raisedExStyle & OS.WS_EX_CLIENTEDGE,
					"Only exact direct Composite/Canvas use the GDI CLIENTEDGE painter");
			button.setBorderRelief(SWT.DEFAULT);
			int edgeMask = OS.WS_EX_CLIENTEDGE | OS.WS_EX_STATICEDGE | OS.WS_EX_WINDOWEDGE;
			assertEquals(originalExStyle & edgeMask,
					OS.GetWindowLong(button.handle, OS.GWL_EXSTYLE) & edgeMask);
		} finally {
			button.dispose();
		}
	}

	private static boolean isComposited(Control control) {
		return (OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE) & OS.WS_EX_COMPOSITED) != 0;
	}

	/**
	 * A canvas that prints itself must use a font scaled for its own zoom. The
	 * device zoom is not a valid substitute: with monitor-specific scaling it
	 * reflects the zoom of the shell whose monitor scaling has changed most
	 * recently, which is a different one as soon as the shells of an application
	 * are spread over monitors with different scale factors.
	 */
	@Test
	public void test_printClientUsesFontOfControlZoom() {
		Shell shell = new Shell(display);
		int deviceZoom = DPIUtil.getNativeDeviceZoom();
		try {
			Canvas canvas = new Canvas(shell, SWT.NONE);
			canvas.setSize(100, 50);
			FontData[] printedFontData = new FontData[1];
			canvas.addPaintListener(event -> printedFontData[0] = event.gc.getFont().getFontData()[0]);
			FontData expectedFontData = canvas.getFont().getFontData()[0];

			Image image = new Image(display, canvas.getSize().x, canvas.getSize().y);
			GC gc = new GC(image);
			try {
				// let the device zoom deviate from the zoom of the canvas, as it does
				// when another shell resides on a monitor with a different scale factor
				DPIUtil.setDeviceZoom(deviceZoom * 2);
				OS.SendMessage(canvas.handle, OS.WM_PRINT, gc.handle, OS.PRF_CLIENT);
			} finally {
				DPIUtil.setDeviceZoom(deviceZoom);
				gc.dispose();
				image.dispose();
			}

			assertNotNull(printedFontData[0], "canvas has not been painted on WM_PRINT");
			assertEquals(expectedFontData.getHeight(), printedFontData[0].getHeight(),
					"printed canvas must use the font for the zoom of the canvas");
		} finally {
			shell.dispose();
		}
	}

}
