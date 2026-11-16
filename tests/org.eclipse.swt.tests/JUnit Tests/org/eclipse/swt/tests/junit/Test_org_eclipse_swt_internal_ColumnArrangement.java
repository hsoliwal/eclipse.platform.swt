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

import org.eclipse.swt.internal.*;
import org.junit.jupiter.api.*;

/** Headless tests of {@link ColumnArrangement}: no display, no widget. */
@SuppressWarnings ("nls")
public class Test_org_eclipse_swt_internal_ColumnArrangement {

	/* the plain model: one entry per position, each a column with its shown flag and width */
	static final class Model {
		final List<int []> columns = new ArrayList<> (); /* {index, shown, width} in display order */

		Model (int count) {
			for (int i = 0; i < count; i++) columns.add (new int [] {i, 1, ColumnArrangement.DEFAULT_WIDTH});
		}

		int [] find (int index) {
			for (int [] column : columns) if (column [0] == index) return column;
			throw new AssertionError ("no column " + index);
		}

		int position (int index) {
			return columns.indexOf (find (index));
		}

		void assertSame (ColumnArrangement arrangement, String where) {
			assertEquals (columns.size (), arrangement.getColumnCount (), where);
			int [] order = new int [columns.size ()];
			List<Integer> shown = new ArrayList<> ();
			for (int p = 0; p < order.length; p++) {
				int [] column = columns.get (p);
				order [p] = column [0];
				if (column [1] == 1) shown.add (column [0]);
				assertEquals (p, arrangement.getPosition (column [0]), where);
				assertEquals (column [1] == 1, arrangement.isVisible (column [0]), where);
				assertEquals (column [2], arrangement.getWidth (column [0]), where);
			}
			assertArrayEquals (order, arrangement.getOrder (), where);
			assertArrayEquals (shown.stream ().mapToInt (Integer::intValue).toArray (), arrangement.getVisibleOrder (), where);
			assertEquals (shown.size (), arrangement.getVisibleCount (), where);
		}
	}

	/* a mirror kept current from the change events alone: each event is one delta */
	static final class Mirror implements ColumnArrangement.Listener {
		final ColumnArrangement arrangement;
		final Model model;
		int events, resets;

		Mirror (ColumnArrangement arrangement) {
			this.arrangement = arrangement;
			model = new Model (arrangement.getColumnCount ());
		}

		@Override
		public void arrangementChanged (int kind, int column, int from, int to) {
			events++;
			switch (kind) {
				case ColumnArrangement.SHOWN -> model.find (column) [1] = 1;
				case ColumnArrangement.HIDDEN -> model.find (column) [1] = 0;
				case ColumnArrangement.RESIZED -> {
					assertEquals (from, model.find (column) [2], "the old width of the event");
					model.find (column) [2] = to;
				}
				case ColumnArrangement.MOVED -> {
					assertEquals (from, model.position (column), "the old position of the event");
					model.columns.add (to, model.columns.remove (from));
				}
				case ColumnArrangement.INSERTED -> {
					for (int [] other : model.columns) if (other [0] >= column) other [0]++;
					model.columns.add (to, new int [] {column, 1, ColumnArrangement.DEFAULT_WIDTH});
				}
				case ColumnArrangement.REMOVED -> {
					assertEquals (from, model.position (column), "the position of the removed column");
					model.columns.remove (from);
					for (int [] other : model.columns) if (other [0] > column) other [0]--;
				}
				default -> {
					/* RESET: everything is read again */
					resets++;
					model.columns.clear ();
					for (int index : arrangement.getOrder ()) {
						model.columns.add (new int [] {index, arrangement.isVisible (index) ? 1 : 0, arrangement.getWidth (index)});
					}
				}
			}
		}
	}

	@Test
	public void test_newArrangementShowsEveryColumnInIndexOrder () {
		ColumnArrangement arrangement = new ColumnArrangement (5);
		new Model (5).assertSame (arrangement, "new");
		assertEquals (5, arrangement.getVisibleCount ());
		new Model (0).assertSame (new ColumnArrangement (0), "empty");
		assertThrows (IllegalArgumentException.class, () -> new ColumnArrangement (-1));
	}

	@Test
	public void test_aHiddenColumnKeepsItsPlaceAndWidth () {
		ColumnArrangement arrangement = new ColumnArrangement (4);
		arrangement.setWidth (2, 120);
		arrangement.move (2, 0);
		arrangement.setVisible (2, false);
		assertArrayEquals (new int [] {0, 1, 3}, arrangement.getVisibleOrder ());
		assertArrayEquals (new int [] {2, 0, 1, 3}, arrangement.getOrder ());
		arrangement.move (3, 0);
		arrangement.setVisible (2, true);
		assertArrayEquals (new int [] {3, 2, 0, 1}, arrangement.getVisibleOrder (), "shown again where it stood");
		assertEquals (120, arrangement.getWidth (2));
	}

	@Test
	public void test_randomScriptsEqualAModelAndReplayFromEvents () {
		Random random = new Random (11);
		for (int round = 0; round < 60; round++) {
			int count = random.nextInt (9);
			ColumnArrangement arrangement = new ColumnArrangement (count);
			Model model = new Model (count);
			Mirror mirror = new Mirror (arrangement);
			arrangement.addListener (mirror);
			for (int step = 0; step < 120; step++) {
				int n = model.columns.size ();
				int column = n == 0 ? 0 : random.nextInt (n);
				switch (random.nextInt (n == 0 ? 2 : 9)) {
					case 0 -> {
						int index = random.nextInt (n + 1), at = random.nextInt (n + 1);
						arrangement.insertColumn (index, at);
						for (int [] other : model.columns) if (other [0] >= index) other [0]++;
						model.columns.add (at, new int [] {index, 1, ColumnArrangement.DEFAULT_WIDTH});
					}
					case 1 -> {
						arrangement.reset ();
						model.columns.clear ();
						for (int i = 0; i < n; i++) model.columns.add (new int [] {i, 1, ColumnArrangement.DEFAULT_WIDTH});
					}
					case 2 -> {
						arrangement.removeColumn (column);
						model.columns.remove (model.position (column));
						for (int [] other : model.columns) if (other [0] > column) other [0]--;
					}
					case 3, 4 -> {
						boolean visible = random.nextBoolean ();
						arrangement.setVisible (column, visible);
						model.find (column) [1] = visible ? 1 : 0;
					}
					case 5, 6 -> {
						int to = random.nextInt (n);
						arrangement.move (column, to);
						model.columns.add (to, model.columns.remove (model.position (column)));
					}
					case 7 -> {
						int width = random.nextInt (5) == 0 ? ColumnArrangement.DEFAULT_WIDTH : random.nextInt (300);
						arrangement.setWidth (column, width);
						model.find (column) [2] = width;
					}
					default -> {
						List<int []> shuffled = new ArrayList<> (model.columns);
						Collections.shuffle (shuffled, random);
						arrangement.setOrder (shuffled.stream ().mapToInt (c -> c [0]).toArray ());
						model.columns.clear ();
						model.columns.addAll (shuffled);
					}
				}
				String where = "round " + round + " step " + step;
				model.assertSame (arrangement, where);
				mirror.model.assertSame (arrangement, where + " (replayed from the events)");

				/* the state of every step restores into a fresh arrangement */
				String state = arrangement.saveState ();
				ColumnArrangement restored = new ColumnArrangement (model.columns.size ());
				restored.restoreState (state);
				model.assertSame (restored, where + " (restored)");
				assertEquals (state, restored.saveState ());
			}
		}
	}

	@Test
	public void test_aChangeThatChangesNothingIsNotReported () {
		ColumnArrangement arrangement = new ColumnArrangement (3);
		Mirror mirror = new Mirror (arrangement);
		arrangement.addListener (mirror);
		arrangement.setVisible (1, true);
		arrangement.move (2, 2);
		arrangement.setWidth (0, ColumnArrangement.DEFAULT_WIDTH);
		arrangement.setOrder (new int [] {0, 1, 2});
		arrangement.reset ();
		arrangement.restoreState (arrangement.saveState ());
		assertEquals (0, mirror.events);
		arrangement.setVisible (1, false);
		arrangement.setVisible (1, false);
		assertEquals (1, mirror.events);
		arrangement.removeListener (mirror);
		arrangement.setVisible (1, true);
		assertEquals (1, mirror.events, "a removed listener hears nothing");
	}

	@Test
	public void test_argumentsAreChecked () {
		ColumnArrangement arrangement = new ColumnArrangement (3);
		assertThrows (IllegalArgumentException.class, () -> arrangement.isVisible (3));
		assertThrows (IllegalArgumentException.class, () -> arrangement.setVisible (-1, true));
		assertThrows (IllegalArgumentException.class, () -> arrangement.move (0, 3));
		assertThrows (IllegalArgumentException.class, () -> arrangement.move (3, 0));
		assertThrows (IllegalArgumentException.class, () -> arrangement.setWidth (0, -2));
		assertThrows (IllegalArgumentException.class, () -> arrangement.setOrder (null));
		assertThrows (IllegalArgumentException.class, () -> arrangement.setOrder (new int [] {0, 1}));
		assertThrows (IllegalArgumentException.class, () -> arrangement.setOrder (new int [] {0, 1, 1}));
		assertThrows (IllegalArgumentException.class, () -> arrangement.setOrder (new int [] {0, 1, 3}));
		assertThrows (IllegalArgumentException.class, () -> arrangement.insertColumn (4, 0));
		assertThrows (IllegalArgumentException.class, () -> arrangement.insertColumn (0, 4));
		assertThrows (IllegalArgumentException.class, () -> arrangement.removeColumn (3));
		assertThrows (IllegalArgumentException.class, () -> arrangement.addListener (null));
		new Model (3).assertSame (arrangement, "nothing changed");
	}

	@Test
	public void test_restoreStateRejectsWhatItCannotRead () {
		ColumnArrangement arrangement = new ColumnArrangement (3);
		arrangement.move (2, 0);
		arrangement.setVisible (1, false);
		arrangement.setWidth (0, 77);
		String state = arrangement.saveState ();
		assertEquals ("ColumnArrangement/1 3\n2 1 -1\n0 1 77\n1 0 -1\n", state);
		String [] bad = {
			"", "x", "ColumnArrangement/2 3\n2 1 -1\n0 1 77\n1 0 -1\n", "ColumnArrangement/1 2\n0 1 -1\n1 1 -1\n",
			state.substring (0, state.length () - 1), state + "x", state + "\n", state.replace ("0 1 77", "2 1 77"),
			state.replace ("0 1 77", "3 1 77"), state.replace ("0 1 77", "0 2 77"), state.replace ("0 1 77", "0 1 -2"),
			state.replace ("0 1 77", "0 1"), state.replace ("0 1 77", "0 1 77 1"), state.replace ("77", "w"), state.replace ("\n", "\r\n"),
		};
		Mirror mirror = new Mirror (arrangement);
		arrangement.addListener (mirror);
		for (String text : bad) {
			assertThrows (IllegalArgumentException.class, () -> arrangement.restoreState (text), text);
			assertEquals (state, arrangement.saveState (), "a rejected state changes nothing");
		}
		assertThrows (IllegalArgumentException.class, () -> arrangement.restoreState (null));
		assertEquals (0, mirror.events);
	}
}
