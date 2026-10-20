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

import org.eclipse.swt.*;

/**
 * The schema of one column of a lane model (public model API, brief rule 26): its kind, which says which lane holds
 * the cells, and its name. A sort or filter picks its path from the kind (a radix pass for numbers, label ranks for
 * text) without a comparator. Instances are immutable.
 *
 * @since 3.138
 */
public final class M3Column {

	/** Text cells, held as label ids of the model's {@link M3LabelTable}. */
	public static final int TEXT = 0;
	/** <code>int</code> cells. */
	public static final int INT = 1;
	/** <code>long</code> cells. */
	public static final int LONG = 2;
	/** <code>double</code> cells. */
	public static final int DOUBLE = 3;
	/** Image cells. */
	public static final int IMAGE = 4;
	/** Text computed on every read by a {@link Text} function, never stored. */
	public static final int CUSTOM = 5;

	/**
	 * The text of a <code>CUSTOM</code> column.
	 *
	 * @since 3.138
	 */
	@FunctionalInterface
	public interface Text {
		/**
		 * Returns the text of a cell.
		 *
		 * @param id the row id
		 * @param slot the row's slot
		 * @return the text, <code>null</code> for an empty cell
		 */
		CharSequence text (long id, int slot);
	}

	private final int kind;
	private final String name;
	private final Text text;

	private M3Column (int kind, String name, Text text) {
		if (name == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		this.kind = kind;
		this.name = name;
		this.text = text;
	}

	/**
	 * Returns a text column.
	 *
	 * @param name the name
	 * @return the column
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the name is null</li>
	 * </ul>
	 */
	public static M3Column text (String name) {
		return new M3Column (TEXT, name, null);
	}

	/**
	 * Returns an <code>int</code> column.
	 *
	 * @param name the name
	 * @return the column
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the name is null</li>
	 * </ul>
	 */
	public static M3Column ofInt (String name) {
		return new M3Column (INT, name, null);
	}

	/**
	 * Returns a <code>long</code> column.
	 *
	 * @param name the name
	 * @return the column
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the name is null</li>
	 * </ul>
	 */
	public static M3Column ofLong (String name) {
		return new M3Column (LONG, name, null);
	}

	/**
	 * Returns a <code>double</code> column.
	 *
	 * @param name the name
	 * @return the column
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the name is null</li>
	 * </ul>
	 */
	public static M3Column ofDouble (String name) {
		return new M3Column (DOUBLE, name, null);
	}

	/**
	 * Returns an image column.
	 *
	 * @param name the name
	 * @return the column
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the name is null</li>
	 * </ul>
	 */
	public static M3Column image (String name) {
		return new M3Column (IMAGE, name, null);
	}

	/**
	 * Returns a column whose text is computed on every read.
	 *
	 * @param name the name
	 * @param text the text of a cell
	 * @return the column
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the name or the text function is null</li>
	 * </ul>
	 */
	public static M3Column custom (String name, Text text) {
		if (text == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		return new M3Column (CUSTOM, name, text);
	}

	/**
	 * Returns the kind.
	 *
	 * @return one of {@link #TEXT}, {@link #INT}, {@link #LONG}, {@link #DOUBLE}, {@link #IMAGE}, {@link #CUSTOM}
	 */
	public int kind () {
		return kind;
	}

	/**
	 * Returns the name.
	 *
	 * @return the name
	 */
	public String name () {
		return name;
	}

	/**
	 * Returns the text function of a <code>CUSTOM</code> column.
	 *
	 * @return the function, or <code>null</code> for the other kinds
	 */
	public Text textFunction () {
		return text;
	}

	@Override
	public String toString () {
		return "M3Column {" + name + ", " + new String [] {"TEXT", "INT", "LONG", "DOUBLE", "IMAGE", "CUSTOM"} [kind] + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
	}
}
