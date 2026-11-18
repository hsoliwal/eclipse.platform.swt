/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.Test;

/** Exercises native frame pixels separately from SWT client geometry. */
public class Test_org_eclipse_swt_widgets_ControlBorder {
	private static final int [] STYLES = { SWT.SHADOW_IN, SWT.SHADOW_OUT,
			SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT, SWT.DEFAULT };

	@Test
	public void defaultAndInvalidStylesPreserveConstructorBits () {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			Canvas canvas = new Canvas (shell, SWT.BORDER);
			Button button = new Button (shell, SWT.PUSH);
			assertEquals (SWT.DEFAULT, canvas.getBorderStyle ());
			assertEquals (SWT.DEFAULT, button.getBorderStyle ());
			int style = canvas.getStyle ();
			for (int invalid : new int [] { SWT.NONE, SWT.SHADOW_IN | SWT.SHADOW_OUT, Integer.MAX_VALUE }) {
				assertThrows (IllegalArgumentException.class, () -> canvas.setBorderStyle (invalid));
				assertEquals (SWT.DEFAULT, canvas.getBorderStyle ());
				assertEquals (style, canvas.getStyle ());
			}
			canvas.setBorderStyle (SWT.DEFAULT);
			assertEquals (style, canvas.getStyle ());
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void unborderedControlsRejectWithoutChangingState () {
		Shell shell = new Shell (Display.getDefault ());
		try {
			Canvas canvas = new Canvas (shell, SWT.NONE);
			SWTError failure = assertThrows (SWTError.class, () -> canvas.setBorderStyle (SWT.SHADOW_IN));
			assertEquals (SWT.ERROR_NOT_IMPLEMENTED, failure.code);
			assertEquals (SWT.DEFAULT, canvas.getBorderStyle ());
			assertEquals (0, canvas.getBorderWidth ());
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void nativeScrollableFramesKeepGeometryChildrenAndSelection () throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			Composite composite = new Composite (shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
			Canvas child = new Canvas (composite, SWT.NONE);
			child.setBounds (11, 13, 40, 30);
			Table table = new Table (shell, SWT.BORDER | SWT.V_SCROLL);
			new TableItem (table, SWT.NONE).setText ("Selected row");
			table.setSelection (0);
			Tree tree = new Tree (shell, SWT.BORDER | SWT.V_SCROLL);
			TreeItem item = new TreeItem (tree, SWT.NONE);
			item.setText ("Selected node");
			tree.setSelection (item);
			Scrollable [] controls = { composite, table, tree };
			for (int i = 0; i < controls.length; i++) controls[i].setBounds (10 + i * 190, 10, 180, 120);
			shell.setSize (700, 250);
			shell.setLocation (40, 40);
			shell.open ();
			settle (display);
			Rectangle childBounds = child.getBounds ();
			Point childOrigin = child.toDisplay (0, 0);
			for (Scrollable control : controls) {
				Rectangle bounds = control.getBounds (), client = control.getClientArea ();
				Rectangle trim = control.computeTrim (3, 7, 70, 50);
				Point origin = control.toDisplay (0, 0);
				int width = control.getBorderWidth (), style = control.getStyle ();
				for (int border : STYLES) {
					if (!selectBorder (control, border)) continue;
					settle (display);
					assertEquals (border, control.getBorderStyle ());
					assertEquals (bounds, control.getBounds ());
					assertEquals (client, control.getClientArea ());
					assertEquals (trim, control.computeTrim (3, 7, 70, 50));
					assertEquals (origin, control.toDisplay (0, 0));
					assertEquals (width, control.getBorderWidth ());
					assertEquals (style, control.getStyle ());
				}
			}
			assertEquals (childBounds, child.getBounds ());
			assertEquals (childOrigin, child.toDisplay (0, 0));
			assertEquals (0, table.getSelectionIndex ());
			assertSame (item, tree.getSelection ()[0]);
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void gtkEntryAndSpinnerKeepTextValuesAndMeasuredClient () throws Exception {
		assumeTrue (SWT.getPlatform ().equals ("gtk"));
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			Text text = new Text (shell, SWT.BORDER | SWT.SINGLE);
			text.setText ("Existing native entry");
			text.setSelection (2, 8);
			Spinner spinner = new Spinner (shell, SWT.BORDER);
			spinner.setValues (37, 0, 100, 0, 1, 10);
			text.setBounds (20, 20, 240, 40);
			spinner.setBounds (20, 80, 180, 40);
			shell.setSize (360, 240);
			shell.setLocation (40, 40);
			shell.open ();
			settle (display);
			for (Scrollable control : new Scrollable [] { text, spinner }) {
				Rectangle client = control.getClientArea (), trim = control.computeTrim (0, 0, 73, 29);
				Point preferred = control.computeSize (SWT.DEFAULT, SWT.DEFAULT);
				for (int border : STYLES) {
					control.setBorderStyle (border);
					settle (display);
					assertEquals (client, control.getClientArea ());
					assertEquals (trim, control.computeTrim (0, 0, 73, 29));
					assertEquals (preferred, control.computeSize (SWT.DEFAULT, SWT.DEFAULT));
				}
			}
			assertEquals ("Existing native entry", text.getText ());
			assertEquals (new Point (2, 8), text.getSelection ());
			assertEquals (37, spinner.getSelection ());
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void nativePrintKeepsBorderDirectionAndCallerGraphicsState () throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			Canvas canvas = new Canvas (shell, SWT.BORDER);
			canvas.setBounds (20, 20, 160, 100);
			shell.setSize (360, 240);
			shell.setLocation (40, 40);
			shell.open ();
			canvas.setBorderStyle (SWT.SHADOW_IN);
			settle (display);
			ImageData inset = print (canvas);
			canvas.setBorderStyle (SWT.SHADOW_OUT);
			settle (display);
			ImageData outset = print (canvas);
			assertTrue (edgeLight (inset, true) < edgeLight (inset, false));
			assertTrue (edgeLight (outset, true) > edgeLight (outset, false));
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void raisedAndSunkenNativePixelsReverseWithoutPaintingClient () throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			Canvas canvas = new Canvas (shell, SWT.BORDER);
			canvas.setBackground (new Color (display, 37, 89, 137));
			canvas.setBounds (20, 20, 160, 100);
			shell.setSize (360, 240);
			shell.setLocation (40, 40);
			shell.open ();
			settle (display);
			ImageData original = capture (canvas, "default");
			canvas.setBorderStyle (SWT.SHADOW_IN);
			settle (display);
			ImageData inset = capture (canvas, "inset");
			canvas.setBorderStyle (SWT.SHADOW_OUT);
			settle (display);
			ImageData outset = capture (canvas, "outset");
			assertTrue (edgeLight (inset, true) < edgeLight (inset, false), "sunken top must be darker than bottom");
			assertTrue (edgeLight (outset, true) > edgeLight (outset, false), "raised top must be lighter than bottom");
			assertClientEquals (canvas, original, inset);
			assertClientEquals (canvas, original, outset);
			canvas.setBorderStyle (SWT.DEFAULT);
			settle (display);
			ImageData restored = capture (canvas, "restored");
			assertPixelsEqual (original, restored);
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void changingColorsDoesNotReplaceExplicitNativeBorder () throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			Canvas canvas = new Canvas (shell, SWT.BORDER);
			canvas.setBounds (20, 20, 160, 100);
			shell.setSize (360, 240);
			shell.setLocation (40, 40);
			shell.open ();
			canvas.setBorderStyle (SWT.SHADOW_OUT);
			canvas.setBackground (new Color (display, 41, 113, 71));
			canvas.setForeground (new Color (display, 191, 53, 71));
			settle (display);
			ImageData pixels = capture (canvas, "outset-after-colors");
			assertEquals (SWT.SHADOW_OUT, canvas.getBorderStyle ());
			assertTrue (edgeLight (pixels, true) > edgeLight (pixels, false));
			assertEquals (new RGB (41, 113, 71), rgb (pixels, pixels.width / 2, pixels.height / 2));
		} finally {
			shell.dispose ();
		}
	}

	@Test
	public void tinyAndEmptyFramesDoNotChangeTrimOrThrow () {
		Shell shell = new Shell (Display.getDefault ());
		try {
			Canvas canvas = new Canvas (shell, SWT.BORDER);
			for (int size = 0; size <= 6; size++) {
				canvas.setSize (size, size);
				Rectangle client = canvas.getClientArea ();
				for (int border : STYLES) {
					selectBorder (canvas, border);
					assertEquals (client, canvas.getClientArea ());
				}
			}
		} finally {
			shell.dispose ();
		}
	}

	private static boolean selectBorder (Control control, int border) {
		int previous = control.getBorderStyle ();
		try {
			control.setBorderStyle (border);
			return true;
		} catch (SWTError failure) {
			/* Cocoa cannot represent two etched rings in a one-point native bezel. */
			if (!SWT.getPlatform ().equals ("cocoa") || failure.code != SWT.ERROR_NOT_IMPLEMENTED
					|| border != SWT.SHADOW_ETCHED_IN && border != SWT.SHADOW_ETCHED_OUT) throw failure;
			assertEquals (previous, control.getBorderStyle ());
			return false;
		}
	}

	private static ImageData print (Control control) {
		Point size = control.getSize ();
		Image image = new Image (control.getDisplay (), size.x, size.y);
		GC gc = new GC (image);
		try {
			gc.setForeground (control.getDisplay ().getSystemColor (SWT.COLOR_RED));
			gc.setBackground (control.getDisplay ().getSystemColor (SWT.COLOR_BLUE));
			gc.setLineWidth (3);
			Color foreground = gc.getForeground (), background = gc.getBackground ();
			Rectangle clipping = gc.getClipping ();
			assertTrue (control.print (gc), "native printing must succeed on the qualified backend");
			assertEquals (foreground, gc.getForeground ());
			assertEquals (background, gc.getBackground ());
			assertEquals (3, gc.getLineWidth ());
			assertEquals (clipping, gc.getClipping ());
			return image.getImageData ();
		} finally {
			gc.dispose ();
			image.dispose ();
		}
	}

	@Test
	public void gettersAndSettersEnforceThreadAndDisposalContracts () throws Exception {
		Shell shell = new Shell (Display.getDefault ());
		Canvas canvas = new Canvas (shell, SWT.BORDER);
		try {
			AtomicReference<Throwable> failure = new AtomicReference<> ();
			Thread thread = new Thread (() -> {
				try { canvas.setBorderStyle (SWT.SHADOW_OUT); }
				catch (Throwable caught) { failure.set (caught); }
			});
			thread.start ();
			thread.join (5000);
			assertFalse (thread.isAlive ());
			assertInstanceOf (SWTException.class, failure.get ());
			assertEquals (SWT.ERROR_THREAD_INVALID_ACCESS, ((SWTException) failure.get ()).code);
			assertEquals (SWT.DEFAULT, canvas.getBorderStyle ());
			canvas.dispose ();
			assertEquals (SWT.ERROR_WIDGET_DISPOSED, assertThrows (SWTException.class, canvas::getBorderStyle).code);
			assertEquals (SWT.ERROR_WIDGET_DISPOSED,
					assertThrows (SWTException.class, () -> canvas.setBorderStyle (SWT.DEFAULT)).code);
		} finally {
			shell.dispose ();
		}
	}

	private static void settle (Display display) throws InterruptedException {
		long until = System.nanoTime () + 250_000_000L;
		do {
			while (display.readAndDispatch ()) { }
			display.update ();
			Thread.sleep (10);
		} while (System.nanoTime () < until);
	}

	private static ImageData capture (Control control, String name) throws Exception {
		Rectangle bounds = control.getBounds ();
		Point origin = control.getParent ().toDisplay (bounds.x, bounds.y);
		Rectangle screenBounds = control.getDisplay ().getBounds ();
		assertTrue (control.isVisible () && bounds.width > 0 && bounds.height > 0);
		assertTrue (screenBounds.contains (origin.x, origin.y)
				&& screenBounds.contains (origin.x + bounds.width - 1, origin.y + bounds.height - 1),
				"native capture must be completely inside the display");
		for (Composite ancestor = control.getParent (); ancestor != null; ancestor = ancestor.getParent ()) {
			Rectangle client = ancestor.getClientArea ();
			Point corner = ancestor.toControl (origin);
			assertTrue (client.contains (corner.x, corner.y)
					&& client.contains (corner.x + bounds.width - 1, corner.y + bounds.height - 1),
					"native capture must not be clipped by an ancestor");
		}
		Image image = new Image (control.getDisplay (), bounds.width, bounds.height);
		GC screen = new GC (control.getDisplay ());
		try {
			screen.copyArea (image, origin.x, origin.y);
			ImageData data = image.getImageData ();
			String directory = System.getProperty ("swt.border.proof.dir");
			if (directory != null) {
				Path folder = Path.of (directory);
				Files.createDirectories (folder);
				ImageLoader loader = new ImageLoader ();
				loader.data = new ImageData [] { data };
				loader.save (folder.resolve (name + ".png").toString (), SWT.IMAGE_PNG);
			}
			return data;
		} finally {
			screen.dispose ();
			image.dispose ();
		}
	}

	private static int edgeLight (ImageData image, boolean top) {
		int total = 0;
		for (int x = image.width / 3; x < image.width * 2 / 3; x++) {
			RGB color = rgb (image, x, top ? 0 : image.height - 1);
			total += color.red + color.green + color.blue;
		}
		return total;
	}

	private static RGB rgb (ImageData image, int x, int y) {
		return image.palette.getRGB (image.getPixel (x, y));
	}

	private static void assertClientEquals (Canvas control, ImageData expected, ImageData actual) {
		Rectangle outer = control.getBounds (), client = control.getClientArea ();
		Point outerOrigin = control.getParent ().toDisplay (outer.x, outer.y);
		Point clientOrigin = control.toDisplay (client.x, client.y);
		double scaleX = (double) expected.width / outer.width;
		double scaleY = (double) expected.height / outer.height;
		int left = (int) Math.ceil ((clientOrigin.x - outerOrigin.x) * scaleX);
		int top = (int) Math.ceil ((clientOrigin.y - outerOrigin.y) * scaleY);
		int right = (int) Math.floor ((clientOrigin.x - outerOrigin.x + client.width) * scaleX);
		int bottom = (int) Math.floor ((clientOrigin.y - outerOrigin.y + client.height) * scaleY);
		assertTrue (left >= 0 && top >= 0 && right <= expected.width && bottom <= expected.height);
		assertTrue (left < right && top < bottom, "client oracle must cover a nonempty native client");
		for (int y = top; y < bottom; y++) {
			for (int x = left; x < right; x++) {
				assertEquals (new RGB (37, 89, 137), rgb (expected, x, y), "native client capture must contain its painted color");
				assertEquals (rgb (expected, x, y), rgb (actual, x, y), "client pixel changed");
			}
		}
	}

	private static void assertPixelsEqual (ImageData expected, ImageData actual) {
		assertEquals (expected.width, actual.width);
		assertEquals (expected.height, actual.height);
		for (int y = 0; y < expected.height; y++) {
			for (int x = 0; x < expected.width; x++) {
				assertEquals (rgb (expected, x, y), rgb (actual, x, y), "restored native pixel differs");
			}
		}
	}
}
