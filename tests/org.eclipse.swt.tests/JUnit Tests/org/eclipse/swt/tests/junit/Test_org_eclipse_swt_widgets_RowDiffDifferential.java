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
 * Recorded differential of RowDiff against the official SWT jar (enhanced M3 API, leaf 10).
 * A diff step derives a new id order from the current items (drops, insertions, moves). The official run
 * rebuilds the List with <code>setItems</code> and the plain Table with <code>removeAll</code> and new items, in
 * both runs; both then scroll to the top and deselect, and record the item texts (hashed) and the public state.
 * The diff of RowDiff is compared with that rebuild in process by RowDiffTests ('Eclipse SWT Tests/win32').
 */
public class Test_org_eclipse_swt_widgets_RowDiffDifferential {
	static long [] ids (String [] texts) {
		return Arrays.stream (texts).mapToLong (text -> Long.parseLong (text.substring (1))).toArray ();
	}

	static long [] reorder (M3OfficialScene scene, long [] ids, long seed) {
		Random random = new Random (seed);
		java.util.List<Long> out = new ArrayList<> ();
		for (long id : ids) if (random.nextInt (5) > 0) out.add (id);
		for (int i = random.nextInt (6); i > 0; i--) out.add (random.nextInt (out.size () + 1), (long) scene.serial++);
		for (int i = 0; i < 3 && out.size () > 1; i++) out.add (random.nextInt (out.size ()), out.remove (random.nextInt (out.size ())));
		return out.stream ().mapToLong (Long::longValue).toArray ();
	}

	static String diff (M3OfficialScene scene, long seed) {
		org.eclipse.swt.widgets.List list = scene.list;
		long [] before = ids (list.getItems ()), after = reorder (scene, before, seed);
		list.setItems (Arrays.stream (after).mapToObj (id -> "x" + id).toArray (String []::new));
		if (scene.style == SWT.NONE) diffTable (scene, seed);
		list.deselectAll ();
		list.setTopIndex (0);
		scene.table.deselectAll ();
		scene.table.setTopIndex (0);
		return "diff";
	}

	static void diffTable (M3OfficialScene scene, long seed) {
		Table table = scene.table;
		String [] texts = Arrays.stream (table.getItems ()).map (TableItem::getText).toArray (String []::new);
		/* rows made by setItemCount have no id: no diff on that table */
		if (!Arrays.stream (texts).allMatch (text -> text.matches ("x[0-9]+"))) return;
		long [] before = ids (texts), after = reorder (scene, before, seed + 1);
		table.removeAll ();
		for (long id : after) new TableItem (table, SWT.NONE).setText ("x" + id);
	}

	static String items (M3OfficialScene scene) {
		String table = scene.style == SWT.NONE ? String.join (",", Arrays.stream (scene.table.getItems ()).map (TableItem::getText).toArray (String []::new)) : "";
		return Integer.toHexString (String.join (",", scene.list.getItems ()).hashCode ()) + " " + Integer.toHexString (table.hashCode ());
	}

	static ViewportDifferential.Op<M3OfficialScene> next (Random random, StringBuilder log) {
		if (random.nextInt (3) > 0) return M3OfficialScene.next (random, log);
		long seed = random.nextLong ();
		log.append ("diff ").append (seed).append ('\n');
		return scene -> diff (scene, seed);
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D33000AL, Test_org_eclipse_swt_widgets_RowDiffDifferential::next).widget ("Table")
			.probe ("items", Test_org_eclipse_swt_widgets_RowDiffDifferential::items)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-diffapply-plain", SWT.NONE);
	}

	@Test
	public void listBesideAVirtualTableAnswersLikeTheOfficialJar () throws IOException {
		run ("m3-diffapply-virtual", SWT.VIRTUAL);
	}
}
