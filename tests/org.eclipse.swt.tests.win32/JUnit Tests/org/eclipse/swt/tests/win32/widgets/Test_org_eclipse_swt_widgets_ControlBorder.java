/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.win32.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.junit.jupiter.api.Test;

/** Exercises subclass non-client handlers with their custom-color paths active. */
@SuppressWarnings("restriction")
public class Test_org_eclipse_swt_widgets_ControlBorder {
	@Test
	public void constructorBevelsShareScreenAndPrintRenderingWithExplicitOverrides () throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			shell.setSize (280, 210);
			shell.open ();
			for (boolean canvas : new boolean [] { false, true }) {
				for (int constructorEdge : new int [] { SWT.SHADOW_IN, SWT.SHADOW_OUT,
						SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT }) {
					int borderWidth = -1;
					for (boolean border : new boolean [] { false, true }) {
						int constructorStyle = constructorEdge | (border ? SWT.BORDER : SWT.NONE);
						Composite control = canvas ? new Canvas (shell, constructorStyle)
								: new Composite (shell, constructorStyle);
						try {
							control.setBounds (20, 20, 200, 120);
							settle (display);
							assertEquals (SWT.DEFAULT, control.getBorderStyle ());
							assertTrue (control.getBorderWidth () > 0);
							if (border) assertEquals (borderWidth, control.getBorderWidth (), "SWT.BORDER must not double the native frame");
							else borderWidth = control.getBorderWidth ();
							Rectangle client = control.getClientArea (), trim = control.computeTrim (3, 7, 70, 50);
							int style = control.getStyle ();
							int windowStyle = OS.GetWindowLong (control.handle, OS.GWL_STYLE);
							int extendedStyle = OS.GetWindowLong (control.handle, OS.GWL_EXSTYLE);
							ImageData original = capture (control), printed = printBorder (control);
							assertEdge (original, constructorEdge, "constructor screen edge");
							assertEdge (printed, constructorEdge, "constructor WM_PRINT edge");
							int override = constructorEdge == SWT.SHADOW_IN || constructorEdge == SWT.SHADOW_ETCHED_IN
									? SWT.SHADOW_OUT : SWT.SHADOW_IN;
							control.setBorderStyle (override);
							settle (display);
							assertEquals (override, control.getBorderStyle ());
							assertEdge (capture (control), override, "explicit screen edge");
							assertEdge (printBorder (control), override, "explicit WM_PRINT edge");
							assertEquals (client, control.getClientArea ());
							assertEquals (trim, control.computeTrim (3, 7, 70, 50));
							control.setBorderStyle (SWT.DEFAULT);
							settle (display);
							assertEquals (SWT.DEFAULT, control.getBorderStyle ());
							assertPixelsEqual (original, capture (control));
							assertPixelsEqual (printed, printBorder (control));
							assertEquals (client, control.getClientArea ());
							assertEquals (trim, control.computeTrim (3, 7, 70, 50));
							assertEquals (borderWidth, control.getBorderWidth ());
							assertEquals (style, control.getStyle ());
							assertEquals (windowStyle, OS.GetWindowLong (control.handle, OS.GWL_STYLE));
							assertEquals (extendedStyle, OS.GetWindowLong (control.handle, OS.GWL_EXSTYLE));
						} finally {
							control.dispose ();
						}
					}
				}
			}
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void tabFolderCustomColorsDoNotReplaceExplicitScreenBorder () throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			TabFolder folder = new TabFolder (shell, SWT.BORDER);
			new TabItem (folder, SWT.NONE).setText ("Native border");
			folder.setBackground (new Color (display, 41, 83, 127));
			folder.setForeground (new Color (display, 227, 221, 211));
			folder.setBounds (20, 20, 200, 120);
			shell.setSize (260, 200);
			shell.open ();
			for (int border : new int [] { SWT.SHADOW_IN, SWT.SHADOW_OUT }) {
				folder.setBorderStyle (border);
				settle (display);
				assertEdge (capture (folder), border, "TabFolder screen");
			}
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void customColorsPrintExplicitBorderWithoutChangingCallerClipOrClient () {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			TabFolder folder = new TabFolder (shell, SWT.BORDER);
			new TabItem (folder, SWT.NONE).setText ("Native border");
			DateTime dateTime = new DateTime (shell, SWT.BORDER | SWT.DATE);
			for (Control control : new Control [] { folder, dateTime }) {
				control.setBackground (new Color (display, 41, 83, 127));
				control.setForeground (new Color (display, 227, 221, 211));
				control.setSize (200, 120);
				for (int border : new int [] { SWT.SHADOW_IN, SWT.SHADOW_OUT }) {
					control.setBorderStyle (border);
					ImageData pixels = printBorder (control);
					assertEdge (pixels, border, control.getClass ().getSimpleName () + " WM_PRINT");
					assertEquals (new RGB (197, 37, 173), rgb (pixels, pixels.width / 2, pixels.height / 2),
							"non-client printing changed client content");
				}
			}
		} finally {
			shell.dispose ();
		}
	}

	private static ImageData printBorder (Control control) {
		Point size = control.getSize ();
		Image image = new Image (control.getDisplay (), size.x, size.y);
		try {
			GC gc = new GC (image);
			try {
				gc.setBackground (new Color (control.getDisplay (), 197, 37, 173));
				gc.fillRectangle (0, 0, size.x, size.y);
				Rectangle clip = gc.getClipping ();
				OS.SendMessage (control.handle, OS.WM_PRINT, gc.handle, OS.PRF_NONCLIENT);
				assertEquals (clip, gc.getClipping (), "WM_PRINT changed the caller's clip");
			} finally {
				gc.dispose ();
			}
			return image.getImageData ();
		} finally {
			image.dispose ();
		}
	}

	private static ImageData capture (Control control) {
		Rectangle bounds = control.getBounds ();
		Point origin = control.getParent ().toDisplay (bounds.x, bounds.y);
		Image image = new Image (control.getDisplay (), bounds.width, bounds.height);
		try {
			GC screen = new GC (control.getDisplay ());
			try {
				screen.copyArea (image, origin.x, origin.y);
			} finally {
				screen.dispose ();
			}
			return image.getImageData ();
		} finally {
			image.dispose ();
		}
	}

	private static void assertEdge (ImageData image, int border, String message) {
		int difference = 0;
		for (int x = image.width / 3; x < image.width * 2 / 3; x++) {
			RGB top = rgb (image, x, 0), bottom = rgb (image, x, image.height - 1);
			difference += top.red + top.green + top.blue - bottom.red - bottom.green - bottom.blue;
		}
		boolean inset = border == SWT.SHADOW_IN || border == SWT.SHADOW_ETCHED_IN;
		assertTrue (inset ? difference < 0 : difference > 0, message);
	}

	private static void assertPixelsEqual (ImageData expected, ImageData actual) {
		assertEquals (expected.width, actual.width);
		assertEquals (expected.height, actual.height);
		for (int y = 0; y < expected.height; y++) {
			for (int x = 0; x < expected.width; x++) {
				assertEquals (rgb (expected, x, y), rgb (actual, x, y), "DEFAULT must restore the constructor-selected frame");
			}
		}
	}

	private static RGB rgb (ImageData image, int x, int y) {
		return image.palette.getRGB (image.getPixel (x, y));
	}

	private static void settle (Display display) throws InterruptedException {
		long until = System.nanoTime () + 250_000_000L;
		do {
			while (display.readAndDispatch ()) { }
			display.update ();
			Thread.sleep (10);
		} while (System.nanoTime () < until);
	}
}
