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

import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.ArrayList;

import javax.swing.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Reads the Swing state behind an Initiative 31 Tree (look and feel, inner JTree row height, layout cache) by
 * reflection. Initiative 31 only; evidence for the viewport design, not a test.
 */
public final class SwingProbe {
	private SwingProbe () {
	}

	public static void main (String [] args) throws Exception {
		java.util.List<String> out = new ArrayList<> ();
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setSize (320, 240);
		Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER | SWT.MULTI);
		tree.addListener (SWT.SetData, e -> ((TreeItem) e.item).setText ("r" + e.index));
		tree.setItemCount (100);
		shell.open ();
		TreeOracle.pump (display);
		Object handle = field (tree, "handle");
		Object treeTable = Class.forName ("org.eclipse.swt.internal.swing.CControl").getMethod ("getSwingComponent").invoke (handle);
		Method inner = Class.forName ("org.eclipse.swt.internal.swing.JTreeTable").getDeclaredMethod ("getInnerTree");
		inner.setAccessible (true);
		JTree jtree = (JTree) inner.invoke (treeTable);
		out.add ("laf=" + UIManager.getLookAndFeel ().getClass ().getName ());
		out.add ("Tree.rowHeight(UIManager)=" + UIManager.get ("Tree.rowHeight"));
		out.add ("jtree.rowHeight=" + jtree.getRowHeight () + " largeModel=" + jtree.isLargeModel () + " rowCount=" + jtree.getRowCount ());
		out.add ("row0=" + jtree.getRowBounds (0) + " row1=" + jtree.getRowBounds (1));
		out.add ("swt itemHeight=" + tree.getItemHeight () + " clientArea=" + tree.getClientArea ());
		display.dispose ();
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
	}

	static Object field (Object target, String name) throws ReflectiveOperationException {
		for (Class<?> c = target.getClass (); c != null; c = c.getSuperclass ()) {
			try {
				Field f = c.getDeclaredField (name);
				f.setAccessible (true);
				return f.get (target);
			} catch (NoSuchFieldException e) {
				// next superclass
			}
		}
		throw new NoSuchFieldException (name);
	}
}
