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

import java.util.Arrays;

import javax.swing.*;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.m3.*;
import org.eclipse.swt.internal.swing.*;

/**
 * Viewport mode of a {@link List} on Swing (the default mode; <code>-Dswt.widget.mode.List=original</code>
 * selects the original implementation): the scroll contract of {@link M3SwingScroll}.
 * <ul>
 * <li><code>setTopIndex</code> puts the row at the top of the viewport, clamped to the scrollable range.</li>
 * <li><code>showSelection</code> does nothing when the first selected row is shown, and otherwise scrolls it
 * to the middle of the viewport, as far as the range allows.</li>
 * <li><code>setItems</code> gives Swing the replacement as one delta (rows removed, rows added), not one event
 * per row, and sends no SWT.Selection (UPSTREAM_DEFECTS I31-B3).</li>
 * <li>Rows have a fixed height and the list a fixed cell width, kept by {@link M3ListRows} from the model's
 * change events: a layout, a resize or a change of rows renders only the rows shown, never every row.</li>
 * <li>While the list is hidden, a change reaches Swing only as row geometry and selection; the list is laid out
 * and painted once when it shows (M3ListModel, brief rule 24 hidden viewports).</li>
 * </ul>
 * The <code>JList</code>, its model and its renderer are those of the original mode.
 */
final class M3ListViewport {
	/** Whether new lists use the viewport mode. */
	static final boolean ENABLED = WidgetMode.of ("List", WidgetMode.VIEWPORT) == WidgetMode.VIEWPORT;

	final List list;
	private final M3SwingScroll scroll = new M3SwingScroll ();
	/** Row geometry of the list; installed when the list is created. */
	private M3ListRows geometry;

	private M3ListViewport (List list) {
		this.list = list;
	}

	/**
	 * The viewport of a list, created on first use.
	 *
	 * @param list the list
	 * @return the viewport of the list
	 */
	static M3ListViewport of (List list) {
		if (list.viewport == null) list.viewport = new M3ListViewport (list);
		return list.viewport;
	}

	/** Hook of <code>List.createWidget()</code>: the model with hidden viewports (M3ListModel), fixed row geometry. */
	void install () {
		M3ListModel.install (rows ());
		geometry = M3ListRows.install (rows ());
	}

	/** Hook of <code>List.setFont(Font)</code>: the rows are measured with the new font. */
	void fontChanged () {
		if (geometry != null) geometry.measureAll ();
	}

	/** Hook of <code>List.setTopIndex(int)</code>. */
	void setTopIndex (int index) {
		JList<?> rows = rows ();
		scroll.setTopRow (viewport (), index, rows.getModel ().getSize (), pitch (rows));
	}

	/** Hook of <code>List.showSelection()</code>. */
	void showSelection () {
		JList<?> rows = rows ();
		int index = rows.getMinSelectionIndex ();
		if (index < 0) return;
		scroll.centerRow (viewport (), index, rows.getModel ().getSize (), pitch (rows));
	}

	/**
	 * Hook of <code>List.setItems(String...)</code> (the items are checked): the model drops every row with one
	 * event and takes the new rows with one more (<code>DefaultListModel.addAll</code>), where the original mode
	 * sends one event per row. The selection events Swing sends for it do not become SWT.Selection, as the official
	 * SWT sends none for a programmatic change.
	 */
	@SuppressWarnings ("unchecked")
	void setItems (String [] items) {
		DefaultListModel<Object> model = (DefaultListModel<Object>) rows ().getModel ();
		boolean adjusting = list.isAdjustingSelection;
		list.isAdjustingSelection = true;
		try {
			model.removeAllElements ();
			model.addAll (Arrays.asList (items));
		} finally {
			list.isAdjustingSelection = adjusting;
		}
	}

	private static int pitch (JList<?> rows) {
		return rows.getModel ().getSize () == 0 ? 0 : rows.getCellBounds (0, 0).height;
	}

	private JList<?> rows () {
		return (JList<?>) ((CList) list.handle).getSwingComponent ();
	}

	private JViewport viewport () {
		return ((JScrollPane) list.handle).getViewport ();
	}
}
