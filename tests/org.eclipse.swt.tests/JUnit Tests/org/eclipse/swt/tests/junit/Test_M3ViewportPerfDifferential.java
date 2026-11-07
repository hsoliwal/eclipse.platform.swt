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

import java.io.IOException;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.jupiter.api.Test;

/**
 * Recorded differential of the viewport performance leaf (<code>docs/m3/VIEWPORT_PERF_20261031.md</code>) against the
 * official SWT jar. Public API only, so the class loads with the official 3.135.0 jar, which records; this SWT compares
 * in the viewport and the original mode.
 * <ul>
 * <li>Tree: root and child counts of a plain <code>SWT.CHECK</code> Tree (CACHED_CHILD_COUNT), the top item after
 * <code>setTopItem (getItem (k))</code> on a plain and a <code>SWT.VIRTUAL</code> Tree (TOP_ITEM_SEEK), check state and
 * texts read back.</li>
 * <li>List: <code>computeSize</code> of a list without and with <code>SWT.H_SCROLL</code> (the widest row and the
 * horizontal extent) over random printable ASCII, numbered rows, mnemonics, tabs, umlauts, CJK and Arabic, in eight
 * fonts, one a synthesized bold italic raster font (DEFERRED_WIDTH_MEASURE, GLYPH_ADVANCE_LANE: the advance lane must answer what DrawText answers).</li>
 * </ul>
 * Recording directory: <code>-Dswt.viewport.recording</code>; without it the test is skipped.
 */
public class Test_M3ViewportPerfDifferential {
	static final String [][] FONTS = {{"Segoe UI", "9", "1"}, {"Times New Roman", "14", "2"}, {"Courier New", "10", "0"},
		{"Tahoma", "11", "0"}, {"MS Sans Serif", "8", "0"}, {"Arial", "20", "3"}, {"Consolas", "12", "0"}, {"MS Sans Serif", "10", "3"}};
	static final String [] SAMPLES = {"Item 1,234,567", "&File && more", "tab\there", "Zeile äöüß",
		"日本語の行", "مرحبا row", "WWWWWWWWWW", "iiiiiiiiiiiiiiii", ""};

	static final class TreeScene {
		final Shell shell;
		final Tree plain, virtual;
		int serial;

		TreeScene (Display display) {
			shell = new Shell (display);
			shell.setBounds (10, 10, 640, 330);
			plain = new Tree (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER);
			plain.setBounds (5, 5, 300, 280);
			new TreeColumn (plain, SWT.NONE).setWidth (260);
			virtual = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
			virtual.setBounds (315, 5, 300, 280);
			virtual.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("v" + event.index));
			shell.open ();
		}

		void reset () {
			plain.removeAll ();
			virtual.removeAll ();
			serial = 0;
		}
	}

	static ViewportDifferential.Op<TreeScene> nextTree (Random random, StringBuilder log) {
		int kind = random.nextInt (9), arg = random.nextInt (1 << 16);
		log.append ("tree ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> applyTree (scene, kind, arg);
	}

	static String applyTree (TreeScene scene, int kind, int arg) {
		Tree tree = scene.plain;
		int n = tree.getItemCount ();
		TreeItem root = n > 0 ? tree.getItem (arg % n) : null;
		switch (kind) {
			case 0 -> tree.setItemCount (arg % 400);
			case 1 -> { if (root != null) root.setText ("t" + scene.serial++); }
			case 2 -> { if (root != null) root.setItemCount (arg % 60); }
			case 3 -> { if (root != null) root.setChecked ((arg & 1) == 0); }
			case 4 -> { if (root != null) tree.setTopItem (root); }
			case 5 -> { if (root != null && root.getItemCount () > 0) root.setExpanded ((arg & 1) == 0); }
			default -> applyVirtual (scene.virtual, kind, arg);
		}
		return "op";
	}

	static void applyVirtual (Tree tree, int kind, int arg) {
		int n = tree.getItemCount ();
		if (kind == 6 || n == 0) tree.setItemCount (arg % 3_000);
		else tree.setTopItem (tree.getItem (arg % n));
	}

	static String counts (Tree tree) {
		int n = tree.getItemCount ();
		StringBuilder out = new StringBuilder ().append (n);
		for (int i = 0; i < Math.min (n, 4); i++) out.append (' ').append (tree.getItem (i).getItemCount ());
		return out.toString ();
	}

	static String top (Tree tree) {
		if (tree.getItemCount () == 0) return "-";
		TreeItem top = tree.getTopItem ();
		if (top == null) return "null";
		TreeItem parent = top.getParentItem ();
		int index = parent == null ? tree.indexOf (top) : parent.indexOf (top);
		String checked = (tree.getStyle () & SWT.CHECK) != 0 ? " " + top.getChecked () : "";
		return (parent == null ? "" : tree.indexOf (parent) + "/") + index + " " + top.getText () + checked;
	}

	static final class ListScene {
		final Shell shell;
		final List plain, wide;
		final Font [] fonts = new Font [FONTS.length];

		ListScene (Display display) {
			shell = new Shell (display);
			shell.setBounds (10, 10, 640, 330);
			plain = new List (shell, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
			plain.setBounds (5, 5, 300, 280);
			wide = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			wide.setBounds (315, 5, 300, 280);
			for (int i = 0; i < FONTS.length; i++) fonts [i] = new Font (display, FONTS [i][0], Integer.parseInt (FONTS [i][1]), Integer.parseInt (FONTS [i][2]));
			shell.open ();
		}

		void reset () {
			plain.removeAll ();
			wide.removeAll ();
			plain.setFont (null);
			wide.setFont (null);
		}

		void dispose () {
			shell.dispose ();
			for (Font font : fonts) font.dispose ();
		}
	}

	static String text (Random random) {
		int kind = random.nextInt (4);
		if (kind == 0) return SAMPLES [random.nextInt (SAMPLES.length)];
		if (kind == 1) return "Item " + String.format (java.util.Locale.ROOT, "%,d", random.nextInt (2_000_000));
		StringBuilder out = new StringBuilder ();
		for (int i = random.nextInt (kind == 2 ? 12 : 40); i > 0; i--) out.append ((char) (' ' + random.nextInt ('~' - ' ' + 1)));
		return out.toString ();
	}

	static ViewportDifferential.Op<ListScene> nextList (Random random, StringBuilder log) {
		int kind = random.nextInt (6), arg = random.nextInt (1 << 16);
		String [] texts = new String [kind == 0 ? random.nextInt (300) : 1];
		for (int i = 0; i < texts.length; i++) texts [i] = text (random);
		log.append ("list ").append (kind).append (' ').append (arg).append (' ').append (texts.length).append ('\n');
		return scene -> applyList (scene, kind, arg, texts);
	}

	static String applyList (ListScene scene, int kind, int arg, String [] texts) {
		for (List list : new List [] {scene.plain, scene.wide}) {
			int n = list.getItemCount ();
			switch (kind) {
				case 0 -> list.setItems (texts);
				case 1 -> list.add (texts [0], n == 0 ? 0 : arg % (n + 1));
				case 2 -> { if (n > 0) list.remove (arg % n); }
				case 3 -> list.setFont (arg % (FONTS.length + 1) == FONTS.length ? null : scene.fonts [arg % (FONTS.length + 1)]);
				case 4 -> { if (n > 0) list.setItem (arg % n, texts [0]); }
				default -> list.add (texts [0]);
			}
		}
		return "op";
	}

	static String size (List list) {
		Point size = list.computeSize (SWT.DEFAULT, SWT.DEFAULT);
		return size.x + "x" + size.y + " n" + list.getItemCount ();
	}

	@Test
	public void treeCountsAndTopItemsAnswerLikeTheOfficialJar () throws IOException {
		TreeScene scene = new TreeScene (Display.getDefault ());
		try {
			ViewportDifferential.of ("perf-tree", 0x4D335046L, Test_M3ViewportPerfDifferential::nextTree).widget ("Tree").seeds (6).steps (80)
				.probe ("counts", s -> counts (s.plain)).probe ("top", s -> top (s.plain))
				.probe ("vcount", s -> String.valueOf (s.virtual.getItemCount ())).probe ("vtop", s -> top (s.virtual))
				.beforeSeed (TreeScene::reset)
				.compareWithRecording (scene, ViewportDifferential.records ("Tree"), System.getProperty ("swt.viewport.recording"));
		} finally {
			scene.shell.dispose ();
		}
	}

	@Test
	public void listWidthsAnswerLikeTheOfficialJar () throws IOException {
		ListScene scene = new ListScene (Display.getDefault ());
		try {
			ViewportDifferential.of ("perf-list", 0x4D33504CL, Test_M3ViewportPerfDifferential::nextList).widget ("List").seeds (8).steps (120)
				.probe ("plain", s -> size (s.plain)).probe ("wide", s -> size (s.wide))
				.beforeSeed (ListScene::reset)
				.compareWithRecording (scene, ViewportDifferential.records ("List"), System.getProperty ("swt.viewport.recording"));
		} finally {
			scene.dispose ();
		}
	}
}
