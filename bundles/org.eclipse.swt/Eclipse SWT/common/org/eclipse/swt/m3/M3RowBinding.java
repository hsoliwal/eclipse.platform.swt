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
 *     Hitesh Soliwal (Synexia) - adapted from TV_5.0 treeviewer SortedElementProvider and
 *         FilteredTreeElementProvider (docs/m3/sort-filter/PROVENANCE.tsv)
 *******************************************************************************/
package org.eclipse.swt.m3;

import java.util.*;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;

/**
 * Drives a virtual Table or Tree from an {@link M3RowView} (enhanced M3 API).
 * <p>
 * The widget's item count follows the view, and each <code>SWT.SetData</code> request fills the
 * item from the source row the view maps it to. When the view changes, only its damaged rows are
 * cleared, so the widget asks again only for the rows it shows (in viewport mode, the resident
 * window). While the widget is hidden (it or an ancestor), the view keeps applying its changes to
 * its lanes but the widget is not touched, and a filter result computed on a worker is kept instead
 * of posted; when the widget shows again it is redone once from the view before it paints. A
 * table keeps its selection by source row: the rows the user selected stay selected,
 * wherever the view moves them, as long as the view shows them. A tree is driven by an
 * {@link M3ColumnSort} in tree mode: the children of an item are the sorted children of its
 * source row.
 * </p>
 *
 * @since 3.138
 */
public final class M3RowBinding {

	/** Fills a widget item from a source row. */
	@FunctionalInterface
	public interface RowFill<T> {
		/**
		 * Fills the item.
		 *
		 * @param item the item to fill
		 * @param sourceRow the source row it shows
		 */
		void fill (T item, int sourceRow);
	}

	private static final String ROW = "org.eclipse.swt.m3.M3RowBinding.row"; //$NON-NLS-1$
	private static final int [] NONE = {};

	private final Composite control;
	private final M3RowView view;
	private Listener setData, selection;
	private final Listener visibility = this::visibility, disposed = event -> unhook ();
	private final M3RowView.Listener listener = this::viewChanged;
	private M3RowView.Listener changed;
	private int [] selected = NONE;
	/* the control and its ancestors up to the shell, whose Show and Hide events tell when the viewport shows */
	private Control [] path = {};
	/* shown: the viewport is visible; dirty: the view changed while it was hidden; showing: a show is being applied */
	private boolean shown, dirty, showing, bound;
	/* widget refresh passes (read by the hidden-viewport gate) */
	int refreshes;

	private M3RowBinding (Composite control, M3RowView view) {
		this.control = control;
		this.view = view;
	}

	private void hook () {
		control.addListener (SWT.SetData, setData);
		view.addListener (listener);
		for (Control c = control; c != null; c = c instanceof Shell ? null : c.getParent ()) {
			c.addListener (SWT.Show, visibility);
			c.addListener (SWT.Hide, visibility);
			path = Arrays.copyOf (path, path.length + 1);
			path [path.length - 1] = c;
		}
		control.addListener (SWT.Dispose, disposed);
		shown = control.isVisible ();
		bound = true;
		viewports (shown ? 1 : 0, shown ? 0 : 1);
	}

	private void unhook () {
		if (!bound) return;
		bound = false;
		view.removeListener (listener);
		for (Control c : path) {
			if (c.isDisposed ()) continue;
			c.removeListener (SWT.Show, visibility);
			c.removeListener (SWT.Hide, visibility);
		}
		if (!control.isDisposed ()) control.removeListener (SWT.Dispose, disposed);
		viewports (shown ? -1 : 0, shown ? 0 : -1);
	}

	private void viewports (int visible, int hidden) {
		if (view instanceof M3RowViewBase base) base.viewports (visible, hidden);
	}

	/*
	 * A change of the view: applied to the widget while the viewport shows; while it is hidden the view's lanes are
	 * already up to date (deltas), so the widget is only marked dirty and redone once when it shows (brief rule 24).
	 */
	private void viewChanged (M3RowChange change) {
		if (control.isDisposed ()) return;
		if (shown && !showing) {
			refreshes++;
			changed.viewChanged (change);
			return;
		}
		dirty = true;
		if (control instanceof Table) renumber (change);
	}

	/* Show and Hide of the control or an ancestor (both are sent before the visibility changes) */
	private void visibility (Event event) {
		if (event.type == SWT.Hide) {
			if (shown) {
				shown = false;
				viewports (-1, 1);
			}
			return;
		}
		if (shown || !visibleAfterShow ((Control) event.widget)) return;
		shown = true;
		showing = true;
		try {
			/* a filter result parked while hidden applies here, once */
			viewports (1, -1);
		} finally {
			showing = false;
		}
		flush ();
	}

	/* whether the control is visible once the control being shown is */
	private boolean visibleAfterShow (Control shownControl) {
		for (Control c : path) {
			if (c != shownControl && !c.getVisible ()) return false;
		}
		return true;
	}

	/* redoes the widget once from the view: item count, the items it shows asked again, selection */
	private void flush () {
		if (!dirty || control.isDisposed ()) return;
		dirty = false;
		refreshes++;
		if (control instanceof Table table) {
			table.setItemCount (view.size ());
			table.clearAll ();
			restore ();
		} else {
			Tree tree = (Tree) control;
			M3ColumnSort sort = (M3ColumnSort) view;
			tree.setItemCount (sort.childCount (-1));
			tree.clearAll (true);
			restoreTree (sort);
		}
	}

	/**
	 * Drives a virtual table from a view.
	 *
	 * @param table a table with <code>SWT.VIRTUAL</code>
	 * @param view the view
	 * @param fill fills an item from a source row
	 * @return the binding
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if an argument is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the table is not virtual</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static M3RowBinding bind (Table table, M3RowView view, RowFill<TableItem> fill) {
		check (table, view, fill);
		M3RowBinding binding = new M3RowBinding (table, view);
		binding.setData = event -> {
			if (event.index < view.size ()) fill.fill ((TableItem) event.item, view.map (event.index));
		};
		binding.changed = binding::refresh;
		binding.selection = event -> binding.capture ();
		binding.hook ();
		table.addListener (SWT.Selection, binding.selection);
		table.addListener (SWT.DefaultSelection, binding.selection);
		table.setItemCount (view.size ());
		table.clearAll ();
		return binding;
	}

	/**
	 * Drives a virtual tree from a sort in tree mode.
	 *
	 * @param tree a tree with <code>SWT.VIRTUAL</code>
	 * @param sort the sort, with parents set
	 * @param fill fills an item from a source row
	 * @return the binding
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if an argument is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the tree is not virtual or the sort is not in tree mode</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static M3RowBinding bind (Tree tree, M3ColumnSort sort, RowFill<TreeItem> fill) {
		check (tree, sort, fill);
		int roots = sort.childCount (-1);
		M3RowBinding binding = new M3RowBinding (tree, sort);
		binding.setData = event -> {
			TreeItem item = (TreeItem) event.item, parentItem = item.getParentItem ();
			int parent = parentItem == null ? -1 : sourceRow (parentItem);
			if (event.index < sort.childCount (parent)) {
				int row = sort.child (parent, event.index);
				item.setData (ROW, row);
				fill.fill (item, row);
				item.setItemCount (sort.childCount (row));
			}
		};
		binding.changed = change -> {
			if (tree.isDisposed ()) return;
			int kind = change.kind ();
			if (kind == M3RowChange.ENTERED || kind == M3RowChange.LEFT || kind == M3RowChange.UPDATED) {
				binding.parentsChanged (tree, sort, change);
			} else {
				/* a new order or a structural source edit: every item's source row may differ */
				tree.setItemCount (sort.childCount (-1));
				tree.clearAll (true);
			}
			binding.restoreTree (sort);
		};
		binding.selection = event -> binding.captureTree ();
		tree.addListener (SWT.Selection, binding.selection);
		tree.addListener (SWT.DefaultSelection, binding.selection);
		binding.hook ();
		tree.setItemCount (roots);
		tree.clearAll (true);
		return binding;
	}

	/**
	 * Returns the source row a tree item shows, or -1 when the binding has not filled it.
	 *
	 * @param item the item
	 * @return the source row, or -1
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the item is null</li>
	 * </ul>
	 */
	public static int sourceRow (TreeItem item) {
		Object row = M3Checks.nonNull (item).getData (ROW);
		return row instanceof Integer ? (Integer) row : -1;
	}

	private static void check (Composite control, M3RowView view, RowFill<?> fill) {
		M3Checks.nonNull (control);
		M3Checks.nonNull (view);
		M3Checks.nonNull (fill);
		M3Checks.argument ((control.getStyle () & SWT.VIRTUAL) != 0);
	}

	/* the table's selection as source rows (O(selected)) */
	private void capture () {
		int [] indices = ((Table) control).getSelectionIndices ();
		selected = new int [indices.length];
		for (int i = 0; i < indices.length; i++) selected [i] = view.map (indices [i]);
	}

	private void refresh (M3RowChange change) {
		Table table = (Table) control;
		if (table.isDisposed ()) return;
		int size = view.size ();
		if (table.getItemCount () != size) table.setItemCount (size);
		/* the patch: the item count carries the extent, and only damaged rows are asked again */
		if (change.firstDamaged () >= 0 && change.firstDamaged () < size) table.clear (change.firstDamaged (), Math.min (change.lastDamaged (), size - 1));
		renumber (change);
		restore ();
	}

	/* source rows move with insertions and removals of the source */
	private void renumber (M3RowChange change) {
		int kind = change.kind (), start = change.start (), count = change.count (), kept = 0;
		if (kind != M3RowChange.INSERTED && kind != M3RowChange.REMOVED) return;
		for (int row : selected) {
			if (row < start) selected [kept++] = row;
			else if (kind == M3RowChange.INSERTED) selected [kept++] = row + count;
			else if (row >= start + count) selected [kept++] = row - count;
		}
		selected = Arrays.copyOf (selected, kept);
	}

	/* selects the view rows of the selected source rows (the table scrolls to show them) */
	private void restore () {
		int [] indices = new int [selected.length];
		int count = 0;
		for (int row : selected) {
			int index = row < view.sourceSize () ? view.unmap (row) : -1;
			if (index >= 0) indices [count++] = index;
		}
		Table table = (Table) control;
		if (count == 0) table.deselectAll (); else table.setSelection (Arrays.copyOf (indices, count));
	}

	/*
	 * Rows entered, left or changed (brief rule 24): only their parents' items are redone, one level each: the child
	 * count and the children asked again. Parents are taken outermost first, so that a parent that entered in the same
	 * change already has its place when its own children are set. A parent outside the view has no item to redo.
	 */
	private void parentsChanged (Tree tree, M3ColumnSort sort, M3RowChange change) {
		IntUnaryOperator parentOf = sort.parents ();
		long [] keyed = new long [change.rowCount ()];
		for (int i = 0; i < keyed.length; i++) {
			int parent = parentOf.applyAsInt (change.row (i)), depth = 0;
			for (int p = parent; p != -1; p = parentOf.applyAsInt (p)) depth++;
			keyed [i] = (long) depth << 32 | parent + 1;
		}
		Arrays.sort (keyed);
		for (int i = 0; i < keyed.length; i++) {
			if (i > 0 && keyed [i] == keyed [i - 1]) continue;
			int parent = (int) keyed [i] - 1;
			if (parent == -1) {
				tree.setItemCount (sort.childCount (-1));
				tree.clearAll (false);
			} else if (sort.indexOfChild (parent) >= 0) {
				TreeItem item = itemOf (tree, sort, parent);
				item.setItemCount (sort.childCount (parent));
				item.clearAll (false);
			}
		}
	}

	/* the tree's selection as source rows (O(selected)) */
	private void captureTree () {
		TreeItem [] items = ((Tree) control).getSelection ();
		selected = new int [items.length];
		int count = 0;
		for (TreeItem item : items) {
			int row = sourceRow (item);
			if (row >= 0) selected [count++] = row;
		}
		selected = Arrays.copyOf (selected, count);
	}

	/* selects the items of the selected source rows, walking down from the root by sibling index (O(depth) each) */
	private void restoreTree (M3ColumnSort sort) {
		Tree tree = (Tree) control;
		TreeItem [] items = new TreeItem [selected.length];
		int count = 0;
		for (int row : selected) {
			TreeItem item = row < sort.sourceSize () ? itemOf (tree, sort, row) : null;
			if (item != null) items [count++] = item;
		}
		if (count == 0) tree.deselectAll (); else tree.setSelection (Arrays.copyOf (items, count));
	}

	private static TreeItem itemOf (Tree tree, M3ColumnSort sort, int row) {
		int [] path = new int [2];
		int depth = 0;
		for (int r = row; r != -1; r = sort.parents ().applyAsInt (r)) {
			if (sort.indexOfChild (r) < 0) return null;
			if (depth == path.length) path = Arrays.copyOf (path, depth * 2);
			path [depth++] = sort.indexOfChild (r);
		}
		TreeItem item = tree.getItem (path [--depth]);
		while (depth > 0) item = item.getItem (path [--depth]);
		return item;
	}

	/**
	 * Returns the selected source rows of the bound widget, as last selected by the user or set
	 * with {@link #setSelectedRows(int...)}.
	 *
	 * @return a copy of the selected source rows
	 */
	public int [] getSelectedRows () {
		return selected.clone ();
	}

	/**
	 * Selects source rows in the bound widget; the rows the view does not show stay selected and
	 * appear selected when the view shows them again (a tree item is reached through its parents).
	 *
	 * @param sourceRows the source rows
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the rows are null</li>
	 * </ul>
	 */
	public void setSelectedRows (int... sourceRows) {
		selected = M3Checks.nonNull (sourceRows).clone ();
		if (control instanceof Table) restore (); else restoreTree ((M3ColumnSort) view);
	}

	/**
	 * Returns the view.
	 *
	 * @return the view
	 */
	public M3RowView getView () {
		return view;
	}

	/** Stops driving the widget; the widget keeps its items. */
	public void dispose () {
		unhook ();
		if (control.isDisposed ()) return;
		control.removeListener (SWT.SetData, setData);
		if (selection != null) {
			control.removeListener (SWT.Selection, selection);
			control.removeListener (SWT.DefaultSelection, selection);
		}
	}
}
