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
 * The one model of a <code>List</code>, <code>Table</code> or <code>Tree</code> (enhanced M3 API, the model seam):
 * the widget's rows plus one lane of row ids per parent, edited by deltas ({@link M3Patch}). A tree's
 * patch edits the children of one parent ({@link M3Patch#parent()}); removing or moving a row drops
 * its children, as disposing its item does.
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
	/*
	 * id -> row of every id in ids, built on demand. A patch keeps it in O(changed rows): the removed ids leave, the
	 * inserted ids come in with their rows, and the rows shifted by the patch keep a stale row as a hint, checked
	 * against ids by indexOf and rebuilt only when a stale hint is asked for.
	 */
	private M3LongIndex positions;
	private Labels labels;
	private Listener setData;
	/* the per-parent id lanes of a Tree; null for a List or Table */
	private M3TreeModel tree;

	private M3Model (Control widget) {
		this.widget = widget;
		if (widget instanceof Tree t) tree = new M3TreeModel (t);
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

	/**
	 * Returns the model of a tree. The roots have positional ids until a patch or
	 * {@link #setIds(long, long[])} gives them; the children of an item get ids from
	 * <code>setIds</code> or from the patch that inserts them. Ids are unique in the whole tree.
	 *
	 * @param tree the tree
	 * @return its model, the same object on every call
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static M3Model of (Tree tree) {
		return model (tree);
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
		widget.checkWidget ();
		if (tree != null) return tree.size (parent);
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
		widget.checkWidget ();
		if (tree != null) return tree.id (parent, index);
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
		if (tree != null) return tree.indexOf (id);
		sync ();
		int count = count ();
		if (ids == null) return id >= 0 && id < count ? (int) id : -1;
		if (positions == null) positions = positionsOf (ids);
		int index = positions.get (id);
		if (index < 0 || index < ids.length && ids [index] == id) return index;
		positions = positionsOf (ids);
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
		widget.checkWidget ();
		if (tree != null) return tree.parentOf (id);
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
		widget.checkWidget ();
		if (tree != null) {
			tree.setIds (parent, rowIds);
			return;
		}
		checkParent (parent);
		if (rowIds == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (rowIds.length != count ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		M3LongIndex check = new M3LongIndex (rowIds.length);
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
		widget.checkWidget ();
		if (tree != null) return tree.knownItem (id).getText (column);
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
		widget.checkWidget ();
		if (tree != null) return tree.knownItem (id).getImage (column);
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
		widget.checkWidget ();
		if (tree != null) {
			labels = rowLabels;
			installSetData ();
			tree.apply (patch, this);
			return;
		}
		checkParent (patch.parent ());
		long [] before = lane ();
		/* an update-only patch keeps the lane; inserts and removes make one new lane in one pass */
		boolean shifts = patch.removedCount () > 0 || patch.insertedCount () > 0;
		long [] after;
		try {
			after = shifts ? patch.apply (before) : updatesFit (patch, before);
		} catch (IllegalArgumentException e) {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT, e);
			return;
		}
		M3LongIndex lanePositions = shifts ? checkInserted (patch, before) : null;
		labels = rowLabels;
		boolean virtual = (widget.getStyle () & SWT.VIRTUAL) != 0;
		installSetData ();
		int top = topIndex ();
		long anchor = before.length > 0 && top < before.length ? before [top] : 0;
		M3BulkUpdates.bulk (widget, () -> {
			ids = after;
			if (shifts) positions = repoint (lanePositions, patch, before);
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
			if (before.length > 0) restoreTop (before, top, anchor, patch);
		});
	}

	/* ------------------------------------------------------------------ helpers */

	/* a virtual widget fills its rows from the labels when it shows them (SWT.SetData) */
	private void installSetData () {
		if ((widget.getStyle () & SWT.VIRTUAL) == 0 || setData != null) return;
		setData = event -> {
			if (labels == null) return;
			if (event.item instanceof TableItem item) {
				sync ();
				fillItem (item, idAt (event.index));
			} else if (event.item instanceof TreeItem item) {
				M3TreeModel.Lane lane = tree.lane (tree.idOf (item.getParentItem ()));
				if (lane != null && event.index < lane.ids.length) fillTreeItem (item, lane.ids [event.index]);
			}
		};
		widget.addListener (SWT.SetData, setData);
	}

	void fillTreeItem (TreeItem item, long id) {
		row.reset ();
		labels.fill (id, row);
		int columns = Math.max (1, item.getParent ().getColumnCount ());
		for (int column = 0; column < columns; column++) {
			item.setText (column, row.text (column));
			item.setImage (column, column < row.images.length ? row.images [column] : null);
		}
		if ((item.getParent ().getStyle () & SWT.CHECK) != 0) item.setChecked (row.checked);
	}

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

	/*
	 * The old top row, or the first surviving row below it, goes back on top. A top row the patch keeps is followed
	 * through the runs in O(runs); only a removed (or moved) top row is looked up by id.
	 */
	private void restoreTop (long [] before, int top, long anchor, M3Patch patch) {
		int count = count ();
		if (count == 0) return;
		int index = top < before.length ? follow (patch, top) : -1;
		if (index < 0) index = indexOf (anchor);
		for (int k = top + 1; index < 0 && k < before.length; k++) index = indexOf (before [k]);
		if (index < 0) index = count - 1;
		if (widget instanceof Table table) table.setTopIndex (index); else ((List) widget).setTopIndex (index);
	}

	/* the final row of an old row through a patch, or -1 when the patch removes it */
	static int follow (M3Patch patch, int old) {
		int index = old;
		for (int i = 0; i < patch.removeRuns (); i++) {
			int at = patch.removeAt (i), count = patch.removeCount (i);
			if (old >= at + count) {
				index -= count;
			} else if (old >= at) {
				return -1;
			}
		}
		for (int i = 0; i < patch.insertRuns () && patch.insertAt (i) <= index; i++) index += patch.insertCount (i);
		return index;
	}

	/* an update-only patch keeps the lane: its runs must fit the rows */
	private static long [] updatesFit (M3Patch patch, long [] lane) {
		for (int i = 0; i < patch.updateRuns (); i++) {
			if (patch.updateAt (i) + patch.updateCount (i) > lane.length) SWT.error (SWT.ERROR_INVALID_RANGE);
		}
		return lane;
	}

	/*
	 * An inserted id must be new to the lane, or a row the same patch removes (a move), and appear once: checked
	 * over the changed rows only. Answers the positions of the lane before the patch.
	 */
	private M3LongIndex checkInserted (M3Patch patch, long [] before) {
		M3LongIndex lane = ids == before && positions != null ? positions : positionsOf (before);
		M3LongIndex removed = new M3LongIndex (patch.removedCount ());
		for (int i = 0; i < patch.removeRuns (); i++) {
			for (int k = 0; k < patch.removeCount (i); k++) removed.put (before [patch.removeAt (i) + k], 0);
		}
		M3LongIndex inserted = new M3LongIndex (patch.insertedCount ());
		for (int i = 0; i < patch.insertRuns (); i++) {
			for (int k = 0; k < patch.insertCount (i); k++) {
				long id = patch.insertedId (i, k);
				if (inserted.put (id, 0) >= 0 || lane.get (id) >= 0 && removed.get (id) < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			}
		}
		return lane;
	}

	/* the positions after a patch: removed ids leave, inserted ids come in with their rows, shifted rows keep hints */
	private static M3LongIndex repoint (M3LongIndex lane, M3Patch patch, long [] before) {
		for (int i = 0; i < patch.removeRuns (); i++) {
			for (int k = 0; k < patch.removeCount (i); k++) lane.remove (before [patch.removeAt (i) + k]);
		}
		for (int i = 0; i < patch.insertRuns (); i++) {
			for (int k = 0; k < patch.insertCount (i); k++) lane.put (patch.insertedId (i, k), patch.insertAt (i) + k);
		}
		return lane;
	}

	private static M3LongIndex positionsOf (long [] lane) {
		M3LongIndex index = new M3LongIndex (lane.length);
		for (int i = 0; i < lane.length; i++) index.put (lane [i], i);
		return index;
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
