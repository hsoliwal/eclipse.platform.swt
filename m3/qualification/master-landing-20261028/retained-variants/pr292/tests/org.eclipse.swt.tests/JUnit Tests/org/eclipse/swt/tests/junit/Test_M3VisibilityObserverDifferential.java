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
 * Recorded differential of M3VisibilityObserver against the official SWT jar (enhanced M3 API, leaf 11).
 * The official run records the rows of the Table and List whose bounds intersect the client area (public
 * API); this SWT records the rows replayed from the ENTERED and LEFT events after the event loop settled,
 * together with the public state of both runs. Recording directory: <code>-Dswt.viewport.recording</code>.
 */
public class Test_M3VisibilityObserverDifferential {
	/* This SWT only: the replayed visible rows of each widget. */
	static final class Fork {
		final BitSet table = new BitSet (), list = new BitSet ();

		Fork (M3OfficialScene scene) {
			M3VisibilityObserver.addListener (scene.table, event -> replay (table, event));
			M3VisibilityObserver.addListener (scene.list, event -> replay (list, event));
		}

		static void replay (BitSet rows, Event event) {
			if (event.detail == M3VisibilityObserver.ENTERED) rows.set (event.start, event.end + 1);
			else rows.clear (event.start, event.end + 1);
		}

		static String range (BitSet rows) {
			return rows.isEmpty () ? "-" : rows.nextSetBit (0) + ".." + (rows.length () - 1);
		}
	}

	static Fork fork;

	static String visible (M3OfficialScene scene) {
		if (M3OfficialScene.OFFICIAL) return "T " + scene.visibleTable () + " L " + scene.visibleList ();
		return "T " + Fork.range (fork.table) + " L " + Fork.range (fork.list);
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D33000BL, M3OfficialScene::next).widget ("Table")
			.probe ("visible", Test_M3VisibilityObserverDifferential::visible)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {
			if (!M3OfficialScene.OFFICIAL) fork = new Fork (scene);
		});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-visibility-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-visibility-virtual", SWT.VIRTUAL);
	}
}
