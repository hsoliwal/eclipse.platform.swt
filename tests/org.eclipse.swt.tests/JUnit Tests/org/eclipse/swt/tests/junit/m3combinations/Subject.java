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
package org.eclipse.swt.tests.junit.m3combinations;

import java.util.*;
import java.util.List;
import java.util.stream.*;

import org.eclipse.swt.widgets.*;

/**
 * The subject of a matrix row: one widget kind behind the operations of the matrix. A subject keeps its data as a
 * source order (<code>order [row] = source id</code>, texts <code>item %06d</code>), so a sort/filter is a new order
 * and every mode sees the same texts. The operations answer what a client observes; {@link #state} is the probe.
 */
public abstract class Subject {
	/** Rows the select-range operation selects at most. */
	static final int RANGE = 10;
	int [] order = new int [0];
	final List<Widget> seen = new ArrayList<> ();

	/** The subject of a widget column value. */
	public static Subject of (String widget) {
		return switch (widget) {
			case "Table" -> new TableSubject ();
			case "Tree" -> new TreeSubject ();
			case "List" -> new ListSubject ();
			case "Combo" -> ComboSubject.combo ();
			case "CCombo" -> ComboSubject.cCombo ();
			case "StyledText" -> new StyledTextSubject ();
			case "Canvas" -> new CanvasSubject ();
			case "CTabFolder" -> new CTabFolderSubject ();
			case "Model" -> new ModelSubject ();
			default -> throw new IllegalArgumentException ("unknown widget " + widget);
		};
	}

	/** The text of a source id. */
	static String text (int id) {
		return String.format ("item %06d", id);
	}

	/** The text of a row of the current order. */
	String rowText (int row) {
		return text (order [row]);
	}

	/** The identity order of n rows. */
	static int [] identity (int n) {
		return IntStream.range (0, n).toArray ();
	}

	/** The order after sort/filter: reversed, without every source id divisible by 3. */
	static int [] sortedFiltered (int [] order) {
		return IntStream.range (0, order.length).map (i -> order [order.length - 1 - i]).filter (id -> id % 3 != 0).toArray ();
	}

	/** The texts of an order. */
	static String [] texts (int [] order) {
		return Arrays.stream (order).mapToObj (Subject::text).toArray (String []::new);
	}

	/** Creates the widget in a shell (no shell: the model layer). */
	abstract void create (Shell shell, int style);

	/** The widget, or null for the model layer. */
	abstract Widget widget ();

	/** Shows the rows of an order (the item count follows its length). */
	abstract void show (int [] order);

	/** Scrolls a row to the top (as the widget's own API does). */
	abstract void scrollTo (int row);

	/** Selects rows [start, end] (one row when the widget has single selection). */
	abstract void select (int start, int end);

	/** Replaces the text of a row. */
	abstract void update (int row, String text);

	/** What a client can read: count, top, selection, a text. */
	abstract String state ();

	/** The items to check after dispose (a sample for large counts). */
	abstract List<Widget> items ();

	/** Sets the row count (identity order). */
	final void setCount (int n) {
		order = identity (n);
		show (order);
	}

	/** Runs a matrix operation and answers what it observed. */
	final String operate (String op, int count) {
		int n = order.length;
		switch (op) {
			case "setItemCount" -> { setCount (count / 2); setCount (count); }
			case "scroll-top" -> { if (n > 0) scrollTo (0); }
			case "scroll-middle" -> { if (n > 0) scrollTo (n / 2); }
			case "scroll-bottom" -> { if (n > 0) scrollTo (n - 1); }
			case "select-range" -> { if (n > 0) select (n / 4, Math.min (n - 1, n / 4 + RANGE - 1)); }
			case "sort-filter" -> { order = sortedFiltered (order); show (order); }
			case "update-item" -> { if (n > 0) update (n / 2, "updated"); }
			case "dispose" -> { return dispose (); }
			default -> throw new IllegalArgumentException ("unknown operation " + op);
		}
		return op + " " + state ();
	}

	/** Disposes the widget; answers how many of the items seen are still alive (0 expected). */
	String dispose () {
		Widget widget = widget ();
		if (widget == null || widget.isDisposed ()) return "disposed";
		seen.addAll (items ());
		widget.dispose ();
		long alive = seen.stream ().filter (w -> !w.isDisposed ()).count ();
		return "disposed, items alive " + alive;
	}

	/** True once the widget is gone. */
	boolean disposed () {
		Widget widget = widget ();
		return widget != null && widget.isDisposed ();
	}

	/** Coalesced ranges of sorted indices, for example <code>[2-4,9]</code>. */
	static String ranges (int [] indices) {
		int [] sorted = indices.clone ();
		Arrays.sort (sorted);
		StringBuilder out = new StringBuilder ("[");
		for (int i = 0; i < sorted.length; i++) {
			int start = sorted [i];
			while (i + 1 < sorted.length && sorted [i + 1] == sorted [i] + 1) i++;
			out.append (out.length () > 1 ? "," : "").append (start).append (sorted [i] == start ? "" : "-" + sorted [i]);
		}
		return out.append (']').toString ();
	}

	/** A sample of an index range: first, middle and last (no duplicates). */
	static int [] sample (int n) {
		return n == 0 ? new int [0] : IntStream.of (0, n / 2, n - 1).distinct ().toArray ();
	}

	/** A text of the subject for the text APIs (the first row, or a fixed sentence when empty). */
	String sampleText () {
		return order.length == 0 ? "empty widget" : rowText (0) + " und Äpfel, éclair 日本語";
	}
}
