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
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.RECT;
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


	/**
	 * Control's etched GDI painter shares one native frame kernel between
	 * WM_NCPAINT and WM_PRINT. Small native frames must not spill pixels
	 * beyond the HWND or permanently modify the caller's clipping region.
	 */
	@Test
	public void test_nativeControlEtchedTinyFramePrintKeepsCallerHdc() {
		Button button = new Button(shell, SWT.PUSH);
		try {
			button.setBounds(10, 10, 96, 32);
			shell.setSize(240, 160);
			shell.open();
			button.setBorderRelief(SWT.SHADOW_ETCHED_IN);
			assertEquals(SWT.SHADOW_ETCHED_IN, button.getBorderRelief());
			Image image = new Image(display, 144, 96);
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));
				for (int extent : new int[] {0, 1, 2, 3, 4}) {
					button.setSize(extent, extent);
					gc.fillRectangle(image.getBounds());
					int outsideBefore = OS.GetPixel(gc.handle, 80, 60);
					assertTrue(outsideBefore != -1);
					Region before = new Region(display);
					Region after = new Region(display);
					try {
						gc.getClipping(before);
						assertTrue(before.contains(80, 60));
						OS.SendMessage(button.handle, OS.WM_PRINT, gc.handle, OS.PRF_NONCLIENT);
						gc.getClipping(after);
						assertTrue(after.contains(80, 60),
								"Etched WM_PRINT must restore the caller's clipping region");
						assertEquals(outsideBefore, OS.GetPixel(gc.handle, 80, 60),
								"Custom edge must not write outside the native frame");
					} finally {
						after.dispose();
						before.dispose();
					}
				}
			} finally {
				gc.dispose();
				image.dispose();
			}
		} finally {
			button.dispose();
		}
	}

	/**
	 * WM_PRINT receives a caller HDC, not a window DC. Verify actual
	 * GDI border pixels, no client-pixel damage and a restored caller clip.
	 */
	@Test
	public void test_nativeReliefPrintingMatchesBevelAndPreservesCallerDC() {
		for (Composite control : new Composite[] {
				new Composite(shell, SWT.BORDER),
				new Canvas(shell, SWT.BORDER) }) {
			try {
				control.setSize(160, 92);
				control.setBevelBorder(SWT.SHADOW_OUT);
				int[] raised = printNativeReliefSamples(control);
				assertTrue(raised[0] > raised[1],
						"Raised WM_PRINT bevel must have a lighter top than bottom");

				control.setBorderRelief(SWT.SHADOW_IN);
				assertEquals(SWT.SHADOW_OUT, control.getBevelBorder(),
						"Control overlay must preserve the saved Composite bevel");
				int[] sunken = printNativeReliefSamples(control);
				assertTrue(sunken[0] < sunken[1],
						"Sunken WM_PRINT bevel must have a darker top than bottom");

				control.setBorderRelief(SWT.DEFAULT);
				assertEquals(SWT.SHADOW_OUT, control.getBevelBorder());
				int[] restored = printNativeReliefSamples(control);
				assertTrue(restored[0] > restored[1],
						"DEFAULT must restore saved raised bevel in WM_PRINT");
				control.setBevelBorder(SWT.NONE);
				assertEquals(SWT.NONE, control.getBevelBorder());
				assertEquals(SWT.DEFAULT, control.getBorderRelief());
			} finally {
				control.dispose();
			}
		}
	}

	private static int[] printNativeReliefSamples(Composite control) {
		RECT rect = new RECT();
		assertTrue(OS.GetWindowRect(control.handle, rect));
		int width = rect.right - rect.left, height = rect.bottom - rect.top;
		assertTrue(width > 8 && height > 8);
		Image image = new Image(display, width + 4, height + 4);
		GC gc = new GC(image);
		try {
			gc.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));
			gc.fillRectangle(image.getBounds());
			int centerX = width / 2, centerY = height / 2;
			int clientBefore = OS.GetPixel(gc.handle, centerX, centerY);
			assertTrue(clientBefore != -1, "Offscreen GDI client is addressable");
			var clipBefore = gc.getClipping();
			OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle, OS.PRF_NONCLIENT);
			assertEquals(clipBefore, gc.getClipping(), "WM_PRINT changed the caller clip");
			assertEquals(clientBefore, OS.GetPixel(gc.handle, centerX, centerY),
					"Relief must not paint into client pixels");
			return new int[] {
					brightness(OS.GetPixel(gc.handle, centerX, 0)),
					brightness(OS.GetPixel(gc.handle, centerX, height - 1))
			};
		} finally {
			gc.dispose();
			image.dispose();
		}
	}

	private static int brightness(int colorRef) {
		assertTrue(colorRef != -1, "Border samples must lie inside the HDC");
		return (colorRef & 0xff) + ((colorRef >>> 8) & 0xff) + ((colorRef >>> 16) & 0xff);
	}


	/**
	 * PRF_CHECKVISIBLE must suppress the custom native bevel for a hidden
	 * HWND, just as it suppresses the default Windows non-client paint.
	 */
	@Test
	public void test_nativeReliefPrintingRespectsPRFCheckVisible() {
		Composite control = new Composite(shell, SWT.BORDER);
		try {
			control.setBounds(12, 12, 120, 68);
			shell.setSize(260, 180);
			shell.open();
			control.setBevelBorder(SWT.SHADOW_OUT);
			assertTrue(OS.IsWindowVisible(control.handle));
			Image image = new Image(display, 144, 80);
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));
				gc.fillRectangle(image.getBounds());
				int sentinel = OS.GetPixel(gc.handle, 60, 0);
				var originalClip = gc.getClipping();
				assertTrue(sentinel != -1);
				control.setVisible(false);
				assertFalse(OS.IsWindowVisible(control.handle));
				OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle,
						OS.PRF_NONCLIENT | OS.PRF_CHECKVISIBLE);
				assertEquals(sentinel, OS.GetPixel(gc.handle, 60, 0),
						"PRF_CHECKVISIBLE must not paint a hidden native bevel");
				assertEquals(originalClip, gc.getClipping());

				control.setVisible(true);
				assertTrue(OS.IsWindowVisible(control.handle));
				OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle,
						OS.PRF_NONCLIENT | OS.PRF_CHECKVISIBLE);
				assertTrue(OS.GetPixel(gc.handle, 60, 0) != sentinel,
						"The visible native window must print its raised bevel");
				assertEquals(originalClip, gc.getClipping());
			} finally {
				gc.dispose();
				image.dispose();
			}
		} finally {
			control.dispose();
		}
	}

	/**
	 * Even without a custom Composite bevel, SWT's inherited themed
	 * WS_EX_CLIENTEDGE print pass must respect PRF_CHECKVISIBLE.
	 */
	@Test
	public void test_hiddenThemedClientEdgeRespectsPRFCheckVisible() {
		Composite control = new Composite(shell, SWT.BORDER);
		try {
			control.setBounds(12, 12, 120, 68);
			shell.setSize(260, 180);
			shell.open();
			assertEquals(SWT.NONE, control.getBevelBorder());
			assertEquals(SWT.DEFAULT, control.getBorderRelief());
			assertTrue((OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE)
					& OS.WS_EX_CLIENTEDGE) != 0, "Native themed client edge required");
			Image image = new Image(display, 144, 80);
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));
				gc.fillRectangle(image.getBounds());
				int sentinel = OS.GetPixel(gc.handle, 60, 0);
				var beforeClip = gc.getClipping();
				assertTrue(sentinel != -1);
				control.setVisible(false);
				assertFalse(OS.IsWindowVisible(control.handle));
				OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle,
						OS.PRF_NONCLIENT | OS.PRF_CHECKVISIBLE);
				assertEquals(sentinel, OS.GetPixel(gc.handle, 60, 0),
						"Hidden default theme border must not modify print pixels");
				assertEquals(beforeClip, gc.getClipping(),
						"Hidden themed WM_PRINT must preserve caller clip");
				control.setVisible(true);
				assertTrue(OS.IsWindowVisible(control.handle));
				OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle,
						OS.PRF_NONCLIENT | OS.PRF_CHECKVISIBLE);
				assertTrue(OS.GetPixel(gc.handle, 60, 0) != sentinel,
						"Visible native themed border must still be printed");
			} finally {
				gc.dispose();
				image.dispose();
			}
		} finally {
			control.dispose();
		}
	}

	/**
	 * The inherited Win32 themed WM_PRINT path must restore the caller's
	 * HDC clipping region for a visible, ordinarily bordered control. The
	 * old ExcludeClipRect implementation removed the center clip permanently.
	 */
	@Test
	public void test_visibleThemedClientEdgeRestoresCallerHdcClip() {
		Composite control = new Composite(shell, SWT.BORDER);
		try {
			control.setBounds(12, 12, 120, 68);
			shell.setSize(260, 180);
			shell.open();
			assertEquals(SWT.NONE, control.getBevelBorder());
			assertEquals(SWT.DEFAULT, control.getBorderRelief());
			assertTrue(OS.IsWindowVisible(control.handle));
			assertTrue((OS.GetWindowLong(control.handle, OS.GWL_EXSTYLE)
					& OS.WS_EX_CLIENTEDGE) != 0);
			Image image = new Image(display, 144, 80);
			GC gc = new GC(image);
			Region before = new Region(display);
			Region after = new Region(display);
			try {
				gc.getClipping(before);
				assertTrue(before.contains(60, 40), "Caller center is initially clipped in");
				var originalClip = gc.getClipping();
				gc.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));
				gc.fillRectangle(image.getBounds());
				int sentinel = OS.GetPixel(gc.handle, 60, 0);
				assertTrue(sentinel != -1);
				OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle,
						OS.PRF_NONCLIENT | OS.PRF_CHECKVISIBLE);
				gc.getClipping(after);
				assertTrue(after.contains(60, 40),
						"Native themed frame must not exclude caller's client clip");
				assertEquals(originalClip, gc.getClipping());
				assertTrue(OS.GetPixel(gc.handle, 60, 0) != sentinel,
						"Visible themed non-client border must still print");
			} finally {
				after.dispose();
				before.dispose();
				gc.dispose();
				image.dispose();
			}
		} finally {
			control.dispose();
		}
	}

	/**
	 * The same native non-client edge atom serves WM_NCPAINT and WM_PRINT.
	 * Zero/tiny HWND geometry must not expand its DrawEdge ring into the
	 * caller-owned client area or leave the print HDC clipped afterwards.
	 */
	@Test
	public void test_nativeReliefTinyFrameKeepsPrintDCUnchanged() {
		Composite control = new Composite(shell, SWT.BORDER);
		try {
			control.setBevelBorder(SWT.SHADOW_OUT);
			shell.setSize(240, 160);
			shell.open();
			Image image = new Image(display, 128, 96);
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_MAGENTA));
				for (int extent : new int[] {0, 1, 2, 3, 4}) {
					control.setSize(extent, extent);
					gc.fillRectangle(image.getBounds());
					int untouched = OS.GetPixel(gc.handle, 80, 60);
					var initialClip = gc.getClipping();
					assertTrue(untouched != -1);
					OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle,
							OS.PRF_NONCLIENT);
					assertEquals(initialClip, gc.getClipping(),
								"Tiny native bevel must restore caller HDC clip");
					assertEquals(untouched, OS.GetPixel(gc.handle, 80, 60),
								"Tiny HWND must not draw outside its frame");
				}
			} finally {
				gc.dispose();
				image.dispose();
			}
		} finally {
			control.dispose();
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
