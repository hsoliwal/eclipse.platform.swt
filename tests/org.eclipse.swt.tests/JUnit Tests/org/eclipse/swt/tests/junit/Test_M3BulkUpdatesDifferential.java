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
import java.util.Random;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3BulkUpdates against the official SWT jar (enhanced M3 API, leaf 8).
 * A burst step runs three public operations on one widget: unbatched in the official run, inside
 * <code>M3BulkUpdates.run</code> in this SWT. The public state after the burst (counts, top indices, visible
 * rows and the selection) must be the same.
 */
public class Test_M3BulkUpdatesDifferential {
	/* This SWT only. */
	static final class Fork {
		static void run (M3OfficialScene scene, int widget, Runnable edits) {
			if (widget == 0) M3BulkUpdates.run (scene.table, edits);
			else if (widget == 1) M3BulkUpdates.run (scene.list, edits);
			else M3BulkUpdates.run (scene.tree, edits);
		}
	}

	static String burst (M3OfficialScene scene, int widget, int [] kinds, int [] args) {
		Runnable edits = () -> {
			for (int i = 0; i < kinds.length; i++) {
				if (widget == 0) scene.tableOp (kinds [i], args [i]);
				else if (widget == 1) scene.listOp (kinds [i], args [i]);
				else scene.treeOp (kinds [i], args [i]);
			}
		};
		if (M3OfficialScene.OFFICIAL) edits.run ();
		else Fork.run (scene, widget, edits);
		return "burst " + widget;
	}

	static String state (M3OfficialScene scene) {
		return scene.visibleTable () + " " + scene.visibleList () + " " + scene.visibleTree () + " sel " + scene.table.getSelectionCount () + "/" + scene.list.getSelectionCount ();
	}

	static ViewportDifferential.Op<M3OfficialScene> next (Random random, StringBuilder log) {
		if (random.nextInt (2) > 0) return M3OfficialScene.next (random, log);
		int widget = random.nextInt (3);
		int [] kinds = {random.nextInt (10), random.nextInt (10), random.nextInt (10)}, args = {random.nextInt (40), random.nextInt (40), random.nextInt (40)};
		log.append ("burst ").append (widget).append (' ').append (java.util.Arrays.toString (kinds)).append (java.util.Arrays.toString (args)).append ('\n');
		return scene -> burst (scene, widget, kinds, args);
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330008L, Test_M3BulkUpdatesDifferential::next).widget ("Table")
			.probe ("state", Test_M3BulkUpdatesDifferential::state)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-bulk-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-bulk-virtual", SWT.VIRTUAL);
	}
}
