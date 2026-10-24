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
package org.eclipse.swt.widgets;

import java.util.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.win32.*;

/**
 * The footer row of a win32 Tree (BEHAVIOURS.md "Tree footer", brief rules 24 and 25): a strip pinned at the bottom of
 * the Tree's client area, one cell per column (the whole width without columns), each showing that column's aggregate
 * over the rows in scope. It exists only while the footer is on or a column has an aggregate; otherwise the Tree has
 * no footer state and no footer work (stock behaviour).
 * <p>
 * Values come from the cells the application sets (TreeItem.setText): numbers are parsed once per write into the
 * column's {@link VirtualTreeAggregate}, which keeps the subtree aggregates by deltas (O(depth) per write, expansion
 * or removal). Rows are keyed by a footer id per item. In the stock path every item is a row of the engine, so COUNT
 * comes from it; in the viewport (SWT.VIRTUAL) only rows the application gave data have values, and COUNT comes from
 * the topology: the known rows (scope ALL, FILTERED) or the rows of expanded branches (VISIBLE).
 * </p>
 * Aggregate kinds and scopes are the shared values of M3Footer (Table and Tree): NONE 0, COUNT 1, FILLED 2, DISTINCT 3
 * (not kept yet: NONE), SUM 4, AVERAGE 5, MIN 6, MAX 7, CUSTOM 8 (not kept yet: NONE); ALL 0, FILTERED 1, VISIBLE 2.
 */
final class TreeFooter {
	static final int NONE = 0, COUNT = 1, FILLED = 2, DISTINCT = 3, SUM = 4, AVERAGE = 5, MIN = 6, MAX = 7, CUSTOM = 8;
	static final int ALL = 0, FILTERED = 1, VISIBLE = 2;
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.widgets.Tree");

	final Tree tree;
	boolean visible;
	int scope = ALL;
	/* per column (index 0 without columns): the kind, and the engine fed by that column's cells */
	int [] kinds = new int [1];
	VirtualTreeAggregate [] engines = new VirtualTreeAggregate [1];
	/* footer ids of items: dense, reused after the item is gone */
	private int nextId;
	private int [] free = new int [8];
	private int freeCount;
	/* rebuilds of a column's engine from the items (aggregate switched on or changed): the gate logs them */
	long rebuilds;

	TreeFooter (Tree tree) {
		this.tree = tree;
	}

	/* ---------------------------------------------------------------- configuration */

	void setKind (int column, int kind) {
		if (kind < NONE || kind > CUSTOM) kind = NONE;
		if (kind == DISTINCT || kind == CUSTOM) kind = NONE;
		ensureColumns (column + 1);
		int old = kinds [column];
		if (old == kind) return;
		kinds [column] = kind;
		boolean filledBefore = old == FILLED, filledNow = kind == FILLED;
		if (kind == NONE) {
			engines [column] = null;
		} else if (engines [column] == null || filledBefore != filledNow) {
			rebuild (column);
		} else if ((kind == MIN || kind == MAX) && engines [column] != null) {
			engines [column].trackMinMax (true);
		}
		redraw ();
	}

	int kind (int column) {
		return column < kinds.length ? kinds [column] : NONE;
	}

	void setScope (int scope) {
		if (scope < ALL || scope > VISIBLE) return;
		if (this.scope == scope) return;
		this.scope = scope;
		for (VirtualTreeAggregate engine : engines) {
			if (engine != null) engine.setScope (scope);
		}
		redraw ();
	}

	void setVisible (boolean visible) {
		if (this.visible == visible) return;
		this.visible = visible;
		tree.footerLayout ();
	}

	/** Pixels the footer takes from the bottom of the client area. */
	int height () {
		return visible ? Math.max (1, tree.getItemHeightInPixels ()) + 1 : 0;
	}

	/* a column was created at index (columns after it move) or destroyed */
	void columnCreated (int index, int count) {
		ensureColumns (count);
		if (count <= 1) return;
		System.arraycopy (kinds, index, kinds, index + 1, count - 1 - index);
		System.arraycopy (engines, index, engines, index + 1, count - 1 - index);
		kinds [index] = NONE;
		engines [index] = null;
	}

	void columnDestroyed (int index, int count) {
		if (index >= kinds.length) return;
		int tail = Math.min (kinds.length - 1, count) - index;
		if (tail > 0) {
			System.arraycopy (kinds, index + 1, kinds, index, tail);
			System.arraycopy (engines, index + 1, engines, index, tail);
		}
		kinds [Math.min (kinds.length - 1, count)] = NONE;
		engines [Math.min (engines.length - 1, count)] = null;
		redraw ();
	}

	private void ensureColumns (int count) {
		if (kinds.length >= count) return;
		kinds = Arrays.copyOf (kinds, count);
		engines = Arrays.copyOf (engines, count);
	}

	/* ---------------------------------------------------------------- rows */

	/* the cell text of an item changed: its value goes into the column's engine (O(depth)) */
	void textChanged (TreeItem item, int column, String text) {
		if (column >= engines.length || engines [column] == null) return;
		int id = row (item);
		if (id < 0) return;
		engines [column].setValue (id, value (kinds [column], text));
		redraw ();
	}

	/* a new item (stock path: every item is a row, so COUNT and the visible scope follow the items) */
	void created (TreeItem item) {
		if (!active () || tree.viewport != null) return;
		row (item);
		redraw ();
	}

	/* an item goes; its subtree goes with it in the engines */
	void destroyed (TreeItem item) {
		int id = item.footerId;
		if (id < 0) return;
		item.footerId = -1;
		for (VirtualTreeAggregate engine : engines) {
			if (engine != null && engine.contains (id)) engine.remove (id);
		}
		if (freeCount == free.length) free = Arrays.copyOf (free, freeCount * 2);
		free [freeCount++] = id;
		redraw ();
	}

	void expanded (TreeItem item, boolean expanded) {
		int id = item.footerId;
		if (id < 0) return;
		for (VirtualTreeAggregate engine : engines) {
			if (engine != null && engine.contains (id)) engine.setExpanded (id, expanded);
		}
		redraw ();
	}

	private boolean active () {
		for (int kind : kinds) {
			if (kind != NONE) return true;
		}
		return false;
	}

	/* the footer id of an item, made (with its ancestors) on first use and added to every engine */
	private int row (TreeItem item) {
		if (item == null || item.isDisposed ()) return -1;
		if (item.footerId >= 0) return item.footerId;
		TreeItem parentItem = parentOf (item);
		int parentId = parentItem == null ? -1 : row (parentItem);
		int id = freeCount > 0 ? free [--freeCount] : nextId++;
		item.footerId = id;
		boolean expanded = expandedOf (item);
		for (int column = 0; column < engines.length; column++) {
			VirtualTreeAggregate engine = engines [column];
			if (engine == null) continue;
			engine.add (id, parentId, value (kinds [column], cellText (item, column)), expanded ? VirtualTreeAggregate.EXPANDED : 0);
		}
		return id;
	}

	/* the engine of a column from the items that exist (the one recount: switching an aggregate on) */
	private void rebuild (int column) {
		rebuilds++;
		VirtualTreeAggregate engine = new VirtualTreeAggregate ();
		engine.setScope (scope);
		int kind = kinds [column];
		if (kind == MIN || kind == MAX) engine.trackMinMax (true);
		engines [column] = engine;
		/* every item with a footer id joins (ancestors first: walk the items in tree order) */
		List<TreeItem> order = new ArrayList<> ();
		collect (order);
		for (TreeItem item : order) {
			if (item.footerId < 0) {
				if (tree.viewport != null && !item.cached) continue;
				row (item);
				continue;
			}
			TreeItem parentItem = parentOf (item);
			int parentId = parentItem == null ? -1 : parentItem.footerId;
			engine.add (item.footerId, parentId, value (kind, cellText (item, column)), expandedOf (item) ? VirtualTreeAggregate.EXPANDED : 0);
		}
		LOG.debug (() -> "Tree footer: column " + column + " aggregate " + kind + " rebuilt over " + order.size () + " items");
	}

	/* items in preorder: in the stock path all of them; in the viewport the facades that exist (no SetData) */
	private void collect (List<TreeItem> out) {
		if (tree.items == null) return;
		if (tree.viewport != null) {
			for (TreeItem item : tree.items) {
				if (item != null && !item.isDisposed ()) out.add (item);
			}
			/* parents before children: by depth */
			out.sort (Comparator.comparingInt (TreeFooter::depth));
			return;
		}
		for (TreeItem item : tree.items) {
			if (item != null && !item.isDisposed ()) out.add (item);
		}
		out.sort (Comparator.comparingInt (TreeFooter::depth));
	}

	private static int depth (TreeItem item) {
		int depth = 0;
		for (TreeItem p = parentOf (item); p != null; p = parentOf (p)) depth++;
		return depth;
	}

	private static TreeItem parentOf (TreeItem item) {
		return item.getParentItem ();
	}

	private boolean expandedOf (TreeItem item) {
		return item.getExpanded ();
	}

	/* the cell text without asking for data (no SetData from the footer) */
	private static String cellText (TreeItem item, int column) {
		if (column == 0) return item.text;
		return item.strings != null && column < item.strings.length ? item.strings [column] : null;
	}

	/* FILLED counts non-empty cells (value 1); the numeric kinds parse the text once per write (NaN: no number) */
	static double value (int kind, String text) {
		if (text == null || text.isEmpty ()) return Double.NaN;
		if (kind == FILLED || kind == COUNT) return 1;
		return parse (text);
	}

	static double parse (String text) {
		int length = text.length ();
		StringBuilder digits = null;
		for (int i = 0; i < length; i++) {
			char c = text.charAt (i);
			if (c == ',' || c == ' ' || c == ' ' || c == '_') {
				if (digits == null) digits = new StringBuilder (text.substring (0, i));
				continue;
			}
			if (digits != null) digits.append (c);
		}
		String number = (digits == null ? text : digits.toString ()).trim ();
		if (number.isEmpty ()) return Double.NaN;
		try {
			return Double.parseDouble (number);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/* ---------------------------------------------------------------- values and text */

	/** The column's aggregate under the scope (NaN without one); COUNT counts rows. */
	double value (int column) {
		int kind = kind (column);
		if (kind == NONE) return Double.NaN;
		if (kind == COUNT) return rowCount ();
		VirtualTreeAggregate engine = engines [column];
		if (engine == null) return Double.NaN;
		return switch (kind) {
			case FILLED -> engine.filled (-1);
			case SUM -> engine.filled (-1) == 0 ? Double.NaN : engine.sum (-1);
			case AVERAGE -> engine.average (-1);
			case MIN -> engine.min (-1);
			case MAX -> engine.max (-1);
			default -> Double.NaN;
		};
	}

	/* rows in scope: the engine's rows in the stock path; the topology in the viewport (cold rows have no item) */
	private double rowCount () {
		if (tree.viewport != null) {
			return scope == VISIBLE ? tree.virtualTopology.visibleRowCount () : tree.virtualTopology.knownRowCount ();
		}
		for (VirtualTreeAggregate engine : engines) {
			if (engine != null) return engine.count (-1);
		}
		return Double.NaN;
	}

	String text (int column) {
		int kind = kind (column);
		if (kind == NONE) return "";
		double value = value (column);
		String label = switch (kind) {
			case COUNT -> "Count";
			case FILLED -> "Filled";
			case SUM -> "Sum";
			case AVERAGE -> "Avg";
			case MIN -> "Min";
			case MAX -> "Max";
			default -> "";
		};
		return label + ": " + format (value);
	}

	static String format (double value) {
		if (Double.isNaN (value)) return "-";
		if (value == Math.rint (value) && Math.abs (value) < 1e15) return String.format (Locale.ROOT, "%,d", (long) value);
		return String.format (Locale.ROOT, "%,.2f", value);
	}

	/* ---------------------------------------------------------------- drawing */

	/** The footer strip in hwndParent client pixels, or null when off. */
	RECT strip () {
		if (!visible || tree.hwndParent == 0) return null;
		RECT client = new RECT ();
		OS.GetClientRect (tree.hwndParent, client);
		RECT rect = new RECT ();
		OS.SetRect (rect, client.left, Math.max (client.top, client.bottom - height ()), client.right, client.bottom);
		return rect;
	}

	/** The cell of a column in hwndParent client pixels (aligned with the header item, scrolled with it), or null. */
	RECT cell (int column) {
		RECT strip = strip ();
		if (strip == null) return null;
		if (tree.hwndHeader == 0 || tree.columnCount == 0) return column == 0 ? strip : null;
		RECT item = new RECT ();
		if (OS.SendMessage (tree.hwndHeader, OS.HDM_GETITEMRECT, column, item) == 0) return null;
		/* header coordinates to hwndParent coordinates (the header scrolls horizontally with the columns) */
		RECT header = new RECT ();
		OS.GetWindowRect (tree.hwndHeader, header);
		OS.MapWindowPoints (0, tree.hwndParent, header, 2);
		OS.SetRect (item, item.left + header.left, strip.top, item.right + header.left, strip.bottom);
		return item;
	}

	/** The column whose footer cell holds the point (hwndParent client pixels), or -1. */
	int columnAt (int x, int y) {
		RECT strip = strip ();
		if (strip == null || y < strip.top || y >= strip.bottom) return -1;
		int count = Math.max (1, tree.columnCount);
		for (int column = 0; column < count; column++) {
			RECT cell = cell (column);
			if (cell != null && x >= cell.left && x < cell.right) return column;
		}
		return -1;
	}

	void paint (long hDC) {
		RECT strip = strip ();
		if (strip == null) return;
		long brush = OS.GetSysColorBrush (OS.COLOR_BTNFACE);
		OS.FillRect (hDC, strip, brush);
		/* a line between the rows and the footer */
		RECT line = new RECT ();
		OS.SetRect (line, strip.left, strip.top, strip.right, strip.top + 1);
		OS.FillRect (hDC, line, OS.GetSysColorBrush (OS.COLOR_BTNSHADOW));
		long font = OS.SendMessage (tree.handle, OS.WM_GETFONT, 0, 0);
		long oldFont = font != 0 ? OS.SelectObject (hDC, font) : 0;
		int oldMode = OS.SetBkMode (hDC, OS.TRANSPARENT);
		int oldColor = OS.SetTextColor (hDC, OS.GetSysColor (OS.COLOR_BTNTEXT));
		int count = Math.max (1, tree.columnCount);
		for (int column = 0; column < count; column++) {
			String text = text (column);
			if (text.isEmpty ()) continue;
			RECT cell = cell (column);
			if (cell == null) continue;
			RECT inset = new RECT ();
			OS.SetRect (inset, cell.left + 6, cell.top + 1, cell.right - 6, cell.bottom);
			char [] chars = text.toCharArray ();
			OS.DrawText (hDC, chars, chars.length, inset, OS.DT_SINGLELINE | OS.DT_VCENTER | OS.DT_RIGHT | OS.DT_ENDELLIPSIS | OS.DT_NOPREFIX);
		}
		OS.SetTextColor (hDC, oldColor);
		OS.SetBkMode (hDC, oldMode);
		if (oldFont != 0) OS.SelectObject (hDC, oldFont);
	}

	/* the footer strip only (the rows and the header are other windows) */
	void redraw () {
		RECT strip = strip ();
		if (strip == null || !tree.getDrawing ()) return;
		OS.InvalidateRect (tree.hwndParent, strip, true);
	}
}
