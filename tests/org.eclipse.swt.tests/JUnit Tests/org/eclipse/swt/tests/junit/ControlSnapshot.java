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

import java.util.*;
import java.util.zip.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * A recorded state of a control, for tests that compare runs.
 * <p>
 * {@link #capture(Control)} records named fields read through the public API (class, bounds, client
 * area, visibility and enablement, the item count, top row, visible rows and selection ranges of a
 * Table, List or Tree, and the vertical and horizontal scroll bars) and a CRC-32 of the control's
 * pixels as <code>Control.print</code> draws them once two prints agree (at most four prints). Two
 * snapshots are equal when every field and the pixel CRC are; {@link #diff(ControlSnapshot)} names the
 * first field that differs. No image is kept. The class uses public SWT API only, so the same capture
 * runs with this SWT and with the official jar.
 * </p>
 */
public final class ControlSnapshot {

	/** A range of rows <code>[start..end]</code>; empty when <code>end &lt; start</code>. */
	public record Rows (int start, int end) {
		/** No rows. */
		public static final Rows EMPTY = new Rows (0, -1);

		/** @return whether the range holds no row */
		public boolean isEmpty () {
			return end < start;
		}

		@Override
		public String toString () {
			return isEmpty () ? "[]" : "[" + start + ".." + end + "]";
		}
	}

	private final String [] names, values;
	private final long pixels;
	private final Rows visible;
	private final Rows [] selection;

	private ControlSnapshot (java.util.List<String> fields, long pixels, Rows visible, Rows [] selection) {
		int n = fields.size () / 2;
		names = new String [n];
		values = new String [n];
		for (int i = 0; i < n; i++) {
			names [i] = fields.get (2 * i);
			values [i] = fields.get (2 * i + 1);
		}
		this.pixels = pixels;
		this.visible = visible;
		this.selection = selection;
	}

	/**
	 * Records the state and the pixels of a control.
	 *
	 * @param control the control
	 * @return the snapshot
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the control is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the control has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the control</li>
	 * </ul>
	 */
	public static ControlSnapshot capture (Control control) {
		if (control == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		java.util.List<String> fields = new ArrayList<> ();
		add (fields, "class", control.getClass ().getName ());
		add (fields, "bounds", control.getBounds ().toString ());
		add (fields, "state", (control.getVisible () ? "visible" : "hidden") + (control.getEnabled () ? " enabled" : " disabled"));
		if (control instanceof Scrollable scrollable) scrollable (fields, scrollable);
		Rows visible = visible (control);
		Rows [] selection = selection (control);
		items (fields, control, visible, selection);
		return new ControlSnapshot (fields, crc (control), visible, selection);
	}

	/**
	 * Returns the recorded fields as <code>name=value</code> lines, one per field, then the pixel CRC.
	 *
	 * @return the state text
	 */
	public String state () {
		StringBuilder out = new StringBuilder ();
		for (int i = 0; i < names.length; i++) out.append (names [i]).append ('=').append (values [i]).append ('\n');
		return out.append ("pixels=").append (Long.toHexString (pixels)).toString ();
	}

	/**
	 * Returns the CRC-32 of the control's printed pixels, 0 for a control of no size.
	 *
	 * @return the pixel CRC
	 */
	public long pixels () {
		return pixels;
	}

	/**
	 * Returns the visible rows of a Table, List or Tree (a Tree's in visible-row order), else empty.
	 *
	 * @return the visible range
	 */
	public Rows visibleRange () {
		return visible;
	}

	/**
	 * Returns the selected rows of a Table or List, or of a Tree's top level, as ranges; else none.
	 *
	 * @return a copy of the selection ranges
	 */
	public Rows [] selectionRanges () {
		return selection.clone ();
	}

	/**
	 * Names the first difference to another snapshot: <code>"name: this | other"</code>, or
	 * <code>null</code> when the snapshots are equal.
	 *
	 * @param other the other snapshot
	 * @return the first difference, or <code>null</code>
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the other snapshot is null</li>
	 * </ul>
	 */
	public String diff (ControlSnapshot other) {
		if (other == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		/* field 0 is the class, and the class fixes the field set: past it the fields line up */
		for (int i = 0; i < names.length; i++) {
			if (!values [i].equals (other.values [i])) return names [i] + ": " + values [i] + " | " + other.values [i];
		}
		return pixels == other.pixels ? null : "pixels: " + Long.toHexString (pixels) + " | " + Long.toHexString (other.pixels);
	}

	@Override
	public boolean equals (Object object) {
		return object instanceof ControlSnapshot other && diff (other) == null;
	}

	@Override
	public int hashCode () {
		return Arrays.hashCode (values) * 31 + Long.hashCode (pixels);
	}

	@Override
	public String toString () {
		return "ControlSnapshot {" + state ().replace ('\n', ' ') + "}";
	}

	/* ------------------------------------------------------------------ fields */

	private static void add (java.util.List<String> fields, String name, String value) {
		fields.add (name);
		fields.add (value);
	}

	private static void scrollable (java.util.List<String> fields, Scrollable scrollable) {
		add (fields, "client", scrollable.getClientArea ().toString ());
		add (fields, "vbar", bar (scrollable.getVerticalBar ()));
		add (fields, "hbar", bar (scrollable.getHorizontalBar ()));
	}

	private static String bar (ScrollBar bar) {
		if (bar == null) return "-";
		return bar.getMinimum () + "," + bar.getMaximum () + "," + bar.getThumb () + "," + bar.getSelection () + (bar.getVisible () ? ",shown" : ",hidden");
	}

	private static void items (java.util.List<String> fields, Control control, Rows visible, Rows [] selection) {
		int count, top;
		if (control instanceof Table table) {
			count = table.getItemCount ();
			top = table.getTopIndex ();
		} else if (control instanceof List list) {
			count = list.getItemCount ();
			top = list.getTopIndex ();
		} else if (control instanceof Tree tree) {
			count = tree.getItemCount ();
			top = visible.isEmpty () ? 0 : visible.start ();
		} else {
			return;
		}
		add (fields, "items", Integer.toString (count));
		add (fields, "top", Integer.toString (top));
		add (fields, "visible", visible.toString ());
		add (fields, "selection", Arrays.toString (selection));
	}

	/* ------------------------------------------------------------------ rows */

	private static Rows visible (Control control) {
		if (control instanceof Table table) {
			int header = table.getHeaderVisible () ? table.getHeaderHeight () : 0;
			return window (table.getTopIndex (), table.getItemCount (), table.getClientArea ().height - header, table.getItemHeight ());
		}
		if (control instanceof List list) {
			return window (list.getTopIndex (), list.getItemCount (), list.getClientArea ().height, list.getItemHeight ());
		}
		if (control instanceof Tree tree) return visible (tree);
		return Rows.EMPTY;
	}

	/* The rows of a tree from its top item, in visible-row order (the preorder of the expanded items). */
	private static Rows visible (Tree tree) {
		TreeItem top = tree.getTopItem ();
		int header = tree.getHeaderVisible () ? tree.getHeaderHeight () : 0;
		int rows = rows (tree.getClientArea ().height - header, tree.getItemHeight ());
		if (top == null || rows == 0) return Rows.EMPTY;
		VisibleOrder walk = new VisibleOrder (tree);
		int first = 0;
		for (TreeItem item = walk.next (); item != null && item != top; item = walk.next ()) first++;
		int more = 0;
		while (more < rows - 1 && walk.next () != null) more++;
		return new Rows (first, first + more);
	}

	/* The items of a tree in visible-row order, read child by child (no item list is built). */
	private static final class VisibleOrder {
		private final Tree tree;
		/* per depth: the parent (null for the roots), and {next child, child count} */
		private final java.util.List<TreeItem> parents = new ArrayList<> ();
		private final java.util.List<int []> cursors = new ArrayList<> ();

		VisibleOrder (Tree tree) {
			this.tree = tree;
			parents.add (null);
			cursors.add (new int [] {0, tree.getItemCount ()});
		}

		TreeItem next () {
			while (!cursors.isEmpty ()) {
				int depth = cursors.size () - 1;
				int [] cursor = cursors.get (depth);
				if (cursor [0] < cursor [1]) {
					TreeItem parent = parents.get (depth);
					TreeItem item = parent == null ? tree.getItem (cursor [0]++) : parent.getItem (cursor [0]++);
					if (item.getExpanded ()) {
						parents.add (item);
						cursors.add (new int [] {0, item.getItemCount ()});
					}
					return item;
				}
				parents.remove (depth);
				cursors.remove (depth);
			}
			return null;
		}
	}

	/* Rows [top, top + rows - 1] clamped to the item count; EMPTY when nothing fits. */
	private static Rows window (int top, int count, int areaHeight, int itemHeight) {
		int rows = rows (areaHeight, itemHeight);
		if (rows == 0 || top >= count) return Rows.EMPTY;
		return new Rows (top, Math.min (count - 1, top + rows - 1));
	}

	/* Number of rows that intersect an area, counting a partial last row. */
	private static int rows (int areaHeight, int itemHeight) {
		int extent = Math.max (1, itemHeight);
		return areaHeight <= 0 ? 0 : (areaHeight + extent - 1) / extent;
	}

	private static Rows [] selection (Control control) {
		if (control instanceof Table table) return ranges (table.getSelectionIndices ());
		if (control instanceof List list) return ranges (list.getSelectionIndices ());
		if (control instanceof Tree tree) {
			/* the selected roots, by index */
			return ranges (Arrays.stream (tree.getSelection ()).filter (item -> item.getParentItem () == null).mapToInt (tree::indexOf).toArray ());
		}
		return new Rows [0];
	}

	/* The indices as ascending runs of consecutive rows. */
	private static Rows [] ranges (int [] indices) {
		int [] sorted = indices.clone ();
		Arrays.sort (sorted);
		java.util.List<Rows> out = new ArrayList<> ();
		for (int i = 0; i < sorted.length; i++) {
			int start = sorted [i];
			while (i + 1 < sorted.length && sorted [i + 1] <= sorted [i] + 1) i++;
			out.add (new Rows (start, sorted [i]));
		}
		return out.toArray (new Rows [0]);
	}

	/*
	 * The CRC-32 of the settled pixels: the control is printed until two prints agree (at most four; a
	 * first print may still show a paint the control had deferred); 0 for a control of no size.
	 */
	private static long crc (Control control) {
		long last = print (control);
		for (int tries = 0; tries < 3; tries++) {
			long next = print (control);
			if (next == last) break;
			last = next;
		}
		return last;
	}

	private static long print (Control control) {
		Point size = control.getSize ();
		if (size.x <= 0 || size.y <= 0) return 0;
		Image image = new Image (control.getDisplay (), size.x, size.y);
		try {
			GC gc = new GC (image);
			try {
				control.print (gc);
			} finally {
				gc.dispose ();
			}
			CRC32 crc = new CRC32 ();
			crc.update (image.getImageData ().data);
			return crc.getValue ();
		} finally {
			image.dispose ();
		}
	}
}
