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
 * Recorded differential of M3Residency against the official SWT jar (enhanced M3 API, leaf 7).
 * The official run records the visible rows of the Table and List from public API. This SWT records the same
 * public rows when M3Residency's resident range covers them, the stats are ordered
 * (<code>FACADES &gt;= HELD_FACADES &gt;= 0</code>, unknown -1 allowed) and asking
 * <code>isMaterialized</code> twice gives the same answer (it never materializes); otherwise the column names
 * the violation. Recording directory: <code>-Dswt.viewport.recording</code>.
 */
public class Test_M3ResidencyDifferential {
	/* This SWT only. */
	static final class Fork {
		static String check (M3OfficialScene scene, String visible, M3ItemRange resident, long [] stats) {
			if (!visible.equals ("-")) {
				String [] ends = visible.split ("\\.\\.");
				int first = Integer.parseInt (ends [0]), last = Integer.parseInt (ends [1]);
				if (resident.isEmpty () || resident.start () > first || resident.end () < last) return "MISS " + resident + " " + visible;
			}
			boolean ordered = stats [M3Residency.FACADES] == -1 || stats [M3Residency.FACADES] >= stats [M3Residency.HELD_FACADES] && stats [M3Residency.HELD_FACADES] >= 0;
			return ordered ? visible : "STATS " + java.util.Arrays.toString (stats);
		}

		static String table (M3OfficialScene scene) {
			int count = scene.table.getItemCount ();
			for (int i = 0; i < Math.min (count, 64); i += 7) {
				if (M3Residency.isMaterialized (scene.table, i) != M3Residency.isMaterialized (scene.table, i)) return "MATERIALIZED " + i;
			}
			return check (scene, scene.visibleTable (), M3Residency.residentRange (scene.table), M3Residency.stats (scene.table));
		}

		static String list (M3OfficialScene scene) {
			return check (scene, scene.visibleList (), M3Residency.residentRange (scene.list), M3Residency.stats (scene.list));
		}
	}

	static String resident (M3OfficialScene scene) {
		if (M3OfficialScene.OFFICIAL) return "T " + scene.visibleTable () + " L " + scene.visibleList ();
		return "T " + Fork.table (scene) + " L " + Fork.list (scene);
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330007L, M3OfficialScene::next).widget ("Table")
			.probe ("resident", Test_M3ResidencyDifferential::resident)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-residency-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-residency-virtual", SWT.VIRTUAL);
	}
}
