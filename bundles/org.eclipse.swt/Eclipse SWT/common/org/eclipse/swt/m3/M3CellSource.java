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
 * Lazy fill of the cells of a row (public model API, brief rule 26): the virtual path. A model asks for a row when
 * its cells are first read, or read again after an update that brought no values; rows nobody reads are never filled.
 *
 * @since 3.138
 */
@FunctionalInterface
public interface M3CellSource {

	/**
	 * Fills the cells of a row.
	 *
	 * @param id the row id
	 * @param slot the row's slot
	 * @param out the receiver of the values; reused between calls
	 */
	void fill (long id, int slot, M3CellWriter out);
}
