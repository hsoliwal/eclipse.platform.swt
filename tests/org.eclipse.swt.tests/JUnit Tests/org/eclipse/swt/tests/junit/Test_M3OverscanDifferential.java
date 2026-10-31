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

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3Overscan against the official SWT jar (enhanced M3 API, M3-OVERSCAN). This SWT sets an
 * overscan of 4 rows before and 6 after on the Table and the List and records their resident ranges; the official
 * run records, from public API only, the rows in view (rows whose bounds meet the client area) widened the same way
 * and clamped to the rows. In every mode the two must be equal after every step.
 */
public class Test_M3OverscanDifferential {
	static final int BEFORE = 4, AFTER = 6;

	/* This SWT only. */
	static final class Fork {
		static void set (M3OfficialScene scene) {
			M3Overscan.set (scene.table, BEFORE, AFTER);
			M3Overscan.set (scene.list, BEFORE, AFTER);
		}

		static String resident (M3OfficialScene scene) {
			return "T " + range (M3Residency.residentRange (scene.table)) + " L " + range (M3Residency.residentRange (scene.list));
		}

		static String range (M3ItemRange range) {
			return range.isEmpty () ? "-" : range.start () + ".." + range.end ();
		}
	}

	/* the rows in view ("first..last" or "-" from public API) widened and clamped to the rows */
	static String widened (String visible, int count) {
		if ("-".equals (visible)) return "-";
		String [] bounds = visible.split ("[.][.]");
		int first = Math.max (0, Integer.parseInt (bounds [0]) - BEFORE);
		int last = Math.min (count - 1, Integer.parseInt (bounds [1]) + AFTER);
		return first + ".." + last;
	}

	static String resident (M3OfficialScene scene) {
		if (!M3OfficialScene.OFFICIAL) return Fork.resident (scene);
		return "T " + widened (scene.visibleTable (), scene.table.getItemCount ()) + " L " + widened (scene.visibleList (), scene.list.getItemCount ());
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330017L, M3OfficialScene::next).widget ("Table")
			.probe ("resident", Test_M3OverscanDifferential::resident)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {
			if (!M3OfficialScene.OFFICIAL) Fork.set (scene);
		});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-overscan-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-overscan-virtual", SWT.VIRTUAL);
	}
}
