/*******************************************************************************
 * Copyright (c) 2000, 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *
 * Derived from the exact SWT style owners recorded in the recipe provenance.
 * Original platform policy, constant aliasing and bit precedence are retained.
 *******************************************************************************/
package org.eclipse.swt.widgets;

/** Exact SWT bit-priority atom, including zero and overlapping masks. */
final class WidgetStyleBits {
	private WidgetStyleBits () {
	}

	static int normalize (int style, int int0, int int1, int int2, int int3, int int4, int int5) {
		int mask = int0 | int1 | int2 | int3 | int4 | int5;
		if ((style & mask) == 0) style |= int0;
		if ((style & int0) != 0) style = (style & ~mask) | int0;
		if ((style & int1) != 0) style = (style & ~mask) | int1;
		if ((style & int2) != 0) style = (style & ~mask) | int2;
		if ((style & int3) != 0) style = (style & ~mask) | int3;
		if ((style & int4) != 0) style = (style & ~mask) | int4;
		if ((style & int5) != 0) style = (style & ~mask) | int5;
		return style;
	}
}
