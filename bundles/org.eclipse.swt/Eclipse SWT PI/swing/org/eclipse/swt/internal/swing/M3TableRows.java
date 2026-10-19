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

/**
 * Bulk row changes of the Swing table behind an SWT Table in the viewport mode: one model event for a run of
 * rows, where the original mode sends one event per row.
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
}
