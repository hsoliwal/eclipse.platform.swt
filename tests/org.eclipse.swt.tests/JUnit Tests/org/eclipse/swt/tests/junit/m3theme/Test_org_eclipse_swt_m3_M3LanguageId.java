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
package org.eclipse.swt.tests.junit.m3theme;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.Character.*;

import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/** Headless tests of M3-LANG-ID: script of pure-script text, majority of mixed text, direction, letter count. */
public class Test_org_eclipse_swt_m3_M3LanguageId {

	@Test
	public void test_pureScripts () {
		assertScript (UnicodeScript.LATIN, false, "Deutsch");
		assertScript (UnicodeScript.ARABIC, true, "العربية");
		assertScript (UnicodeScript.HEBREW, true, "עברית");
		assertScript (UnicodeScript.HAN, false, "中文");
		assertScript (UnicodeScript.DEVANAGARI, false, "हिन्दी");
		assertScript (UnicodeScript.CYRILLIC, false, "русский");
		assertScript (UnicodeScript.THAANA, true, "ދިވެހި");
	}

	private static void assertScript (UnicodeScript script, boolean rtl, String text) {
		int facts = M3LanguageId.detect (text);
		assertEquals (script, M3LanguageId.script (facts), text);
		assertEquals (rtl, M3LanguageId.rightToLeft (facts), text);
		assertFalse (M3LanguageId.mixed (facts), text);
		assertEquals (script, M3LanguageId.script (text));
	}

	@Test
	public void test_mixedTextReportsTheMajority () {
		int facts = M3LanguageId.detect ("SWT مرحبا بالعالم");
		assertEquals (UnicodeScript.ARABIC, M3LanguageId.script (facts));
		assertTrue (M3LanguageId.mixed (facts));
		assertTrue (M3LanguageId.rightToLeft (facts));
		assertEquals (15, M3LanguageId.letters (facts));
		int latin = M3LanguageId.detect ("Eclipse SWT שלום");
		assertEquals (UnicodeScript.LATIN, M3LanguageId.script (latin));
		assertFalse (M3LanguageId.rightToLeft (latin));
	}

	@Test
	public void test_noLetters () {
		int facts = M3LanguageId.detect ("123 + 456 = ?");
		assertEquals (UnicodeScript.COMMON, M3LanguageId.script (facts));
		assertEquals (0, M3LanguageId.letters (facts));
		assertFalse (M3LanguageId.mixed (facts));
		assertFalse (M3LanguageId.rightToLeft (facts));
		assertEquals (UnicodeScript.COMMON, M3LanguageId.script (""));
		assertThrows (IllegalArgumentException.class, () -> M3LanguageId.detect (null));
	}

	@Test
	public void test_letterCountIsCapped () {
		int facts = M3LanguageId.detect ("a".repeat (600_000));
		assertEquals ((1 << 19) - 1, M3LanguageId.letters (facts));
		assertEquals (UnicodeScript.LATIN, M3LanguageId.script (facts));
		assertTrue (facts > 0, "the sign bit stays clear");
	}

	@Test
	public void test_commonLettersAndNeutralDirection () {
		int facts = M3LanguageId.detect (String.valueOf ((char) 0x02B9));
		assertEquals (0, M3LanguageId.letters (facts), "a COMMON letter counts for no script");
		assertFalse (M3LanguageId.rightToLeft (facts));
	}
}
