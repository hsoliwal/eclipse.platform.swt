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
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of M3ProxyModel against the official SWT jar (enhanced M3 API, leaf 14).
 * A virtual Table shows a filtered and sorted view of a source of rows. The official run computes the view
 * with boxed streams and drives the table with <code>setItemCount</code>, <code>clearAll</code> and its own
 * <code>SWT.SetData</code> listener; this SWT binds an M3ProxyModel. Both record the view size, the top index and
 * the texts of the first rows.
 */
public class Test_M3ProxyModelDifferential {
	static final class Scene {
		final Shell shell;
		final Table table;
		int source = 50, modulus = 2, direction = 1;
		int [] view = new int [0];

		Scene (Display display) {
			shell = new Shell (display);
			shell.setBounds (10, 10, 260, 330);
			table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
			table.setBounds (5, 5, 240, 280);
			if (M3OfficialScene.OFFICIAL) table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("s" + view [event.index]));
			shell.open ();
		}

		int key (int s) {
			return (s * 7919) % 101;
		}

		void show () {
			if (M3OfficialScene.OFFICIAL) {
				view = IntStream.range (0, source).filter (s -> s % modulus != 0).boxed ()
					.sorted ((a, b) -> direction * Integer.compare (key (a), key (b))).mapToInt (Integer::intValue).toArray ();
				table.setItemCount (view.length);
				table.clearAll ();
			} else {
				Fork.bind (this);
			}
		}
	}

	/* This SWT only. */
	static final class Fork {
		static void bind (Scene scene) {
			org.eclipse.swt.m3.M3ProxyModel model = new org.eclipse.swt.m3.M3ProxyModel (scene.source).addFilter (s -> s % scene.modulus != 0)
				.addSort ((a, b) -> scene.direction * Integer.compare (scene.key (a), scene.key (b))).refresh ();
			model.bind (scene.table, (item, s) -> item.setText ("s" + s));
		}
	}

	static String apply (Scene scene, int kind, int arg) {
		switch (kind) {
			case 0 -> scene.modulus = 2 + arg % 4;
			case 1 -> scene.direction = -scene.direction;
			case 2 -> scene.source = arg * 10;
			default -> {
				scene.table.setTopIndex (arg);
				return "top";
			}
		}
		scene.show ();
		return "view";
	}

	static String rows (Scene scene) {
		int count = scene.table.getItemCount ();
		StringBuilder out = new StringBuilder ().append (count).append ('@').append (scene.table.getTopIndex ());
		for (int i = 0; i < Math.min (count, 12); i++) out.append (' ').append (scene.table.getItem (i).getText ());
		return out.toString ();
	}

	static ViewportDifferential.Op<Scene> next (Random random, StringBuilder log) {
		int kind = random.nextInt (5), arg = random.nextInt (60);
		log.append ("op ").append (kind).append (' ').append (arg).append ('\n');
		return scene -> apply (scene, kind, arg);
	}

	@Test
	public void boundVirtualTableAnswersLikeTheOfficialJar () throws IOException {
		Scene scene = new Scene (Display.getDefault ());
		ViewportDifferential<Scene> harness = ViewportDifferential.of ("m3-proxymodel", 0x4D33000EL, Test_M3ProxyModelDifferential::next).widget ("Table")
			.probe ("rows", Test_M3ProxyModelDifferential::rows)
			.beforeSeed (s -> {
				s.source = 50;
				s.modulus = 2;
				s.direction = 1;
				s.show ();
			});
		try {
			harness.compareWithRecording (scene, ViewportDifferential.records ("Table"), System.getProperty ("swt.viewport.recording"));
		} finally {
			scene.shell.dispose ();
		}
	}
}
