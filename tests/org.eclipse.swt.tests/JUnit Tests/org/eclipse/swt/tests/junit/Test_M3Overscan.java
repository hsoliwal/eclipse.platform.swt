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

import java.util.Arrays;
import java.util.BitSet;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3Overscan (enhanced M3 API, M3-OVERSCAN). The oracle is the rows in view
 * (<code>M3ItemSelection.getVisibleRange</code>) widened by the overscan and clamped to the rows; the resident
 * range and the prefetch window must equal it after every step, in every mode.
 */
public class Test_M3Overscan {
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 320, 420);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private void flush () {
		while (display.readAndDispatch ()) {
			// paint and deliver the pending notifications
		}
	}

	private static M3ItemRange widened (M3ItemRange visible, long count, int before, int after) {
		if (visible.isEmpty ()) return visible;
		return M3ItemRange.of (Math.max (0, visible.start () - before), (int) Math.min (count - 1, (long) visible.end () + after));
	}

	private Table table (int rows) {
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		table.setItemCount (rows);
		return table;
	}

	@Test
	public void test_tableResidentRangeIsTheWidenedView () {
		Table table = table (5000);
		M3Overscan.set (table, 10, 30);
		assertArrayEquals (new int [] {10, 30}, M3Overscan.get (table));
		for (int top : new int [] {0, 5, 2500, 4999}) {
			table.setTopIndex (top);
			table.update ();
			flush ();
			M3ItemRange visible = M3ItemSelection.getVisibleRange (table);
			assertEquals (widened (visible, 5000, 10, 30), M3Residency.residentRange (table), "top " + top);
			assertEquals (M3Residency.residentRange (table).size (), M3Residency.stats (table) [M3Residency.RESIDENT_ROWS]);
		}
		M3Overscan.set (table, 0, 0);
		assertEquals (M3ItemSelection.getVisibleRange (table), M3Residency.residentRange (table));
		assertArrayEquals (new int [] {0, 0}, M3Overscan.get (table));
	}

	@Test
	public void test_listAndTreeAreWidenedInTheirRows () {
		List list = new List (shell, SWT.MULTI | SWT.V_SCROLL);
		list.setBounds (5, 5, 300, 380);
		String [] strings = new String [3000];
		Arrays.setAll (strings, i -> "row " + i);
		list.setItems (strings);
		list.setTopIndex (1000);
		M3Overscan.set (list, 4, 7);
		assertEquals (widened (M3ItemSelection.getVisibleRange (list), 3000, 4, 7), M3Residency.residentRange (list));
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("node " + event.index));
		tree.setItemCount (2000);
		TreeItem first = tree.getItem (0);
		first.setItemCount (5);
		first.setExpanded (true);
		tree.setTopItem (tree.getItem (1000));
		M3Overscan.set (tree, 3, 5);
		long rows = M3RowMath.visibleRowCount (tree);
		assertEquals (widened (M3ItemSelection.getVisibleRange (tree), rows, 3, 5), M3Residency.residentRange (tree));
		tree.setTopItem (tree.getItem (1999));
		tree.update ();
		flush ();
		M3ItemRange resident = M3Residency.residentRange (tree);
		assertEquals (rows - 1, resident.end (), "clamped to the last visible row");
		assertEquals (widened (M3ItemSelection.getVisibleRange (tree), rows, 3, 5), resident);
	}

	@Test
	public void test_settingTheOverscanAnnouncesTheNewWindow () {
		Table table = table (5000);
		table.update ();
		flush ();
		BitSet announced = new BitSet ();
		M3Prefetch.addListener (table, event -> {
			if (event.detail == M3Prefetch.PREFETCH) announced.set (event.start, event.end + 1);
			else announced.clear (event.start, event.end + 1);
		});
		M3Overscan.set (table, 0, 50);
		M3ItemRange resident = M3Residency.residentRange (table);
		assertEquals (resident.start (), announced.nextSetBit (0));
		assertEquals (resident.end (), announced.length () - 1);
		assertEquals (resident.size (), announced.cardinality ());
		M3Overscan.set (table, 0, 0);
		assertEquals (M3Residency.residentRange (table).size (), announced.cardinality (), "narrowing cancels the overscan rows");
	}

	@Test
	public void test_notVisibleAndEmptyWidgetsHoldNoRows () {
		Table table = table (0);
		M3Overscan.set (table, 5, 5);
		assertTrue (M3Residency.residentRange (table).isEmpty ());
		Shell hidden = new Shell (display);
		Table other = new Table (hidden, SWT.NONE);
		other.setItemCount (100);
		M3Overscan.set (other, 5, 5);
		assertTrue (M3Residency.residentRange (other).isEmpty ());
		hidden.dispose ();
	}

	@Test
	public void test_argumentsAreChecked () {
		Table table = table (10);
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.set ((Table) null, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.set ((List) null, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.set ((Tree) null, 0, 0));
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.get ((Table) null));
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.get ((List) null));
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.get ((Tree) null));
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.set (table, -1, 0));
		assertThrows (IllegalArgumentException.class, () -> M3Overscan.set (table, 0, -1));
		table.dispose ();
		assertThrows (SWTException.class, () -> M3Overscan.set (table, 1, 1));
		assertThrows (SWTException.class, () -> M3Overscan.get (table));
	}
}
