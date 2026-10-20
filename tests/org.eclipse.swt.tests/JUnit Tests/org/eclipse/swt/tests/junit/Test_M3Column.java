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

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3Column and M3StyleTable (public model API, brief rule 26), headless: column kinds and names, the
 * computed column, and style interning by value.
 */
public class Test_M3Column {

	@Test
	public void test_kindsAndNames () {
		M3Column [] columns = {M3Column.text ("Name"), M3Column.ofInt ("Line"), M3Column.ofLong ("Id"), M3Column.ofDouble ("Price"),
				M3Column.image ("Icon"), M3Column.custom ("Path", (id, slot) -> "/" + id)};
		int [] kinds = {M3Column.TEXT, M3Column.INT, M3Column.LONG, M3Column.DOUBLE, M3Column.IMAGE, M3Column.CUSTOM};
		String [] names = {"TEXT", "INT", "LONG", "DOUBLE", "IMAGE", "CUSTOM"};
		for (int i = 0; i < columns.length; i++) {
			assertEquals (kinds [i], columns [i].kind ());
			assertTrue (columns [i].toString ().contains (names [i]), columns [i].toString ());
			assertEquals (i < 5, columns [i].textFunction () == null);
		}
		assertEquals ("Price", columns [3].name ());
		assertEquals ("/7", columns [5].textFunction ().text (7, 0).toString ());
		assertThrows (IllegalArgumentException.class, () -> M3Column.text (null));
		assertThrows (IllegalArgumentException.class, () -> M3Column.custom ("Path", null));
		assertThrows (IllegalArgumentException.class, () -> M3Column.custom (null, (id, slot) -> ""));
	}

	@Test
	public void test_stylesInternByValue () {
		M3StyleTable styles = new M3StyleTable ();
		assertEquals (0, styles.intern (null, null, null));
		assertEquals (0, styles.size ());
		Color red = new Color (255, 0, 0), alsoRed = new Color (255, 0, 0), blue = new Color (0, 0, 255);
		int warning = styles.intern (null, red, null);
		assertEquals (warning, styles.intern (null, alsoRed, null), "equal colors are one style");
		int selected = styles.intern (null, red, blue);
		assertNotEquals (warning, selected);
		assertSame (red, styles.foreground (warning));
		assertNull (styles.background (warning));
		assertSame (blue, styles.background (selected));
		assertNull (styles.font (selected));
		assertNull (styles.font (0));
		for (int i = 0; i < 100; i++) styles.intern (null, new Color (i, i, i), null);
		assertEquals (102, styles.size ());
		assertEquals (warning, styles.intern (null, red, null), "found again after growth");
		assertEquals (new Color (40, 40, 40), styles.foreground (styles.intern (null, new Color (40, 40, 40), null)));
		assertThrows (IllegalArgumentException.class, () -> styles.font (103));
		assertThrows (IllegalArgumentException.class, () -> styles.foreground (-1));
		int backgroundOnly = styles.intern (null, null, blue);
		assertSame (blue, styles.background (backgroundOnly));
		assertNull (styles.foreground (backgroundOnly));
	}

	@Test
	public void test_stylesWithEqualHashesStayApart () {
		/* two gray pairs whose Objects.hash collide, found by search: interned apart, found again */
		M3StyleTable styles = new M3StyleTable ();
		java.util.Map<Integer, int []> seen = new java.util.HashMap<> ();
		int [] first = null, second = null;
		for (int f = 0; f < 256 && second == null; f++) {
			for (int b = 0; b < 256; b++) {
				int hash = java.util.Objects.hash (null, new Color (f, f, f), new Color (b, b, b));
				int [] previous = seen.putIfAbsent (hash, new int [] {f, b});
				if (previous != null) {
					first = previous;
					second = new int [] {f, b};
					break;
				}
			}
		}
		assertNotNull (second, "Color hashes are linear in the channels, so equal hashes exist");
		Color f1 = new Color (first [0], first [0], first [0]), b1 = new Color (first [1], first [1], first [1]);
		Color f2 = new Color (second [0], second [0], second [0]), b2 = new Color (second [1], second [1], second [1]);
		int one = styles.intern (null, f1, b1), two = styles.intern (null, f2, b2);
		assertNotEquals (one, two);
		assertEquals (one, styles.intern (null, f1, b1));
		assertEquals (two, styles.intern (null, f2, b2));
	}

	@Test
	public void test_stylesWithFonts () {
		org.eclipse.swt.widgets.Display display = org.eclipse.swt.widgets.Display.getDefault ();
		Font font = new Font (display, "Arial", 10, org.eclipse.swt.SWT.BOLD);
		try {
			M3StyleTable styles = new M3StyleTable ();
			int bold = styles.intern (font, null, null);
			assertEquals (bold, styles.intern (font, null, null));
			assertSame (font, styles.font (bold));
			assertNotEquals (bold, styles.intern (font, new Color (1, 2, 3), null));
		} finally {
			font.dispose ();
		}
	}
}
