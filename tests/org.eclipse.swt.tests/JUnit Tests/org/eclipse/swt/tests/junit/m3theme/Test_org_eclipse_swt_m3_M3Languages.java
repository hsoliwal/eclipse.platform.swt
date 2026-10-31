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
import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/** Headless tests of the language list generated from HF_LANGUAGE_TAGS.tsv (tag, native name, script, direction, models). */
public class Test_org_eclipse_swt_m3_M3Languages {

	@Test
	public void test_listIsSortedAndComplete () {
		assertEquals (1669, M3Languages.count ());
		List<String> tags = IntStream.range (0, M3Languages.count ()).mapToObj (M3Languages::tag).toList ();
		assertEquals (tags.stream ().sorted ().toList (), tags, "sorted by tag");
		assertEquals (tags.size (), new HashSet<> (tags).size (), "unique tags");
		IntStream.range (0, M3Languages.count ()).forEach (i -> assertTrue (M3Languages.models (i) > 0 && !M3Languages.nativeName (i).isEmpty ()));
	}

	@Test
	public void test_entries () {
		int ar = M3Languages.indexOf ("ar");
		assertEquals ("Arabic", M3Languages.name (ar));
		assertEquals ("العربية", M3Languages.nativeName (ar));
		assertEquals (UnicodeScript.ARABIC, M3Languages.script (ar));
		assertTrue (M3Languages.rightToLeft (ar));
		assertEquals ("cldr", M3Languages.source (ar));
		assertEquals (Locale.forLanguageTag ("ar"), M3Languages.locale (ar));
		int de = M3Languages.indexOf ("DE");
		assertEquals ("Deutsch", M3Languages.nativeName (de));
		assertFalse (M3Languages.rightToLeft (de));
		int dv = M3Languages.indexOf ("dv");
		assertEquals ("override", M3Languages.source (dv));
		assertEquals (UnicodeScript.THAANA, M3Languages.script (dv));
		assertEquals ("hf", M3Languages.source (M3Languages.indexOf ("aai")));
	}

	@Test
	public void test_rightToLeftLanguages () {
		for (String tag : List.of ("ar", "he", "fa", "ur", "yi", "ps", "sd", "ug", "ckb", "dv", "syr", "nqo", "arz")) {
			assertTrue (M3Languages.rightToLeft (M3Languages.indexOf (tag)), tag);
		}
		for (String tag : List.of ("en", "de", "hi", "ja", "zh", "ru", "tr", "az", "kk")) {
			assertFalse (M3Languages.rightToLeft (M3Languages.indexOf (tag)), tag);
		}
		assertEquals (60, IntStream.range (0, M3Languages.count ()).filter (M3Languages::rightToLeft).count ());
	}

	@Test
	public void test_indexOf () {
		assertEquals (M3Languages.indexOf ("pt"), M3Languages.indexOf ("pt-BR"));
		assertEquals (M3Languages.indexOf ("zh"), M3Languages.indexOf ("zh_Hant_TW"));
		assertEquals (-1, M3Languages.indexOf ("xx"));
		assertEquals (-1, M3Languages.indexOf (""));
		assertThrows (IllegalArgumentException.class, () -> M3Languages.indexOf (null));
		assertThrows (IllegalArgumentException.class, () -> M3Languages.tag (-1));
		assertThrows (IllegalArgumentException.class, () -> M3Languages.models (M3Languages.count ()));
		assertThrows (IllegalArgumentException.class, () -> M3Languages.script (M3Languages.count ()));
	}

	@Test
	public void test_topByModels () {
		int [] top = M3Languages.top (5);
		assertEquals (5, top.length);
		assertEquals ("en", M3Languages.tag (top [0]));
		IntStream.range (1, top.length).forEach (i -> assertTrue (M3Languages.models (top [i - 1]) >= M3Languages.models (top [i])));
		assertEquals (0, M3Languages.top (-1).length);
		assertEquals (M3Languages.count (), M3Languages.top (Integer.MAX_VALUE).length);
	}
}
