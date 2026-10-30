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
 * Recorded differential of M3ScrollMemento against the official SWT jar (enhanced M3 API, leaf 13).
 * Besides the shared operations, a memento step saves the top row of the Table or List, inserts or removes
 * rows above or below it, scrolls to the top and restores. The official run computes the anchor row by
 * arithmetic and calls <code>setTopIndex</code>; this SWT uses <code>save</code>, <code>remap</code> and
 * <code>restore</code>. The top index after the step is recorded in both runs, with the public state.
 */
public class Test_M3ScrollMementoDifferential {
	/* This SWT only. */
	static final class Fork {
		static long save (Scrollable widget) {
			return widget instanceof Table table ? M3ScrollMemento.save (table) : M3ScrollMemento.save ((List) widget);
		}

		static void restore (Scrollable widget, long memento) {
			if (widget instanceof Table table) M3ScrollMemento.restore (table, memento);
			else M3ScrollMemento.restore ((List) widget, memento);
		}
	}

	static int top (Scrollable widget) {
		return widget instanceof Table table ? table.getTopIndex () : ((List) widget).getTopIndex ();
	}

	static void setTop (Scrollable widget, int row) {
		if (widget instanceof Table table) table.setTopIndex (row);
		else ((List) widget).setTopIndex (row);
	}

	/* inserts (delta > 0) or removes (delta < 0) rows at index, through the public API */
	static void edit (M3OfficialScene scene, Scrollable widget, int index, int delta) {
		for (int i = 0; i < delta; i++) {
			if (widget instanceof Table table) new TableItem (table, SWT.NONE, index).setText (scene.text ());
			else ((List) widget).add (scene.text (), index);
		}
		if (delta < 0 && widget instanceof Table table) table.remove (index, index - delta - 1);
		if (delta < 0 && widget instanceof List list) list.remove (index, index - delta - 1);
	}

	static String memento (M3OfficialScene scene, boolean onTable, int at, int delta) {
		Scrollable widget = onTable ? scene.table : scene.list;
		int count = onTable ? scene.table.getItemCount () : scene.list.getItemCount ();
		if (count < 8) return "memento skipped";
		int index = at % count, change = delta < 0 ? -Math.min (-delta, count - index) : delta, row = top (widget);
		long memento = M3OfficialScene.OFFICIAL ? 0 : Fork.save (widget);
		edit (scene, widget, index, change);
		setTop (widget, 0);
		if (M3OfficialScene.OFFICIAL) oracle (widget, row, index, change);
		else Fork.restore (widget, M3ScrollMemento.remap (memento, index, change));
		return "memento top " + top (widget);
	}

	/* restore clamps the anchor to the last row */
	static void oracle (Scrollable widget, int row, int index, int change) {
		int rows = widget instanceof Table table ? table.getItemCount () : ((List) widget).getItemCount ();
		setTop (widget, Math.min (anchor (row, index, change), rows - 1));
	}

	/* the contract of remap: rows above stay, rows below move, a removed anchor becomes the first row after the removal */
	static int anchor (int row, int index, int change) {
		if (change > 0) return row >= index ? row + change : row;
		if (row >= index - change) return row + change;
		return row >= index ? index : row;
	}

	static ViewportDifferential.Op<M3OfficialScene> next (Random random, StringBuilder log) {
		if (random.nextInt (3) > 0) return M3OfficialScene.next (random, log);
		boolean onTable = random.nextBoolean ();
		int at = random.nextInt (200), delta = random.nextInt (13) - 6;
		log.append ("memento ").append (onTable ? "table " : "list ").append (at).append (' ').append (delta).append ('\n');
		return scene -> memento (scene, onTable, at, delta);
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D33000DL, Test_M3ScrollMementoDifferential::next).widget ("Table")
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-memento-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-memento-virtual", SWT.VIRTUAL);
	}
}
