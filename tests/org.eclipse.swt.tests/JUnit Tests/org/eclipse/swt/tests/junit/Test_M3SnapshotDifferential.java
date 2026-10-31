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
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3Snapshot against the official SWT jar (enhanced M3 API, row M3-SNAPSHOT). After
 * every shared operation the official run writes the Table's and List's snapshot fields from the public API
 * (bounds, client area, bars, item count, top row, rows whose bounds meet the client area, coalesced selection);
 * this SWT writes <code>M3Snapshot.state ()</code> without the pixel line. The pixels are compared by the
 * widgets' own viewport differentials.
 */
public class Test_M3SnapshotDifferential {
	/* This SWT only. */
	static final class Fork {
		static String state (Control control) {
			String state = M3Snapshot.capture (control).state ();
			return state.substring (0, state.lastIndexOf ("\npixels=")).replace ('\n', ';');
		}
	}

	static String bar (ScrollBar bar) {
		if (bar == null) return "-";
		return bar.getMinimum () + "," + bar.getMaximum () + "," + bar.getThumb () + "," + bar.getSelection () + (bar.getVisible () ? ",shown" : ",hidden");
	}

	static String ranges (int [] indices) {
		java.util.List<String> out = new ArrayList<> ();
		Arrays.sort (indices);
		for (int i = 0; i < indices.length; i++) {
			int start = indices [i];
			while (i + 1 < indices.length && indices [i + 1] == indices [i] + 1) i++;
			out.add ("M3ItemRange [" + start + ".." + indices [i] + "]");
		}
		return out.toString ();
	}

	static String official (Scrollable control, String visible, int count, int top, int [] selection) {
		String range = visible.equals ("-") ? "M3ItemRange []" : "M3ItemRange [" + visible + "]";
		return "class=" + control.getClass ().getName () + ";bounds=" + control.getBounds () + ";state=" + (control.getVisible () ? "visible" : "hidden")
			+ (control.getEnabled () ? " enabled" : " disabled") + ";client=" + control.getClientArea () + ";vbar=" + bar (control.getVerticalBar ())
			+ ";hbar=" + bar (control.getHorizontalBar ()) + ";items=" + count + ";top=" + top + ";visible=" + range + ";selection=" + ranges (selection);
	}

	static String snapshots (M3OfficialScene scene) {
		if (!M3OfficialScene.OFFICIAL) return Fork.state (scene.table) + " | " + Fork.state (scene.list);
		Table table = scene.table;
		org.eclipse.swt.widgets.List list = scene.list;
		return official (table, scene.visibleTable (), table.getItemCount (), table.getTopIndex (), table.getSelectionIndices ()) + " | "
			+ official (list, scene.visibleList (), list.getItemCount (), list.getTopIndex (), list.getSelectionIndices ());
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330015L, M3OfficialScene::next).widget ("Table")
			.probe ("snapshots", Test_M3SnapshotDifferential::snapshots)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-snapshot-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-snapshot-virtual", SWT.VIRTUAL);
	}
}
