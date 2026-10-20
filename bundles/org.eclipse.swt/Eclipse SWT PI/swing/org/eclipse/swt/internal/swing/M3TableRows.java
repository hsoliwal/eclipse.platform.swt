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
package org.eclipse.swt.internal.swing;

import javax.swing.JTable;

/**
 * Bulk row changes of the Swing table behind an SWT Table in the viewport mode: one model event for a run of
 * rows, where the original mode sends one event per row; while the table is hidden, no event at all.
 * <p>
 * IMPORTANT: This class is <em>not</em> part of the SWT public API.
 * </p>
 */
public final class M3TableRows {
	private M3TableRows () {
	}

	/**
	 * Adds rows to the table model.
	 *
	 * @param table the Swing side of the SWT Table
	 * @param index the first new row
	 * @param count the number of new rows, at least 1
	 */
	public static void inserted (CTable table, int index, int count) {
		CTableImplementation implementation = (CTableImplementation) table;
		implementation.rowCount += count;
		implementation.getModel ().fireTableRowsInserted (index, index + count - 1);
	}

	/**
	 * Removes rows from the table model.
	 *
	 * @param table the Swing side of the SWT Table
	 * @param index the first removed row
	 * @param count the number of removed rows, at least 1
	 */
	public static void removed (CTable table, int index, int count) {
		CTableImplementation implementation = (CTableImplementation) table;
		implementation.rowCount -= count;
		implementation.getModel ().fireTableRowsDeleted (index, index + count - 1);
	}

	/**
	 * Adds rows while the table is not showing (brief rule 24, hidden viewports): the row count and the selection
	 * move as {@link JTable} would move them for the event, but no model event reaches Swing; the caller revalidates
	 * and repaints the table once when it shows.
	 *
	 * @param table the Swing side of the SWT Table
	 * @param index the first new row
	 * @param count the number of new rows, at least 1
	 */
	public static void insertedHidden (CTable table, int index, int count) {
		CTableImplementation implementation = (CTableImplementation) table;
		implementation.rowCount += count;
		implementation.getSelectionModel ().insertIndexInterval (index, count, true);
	}

	/**
	 * Removes rows while the table is not showing: the row count and the selection move, no model event reaches
	 * Swing (see {@link #insertedHidden(CTable, int, int)}).
	 *
	 * @param table the Swing side of the SWT Table
	 * @param index the first removed row
	 * @param count the number of removed rows, at least 1
	 */
	public static void removedHidden (CTable table, int index, int count) {
		CTableImplementation implementation = (CTableImplementation) table;
		implementation.rowCount -= count;
		implementation.getSelectionModel ().removeIndexInterval (index, index + count - 1);
	}
}
