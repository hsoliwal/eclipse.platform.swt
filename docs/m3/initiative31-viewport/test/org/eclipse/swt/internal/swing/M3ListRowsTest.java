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
package org.eclipse.swt.internal.swing;

import static org.junit.Assert.*;

import java.awt.*;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.event.ListDataEvent;

import org.junit.*;

/** Headless tests of {@link M3ListRows}: fixed cell sizes from the model's change events, no renderer per row. */
public class M3ListRowsTest {
	private DefaultListModel<Object> model;
	private JList<Object> list;
	private M3ListRows rows;
	private int renders;

	@Before
	public void setUp () {
		model = new DefaultListModel<> ();
		model.addAll (Arrays.asList ("a", "bbbb", "cc"));
		list = new JList<> (model);
		ListCellRenderer<Object> renderer = new DefaultListCellRenderer ();
		list.setCellRenderer ((l, value, index, selected, focus) -> {
			renders++;
			return renderer.getListCellRendererComponent (l, value, index, selected, focus);
		});
		rows = M3ListRows.install (list);
	}

	private int width (String text) {
		return SwingUtilities.computeStringWidth (list.getFontMetrics (list.getFont ()), text);
	}

	@Test
	public void cellsHaveFixedSizesFromTheWidestRow () {
		assertTrue (list.getFixedCellHeight () > 0);
		assertEquals (width ("bbbb"), rows.widest ());
		int extra = list.getFixedCellWidth () - rows.widest ();
		assertTrue ("insets: " + extra, extra >= 0 && extra < 20);
	}

	@Test
	public void addingAndRemovingRowsFollowsTheWidest () {
		int extra = list.getFixedCellWidth () - rows.widest ();
		model.add (1, "wwwwwwwwwwww");
		assertEquals (width ("wwwwwwwwwwww"), rows.widest ());
		assertEquals (rows.widest () + extra, list.getFixedCellWidth ());
		model.remove (1);
		assertEquals (width ("bbbb"), rows.widest ());
		model.addElement ("bbbb");
		model.removeElementAt (1);
		assertEquals ("the other bbbb row is still there", width ("bbbb"), rows.widest ());
		model.setElementAt ("", 2);
		assertEquals (width ("cc"), rows.widest ());
		model.removeAllElements ();
		assertEquals (0, rows.widest ());
		model.addAll (Arrays.asList ("x", null, "yyy"));
		assertEquals (width ("yyy"), rows.widest ());
	}

	@Test
	public void layoutRendersNoRow () {
		String [] many = new String [100_000];
		Arrays.setAll (many, i -> "row " + i);
		model.addAll (Arrays.asList (many));
		renders = 0;
		Dimension size = list.getPreferredSize ();
		model.addElement ("one more");
		size = list.getPreferredSize ();
		assertEquals ("Swing measured no row", 0, renders);
		assertEquals (list.getFixedCellHeight () * model.getSize (), size.height);
	}

	@Test
	public void aNewFontOrModelMeasuresAgain () {
		int height = list.getFixedCellHeight ();
		list.setFont (list.getFont ().deriveFont (list.getFont ().getSize2D () * 3));
		assertTrue (list.getFixedCellHeight () > height);
		assertEquals (width ("bbbb"), rows.widest ());
		DefaultListModel<Object> next = new DefaultListModel<> ();
		next.addElement ("zzzzzzzzzzzzzzzzzzzz");
		list.setModel (next);
		assertEquals (width ("zzzzzzzzzzzzzzzzzzzz"), rows.widest ());
		model.addElement ("the old model is no longer followed, even when much wider than any row of the new one");
		assertEquals (width ("zzzzzzzzzzzzzzzzzzzz"), rows.widest ());
		list.updateUI ();
		assertEquals (width ("zzzzzzzzzzzzzzzzzzzz"), rows.widest ());
	}

	@Test
	public void eventsOutsideTheRowsAreHarmless () {
		int widest = rows.widest ();
		rows.intervalRemoved (new ListDataEvent (model, ListDataEvent.INTERVAL_REMOVED, 5, 9));
		assertEquals (widest, rows.widest ());
		rows.contentsChanged (new ListDataEvent (model, ListDataEvent.CONTENTS_CHANGED, -1, -1));
		assertEquals (widest, rows.widest ());
	}
}
