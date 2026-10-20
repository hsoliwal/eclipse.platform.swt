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

import org.eclipse.swt.graphics.*;

/**
 * Receiver of the values of one row (public model API, brief rule 26): the lazy fill of an {@link M3CellSource} and
 * the eager values of a patch (<code>M3Patch.Builder.values</code>). A writer is reused; nothing is allocated per
 * cell except the first occurrence of a text, which is interned into the {@link M3LabelTable}.
 *
 * @since 3.138
 */
public interface M3CellWriter {

	/**
	 * Sets the text of a <code>TEXT</code> cell.
	 *
	 * @param column the column
	 * @param text the text, <code>null</code> or empty for an empty cell
	 * @return this writer
	 */
	M3CellWriter text (int column, CharSequence text);

	/**
	 * Sets a text cell to an interned label.
	 *
	 * @param column the column
	 * @param labelId the label id of the model's label table, 0 for an empty cell
	 * @return this writer
	 */
	M3CellWriter label (int column, int labelId);

	/**
	 * Sets an <code>INT</code> cell.
	 *
	 * @param column the column
	 * @param value the value
	 * @return this writer
	 */
	M3CellWriter intValue (int column, int value);

	/**
	 * Sets a <code>LONG</code> cell.
	 *
	 * @param column the column
	 * @param value the value
	 * @return this writer
	 */
	M3CellWriter longValue (int column, long value);

	/**
	 * Sets a <code>DOUBLE</code> cell.
	 *
	 * @param column the column
	 * @param value the value
	 * @return this writer
	 */
	M3CellWriter doubleValue (int column, double value);

	/**
	 * Sets the image of a cell.
	 *
	 * @param column the column
	 * @param image the image, owned by the client, or <code>null</code>
	 * @return this writer
	 */
	M3CellWriter image (int column, Image image);

	/**
	 * Sets a flag of the row.
	 *
	 * @param flag the flag lane, 0 to 63
	 * @param value the value
	 * @return this writer
	 */
	M3CellWriter flag (int flag, boolean value);

	/**
	 * Sets the style of a cell or of the row.
	 *
	 * @param column the column, or -1 for the row
	 * @param styleId the style id of the model's style table, 0 for none
	 * @return this writer
	 */
	M3CellWriter style (int column, int styleId);

	/**
	 * Sets the height of the row.
	 *
	 * @param extent the height in points, 0 for the widget's default
	 * @return this writer
	 */
	M3CellWriter extent (int extent);
}
