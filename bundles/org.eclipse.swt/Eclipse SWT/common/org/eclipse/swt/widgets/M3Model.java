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

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.m3.*;

/**
 * The one model of a <code>List</code> or <code>Table</code> (enhanced M3 API, the model seam):
 * the widget's rows plus one lane of row ids, edited by deltas ({@link M3Patch}).
 * <p>
 * A viewer or a binding keeps its domain objects and refers to rows by id; it does not keep a second
 * row store. Ids are positional (the id of a row is its index) until {@link #setIds(long, long[])} or
 * the first patch gives them. {@link #apply(M3Patch, Labels)} applies a delta as one update
 * ({@link M3BulkUpdates}) and keeps the top row on screen (anchored by id), so a delta entirely above
 * or below the visible rows changes only the scroll bar. Labels are asked for the rows a patch inserts
 * or updates; in a table with <code>SWT.VIRTUAL</code> they are asked only when the table shows the row
 * (through <code>SWT.SetData</code>), so a delta outside the window asks for no label at all.
 * </p>
 * <p>
 * The result of <code>apply</code> equals the same edits made through the stock item API: rows,
 * labels, selection, events and pixels.
 * </p>
 *
 * @since 3.138
 */
public final class M3Model {

	/** The parent id of every row of a List or Table. */
	public static final long ROOT = M3Patch.ROOT;

	/** Fills the labels of one row. */
	@FunctionalInterface
	public interface Labels {
		/**
		 * Fills the labels of a row.
		 *
		 * @param id the row id
		 * @param row the receiver of the labels; reused between calls
		 */
		void fill (long id, Row row);
	}

	/** The labels of one row, written by {@link Labels#fill(long, Row)}; reused between rows. */
	public static final class Row {
		String [] texts = new String [1];
		Image [] images = new Image [1];
		boolean checked;

		Row () {
		}

		/**
		 * Sets the text of a column.
		 *
		 * @param column the column, 0 for a List
		 * @param text the text
		 */
		public void setText (int column, String text) {
			if (column >= texts.length) texts = Arrays.copyOf (texts, column + 1);
			texts [column] = text;
		}

		/**
		 * Sets the image of a column (ignored by a List).
		 *
		 * @param column the column
		 * @param image the image, or <code>null</code>
		 */
		public void setImage (int column, Image image) {
			if (column >= images.length) images = Arrays.copyOf (images, column + 1);
			images [column] = image;
		}

		/**
		 * Sets the checked state (a table with <code>SWT.CHECK</code>).
		 *
		 * @param checked whether checked
		 */
		public void setChecked (boolean checked) {
			this.checked = checked;
		}

		void reset () {
			Arrays.fill (texts, null);
			Arrays.fill (images, null);
			checked = false;
		}

		String text (int column) {
			return column < texts.length && texts [column] != null ? texts [column] : ""; //$NON-NLS-1$
		}
	}

	private static final String KEY = "org.eclipse.swt.widgets.M3Model"; //$NON-NLS-1$

	private final Control widget;
	private final Row row = new Row ();
	/* row -> id; null while ids are positional */
	private long [] ids;
	private M3DiffApply.Positions positions;
	private Labels labels;
	private Listener setData;

	private M3Model (Control widget) {
		this.widget = widget;
	}

	/**
	 * Returns the model of a table.
	 *
	 * @param table the table
	 * @return its model, the same object on every call
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static M3Model of (Table table) {
		return model (table);
	}

	/**
	 * Returns the model of a list.
	 *
	 * @param list the list
	 * @return its model, the same object on every call
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the list is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the list has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the list</li>
	 * </ul>
	 */
	public static M3Model of (List list) {
		return model (list);
	}

	private static M3Model model (Control widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
		if (widget.getData (KEY) instanceof M3Model model) return model;
		M3Model model = new M3Model (widget);
		widget.setData (KEY, model);
		return model;
	}

	/**
	 * Returns the number of rows of a parent.
	 *
	 * @param parent {@link #ROOT}
	 * @return the row count
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the parent is not {@link #ROOT}</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public int size (long parent) {
		checkParent (parent);
		return count ();
	}

	/**
	 * Returns the id of a row.
	 *
	 * @param parent {@link #ROOT}
	 * @param index the row
	 * @return its id
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the parent is not {@link #ROOT}</li>
	 *    <li>ERROR_INVALID_RANGE - if the index is not a row</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public long id (long parent, int index) {
		checkParent (parent);
		if (index < 0 || index >= count ()) SWT.error (SWT.ERROR_INVALID_RANGE);
		return idAt (index);
	}

	/**
	 * Returns the row of an id.
	 *
	 * @param id the id
	 * @return its row, or -1 when no row has the id
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public int indexOf (long id) {
		widget.checkWidget ();
		sync ();
		int count = count ();
		if (ids == null) return id >= 0 && id < count ? (int) id : -1;
		if (positions == null) {
			positions = new M3DiffApply.Positions (count);
			for (int i = 0; i < count; i++) positions.put (ids [i], i);
		}
		return positions.get (id);
	}

	/**
	 * Returns the parent id of a row: {@link #ROOT} for every row of a List or Table.
	 *
	 * @param id the id
	 * @return {@link #ROOT}, or -2 when no row has the id
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public long parentOf (long id) {
		return indexOf (id) >= 0 ? ROOT : -2;
	}

	/**
	 * Gives ids to the existing rows; no row changes.
	 *
	 * @param parent {@link #ROOT}
	 * @param rowIds one unique id per row
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the ids are null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the parent is not {@link #ROOT}, the count differs from the rows, or an id repeats</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public void setIds (long parent, long [] rowIds) {
		checkParent (parent);
		if (rowIds == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (rowIds.length != count ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		M3DiffApply.Positions check = new M3DiffApply.Positions (rowIds.length);
		for (int i = 0; i < rowIds.length; i++) {
			if (check.put (rowIds [i], i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		ids = rowIds.clone ();
		positions = check;
	}

	/**
	 * Returns the text of a row.
	 *
	 * @param id the row id
	 * @param column the column, 0 for a List
	 * @return the text
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no row has the id</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public String text (long id, int column) {
		int index = indexOf (id);
		if (index < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		if (widget instanceof Table table) return table.getItem (index).getText (column);
		return ((List) widget).getItem (index);
	}

	/**
	 * Returns the image of a row (<code>null</code> for a List).
	 *
	 * @param id the row id
	 * @param column the column
	 * @return the image, or <code>null</code>
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no row has the id</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public Image image (long id, int column) {
		int index = indexOf (id);
		if (index < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		return widget instanceof Table table ? table.getItem (index).getImage (column) : null;
	}

	/**
	 * Applies a delta as one update. Removed rows go, inserted rows get the patch's ids, updated rows
	 * get new labels, and the row that was on top stays on top (or the first surviving row below it).
	 * For a table with <code>SWT.VIRTUAL</code> the labels are kept as the fill source of
	 * <code>SWT.SetData</code> and asked only for the rows the table shows.
	 *
	 * @param patch the delta of the parent {@link #ROOT}
	 * @param rowLabels the labels of inserted and updated rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if an argument is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the patch edits another parent, a run does not fit the rows, or an inserted id is already used</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public void apply (M3Patch patch, Labels rowLabels) {
		if (patch == null || rowLabels == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		checkParent (patch.parent ());
		long [] before = lane ();
		long [] after;
		try {
			after = patch.apply (before);
		} catch (IllegalArgumentException e) {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT, e);
			return;
		}
		M3DiffApply.Positions next = new M3DiffApply.Positions (after.length);
		for (int i = 0; i < after.length; i++) {
			if (next.put (after [i], i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		labels = rowLabels;
		boolean virtual = (widget.getStyle () & SWT.VIRTUAL) != 0;
		if (virtual && setData == null) {
			setData = event -> {
				if (labels != null && event.item instanceof TableItem item) {
					sync ();
					fillItem (item, idAt (event.index));
				}
			};
			widget.addListener (SWT.SetData, setData);
		}
		int top = topIndex ();
		long anchor = before.length > 0 && top < before.length ? before [top] : 0;
		M3BulkUpdates.bulk (widget, () -> {
			ids = after;
			positions = next;
			patch.applyTo (new M3Patch.Target () {
				int run;

				@Override
				public void remove (int index, int count) {
					if (widget instanceof Table table) table.remove (index, index + count - 1); else ((List) widget).remove (index, index + count - 1);
				}

				@Override
				public void insert (int index, int count) {
					for (int k = 0; k < count; k++) insertRow (index + k, patch.insertedId (run, k), virtual);
					run++;
				}

				@Override
				public void update (int index, int count) {
					for (int k = 0; k < count; k++) updateRow (index + k, virtual);
				}
			});
			if (before.length > 0) restoreTop (before, top, anchor);
		});
	}

	/* ------------------------------------------------------------------ helpers */

	private void insertRow (int index, long id, boolean virtual) {
		if (widget instanceof Table table) {
			TableItem item = new TableItem (table, SWT.NONE, index);
			if (virtual) {
				table.clear (index);
			} else {
				fillItem (item, id);
			}
			return;
		}
		row.reset ();
		labels.fill (id, row);
		((List) widget).add (row.text (0), index);
	}

	private void updateRow (int index, boolean virtual) {
		if (widget instanceof Table table) {
			if (virtual) {
				table.clear (index);
			} else {
				fillItem (table.getItem (index), ids [index]);
			}
			return;
		}
		row.reset ();
		labels.fill (ids [index], row);
		((List) widget).setItem (index, row.text (0));
	}

	private void fillItem (TableItem item, long id) {
		row.reset ();
		labels.fill (id, row);
		int columns = Math.max (1, item.getParent ().getColumnCount ());
		for (int column = 0; column < columns; column++) {
			item.setText (column, row.text (column));
			item.setImage (column, column < row.images.length ? row.images [column] : null);
		}
		if ((item.getParent ().getStyle () & SWT.CHECK) != 0) item.setChecked (row.checked);
	}

	/* the old top row, or the first surviving row below it, goes back on top */
	private void restoreTop (long [] before, int top, long anchor) {
		int count = count ();
		if (count == 0) return;
		int index = indexOf (anchor);
		for (int k = top + 1; index < 0 && k < before.length; k++) index = indexOf (before [k]);
		if (index < 0) index = count - 1;
		if (widget instanceof Table table) table.setTopIndex (index); else ((List) widget).setTopIndex (index);
	}

	private int topIndex () {
		return widget instanceof Table table ? table.getTopIndex () : ((List) widget).getTopIndex ();
	}

	private int count () {
		return widget instanceof Table table ? table.getItemCount () : ((List) widget).getItemCount ();
	}

	private long idAt (int index) {
		return ids == null ? index : ids [index];
	}

	/* the id lane, made explicit (positional ids become values) */
	private long [] lane () {
		int count = count ();
		if (ids != null && ids.length == count) return ids;
		long [] lane = new long [count];
		for (int i = 0; i < count; i++) lane [i] = idAt (i);
		return lane;
	}

	/* rows added or removed through the item API, outside the model, make the ids positional again */
	private void sync () {
		if (ids != null && ids.length != count ()) {
			ids = null;
			positions = null;
		}
	}

	private void checkParent (long parent) {
		widget.checkWidget ();
		sync ();
		if (parent != ROOT) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
	}
}
