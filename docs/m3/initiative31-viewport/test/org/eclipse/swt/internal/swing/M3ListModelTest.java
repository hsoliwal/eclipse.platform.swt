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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;

import org.junit.*;

/**
 * Headless tests of {@link M3ListModel}: a list that is not showing (always, headless) takes changes without Swing
 * model events; the geometry follows every change; the patch on show is one event.
 */
public class M3ListModelTest {
	private JList<Object> list;
	private M3ListModel model;
	private M3ListRows rows;
	private final List<String> events = new ArrayList<> ();

	@Before
	public void setUp () {
		list = new JList<> ();
		model = M3ListModel.install (list);
		rows = M3ListRows.install (list);
		model.addListDataListener (new ListDataListener () {
			@Override
			public void intervalAdded (ListDataEvent e) {
				events.add ("+" + e.getIndex0 () + ".." + e.getIndex1 ());
			}

			@Override
			public void intervalRemoved (ListDataEvent e) {
				events.add ("-" + e.getIndex0 () + ".." + e.getIndex1 ());
			}

			@Override
			public void contentsChanged (ListDataEvent e) {
				events.add ("~" + e.getIndex0 () + ".." + e.getIndex1 ());
			}
		});
	}

	@Test
	public void hiddenChangesReachOnlyTheGeometryAndTheSelection () {
		model.addAll (Arrays.asList ("a", "b", "c"));
		list.setSelectedIndices (new int [] {0, 2});
		for (int i = 0; i < 10_000; i++) model.addElement ("h" + i);
		model.add (1, "wwwwwwwwwwwwwwwwwwwwwwww");
		model.remove (2);
		model.setElementAt ("c2", 2);
		assertEquals ("no event while hidden", List.of (), events);
		assertTrue (model.isDirty ());
		assertArrayEquals ("the selection moved as BasicListUI moves it", new int [] {0, 2}, list.getSelectedIndices ());
		assertEquals ("the geometry followed", SwingUtilities.computeStringWidth (list.getFontMetrics (list.getFont ()), "wwwwwwwwwwwwwwwwwwwwwwww"), rows.widest ());
		model.shown ();
		assertEquals ("one patch", List.of ("~0..10002"), events);
		assertFalse (model.isDirty ());
		model.shown ();
		assertEquals ("nothing more to tell", 1, events.size ());
	}

	@Test
	public void anEmptyModelPatchesItsFirstRow () {
		model.addElement ("x");
		model.removeAllElements ();
		model.shown ();
		assertEquals (List.of ("~0..0"), events);
		assertEquals (0, rows.widest ());
	}
}
