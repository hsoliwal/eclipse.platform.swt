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

import org.eclipse.swt.*;

/**
 * Lazy content for a virtual <code>Tree</code> or <code>Table</code> from an application source of ids
 * (enhanced M3 API).
 * <p>
 * The application describes its data as ids: {@link Source#childCount(long)} and
 * {@link Source#childId(long, int)} give the structure below an id ({@link #ROOT} for the top level),
 * {@link Source#text(long, int)} the cell texts. {@link #install(Tree, Source)} drives the widget
 * through the stock <code>SWT.VIRTUAL</code> contract: the item count of the top level is asked once,
 * and each <code>SWT.SetData</code> request asks the id, the texts and (in a tree) the child count of
 * that one row only. Rows that are never shown cost nothing, so a source of millions of rows scrolls
 * with work proportional to the rows on screen. The answers are the same in every startup mode.
 * </p>
 * <p>
 * The id of a filled row is kept as the item's data under a private key; {@link #idOf(TreeItem)}
 * reads it. {@link #refresh(Tree, TreeItem)} asks the structure below a row again after the source
 * changed.
 * </p>
 *
 * @since 3.138
 */
public final class M3LazyContent {
	/** The id of the invisible root: its children are the top-level rows. */
	public static final long ROOT = -1;
	/** Index in {@link #stats(Control)}: child counts asked. */
	public static final int CHILD_COUNTS = 0;
	/** Index in {@link #stats(Control)}: child ids asked. */
	public static final int CHILD_IDS = 1;
	/** Index in {@link #stats(Control)}: rows filled (one text request per column). */
	public static final int FILLS = 2;
	private static final String KEY = "org.eclipse.swt.widgets.M3LazyContent"; //$NON-NLS-1$
	private static final String ID = "org.eclipse.swt.widgets.M3LazyContent.id"; //$NON-NLS-1$

	/** The application's data, described by ids. */
	public interface Source {
		/**
		 * Returns the number of children of an id.
		 *
		 * @param id an id, or {@link M3LazyContent#ROOT}
		 * @return the child count, at least 0
		 */
		int childCount (long id);

		/**
		 * Returns the id of a child.
		 *
		 * @param parentId the parent's id, or {@link M3LazyContent#ROOT}
		 * @param index the child index, from 0 to <code>childCount (parentId) - 1</code>
		 * @return the child's id (any value but {@link M3LazyContent#ROOT})
		 */
		long childId (long parentId, int index);

		/**
		 * Returns the text of a cell.
		 *
		 * @param id the row's id
		 * @param column the column index (0 for a widget without columns)
		 * @return the text, not null
		 */
		String text (long id, int column);
	}

	/* The installed source, the SetData listener and the counters. */
	private static final class Binding implements Listener {
		final Source source;
		long counts, ids, fills;

		Binding (Source source) {
			this.source = source;
		}

		@Override
		public void handleEvent (Event event) {
			if (event.item instanceof TreeItem item) fill (item, event.index);
			else fill ((TableItem) event.item, event.index);
		}

		void fill (TreeItem item, int index) {
			TreeItem parent = item.getParentItem ();
			long id = child (parent == null ? ROOT : idOf (parent), index);
			item.setData (ID, id);
			int columns = Math.max (1, item.getParent ().getColumnCount ());
			for (int column = 0; column < columns; column++) item.setText (column, source.text (id, column));
			fills++;
			item.setItemCount (count (id));
		}

		void fill (TableItem item, int index) {
			long id = child (ROOT, index);
			item.setData (ID, id);
			int columns = Math.max (1, item.getParent ().getColumnCount ());
			for (int column = 0; column < columns; column++) item.setText (column, source.text (id, column));
			fills++;
		}

		int count (long id) {
			counts++;
			return Math.max (0, source.childCount (id));
		}

		long child (long parentId, int index) {
			ids++;
			return source.childId (parentId, index);
		}
	}

	private M3LazyContent () {
	}

	/**
	 * Drives a virtual tree from a source: the top-level count is asked now, every other answer when
	 * the tree asks for a row. A source installed before is replaced and the tree emptied first.
	 *
	 * @param tree the tree, created with <code>SWT.VIRTUAL</code>
	 * @param source the source
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree or the source is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the tree is not virtual</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static void install (Tree tree, Source source) {
		Binding binding = bind (tree, source);
		tree.removeAll ();
		tree.setItemCount (binding.count (ROOT));
	}

	/**
	 * Drives a virtual table from a source: its rows are the children of {@link #ROOT}.
	 *
	 * @param table the table, created with <code>SWT.VIRTUAL</code>
	 * @param source the source
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table or the source is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the table is not virtual</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static void install (Table table, Source source) {
		Binding binding = bind (table, source);
		table.removeAll ();
		table.setItemCount (binding.count (ROOT));
	}

	/**
	 * Asks the structure below a row again: its child count, and the ids and texts of its children
	 * when they are shown next. The row itself keeps its id and texts.
	 *
	 * @param tree the tree
	 * @param item the row, or <code>null</code> for the top level
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if no source is installed, or the row is not a filled row of the tree</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public static void refresh (Tree tree, TreeItem item) {
		Binding binding = binding (tree);
		if (item == null) {
			tree.setItemCount (binding.count (ROOT));
			tree.clearAll (true);
			return;
		}
		if (item.isDisposed () || item.getParent () != tree || !(item.getData (ID) instanceof Long)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		item.setItemCount (binding.count (idOf (item)));
		item.clearAll (true);
	}

	/**
	 * Asks the rows of a table again: the row count now, the ids and texts when rows are shown next.
	 *
	 * @param table the table
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if no source is installed</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public static void refresh (Table table) {
		Binding binding = binding (table);
		table.setItemCount (binding.count (ROOT));
		table.clearAll ();
	}

	/**
	 * Returns the id of a filled tree row.
	 *
	 * @param item the row
	 * @return its id, or {@link #ROOT} when the row has not been filled by an installed source
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the item is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the item has been disposed</li>
	 * </ul>
	 */
	public static long idOf (TreeItem item) {
		if (item == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		return item.getData (ID) instanceof Long id ? id : ROOT;
	}

	/**
	 * Returns the id of a filled table row.
	 *
	 * @param item the row
	 * @return its id, or {@link #ROOT} when the row has not been filled by an installed source
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the item is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the item has been disposed</li>
	 * </ul>
	 */
	public static long idOf (TableItem item) {
		if (item == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		return item.getData (ID) instanceof Long id ? id : ROOT;
	}

	/**
	 * Removes the installed source: the rows keep what they show, and no further request reaches the source.
	 *
	 * @param widget the tree or table
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the widget is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public static void uninstall (Control widget) {
		check (widget);
		if (!(widget.getData (KEY) instanceof Binding binding)) return;
		widget.removeListener (SWT.SetData, binding);
		widget.setData (KEY, null);
	}

	/**
	 * Returns how often the source was asked, indexed by {@link #CHILD_COUNTS}, {@link #CHILD_IDS} and
	 * {@link #FILLS}; zeros without a source.
	 *
	 * @param widget the tree or table
	 * @return the counts
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the widget is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the widget has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the widget</li>
	 * </ul>
	 */
	public static long [] stats (Control widget) {
		check (widget);
		if (!(widget.getData (KEY) instanceof Binding binding)) return new long [3];
		return new long [] {binding.counts, binding.ids, binding.fills};
	}

	/* ------------------------------------------------------------------ helpers */

	private static Binding bind (Control widget, Source source) {
		check (widget);
		if (source == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if ((widget.getStyle () & SWT.VIRTUAL) == 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		uninstall (widget);
		Binding binding = new Binding (source);
		widget.setData (KEY, binding);
		widget.addListener (SWT.SetData, binding);
		return binding;
	}

	private static Binding binding (Control widget) {
		check (widget);
		Object data = widget.getData (KEY);
		if (!(data instanceof Binding)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		return (Binding) data;
	}

	private static void check (Control widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		widget.checkWidget ();
	}
}
