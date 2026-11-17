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
package org.eclipse.swt.internal;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * The value list of a column filter: every value of the column, with the number of rows that have
 * it, as a check list under a search field, and a first item that checks or unchecks every value
 * the search shows.
 * <p>
 * The values are those of the rows that pass the filter's other criteria
 * ({@link ColumnValues#of(ColumnFilter, int)}), so a value the column's own criterion hides can be
 * checked again. A value is checked when a row that has it passes the whole filter. The search
 * shows the values that contain its text, ignoring case; checks of values it hides are kept.
 * {@link #apply()} replaces the column's criterion: a value list of the checked values, or no
 * criterion when every value is checked. The list is virtual, so a column with many values costs
 * only the items shown.
 * </p>
 *
 * @see FilterPopup
 * @since 3.138
 */
public final class ValueListPanel extends Composite {

	static final String SELECT_ALL = "SWT_Values_Select_All"; //$NON-NLS-1$
	static final String BLANK = "SWT_Values_Blank"; //$NON-NLS-1$
	static final String COUNT = "SWT_Values_Count"; //$NON-NLS-1$
	static final String SEARCH = "SWT_Values_Search"; //$NON-NLS-1$
	static final int VISIBLE_ITEMS = 10;

	private final ColumnFilter filter;
	private final int column;
	private final Text search;
	private final Table list;
	private final Listener listener = this::handleEvent;
	private ColumnValues values;
	private BitSet checked = new BitSet ();
	/* the indices of the values the search shows, in value order; item i + 1 shows value shown [i] */
	private int [] shown = {};
	private boolean modified;

	/**
	 * Constructs the value list of a column.
	 *
	 * @param parent the parent
	 * @param filter the filter, which must read cell texts
	 * @param column the column
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the filter is null</li>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column of the filter</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the filter has no cell text</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
	 * </ul>
	 */
	public ValueListPanel (Composite parent, ColumnFilter filter, int column) {
		super (checked (parent, filter, column), SWT.NONE);
		this.filter = filter;
		this.column = column;
		setLayout (new GridLayout ());
		search = new Text (this, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
		search.setMessage (SWT.getMessage (SEARCH));
		search.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		list = new Table (this, SWT.CHECK | SWT.VIRTUAL | SWT.BORDER | SWT.V_SCROLL);
		GridData data = new GridData (SWT.FILL, SWT.FILL, true, true);
		data.heightHint = list.getItemHeight () * VISIBLE_ITEMS;
		list.setLayoutData (data);
		search.addListener (SWT.Modify, listener);
		list.addListener (SWT.SetData, listener);
		list.addListener (SWT.Selection, listener);
		refresh ();
	}

	private static Composite checked (Composite parent, ColumnFilter filter, int column) {
		RowChecks.nonNull (filter);
		RowChecks.range (column, filter.getColumnCount ());
		RowChecks.argument (filter.cellText () != null);
		return parent;
	}

	/**
	 * Reads the values and their checks from the filter again and forgets the changes not applied.
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the panel has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the panel</li>
	 * </ul>
	 */
	public void refresh () {
		checkWidget ();
		values = ColumnValues.of (filter, column);
		ColumnValues passing = new ColumnValues (filter.cellText (), column, filter.sourceSize (), filter::matches);
		checked = new BitSet (values.size ());
		for (int i = 0; i < values.size (); i++) checked.set (i, passing.indexOf (values.getValue (i)) >= 0);
		modified = false;
		showMatches ();
	}

	/**
	 * Returns whether a value is checked.
	 *
	 * @param value the value; <code>null</code> is the empty text
	 * @return <code>true</code> if the value is in the list and checked
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the panel has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the panel</li>
	 * </ul>
	 */
	public boolean isChecked (String value) {
		checkWidget ();
		int index = values.indexOf (value);
		return index >= 0 && checked.get (index);
	}

	/**
	 * Returns whether checks changed since the panel read them or last applied them.
	 *
	 * @return <code>true</code> if {@link #apply()} would change the filter's criterion
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the panel has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the panel</li>
	 * </ul>
	 */
	public boolean isModified () {
		checkWidget ();
		return modified;
	}

	/**
	 * Sets the column's criterion from the checks, when they changed: no criterion when every value
	 * is checked, otherwise a value list of the checked values (none checked: no row passes).
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the panel has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the panel</li>
	 * </ul>
	 */
	public void apply () {
		checkWidget ();
		if (!modified) return;
		modified = false;
		if (checked.cardinality () == values.size ()) {
			filter.clear (column);
		} else {
			filter.setValues (column, checked.stream ().mapToObj (values::getValue).toArray (String []::new));
		}
	}

	private void handleEvent (Event event) {
		switch (event.type) {
			case SWT.Modify -> showMatches ();
			case SWT.SetData -> setData ((TableItem) event.item);
			case SWT.Selection -> {
				if (event.detail == SWT.CHECK) toggled ((TableItem) event.item);
			}
			default -> { /* not listened to */ }
		}
	}

	/* the values whose text contains the search text, ignoring case */
	private void showMatches () {
		String needle = search.getText ().toLowerCase (Locale.ROOT);
		int [] matches = new int [values.size ()];
		int n = 0;
		for (int i = 0; i < matches.length; i++) {
			if (needle.isEmpty () || values.getValue (i).toLowerCase (Locale.ROOT).contains (needle)) matches [n++] = i;
		}
		shown = Arrays.copyOf (matches, n);
		list.setItemCount (n + 1);
		list.clearAll ();
	}

	private void setData (TableItem item) {
		int index = list.indexOf (item);
		if (index == 0) {
			item.setText (SWT.getMessage (SELECT_ALL));
			markAll (item);
			return;
		}
		int value = shown [index - 1];
		String text = values.getValue (value);
		item.setText (SWT.getMessage (COUNT, new Object [] {text.isEmpty () ? SWT.getMessage (BLANK) : text, Integer.valueOf (values.getCount (value))}));
		item.setChecked (checked.get (value));
	}

	/* the first item: checked when every value shown is, grayed when only some are */
	private void markAll (TableItem item) {
		int on = 0;
		for (int value : shown) {
			if (checked.get (value)) on++;
		}
		item.setChecked (on > 0);
		item.setGrayed (on > 0 && on < shown.length);
	}

	private void toggled (TableItem item) {
		int index = list.indexOf (item);
		modified = true;
		if (index > 0) {
			checked.set (shown [index - 1], item.getChecked ());
			markAll (list.getItem (0));
			return;
		}
		boolean every = true;
		for (int value : shown) every &= checked.get (value);
		for (int value : shown) checked.set (value, !every);
		list.clearAll ();
	}
}
