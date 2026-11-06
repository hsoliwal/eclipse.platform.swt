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
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3WidthTrack against the official SWT jar (enhanced M3 API, rows M3-WIDTH-TRACK and
 * M3-SHAPING-MEASURE). After every shared operation the official run records the widest row and width of the
 * Table and the List from a fresh <code>GC.textExtent</code> scan over the public items; this SWT records
 * M3WidthTrack's answers, which reuse widths measured in earlier steps.
 */
public class Test_M3WidthTrackDifferential {
	/* This SWT only. */
	static final class Fork {
		static String widths (M3OfficialScene scene) {
			return "T " + M3WidthTrack.widestRow (scene.table, 0) + "/" + M3WidthTrack.contentWidth (scene.table, 0)
				+ " L " + M3WidthTrack.widestRow (scene.list) + "/" + M3WidthTrack.contentWidth (scene.list);
		}
	}

	/* the oracle: a fresh GC scan, first widest row */
	static String scan (Control control, String [] texts, Font [] fonts) {
		GC gc = new GC (control);
		try {
			int best = 0, row = -1;
			for (int i = 0; i < texts.length; i++) {
				gc.setFont (fonts [i]);
				int width = gc.textExtent (texts [i]).x;
				if (row < 0 || width > best) {
					best = width;
					row = i;
				}
			}
			return row + "/" + best;
		} finally {
			gc.dispose ();
		}
	}

	static String widths (M3OfficialScene scene) {
		if (!M3OfficialScene.OFFICIAL) return Fork.widths (scene);
		TableItem [] items = scene.table.getItems ();
		String [] texts = new String [items.length];
		Font [] fonts = new Font [items.length];
		for (int i = 0; i < items.length; i++) {
			texts [i] = items [i].getText (0);
			fonts [i] = items [i].getFont (0);
		}
		String [] listItems = scene.list.getItems ();
		Font [] listFonts = new Font [listItems.length];
		java.util.Arrays.fill (listFonts, scene.list.getFont ());
		return "T " + scan (scene.table, texts, fonts) + " L " + scan (scene.list, listItems, listFonts);
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330012L, M3OfficialScene::next).widget ("Table")
			.probe ("widths", Test_M3WidthTrackDifferential::widths)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-widthtrack-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-widthtrack-virtual", SWT.VIRTUAL);
	}
}
