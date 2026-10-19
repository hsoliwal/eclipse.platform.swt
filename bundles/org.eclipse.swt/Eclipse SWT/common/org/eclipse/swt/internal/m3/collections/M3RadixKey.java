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
 * com.m3.collections.M3RadixKey (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * One sort key of an {@link M3PermutationIndex}: it writes an unsigned radix key per row into the index's
 * {@code keys} lane and answers how many low bytes of the key are significant (1 to 8).
 */
public abstract class M3RadixKey {
	/** For subclasses. */
	protected M3RadixKey () {
	}

	/**
	 * Writes {@code lanes.keys [i]} for every row {@code rows [i]}, {@code i < n} (unsigned order of the keys is the
	 * row order this key wants) and answers the number of significant low bytes.
	 */
	public abstract int radixKeys (int [] rows, int n, M3PermutationIndex lanes);
}
