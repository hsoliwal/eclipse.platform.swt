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
package m3.i31.harness;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.ArrayList;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Diagnostic: which thread and which call path send SetData for a row far below the viewport of a virtual tree.
 * Creates and disposes a first tree (as a test suite does), then a tree of 100,000 items, and writes the stack of
 * the first SetData for a root index above 200, with the thread name and the SetData count per phase.
 * <p>
 * Usage: <code>SetDataTrace &lt;out-file&gt;</code>
 * </p>
 */
public final class SetDataTrace {
	private static final java.util.List<String> OUT = new ArrayList<> ();
	private static volatile int calls;
	private static volatile boolean traced;

	private SetDataTrace () {
	}

	public static void main (String [] args) throws IOException {
		Display display = new Display ();
		try {
			for (int round = 0; round < 2; round++) {
				Shell shell = new Shell (display);
				shell.setLayout (new FillLayout ());
				shell.setSize (320, 240);
				Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
				calls = 0;
				tree.addListener (SWT.SetData, e -> {
					calls++;
					((TreeItem) e.item).setText ("r" + e.index);
					if (e.index > 200 && !traced) {
						traced = true;
						StringWriter text = new StringWriter ();
						new Throwable ("SetData " + e.index + " on " + Thread.currentThread ().getName ()).printStackTrace (new PrintWriter (text));
						OUT.add (text.toString ());
					}
				});
				tree.setItemCount (100_000);
				OUT.add ("round " + round + " after setItemCount: " + calls);
				shell.open ();
				TreeOracle.pump (display);
				OUT.add ("round " + round + " after open: " + calls);
				shell.dispose ();
				TreeOracle.pump (display);
			}
		} finally {
			display.dispose ();
			Files.write (Paths.get (args [0]), OUT, StandardCharsets.UTF_8);
		}
	}
}
