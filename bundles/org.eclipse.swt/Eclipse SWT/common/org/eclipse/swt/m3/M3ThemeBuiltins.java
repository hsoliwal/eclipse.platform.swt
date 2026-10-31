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

import java.util.stream.*;

/*
 * The values of the built-in themes, in slot order (M3ThemeSlot). Colours are this project's own; every text pair of
 * M3ThemeSlot.TEXT_PAIRS reaches 4.5:1 and every UI pair 3:1 (Test_org_eclipse_swt_m3_M3Theme; minimum text contrast
 * light 4.76, dark 5.94, high contrast 7.09; focus ring at least 4.47). Colours are written 0xRRGGBB and made opaque here.
 */
//FLLDCIM: TmOP — data tables, one row per theme; the slot order is the M3ThemeSlot id order
final class M3ThemeBuiltins {
	private static final int [] LIGHT = {
		/* web: bg fg muted border accent accent-2 surface radius pad */
		0xF4F5F7, 0x1C1D1F, 0x5F6368, 0xC5C8CD, 0x2F6FD0, 0x0F8A7A, 0xFFFFFF, 6, 8,
		/* widget: background foreground border light normal dark highlight disabled-foreground */
		0xF4F5F7, 0x1C1D1F, 0xC5C8CD, 0xE8EAED, 0xA0A4AB, 0x6B7078, 0xFFFFFF, 0x8A8E95,
		/* list: background foreground selection selection-text; info; title; title inactive; link; text disabled */
		0xFFFFFF, 0x1C1D1F, 0x2F6FD0, 0xFFFFFF, 0xFFFBE6, 0x1C1D1F, 0x2F6FD0, 0xFFFFFF, 0xE1E3E6, 0x5F6368, 0x1F5FBF, 0xECEEF1,
		/* drawn: stripe selection selection-text inactive hover focus header-bg header-fg grid error warning success */
		0xF7F8FA, 0x2F6FD0, 0xFFFFFF, 0x64748B, 0xEAF1FB, 0x2F6FD0, 0xEEF0F3, 0x1C1D1F, 0xE1E3E6, 0xC62828, 0x8A5A00, 0x1E7B34,
		/* font-height font-style row-height indent focus-width border-width */
		0, 0, -1, -1, 1, 1};
	private static final int [] DARK = {
		0x1E1F22, 0xDFE1E5, 0xA0A4AB, 0x43454A, 0x4C8DF6, 0x3FB8A8, 0x2B2D30, 6, 8,
		0x2B2D30, 0xDFE1E5, 0x43454A, 0x3A3C40, 0x55585E, 0x141517, 0x4A4D52, 0x73777F,
		0x1E1F22, 0xDFE1E5, 0x2C5FB3, 0xFFFFFF, 0x3A3C40, 0xDFE1E5, 0x2B2D30, 0xDFE1E5, 0x232427, 0x8C9096, 0x6EA4FF, 0x26272A,
		0x232427, 0x2C5FB3, 0xFFFFFF, 0x3A3D42, 0x2A2F38, 0x4C8DF6, 0x2B2D30, 0xDFE1E5, 0x34363A, 0xFF6B6B, 0xE0B040, 0x5CC26F,
		0, 0, -1, -1, 1, 1};
	private static final int [] HIGH_CONTRAST = {
		0x000000, 0xFFFFFF, 0xD0D0D0, 0xFFFFFF, 0x1AEBFF, 0xFFFF00, 0x000000, 0, 8,
		0x000000, 0xFFFFFF, 0xFFFFFF, 0x808080, 0xC0C0C0, 0xFFFFFF, 0xFFFFFF, 0x3FF23F,
		0x000000, 0xFFFFFF, 0x1AEBFF, 0x000000, 0x000000, 0xFFFFFF, 0x1AEBFF, 0x000000, 0x000000, 0xFFFFFF, 0xFFFF00, 0x000000,
		0x000000, 0x1AEBFF, 0x000000, 0xC0C0C0, 0x1A1A1A, 0xFFFF00, 0x000000, 0xFFFFFF, 0xFFFFFF, 0xFF6060, 0xFFFF00, 0x3FF23F,
		0, 0, -1, -1, 2, 2};

	private M3ThemeBuiltins () {
	}

	static int [] light () {
		return opaque (LIGHT);
	}

	static int [] dark () {
		return opaque (DARK);
	}

	static int [] highContrast () {
		return opaque (HIGH_CONTRAST);
	}

	/* every colour slot made opaque ARGB; metric slots as written */
	static int [] opaque (int [] values) {
		if (values.length != M3ThemeSlot.COUNT) throw new IllegalStateException ("built-in theme with " + values.length + " slots");
		return IntStream.range (0, values.length).map (i -> M3ThemeSlot.kind (i) == M3ThemeSlot.COLOR ? 0xFF000000 | values [i] : values [i]).toArray ();
	}
}
