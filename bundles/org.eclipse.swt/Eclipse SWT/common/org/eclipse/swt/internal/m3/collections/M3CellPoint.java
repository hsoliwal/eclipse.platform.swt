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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3CellPoint (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * A mutable (column, row) cell coordinate for {@link M3CellSelectionCounts}. Two points are equal when both
 * coordinates are equal; the hash is {@code x ^ y}. This is the value contract the Grid cell selection relied on for
 * its list operations, written here without toolkit types.
 */
public final class M3CellPoint {
	/** The column (x) coordinate. */
	public int x;

	/** The row (y) coordinate. */
	public int y;

	/** Creates the point {@code (x, y)}. */
	public M3CellPoint (int x, int y) {
		this.x = x;
		this.y = y;
	}

	@Override
	public boolean equals (Object object) {
		return object instanceof M3CellPoint p && p.x == x && p.y == y;
	}

	@Override
	public int hashCode () {
		return x ^ y;
	}

	@Override
	public String toString () {
		return "M3CellPoint {" + x + ", " + y + "}";
	}
}
