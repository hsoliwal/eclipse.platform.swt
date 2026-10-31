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
import java.util.zip.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * A recorded state of a control, for tests that compare runs (enhanced M3 API).
 * <p>
 * {@link #capture(Control)} records named fields read through the public API (class, bounds, client
 * area, visibility and enablement, the item count, top row, visible rows and selection ranges of a
 * Table, List or Tree, and the vertical and horizontal scroll bars) and a CRC-32 of the control's
 * pixels as <code>Control.print</code> draws them once two prints agree (at most four prints). Two
 * snapshots are equal when every field and the
 * pixel CRC are; {@link #diff(M3Snapshot)} names the first field that differs. No image is kept.
 * </p>
 *
 * @since 3.138
 */
public final class M3Snapshot {
	private final String [] names, values;
	private final long pixels;
	private final M3ItemRange visible;
	private final M3ItemRange [] selection;

	private M3Snapshot (java.util.List<String> fields, long pixels, M3ItemRange visible, M3ItemRange [] selection) {
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
	public static M3Snapshot capture (Control control) {
		if (control == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		control.checkWidget ();
		java.util.List<String> fields = new ArrayList<> ();
		add (fields, "class", control.getClass ().getName ());
		add (fields, "bounds", control.getBounds ().toString ());
		add (fields, "state", (control.getVisible () ? "visible" : "hidden") + (control.getEnabled () ? " enabled" : " disabled"));
		if (control instanceof Scrollable scrollable) scrollable (fields, scrollable);
		M3ItemRange visible = visible (control);
		M3ItemRange [] selection = selection (control);
		items (fields, control, visible, selection);
		return new M3Snapshot (fields, crc (control), visible, selection);
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
	public M3ItemRange visibleRange () {
		return visible;
	}

	/**
	 * Returns the selected rows of a Table or List, or of a Tree's top level, as ranges; else none.
	 *
	 * @return a copy of the selection ranges
	 */
	public M3ItemRange [] selectionRanges () {
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
	public String diff (M3Snapshot other) {
		if (other == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		/* field 0 is the class, and the class fixes the field set: past it the fields line up */
		for (int i = 0; i < names.length; i++) {
			if (!values [i].equals (other.values [i])) return names [i] + ": " + values [i] + " | " + other.values [i];
		}
		return pixels == other.pixels ? null : "pixels: " + Long.toHexString (pixels) + " | " + Long.toHexString (other.pixels);
	}

	@Override
	public boolean equals (Object object) {
		return object instanceof M3Snapshot other && diff (other) == null;
	}

	@Override
	public int hashCode () {
		return Arrays.hashCode (values) * 31 + Long.hashCode (pixels);
	}

	@Override
	public String toString () {
		return "M3Snapshot {" + state ().replace ('\n', ' ') + "}";
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

	private static void items (java.util.List<String> fields, Control control, M3ItemRange visible, M3ItemRange [] selection) {
		long countTop = countTop (control, visible);
		if (countTop == -1) return;
		add (fields, "items", Integer.toString ((int) (countTop >>> 32)));
		add (fields, "top", Integer.toString ((int) countTop));
		add (fields, "visible", visible.toString ());
		add (fields, "selection", Arrays.toString (selection));
	}

	/* the item count (high int) and top row (low int) of a Table, List or Tree, packed; -1 for other controls */
	private static long countTop (Control control, M3ItemRange visible) {
		if (control instanceof Table table) return (long) table.getItemCount () << 32 | table.getTopIndex ();
		if (control instanceof List list) return (long) list.getItemCount () << 32 | list.getTopIndex ();
		if (control instanceof Tree tree) return (long) tree.getItemCount () << 32 | (visible.isEmpty () ? 0 : visible.start ());
		return -1;
	}

	private static M3ItemRange visible (Control control) {
		if (control instanceof Table table) return M3ItemSelection.getVisibleRange (table);
		if (control instanceof List list) return M3ItemSelection.getVisibleRange (list);
		if (control instanceof Tree tree) return M3ItemSelection.getVisibleRange (tree);
		return M3ItemRange.EMPTY;
	}

	private static M3ItemRange [] selection (Control control) {
		if (control instanceof Table table) return M3ItemSelection.getSelectionRanges (table);
		if (control instanceof List list) return M3ItemSelection.getSelectionRanges (list);
		if (control instanceof Tree tree) return M3ItemSelection.getSelectionRanges (tree, null);
		return new M3ItemRange [0];
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
