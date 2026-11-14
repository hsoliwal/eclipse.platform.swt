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
import java.util.Arrays;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of the scroll bars against the official SWT jar (row SCROLLBAR-GEOMETRY). After every
 * shared operation both runs record the vertical scroll bars of the Table, List and Tree from the stock
 * getters; the internal path is compared with the stock path in process by ScrollBarModelTests ('Eclipse SWT Tests/win32') (the lanes, which with the viewport are the logical
 * bars the viewport owns, equal the getters).
 */
public class Test_org_eclipse_swt_widgets_ScrollBarModelDifferential {
	static String getters (Scrollable widget) {
		ScrollBar bar = widget.getVerticalBar ();
		return Arrays.toString (new int [] {bar.getMinimum (), bar.getMaximum (), bar.getThumb (), bar.getIncrement (),
			bar.getPageIncrement (), bar.getSelection (), bar.getVisible () ? 1 : 0, bar.getEnabled () ? 1 : 0});
	}

	static String bars (M3OfficialScene scene) {
		StringBuilder out = new StringBuilder ();
		for (Scrollable widget : new Scrollable [] {scene.table, scene.list, scene.tree}) {
			out.append (getters (widget)).append (' ');
		}
		return out.toString ().trim ();
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330013L, M3OfficialScene::next).widget ("Table")
			.probe ("bars", Test_org_eclipse_swt_widgets_ScrollBarModelDifferential::bars)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-scrollbar-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-scrollbar-virtual", SWT.VIRTUAL);
	}
}
