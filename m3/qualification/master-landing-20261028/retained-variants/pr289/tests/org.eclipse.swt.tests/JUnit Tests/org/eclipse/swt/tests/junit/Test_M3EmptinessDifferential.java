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
import java.util.Random;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3Emptiness against the official SWT jar (enhanced M3 API, leaf 5).
 * <p>
 * Seeded scripts of public structural operations run on one Table, List and Tree (plain and
 * <code>SWT.VIRTUAL</code>). With the official jar on the class path the test records, per step, the
 * zero crossings the item counts make (the contract of M3Emptiness, derived from
 * <code>getItemCount</code> only), the stock <code>SWT.EmptinessChanged</code> events of the Tree and the
 * counts. With this SWT, in any mode, the same scripts compare: the crossing column is then written
 * from the M3Emptiness events actually received, so a missing, extra or repeated event, a wrong count,
 * or any change of the stock event fails with the seed's operation log. The recording directory is
 * <code>-Dswt.viewport.recording</code>; without it the test is skipped.
 * </p>
 */
public class Test_M3EmptinessDifferential {
	static final boolean OFFICIAL = "official".equals (ViewportDifferential.reference ("Table"));
	static final String [] KEYS = {"T", "L", "R"};

	/* One Table, List and Tree, the counts after the previous step, and the events of the current step. */
	static final class Scene {
		final Shell shell;
		final Table table;
		final List list;
		final Tree tree;
		final int [] last = new int [3];
		final StringBuilder stock = new StringBuilder (), m3 = new StringBuilder ();
		int serial;

		Scene (Display display, int style) {
			shell = new Shell (display);
			shell.setBounds (10, 10, 600, 300);
			table = new Table (shell, SWT.MULTI | SWT.BORDER | style);
			table.setBounds (5, 5, 180, 250);
			list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
			list.setBounds (195, 5, 180, 250);
			tree = new Tree (shell, SWT.MULTI | SWT.BORDER | style);
			tree.setBounds (385, 5, 180, 250);
			table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("t" + event.index));
			tree.addListener (SWT.SetData, event -> ((TreeItem) event.item).setText ("n" + event.index));
			tree.addListener (SWT.EmptinessChanged, event -> stock.append ('d').append (event.detail).append (' '));
			if (!OFFICIAL) Fork.listen (this);
			shell.open ();
		}

		int count (int widget) {
			return widget == 0 ? table.getItemCount () : widget == 1 ? list.getItemCount () : tree.getItemCount ();
		}

		String text () {
			return "x" + serial++;
		}

		void reset () {
			table.removeAll ();
			list.removeAll ();
			tree.removeAll ();
			Arrays.fill (last, 0);
			serial = 0;
			stock.setLength (0);
			m3.setLength (0);
		}
	}

	/* This SWT only: the M3 listeners (never loaded with the official jar). */
	static final class Fork {
		static void listen (Scene scene) {
			M3Emptiness.addListener (scene.table, event -> record (scene, "T", event));
			M3Emptiness.addListener (scene.list, event -> record (scene, "L", event));
			M3Emptiness.addListener (scene.tree, event -> record (scene, "R", event));
		}

		static void record (Scene scene, String key, Event event) {
			String kind = event.detail == M3Emptiness.BECAME_EMPTY ? "E" : "N";
			scene.m3.append (key).append (':').append (kind).append (event.count).append (' ');
		}
	}

	/* The crossing column: derived from the counts (official) or the received M3 events (this SWT). */
	static String crossings (Scene scene) {
		StringBuilder derived = new StringBuilder ();
		for (int w = 0; w < 3; w++) {
			int before = scene.last [w], after = scene.count (w);
			if (before > 0 && after == 0) derived.append (KEYS [w]).append (":E0 ");
			if (before == 0 && after > 0) derived.append (KEYS [w]).append (":N").append (after).append (' ');
			scene.last [w] = after;
		}
		return (OFFICIAL ? derived : scene.m3).toString ().trim ();
	}

	static String counts (Scene scene) {
		return "T" + scene.count (0) + " L" + scene.count (1) + " R" + scene.count (2);
	}

	static ViewportDifferential.Op<Scene> tableOp (int kind, Random random, StringBuilder log) {
		int arg = random.nextInt (4);
		log.append ("table op ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> table (scene, kind, arg);
	}

	static String table (Scene scene, int kind, int arg) {
		Table table = scene.table;
		int n = table.getItemCount ();
		switch (kind) {
			case 0 -> new TableItem (table, SWT.NONE).setText (scene.text ());
			case 1 -> new TableItem (table, SWT.NONE, 0).setText (scene.text ());
			case 2 -> { if (n > 0) table.remove (arg % n); }
			case 3 -> { if (n > 0) table.remove (0, Math.min (arg, n - 1)); }
			case 4 -> { if (n > 0) table.remove (new int [] {n - 1, 0}); }
			case 5 -> table.removeAll ();
			case 6 -> table.setItemCount (arg);
			case 7 -> { if (n > 0) table.getItem (0).dispose (); }
			default -> table.clearAll ();
		}
		return "table";
	}

	static ViewportDifferential.Op<Scene> listOp (int kind, Random random, StringBuilder log) {
		int arg = random.nextInt (4);
		log.append ("list op ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> list (scene, kind, arg);
	}

	static String list (Scene scene, int kind, int arg) {
		List list = scene.list;
		int n = list.getItemCount ();
		switch (kind) {
			case 0 -> list.add (scene.text ());
			case 1 -> list.add (scene.text (), 0);
			case 2 -> { if (n > 0) list.remove (arg % n); }
			case 3 -> { if (n > 0) list.remove (0, Math.min (arg, n - 1)); }
			case 4 -> { if (n > 0) list.remove (new int [] {n - 1, 0}); }
			case 5 -> list.removeAll ();
			case 6 -> { if (n > 0) list.remove (list.getItem (n - 1)); }
			case 7 -> { if (n > 0) list.setItem (0, scene.text ()); }
			default -> list.setItems (items (scene, arg));
		}
		return "list";
	}

	static String [] items (Scene scene, int count) {
		String [] items = new String [count];
		for (int i = 0; i < count; i++) items [i] = scene.text ();
		return items;
	}

	static ViewportDifferential.Op<Scene> treeOp (int kind, Random random, StringBuilder log) {
		int arg = random.nextInt (4);
		log.append ("tree op ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> tree (scene, kind, arg);
	}

	static String tree (Scene scene, int kind, int arg) {
		Tree tree = scene.tree;
		int n = tree.getItemCount ();
		switch (kind) {
			case 0 -> new TreeItem (tree, SWT.NONE).setText (scene.text ());
			case 1 -> new TreeItem (tree, SWT.NONE, 0).setText (scene.text ());
			case 2 -> { if (n > 0) new TreeItem (tree.getItem (0), SWT.NONE).setText (scene.text ()); }
			case 3 -> { if (n > 0) tree.getItem (arg % n).dispose (); }
			case 4 -> { if (n > 0 && tree.getItem (0).getItemCount () > 0) tree.getItem (0).getItem (0).dispose (); }
			case 5 -> tree.removeAll ();
			case 6 -> tree.setItemCount (arg);
			case 7 -> { if (n > 0) tree.getItem (0).setItemCount (arg); }
			default -> tree.clearAll (true);
		}
		return "tree";
	}

	static ViewportDifferential.Op<Scene> next (Random random, StringBuilder log) {
		int widget = random.nextInt (3), kind = random.nextInt (9);
		if (widget == 0) return tableOp (kind, random, log);
		return widget == 1 ? listOp (kind, random, log) : treeOp (kind, random, log);
	}

	static ViewportDifferential<Scene> harness (String name) {
		return ViewportDifferential.of (name, 0x4D330001L, Test_M3EmptinessDifferential::next).widget ("Table")
			.probe ("crossings", Test_M3EmptinessDifferential::crossings)
			.probe ("stock", scene -> scene.stock.toString ().trim ())
			.probe ("counts", Test_M3EmptinessDifferential::counts)
			.beforeSeed (Scene::reset)
			.beforeStep (scene -> {
				scene.stock.setLength (0);
				scene.m3.setLength (0);
			});
	}

	static void run (String name, int style) throws IOException {
		Scene scene = new Scene (Display.getDefault (), style);
		try {
			harness (name).compareWithRecording (scene, ViewportDifferential.records ("Table"), System.getProperty ("swt.viewport.recording"));
		} finally {
			scene.shell.dispose ();
		}
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-emptiness-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-emptiness-virtual", SWT.VIRTUAL);
	}
}
