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
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The vector glyph leg of the drawn modes at a zoom (docs/m3/RENDER_MODES.md, vector glyphs, leaf SV3): a Tree with check
 * boxes and children in a drawn mode under a non-native look and feel, at the zoom <code>-Dswt.autoScale</code> set
 * (100, 125, 150, 200), captured with WM_PRINT on the invisible desktop and saved as
 * <code>glyphzoom-&lt;zoom&gt;-&lt;laf&gt;-&lt;mode&gt;.png</code> in <code>-Dswt.viewport.evidence</code> (the three modes are compared
 * pixel for pixel by tools/glyph_zoom_legs.sh). Asserted here:
 * <ul>
 * <li>crisp (swt #3631): the expander of the first row appears in the capture exactly as the SVG set rasterizes it at this
 * zoom, opaque and transparent pixels matched 1:1, so no bitmap was scaled;</li>
 * <li>no seam at a fractional zoom (FlatLaf #1022): in a selected row the pixels right next to the glyph are the selection
 * colour;</li>
 * <li>the unfocused selection of a dark look and feel (swt #3659): with the focus elsewhere the selected row is filled with
 * the look and feel's <code>Tree.selectionInactiveBackground</code>, not the platform's grey.</li>
 * </ul>
 */
public class Test_org_eclipse_swt_widgets_Tree_DrawnGlyphZoom {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Tree_DrawnGlyphZoom.class.getName ());

	@Test
	public void glyphsAreCrispAndSeamlessAtTheZoom () {
		Assumptions.assumeTrue (ViewportDifferential.drawn ("Tree"), "a drawn mode (custom, m3, gpu) only");
		Assumptions.assumeTrue (Glyphs.rasterizer (glyph -> 0) != null, "no SVG rasterizer (org.eclipse.swt.svg fragment) on the class path");
		Display display = Display.getDefault ();
		int zoom = DPIUtil.getDeviceZoom ();
		String laf = M3LookAndFeel.getLookAndFeel ();
		Assumptions.assumeFalse ("native".equals (laf), "a non-native look and feel draws the SVG glyphs");
		Shell shell = new Shell (display);
		shell.setBounds (display.getBounds ().x, display.getBounds ().y, 420, 420);
		Tree tree = new Tree (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		tree.setBounds (5, 5, 400, 380);
		for (int i = 0; i < 12; i++) {
			TreeItem item = new TreeItem (tree, SWT.NONE);
			item.setText ("root " + i);
			item.setChecked (i % 2 == 0);
			item.setGrayed (i % 4 == 2);
			for (int c = 0; c < 3; c++) new TreeItem (item, SWT.NONE).setText ("child " + i + "." + c);
			item.setExpanded (i % 3 == 0);
		}
		/* the focus on a one-pixel button, as the differentials do: the selection is inactive */
		Button focus = new Button (shell, SWT.PUSH);
		focus.setBounds (410, 5, 1, 1);
		tree.setSelection (tree.getItem (1));
		shell.open ();
		focus.setFocus ();
		ViewportDifferential.settle (display, 200);
		/* the control's own pixels at the zoom (a 100 % capture would scale them down) */
		ImageData capture = ViewportDifferential.render (tree, zoom);
		String mode = ViewportDifferential.reference ("Tree");
		String file = ViewportDifferential.save ("glyphzoom-" + zoom + "-" + laf + "-" + mode, capture);
		M3UIDefaults defaults = M3LookAndFeel.getDefaults ();
		/* the theme the plane paints with: its installed row delegate's */
		Theme theme = M3LookAndFeel.getUI (tree) instanceof M3DrawnRowUI drawn ? drawn.theme () : ThemeDefaults.of ("Tree", ThemeDefaults.look (laf), defaults);
		int background = defaults.color (M3UIKeys.of ("Tree", "background", M3UIKeys.COLOR), 0xFFFFFF) & 0xFFFFFF;
		int inactive = defaults.color (M3UIKeys.of ("Tree", "selectionInactiveBackground", M3UIKeys.COLOR), -1) & 0xFFFFFF;
		Rectangle row0 = pixels (tree.getItem (0).getBounds (), zoom), row1 = pixels (tree.getItem (1).getBounds (), zoom);
		/* crisp: the open chevron of root 0 (expanded), tinted as the renderer tints it, found 1:1 left of the label */
		ImageData chevron = new GlyphCache (4, Glyphs.rasterizer (glyph -> theme.border)).get (Glyphs.CHEVRON_DOWN, 16, zoom, 0);
		int found = find (capture, chevron, 0, row0.y - 1, row0.x, row0.y + row0.height + 1);
		LOG.info (() -> "glyph zoom " + zoom + " " + laf + " " + mode + ": chevron " + chevron.width + " px found at x " + found + " in " + file);
		assertTrue (found >= 0, "swt #3631: the " + chevron.width + " px chevron is not in the capture 1:1 (scaled?): " + file);
		/* no seam: root 1 is selected and unfocused; the pixels next to its check box are the inactive selection colour */
		ImageData box = new GlyphCache (4, Glyphs.rasterizer (glyph -> theme.checkFill != -1 ? theme.checkFill : theme.border)).get (Glyphs.CHECK_OFF, 16, zoom, 0);
		int boxX = find (capture, box, 0, row1.y - 1, row1.x, row1.y + row1.height + 1);
		assertTrue (boxX >= 0, "the check box of the selected row is in the capture 1:1: " + file);
		int middle = row1.y + row1.height / 2;
		int left = rgb (capture, boxX - 1, middle), right = rgb (capture, boxX + box.width, middle);
		assertEquals (inactive, left, "FlatLaf #1022: a seam left of the check box at " + zoom + " %: " + file);
		assertEquals (inactive, right, "FlatLaf #1022: a seam right of the check box at " + zoom + " %: " + file);
		/* the unfocused selection fill is the look and feel's, not the platform's */
		int fill = rgb (capture, row1.x + 2, middle);
		assertEquals (inactive, fill, "swt #3659: the unfocused selection of " + laf + " is not Tree.selectionInactiveBackground: " + file);
		assertNotEquals (background, inactive, "the look and feel distinguishes the inactive selection");
		shell.dispose ();
	}

	/* item bounds (points) in the capture's device pixels */
	static Rectangle pixels (Rectangle r, int zoom) {
		return new Rectangle (Math.round (r.x * zoom / 100f), Math.round (r.y * zoom / 100f), Math.round (r.width * zoom / 100f), Math.round (r.height * zoom / 100f));
	}

	/* the x where a glyph's opaque and transparent pixels match the capture 1:1 within [x0, x1) at the row [y0, y1), or -1 */
	static int find (ImageData capture, ImageData glyph, int x0, int y0, int x1, int y1) {
		for (int y = y0; y + glyph.height <= y1; y++) {
			for (int x = x0; x + glyph.width <= x1 && x + glyph.width <= capture.width; x++) {
				if (matches (capture, glyph, x, y)) return x;
			}
		}
		return -1;
	}

	static boolean matches (ImageData capture, ImageData glyph, int x, int y) {
		int opaque = 0;
		for (int gy = 0; gy < glyph.height; gy++) {
			for (int gx = 0; gx < glyph.width; gx++) {
				int alpha = glyph.getAlpha (gx, gy);
				if (alpha == 255) {
					opaque++;
					RGB c = glyph.palette.getRGB (glyph.getPixel (gx, gy));
					if (rgb (capture, x + gx, y + gy) != (c.red << 16 | c.green << 8 | c.blue)) return false;
				}
			}
		}
		return opaque > 0;
	}

	static int rgb (ImageData image, int x, int y) {
		if (x < 0 || y < 0 || x >= image.width || y >= image.height) return -1;
		RGB c = image.palette.getRGB (image.getPixel (x, y));
		return c.red << 16 | c.green << 8 | c.blue;
	}
}
