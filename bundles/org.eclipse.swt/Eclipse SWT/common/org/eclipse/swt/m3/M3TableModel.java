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
package org.eclipse.swt.m3;

/**
 * Rows with columns (public model API, brief rule 26): what a <code>Table</code> shows. Each column has a kind
 * ({@link M3Column}) that says which lane holds its cells.
 *
 * @since 3.138
 */
public interface M3TableModel extends M3ListModel {

	/**
	 * Returns the number of columns.
	 *
	 * @return the columns, at least 1
	 */
	int columnCount ();

	/**
	 * Returns a column.
	 *
	 * @param index the column
	 * @return the column
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the index is out of range</li>
	 * </ul>
	 */
	M3Column column (int index);

	@Override
	M3TableModel snapshot ();
}
