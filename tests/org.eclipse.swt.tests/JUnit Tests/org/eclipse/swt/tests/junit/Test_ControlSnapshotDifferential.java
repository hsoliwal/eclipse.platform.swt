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
 * Recorded differential of {@link ControlSnapshot} against the official SWT jar. After every shared operation of
 * the scene each run writes the snapshot fields of the Table and the List (bounds, state, client area, bars, item
 * count, top row, visible rows, selection ranges), without the pixel line: the pixels are compared by the
 * widgets' own viewport differentials. <code>ControlSnapshot</code> reads public API only, so both runs use it.
 */
public class Test_ControlSnapshotDifferential {

	static String state (Control control) {
		String state = ControlSnapshot.capture (control).state ();
		return state.substring (0, state.lastIndexOf ("
pixels=")).replace ('
', ';');
	}

	static String snapshots (M3OfficialScene scene) {
		return state (scene.table) + " | " + state (scene.list);
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330015L, M3OfficialScene::next).widget ("Table")
			.probe ("snapshots", Test_ControlSnapshotDifferential::snapshots)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("control-snapshot-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("control-snapshot-virtual", SWT.VIRTUAL);
	}
}
