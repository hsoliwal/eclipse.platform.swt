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

import java.lang.Character.*;
import java.util.stream.*;

import org.eclipse.swt.*;

/**
 * Script facts of a text for font, segmentation and direction choices (enhanced API M3-LANG-ID): the majority script of its
 * letters (<code>Character.UnicodeScript</code> counts in one int lane; COMMON and INHERITED characters are not letters of a
 * script), whether more than one script occurs, and whether the strong right-to-left letters (bidi classes R and AL)
 * outnumber the left-to-right ones. No model and no data file: the JDK's Unicode tables only.
 * <p>
 * {@link #detect(CharSequence)} answers one packed int; {@link #script(int)}, {@link #rightToLeft(int)}, {@link #mixed(int)}
 * and {@link #letters(int)} unpack it.
 * </p>
 *
 * @since 3.138
 */
public final class M3LanguageId {
	private static final int SCRIPT_BITS = 10, SCRIPT_MASK = (1 << SCRIPT_BITS) - 1, RTL = 1 << SCRIPT_BITS, MIXED = RTL << 1;
	private static final int LETTERS_SHIFT = SCRIPT_BITS + 2, MAX_LETTERS = (1 << (32 - LETTERS_SHIFT - 1)) - 1;
	private static final UnicodeScript [] SCRIPTS = UnicodeScript.values ();

	private M3LanguageId () {
	}

	/**
	 * @param text a text
	 * @return the packed facts: script ordinal, right-to-left, mixed and the letter count (capped at 2^19 - 1)
	 * @exception IllegalArgumentException <ul><li>ERROR_NULL_ARGUMENT - if the text is null</li></ul>
	 */
	public static int detect (CharSequence text) {
		if (text == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		int [] counts = new int [SCRIPTS.length];
		int [] direction = new int [2];
		text.codePoints ().filter (Character::isLetter).forEach (cp -> count (cp, counts, direction));
		int majority = IntStream.range (0, counts.length).reduce ((a, b) -> counts [b] > counts [a] ? b : a).orElse (0);
		int letters = IntStream.of (counts).sum ();
		int script = letters == 0 ? UnicodeScript.COMMON.ordinal () : majority;
		boolean mixed = IntStream.of (counts).filter (c -> c > 0).count () > 1;
		return script | (direction [1] > direction [0] ? RTL : 0) | (mixed ? MIXED : 0) | Math.min (letters, MAX_LETTERS) << LETTERS_SHIFT;
	}

	private static void count (int codePoint, int [] counts, int [] direction) {
		UnicodeScript script = UnicodeScript.of (codePoint);
		if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED) counts [script.ordinal ()]++;
		byte bidi = Character.getDirectionality (codePoint);
		if (bidi == Character.DIRECTIONALITY_LEFT_TO_RIGHT) direction [0]++;
		if (bidi == Character.DIRECTIONALITY_RIGHT_TO_LEFT || bidi == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) direction [1]++;
	}

	/**
	 * @param text a text
	 * @return the majority script of its letters, <code>COMMON</code> when it has none
	 */
	public static UnicodeScript script (CharSequence text) {
		return script (detect (text));
	}

	/** @return the majority script of packed facts */
	public static UnicodeScript script (int facts) {
		return SCRIPTS [facts & SCRIPT_MASK];
	}

	/** @return true when the facts have more strong right-to-left letters than left-to-right ones */
	public static boolean rightToLeft (int facts) {
		return (facts & RTL) != 0;
	}

	/** @return true when the letters of the facts belong to more than one script */
	public static boolean mixed (int facts) {
		return (facts & MIXED) != 0;
	}

	/** @return the number of letters counted (capped at 2^19 - 1) */
	public static int letters (int facts) {
		return facts >>> LETTERS_SHIFT;
	}
}
