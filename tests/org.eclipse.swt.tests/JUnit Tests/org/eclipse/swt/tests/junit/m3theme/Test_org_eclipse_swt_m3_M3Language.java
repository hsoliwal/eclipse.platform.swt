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

import java.text.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/**
 * Headless tests of the language switch without widgets: the lookup chain and its fallbacks, message formats, right-to-left
 * detection, the locale formats and collation, listeners, the startup property and argument checks.
 */
public class Test_org_eclipse_swt_m3_M3Language {
	static final String BASE = Test_org_eclipse_swt_m3_M3Language.class.getName () + "$Msgs";

	/** Root messages. */
	public static class Msgs extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"hello", "Hello"}, {"only.root", "Root"}, {"shared", "Same"}, {"count", "{0} things"}};
		}
	}

	/** German. */
	public static class Msgs_de extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"hello", "Hallo"}, {"shared", "Same"}, {"count", "{0} Dinge"}};
		}
	}

	/** Arabic. */
	public static class Msgs_ar extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"hello", "مرحبا"}};
		}
	}

	/** French, used as a fallback. */
	public static class Msgs_fr extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"hello", "Bonjour"}, {"only.fr", "Seulement"}};
		}
	}

	@BeforeEach
	public void setUp () {
		M3Language.reset ();
		M3Language.addBundle (BASE, getClass ().getClassLoader ());
	}

	@AfterEach
	public void tearDown () {
		M3Language.reset ();
		System.clearProperty (M3Language.PROPERTY);
	}

	@Test
	public void test_chain () {
		M3Language.setFallbacks (Locale.FRENCH);
		assertEquals (List.of (Locale.forLanguageTag ("ar-EG"), Locale.forLanguageTag ("ar"), Locale.FRENCH, Locale.ROOT), M3Language.chain (Locale.forLanguageTag ("ar-EG")));
		M3Language.setFallbacks ();
		assertEquals (List.of (Locale.GERMAN, Locale.ROOT), M3Language.chain (Locale.GERMAN));
		assertEquals (List.of (Locale.ROOT), M3Language.chain (Locale.ROOT));
		assertThrows (IllegalArgumentException.class, () -> M3Language.setFallbacks ((Locale []) null));
		assertThrows (IllegalArgumentException.class, () -> M3Language.setFallbacks (Locale.FRENCH, null));
	}

	@Test
	public void test_lookupAlongTheChain () {
		assertEquals (0, M3Language.setLocale (Locale.GERMAN), "no display, nothing relabelled");
		assertEquals ("Hallo", M3Language.message ("hello"));
		assertEquals ("Root", M3Language.message ("only.root"));
		assertEquals ("Same", M3Language.message ("shared"));
		assertEquals ("only.fr", M3Language.message ("only.fr"), "no fallback: the key");
		assertEquals (Optional.empty (), M3Language.find ("only.fr"));
		M3Language.setFallbacks (Locale.FRENCH);
		assertEquals ("Seulement", M3Language.message ("only.fr"), "the fallback before the root bundle");
		assertEquals ("Hallo", M3Language.message ("hello"), "the active locale before the fallback");
		M3Language.setLocale (Locale.forLanguageTag ("ar-EG"));
		assertEquals ("مرحبا", M3Language.message ("hello"));
		M3Language.setLocale (Locale.JAPANESE);
		assertEquals ("Bonjour", M3Language.message ("hello"), "Japanese has no bundle: the fallback");
		assertThrows (IllegalArgumentException.class, () -> M3Language.find (null));
	}

	@Test
	public void test_bundles () {
		M3Language.addBundle ("no.such.Bundle", getClass ().getClassLoader ());
		M3Language.setLocale (Locale.GERMAN);
		assertEquals ("Hallo", M3Language.message ("hello"), "a missing bundle is skipped");
		M3Language.addBundle (BASE, getClass ().getClassLoader ());
		M3Language.removeBundle (BASE);
		assertEquals ("hello", M3Language.message ("hello"));
		assertThrows (IllegalArgumentException.class, () -> M3Language.addBundle (null, getClass ().getClassLoader ()));
		assertThrows (IllegalArgumentException.class, () -> M3Language.addBundle (BASE, null));
	}

	@Test
	public void test_format () {
		M3Language.setLocale (Locale.GERMAN);
		assertEquals ("1.234.567 Dinge", M3Language.format ("count", 1234567));
		M3Language.setLocale (Locale.US);
		assertEquals ("1,234,567 things", M3Language.format ("count", 1234567));
	}

	@Test
	public void test_rightToLeft () {
		for (String tag : List.of ("ar", "he", "fa", "ur", "iw", "yi", "dv", "pa-Arab", "az-Arab")) {
			assertTrue (M3Language.isRightToLeft (Locale.forLanguageTag (tag)), tag);
			assertEquals (SWT.RIGHT_TO_LEFT, M3Language.orientation (Locale.forLanguageTag (tag)), tag);
		}
		for (String tag : List.of ("en", "de", "ja", "sr-Latn", "en-Qaaa", "xx", "pa")) {
			assertFalse (M3Language.isRightToLeft (Locale.forLanguageTag (tag)), tag);
			assertEquals (SWT.LEFT_TO_RIGHT, M3Language.orientation (Locale.forLanguageTag (tag)), tag);
		}
		assertFalse (M3Language.isRightToLeft (Locale.ROOT));
		assertThrows (IllegalArgumentException.class, () -> M3Language.isRightToLeft (null));
	}

	@Test
	public void test_localeFormatsAndCollation () {
		M3Language.setLocale (Locale.GERMAN);
		assertEquals ("1.234,5", M3Language.numberFormat ().format (1234.5));
		assertEquals (DateFormat.getDateInstance (DateFormat.SHORT, Locale.GERMAN), M3Language.dateFormat (DateFormat.SHORT));
		M3SortKeys keys = M3Language.collate (new String [] {"banana", "Äpfel", "apple"});
		int [] order = keys.sortedOrder ();
		assertArrayEquals (new int [] {1, 2, 0}, order, "German sorts Ä with a: Äpfel < apple < banana");
		M3Language.setLocale (Locale.forLanguageTag ("sv"));
		assertArrayEquals (new int [] {2, 0, 1}, M3Language.collate (new String [] {"banana", "Äpfel", "apple"}).sortedOrder (), "Swedish after z");
	}

	@Test
	public void test_listenersAndStartupLocale () {
		List<String> told = new ArrayList<> ();
		M3Language.Listener listener = (from, to) -> told.add (from.toLanguageTag () + ">" + to.toLanguageTag ());
		M3Language.addListener (listener);
		M3Language.setLocale (Locale.GERMAN);
		M3Language.setLocale (Locale.FRENCH);
		M3Language.removeListener (listener);
		M3Language.setLocale (Locale.ITALIAN);
		assertEquals (List.of (Locale.getDefault ().toLanguageTag () + ">de", "de>fr"), told);
		M3Language.reset ();
		System.setProperty (M3Language.PROPERTY, " ja-JP ");
		assertEquals (Locale.forLanguageTag ("ja-JP"), M3Language.getLocale ());
		System.setProperty (M3Language.PROPERTY, " ");
		assertEquals (Locale.getDefault (), M3Language.getLocale ());
		assertThrows (IllegalArgumentException.class, () -> M3Language.addListener (null));
		assertThrows (IllegalArgumentException.class, () -> M3Language.setLocale (null));
	}

	@Test
	public void test_argumentChecksWithoutWidgets () {
		assertThrows (IllegalArgumentException.class, () -> M3Language.register (null, "k"));
		assertThrows (IllegalArgumentException.class, () -> M3Language.register (null, M3Language.TEXT, "k", "f"));
		assertThrows (IllegalArgumentException.class, () -> M3Language.unregister (null));
	}
}
