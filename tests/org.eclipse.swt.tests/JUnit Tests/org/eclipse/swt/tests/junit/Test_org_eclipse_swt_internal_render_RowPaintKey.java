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

import java.util.*;

import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * The row key of the drawn planes' retained rows (brief rule 24, delta based view), headless: the same row gives the
 * same key; any field the renderer reads changes it; texts are kept for an exact comparison (two strings with one hash
 * still differ); a row with a cell image has no key (always drawn).
 */
public class Test_org_eclipse_swt_internal_render_RowPaintKey {

	static RowPaint row (String text) {
		RowPaint row = Test_org_eclipse_swt_internal_render_RowRenderer.row (RowPaint.CHILDREN | RowPaint.CHECKBOX);
		row.cells (2);
		row.text [0] = text;
		row.text [1] = "second";
		return row;
	}

	static int [] key (RowPaint row, String [] texts) {
		int [] ints = new int [RowPaint.keyLength (row.cells)];
		int n = row.key (ints, texts);
		return n < 0 ? null : Arrays.copyOf (ints, n);
	}

	@Test
	public void sameRowSameKeyAnyFieldChangesIt () {
		String [] a = new String [2], b = new String [2];
		assertArrayEquals (key (row ("row 1"), a), key (row ("row 1"), b));
		assertArrayEquals (a, b);
		RowPaint changed = row ("row 1");
		changed.state |= RowPaint.SELECTED;
		assertFalse (Arrays.equals (key (row ("row 1"), a), key (changed, b)), "state");
		changed = row ("row 1");
		changed.cellForeground [1] = 0xFF0000;
		assertFalse (Arrays.equals (key (row ("row 1"), a), key (changed, b)), "a cell colour");
		changed = row ("row 1");
		changed.y += 18;
		assertFalse (Arrays.equals (key (row ("row 1"), a), key (changed, b)), "the place");
		changed = row ("row 1");
		changed.zoom = 150;
		assertFalse (Arrays.equals (key (row ("row 1"), a), key (changed, b)), "the zoom");
	}

	@Test
	public void textsAreComparedNotHashed () {
		assertEquals ("Aa".hashCode (), "BB".hashCode (), "a known String hash collision");
		String [] a = new String [2], b = new String [2];
		assertArrayEquals (key (row ("Aa"), a), key (row ("BB"), b), "the ints do not carry the text");
		assertNotEquals (a [0], b [0], "the kept texts tell the rows apart");
	}

	@Test
	public void aRowWithAnImageHasNoKey () {
		RowPaint row = row ("row 1");
		row.image [1] = new org.eclipse.swt.graphics.Image (null, 1, 1);
		try {
			assertNull (key (row, new String [2]));
		} finally {
			row.image [1].dispose ();
		}
	}
}
