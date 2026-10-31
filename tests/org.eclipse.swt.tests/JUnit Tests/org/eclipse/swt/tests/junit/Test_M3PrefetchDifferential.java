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

import java.io.*;
import java.util.BitSet;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3Prefetch against the official SWT jar (enhanced M3 API, leaf 9).
 * This SWT listens with a window of 2 rows before and 3 after on the Table and the List and replays the
 * PREFETCH and CANCEL ranges into the announced rows. The official run records, from public API only, the rows
 * in view (rows whose bounds meet the client area) widened by the same window and clamped to the rows; this SWT
 * records the announced rows, so in every mode they must be exactly those rows after every step.
 */
public class Test_M3PrefetchDifferential {
	static final int BEFORE = 2, AFTER = 3;

	/* This SWT only. */
	static final class Fork {
		final BitSet table = new BitSet (), list = new BitSet ();

		Fork (M3OfficialScene scene) {
			M3Prefetch.setWindow (scene.table, BEFORE, AFTER);
			M3Prefetch.setWindow (scene.list, BEFORE, AFTER);
			M3Prefetch.addListener (scene.table, event -> replay (table, event));
			M3Prefetch.addListener (scene.list, event -> replay (list, event));
		}

		static void replay (BitSet rows, Event event) {
			if (event.detail == M3Prefetch.PREFETCH) rows.set (event.start, event.end + 1);
			else rows.clear (event.start, event.end + 1);
		}

		String announced () {
			return "T " + ranges (table) + " L " + ranges (list);
		}
	}

	static Fork fork;

	/* "first..last" of a contiguous set, "-" when empty, the set otherwise */
	static String ranges (BitSet rows) {
		if (rows.isEmpty ()) return "-";
		int first = rows.nextSetBit (0), last = rows.length () - 1;
		return rows.cardinality () == last - first + 1 ? first + ".." + last : rows.toString ();
	}

	/* the rows in view ("first..last" or "-" from public API) widened by the window and clamped to the rows */
	static String window (String visible, int count) {
		if ("-".equals (visible)) return "-";
		String [] bounds = visible.split ("[.][.]");
		int first = Math.max (0, Integer.parseInt (bounds [0]) - BEFORE);
		int last = Math.min (count - 1, Integer.parseInt (bounds [1]) + AFTER);
		return first + ".." + last;
	}

	static String prefetch (M3OfficialScene scene) {
		if (!M3OfficialScene.OFFICIAL) return fork.announced ();
		return "T " + window (scene.visibleTable (), scene.table.getItemCount ()) + " L " + window (scene.visibleList (), scene.list.getItemCount ());
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330009L, M3OfficialScene::next).widget ("Table")
			.probe ("prefetch", Test_M3PrefetchDifferential::prefetch)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {
			if (!M3OfficialScene.OFFICIAL) fork = new Fork (scene);
		});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-prefetch-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-prefetch-virtual", SWT.VIRTUAL);
	}
}
