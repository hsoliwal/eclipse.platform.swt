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
 * PREFETCH and CANCEL ranges; after every step the announced rows must lie inside the rows and equal the
 * resident range widened by the window (clamped), or be empty without a resident window. The column is
 * "ok" then, as in the official run, which records the public state the listeners must not change.
 */
public class Test_M3PrefetchDifferential {
	/* This SWT only. */
	static final class Fork {
		final BitSet table = new BitSet (), list = new BitSet ();

		Fork (M3OfficialScene scene) {
			M3Prefetch.setWindow (scene.table, 2, 3);
			M3Prefetch.setWindow (scene.list, 2, 3);
			M3Prefetch.addListener (scene.table, event -> replay (table, event));
			M3Prefetch.addListener (scene.list, event -> replay (list, event));
		}

		static void replay (BitSet rows, Event event) {
			if (event.detail == M3Prefetch.PREFETCH) rows.set (event.start, event.end + 1);
			else rows.clear (event.start, event.end + 1);
		}

		static String check (String name, BitSet announced, M3ItemRange resident, int count, boolean window) {
			BitSet expected = new BitSet ();
			if (window && !resident.isEmpty ()) expected.set (Math.max (0, resident.start () - 2), Math.min (count, resident.end () + 4));
			return expected.equals (announced) ? "ok" : name + " announced " + announced + " expected " + expected;
		}

		String verdict (M3OfficialScene scene) {
			/* the Table viewport serves tables without SWT.VIRTUAL; a virtual table is stock and announces nothing */
			boolean tableWindow = M3Mode.of (scene.table) == M3Mode.VIEWPORT && (scene.style & SWT.VIRTUAL) == 0;
			String t = check ("T", table, M3Residency.residentRange (scene.table), scene.table.getItemCount (), tableWindow);
			String l = check ("L", list, M3Residency.residentRange (scene.list), scene.list.getItemCount (), M3Mode.of (scene.list) == M3Mode.VIEWPORT);
			return t + " " + l;
		}
	}

	static Fork fork;

	static String prefetch (M3OfficialScene scene) {
		return M3OfficialScene.OFFICIAL ? "ok ok" : fork.verdict (scene);
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
