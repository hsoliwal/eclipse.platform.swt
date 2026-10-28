/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/**
 * Thirty-second health probe of the viewport-mediated row widgets (viewport_probe.sh): one widget per run, 300 rows
 * in a shell on the test desktop, one line of facts: the viewport class, whether it is parked, the visibility gate's
 * verdict, the vertical bar's maximum and thumb, and the first row's text. GOOD means the rows materialize and the
 * bar spans the rows (the official 3.135.0 jar answers maximum 299, thumb 12 for a 300-row Tree). The user's rule:
 * every widget must be a viewport; this is the fastest answer to "is it one, on this jar?".
 * Usage: java -cp <probe classes>;<swt jar> ViewportProbe [Tree|Table|List] [rows]
 */
public class ViewportProbe {
	static Object field (Object target, Class<?> type, String name) {
		try {
			java.lang.reflect.Field f = type.getDeclaredField (name);
			f.setAccessible (true);
			return f.get (target);
		} catch (ReflectiveOperationException e) {
			return "n/a";
		}
	}

	static Object gate (Control control) {
		try {
			Class<?> gate = Class.forName ("org.eclipse.swt.internal.VisibilityGate");
			java.lang.reflect.Method m = gate.getDeclaredMethod ("defer", Control.class, int.class);
			m.setAccessible (true);
			return m.invoke (null, control, 2 | 8); // LAYOUT | DATA
		} catch (ReflectiveOperationException e) {
			return "n/a";
		}
	}

	public static void main (String [] args) throws Exception {
		String widget = args.length > 0 ? args [0] : "Tree";
		int rows = args.length > 1 ? Integer.parseInt (args [1]) : 300;
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setSize (400, 300);
		Control control;
		switch (widget) {
			case "Table" -> {
				/* a plain Table: the viewport mediates it (a SWT.VIRTUAL Table is the native owner-data list and has no viewport by design) */
				Table table = new Table (shell, SWT.BORDER);
				for (int i = 0; i < rows; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
				control = table;
			}
			case "List" -> {
				List list = new List (shell, SWT.BORDER | SWT.V_SCROLL);
				String [] items = new String [rows];
				for (int i = 0; i < rows; i++) items [i] = "row " + i;
				list.setItems (items);
				control = list;
			}
			default -> {
				Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
				tree.addListener (SWT.SetData, e -> ((TreeItem) e.item).setText ("row " + tree.indexOf ((TreeItem) e.item)));
				tree.setItemCount (rows);
				control = tree;
			}
		}
		control.setBounds (10, 10, 360, 240);
		shell.open ();
		for (int i = 0; i < 50; i++) {
			while (display.readAndDispatch ()) {
			}
			Thread.sleep (10);
		}
		Object viewport = field (control, control.getClass (), "viewport");
		Object parked = viewport == null || viewport instanceof String ? "n/a" : field (viewport, viewport.getClass (), "parked");
		int max = ((org.eclipse.swt.widgets.Scrollable) control).getVerticalBar ().getMaximum ();
		int thumb = ((org.eclipse.swt.widgets.Scrollable) control).getVerticalBar ().getThumb ();
		String first = switch (widget) {
			case "Table" -> ((Table) control).getItemCount () > 0 ? ((Table) control).getItem (0).getText () : "-";
			case "List" -> ((List) control).getItemCount () > 0 ? ((List) control).getItem (0) : "-";
			default -> String.valueOf (((Tree) control).getTopItem ());
		};
		/* mediated unless the startup mode is original: the viewport object must exist, the rows materialize and the bar spans them */
		boolean original = "original".equalsIgnoreCase (System.getProperty ("swt.widget.mode", "")) || "original".equalsIgnoreCase (System.getProperty ("swt.widget.mode." + widget, ""));
		boolean mediated = viewport != null && !(viewport instanceof String);
		boolean good = max == rows - 1 && thumb > 0 && first.contains ("row 0") && (original || mediated);
		System.out.println ((good ? "GOOD" : "BAD") + " widget=" + widget + " rows=" + rows + " viewport=" + (viewport == null ? "null" : viewport instanceof String ? viewport : viewport.getClass ().getSimpleName ()) + " parked=" + parked + " gate.defer=" + gate (control) + " vbar.max=" + max + " thumb=" + thumb + " first=" + first + " mode=" + System.getProperty ("swt.widget.mode"));
		display.dispose ();
		System.exit (good ? 0 : 1);
	}
}
