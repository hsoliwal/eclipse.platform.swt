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

import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The viewport listener (brief rule 25 sub-rule, docs/m3/visibility/VISIBILITY_AND_VIEWPORT.md) through its firing
 * helper: event sequences for a scroll, a resize, an expand, a model delta inside and outside the window, and
 * hide/show; exactly one event per coalesced change (one event-loop turn), nothing while not part-visible,
 * VISIBILITY on the first event after a PartVisible, and nothing tracked without a listener.
 */
public class Test_org_eclipse_swt_internal_ViewportReporter {
	Display display;
	Shell shell;
	Canvas viewport;
	final java.util.List<ViewportEvent> events = new ArrayList<> ();
	static final int [] NONE = {};

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display, SWT.SHELL_TRIM);
		shell.setBounds (40, 40, 300, 300);
		shell.setLayout (new FillLayout ());
		viewport = new Canvas (shell, SWT.V_SCROLL);
		shell.open ();
		ViewportDifferential.drain (display);
	}

	@AfterEach
	public void tearDown () {
		if (shell != null && !shell.isDisposed ()) shell.dispose ();
	}

	void listen () {
		viewport.addViewportListener (events::add);
	}

	ViewportEvent single () {
		ViewportDifferential.drain (display);
		assertEquals (1, events.size (), () -> "events " + events);
		ViewportEvent event = events.get (0);
		events.clear ();
		return event;
	}

	void none () {
		ViewportDifferential.drain (display);
		assertEquals (0, events.size (), () -> "events " + events);
	}

	static void window (ViewportEvent e, int first, int last, int [] entered, int [] left, boolean extents, int cause) {
		assertEquals (first, e.firstVisible, "first");
		assertEquals (last, e.lastVisible, "last");
		assertArrayEquals (entered, e.entered, "entered");
		assertArrayEquals (left, e.left, "left");
		assertEquals (extents, e.extentsChanged, "extents");
		assertEquals (cause, e.cause, "cause");
	}

	@Test
	public void nothingIsTrackedWithoutAListener () {
		ViewportReporter.report (viewport, 0, 9, ViewportEvent.SCROLL, false);
		assertNull (viewport.getData (ViewportReporter.class.getName ()), "no reporter without a listener");
		listen ();
		none ();
	}

	@Test
	public void scrollResizeAndExpand () {
		listen ();
		ViewportReporter.report (viewport, 0, 9, ViewportEvent.LAYOUT, true);
		window (single (), 0, 9, new int [] {0, 9}, NONE, true, ViewportEvent.LAYOUT);
		ViewportReporter.report (viewport, 5, 14, ViewportEvent.SCROLL, false);
		window (single (), 5, 14, new int [] {10, 14}, new int [] {0, 4}, false, ViewportEvent.SCROLL);
		ViewportReporter.report (viewport, 5, 20, ViewportEvent.RESIZE, false);
		window (single (), 5, 20, new int [] {15, 20}, NONE, false, ViewportEvent.RESIZE);
		ViewportReporter.report (viewport, 5, 20, ViewportEvent.EXPAND, true, new int [] {8, 12}, null);
		window (single (), 5, 20, new int [] {8, 12}, NONE, true, ViewportEvent.EXPAND);
		ViewportReporter.report (viewport, 30, 45, ViewportEvent.SCROLL, false);
		window (single (), 30, 45, new int [] {30, 45}, new int [] {5, 20}, false, ViewportEvent.SCROLL);
		ViewportReporter.report (viewport, -1, -1, ViewportEvent.MODEL_DELTA, true);
		window (single (), -1, -1, NONE, new int [] {30, 45}, true, ViewportEvent.MODEL_DELTA);
	}

	@Test
	public void modelDeltasInsideAndOutsideTheWindow () {
		listen ();
		ViewportReporter.report (viewport, 100, 119, ViewportEvent.LAYOUT, true);
		single ();
		/* rows inserted above the window: the same rows stay on screen at shifted indexes, the extents change */
		ViewportReporter.report (viewport, 103, 122, ViewportEvent.MODEL_DELTA, true, NONE, NONE);
		window (single (), 103, 122, new int [] {120, 122}, new int [] {100, 102}, true, ViewportEvent.MODEL_DELTA);
		/* rows replaced inside the window: explicit entered and left ranges */
		ViewportReporter.report (viewport, 103, 122, ViewportEvent.MODEL_DELTA, false, new int [] {110, 111}, new int [] {110, 111});
		window (single (), 103, 122, new int [] {110, 111}, new int [] {110, 111}, false, ViewportEvent.MODEL_DELTA);
		/* a delta far below the window: only the extents */
		ViewportReporter.report (viewport, 103, 122, ViewportEvent.MODEL_DELTA, true);
		window (single (), 103, 122, NONE, NONE, true, ViewportEvent.MODEL_DELTA);
	}

	@Test
	public void shiftedRowsAreNeitherEnteredNorLeftAndRemovedRowsLeaveAtTheirOldIndexes () {
		listen ();
		ViewportReporter.report (viewport, 100, 119, ViewportEvent.LAYOUT, true);
		single ();
		/* 3 rows inserted above the window, the same 20 rows on screen at shifted indexes */
		ViewportReporter.shifted (viewport, 50, 3);
		ViewportReporter.report (viewport, 103, 122, ViewportEvent.MODEL_DELTA, true);
		window (single (), 103, 122, NONE, NONE, true, ViewportEvent.MODEL_DELTA);
		/* in one batch: 3 rows inserted above, then 2 removed inside (old indexes 105 and 106), 2 more come in below */
		ViewportReporter.shifted (viewport, 50, 3);
		ViewportReporter.shifted (viewport, 108, -2);
		ViewportReporter.report (viewport, 106, 125, ViewportEvent.MODEL_DELTA, true);
		window (single (), 106, 125, new int [] {124, 125}, new int [] {105, 106}, true, ViewportEvent.MODEL_DELTA);
		/* rows inserted inside the window: they entered; the last two rows were pushed out (at their old indexes) */
		ViewportReporter.shifted (viewport, 110, 2);
		ViewportReporter.report (viewport, 106, 125, ViewportEvent.MODEL_DELTA, true);
		window (single (), 106, 125, new int [] {110, 111}, new int [] {124, 125}, true, ViewportEvent.MODEL_DELTA);
	}

	@Test
	public void oneEventPerTurnCoalescesTheCausesAndTheRanges () {
		listen ();
		ViewportReporter.report (viewport, 0, 9, ViewportEvent.LAYOUT, true);
		single ();
		ViewportReporter.report (viewport, 2, 11, ViewportEvent.SCROLL, false);
		ViewportReporter.report (viewport, 4, 13, ViewportEvent.SCROLL, false);
		ViewportReporter.report (viewport, 4, 15, ViewportEvent.RESIZE, false, new int [] {6, 6}, null);
		ViewportReporter.report (viewport, 4, 15, ViewportEvent.MODEL_DELTA, true, new int [] {7, 8}, new int [] {7, 7});
		window (single (), 4, 15, new int [] {6, 8, 10, 15}, new int [] {0, 3, 7, 7}, true,
			ViewportEvent.SCROLL | ViewportEvent.RESIZE | ViewportEvent.MODEL_DELTA);
	}

	@Test
	public void nothingWhileHiddenAndVisibilityOnTheFirstEventAfterShow () {
		listen ();
		ViewportReporter.report (viewport, 0, 9, ViewportEvent.LAYOUT, true);
		single ();
		viewport.setVisible (false);
		ViewportDifferential.drain (display);
		for (int i = 0; i < 10_000; i++) ViewportReporter.report (viewport, i, i + 9, ViewportEvent.SCROLL, (i & 1) == 0);
		none ();
		viewport.setVisible (true);
		window (single (), 9999, 10008, new int [] {9999, 10008}, new int [] {0, 9}, true, ViewportEvent.SCROLL | ViewportEvent.VISIBILITY);
		/* showing again with nothing changed still reports the window once, with VISIBILITY */
		shell.setMinimized (true);
		ViewportDifferential.drain (display);
		none ();
		shell.setMinimized (false);
		window (single (), 9999, 10008, NONE, NONE, false, ViewportEvent.VISIBILITY);
	}

	@Test
	public void theUntypedEventCarriesTheSameFields () {
		java.util.List<Event> raw = new ArrayList<> ();
		viewport.addListener (SWT.Viewport, raw::add);
		ViewportReporter.report (viewport, 3, 7, ViewportEvent.SCROLL, true);
		ViewportDifferential.drain (display);
		assertEquals (1, raw.size ());
		Event e = raw.get (0);
		assertEquals (3, e.start);
		assertEquals (7, e.end);
		assertEquals (ViewportEvent.SCROLL, e.detail);
		assertEquals (1, e.count);
		assertArrayEquals (new int [] {3, 7}, ((int [][]) e.data) [0]);
		ViewportListener typed = events::add;
		viewport.addViewportListener (typed);
		viewport.removeViewportListener (typed);
		assertThrows (IllegalArgumentException.class, () -> viewport.removeViewportListener (null));
		assertTrue (new ViewportEvent (e).toString ().contains ("first=3 last=7"));
	}
}
