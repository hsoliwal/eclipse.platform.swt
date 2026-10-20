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
package org.eclipse.swt.tests.junit;

import static org.eclipse.swt.m3.M3TextMatch.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.*;

import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/** Headless tests of {@link M3TextMatch}: modes, flags, tokens, wildcard and regular-expression semantics. */
@SuppressWarnings ("nls")
public class Test_M3TextMatch {

	static boolean m (String pattern, int mode, String text) {
		return M3TextMatch.compile (pattern, mode).matches (text);
	}

	@Test
	public void test_containsStartsWithExact () {
		assertTrue (m ("b", CONTAINS, "abc"));
		assertFalse (m ("B", CONTAINS, "abc"));
		assertTrue (m ("B", CONTAINS | IGNORE_CASE, "aBc"));
		assertTrue (m ("C", CONTAINS | IGNORE_CASE, "abc"));
		assertFalse (m ("abcd", CONTAINS | IGNORE_CASE, "abc"));
		assertTrue (m ("ab", STARTS_WITH, "abc"));
		assertFalse (m ("bc", STARTS_WITH, "abc"));
		assertTrue (m ("AB", STARTS_WITH | IGNORE_CASE, "abc"));
		assertTrue (m ("abc", EXACT, "abc"));
		assertFalse (m ("ab", EXACT, "abc"));
		assertTrue (m ("ABC", EXACT | IGNORE_CASE, "abc"));
		assertFalse (m ("x", CONTAINS, null));
		assertTrue (m ("x", CONTAINS | NEGATE, null));
	}

	@Test
	public void test_wildcardMatchesTheWholeText () {
		assertTrue (m ("a*c", WILDCARD, "abc"));
		assertTrue (m ("a*c", WILDCARD, "ac"));
		assertFalse (m ("a*c", WILDCARD, "abd"));
		assertFalse (m ("a*c", WILDCARD, "xabc"));
		assertTrue (m ("a?c", WILDCARD, "abc"));
		assertFalse (m ("a?c", WILDCARD, "ac"));
		assertTrue (m ("*", WILDCARD, ""));
		assertTrue (m ("*", WILDCARD, "anything"));
		assertTrue (m ("**a**", WILDCARD, "xxaxx"));
		assertTrue (m ("*a*b", WILDCARD, "xaxaxb"));
		assertFalse (m ("*a*b", WILDCARD, "xaxaxbc"));
		assertTrue (m ("a\\*c", WILDCARD, "a*c"));
		assertFalse (m ("a\\*c", WILDCARD, "abc"));
		assertTrue (m ("a\\?", WILDCARD, "a?"));
		assertFalse (m ("a\\?", WILDCARD, "ab"));
		assertTrue (m ("a\\\\", WILDCARD, "a\\"));
		assertTrue (m ("a\\b", WILDCARD, "a\\b"));
		assertTrue (m ("A*C", WILDCARD | IGNORE_CASE, "abc"));
		assertFalse (m ("A*C", WILDCARD, "abc"));
		assertTrue (m ("\u0130*", WILDCARD | IGNORE_CASE, "i"));
		assertTrue (m ("a\\", WILDCARD, "a\\"));
	}

	@Test
	public void test_regexMatchesTheWholeText () {
		assertFalse (m ("b", REGEX, "abc"));
		assertTrue (m (".*b.*", REGEX, "abc"));
		assertTrue (m ("A.C", REGEX | IGNORE_CASE, "abc"));
		assertFalse (m ("A.C", REGEX, "abc"));
		assertTrue (m ("a{1\\,3}", REGEX, "aaa"));
		assertFalse (m ("a{1\\,3}", REGEX, "aaaa"));
		assertTrue (m ("x,a+", REGEX, "aa"));
		assertFalse (m ("x,a+", REGEX | NEGATE, "aa"));
	}

	@Test
	public void test_tokensAndNegation () {
		assertArrayEquals (new String [] {"a", "b"}, tokens ("a,b"));
		assertArrayEquals (new String [] {"a,b"}, tokens ("a\\,b"));
		assertArrayEquals (new String [] {"a"}, tokens (",,a,"));
		assertArrayEquals (new String [] {"a\\x"}, tokens ("a\\x"));
		assertArrayEquals (new String [] {"a\\"}, tokens ("a\\"));
		assertArrayEquals (new String [0], tokens (""));
		assertTrue (m ("x,b", CONTAINS, "abc"));
		assertFalse (m ("x,b", CONTAINS | NEGATE, "abc"));
		assertTrue (m ("x,y", CONTAINS | NEGATE, "abc"));
		M3TextMatch none = M3TextMatch.compile (",", CONTAINS | NEGATE);
		assertFalse (none.isActive ());
		assertTrue (none.matches ("abc"));
		M3TextMatch match = M3TextMatch.compile ("a,b", WILDCARD | IGNORE_CASE);
		assertEquals ("a,b", match.pattern ());
		assertEquals (WILDCARD | IGNORE_CASE, match.mode ());
		assertTrue (match.isActive ());
		assertTrue (match.toString ().contains ("a,b"));
	}

	@Test
	public void test_checkAndErrors () {
		assertEquals ("", check (".*", REGEX));
		assertEquals ("", check ("(", WILDCARD));
		String problem = check ("ok,(", REGEX);
		assertFalse (problem.isEmpty ());
		try {
			Pattern.compile ("(");
			fail ();
		} catch (PatternSyntaxException e) {
			assertEquals (e.getMessage (), problem);
		}
		IllegalArgumentException e = assertThrows (IllegalArgumentException.class, () -> M3TextMatch.compile ("(", REGEX));
		assertTrue (e.getMessage ().contains ("Unclosed group"), e.getMessage ());
		assertThrows (IllegalArgumentException.class, () -> M3TextMatch.compile (null, CONTAINS));
		assertThrows (IllegalArgumentException.class, () -> M3TextMatch.compile ("x", 7));
		assertThrows (IllegalArgumentException.class, () -> M3TextMatch.compile ("x", 1 << 12));
		assertThrows (IllegalArgumentException.class, () -> check (null, CONTAINS));
		assertThrows (IllegalArgumentException.class, () -> check ("x", 7));
		assertThrows (IllegalArgumentException.class, () -> check ("x", -1));
		assertThrows (IllegalArgumentException.class, () -> tokens (null));
	}

	@Test
	public void test_wildcardAgainstRegexTranslation () {
		Random random = new Random (5);
		char [] alphabet = {'a', 'b', '*', '?'};
		for (int i = 0; i < 3000; i++) {
			StringBuilder pattern = new StringBuilder (), regex = new StringBuilder (), text = new StringBuilder ();
			for (int k = random.nextInt (6); k > 0; k--) {
				char c = alphabet [random.nextInt (4)];
				pattern.append (c);
				regex.append (c == '*' ? ".*" : c == '?' ? "." : String.valueOf (c));
			}
			for (int k = random.nextInt (7); k > 0; k--) text.append (alphabet [random.nextInt (2)]);
			String p = pattern.toString ();
			if (p.isEmpty ()) continue;
			assertEquals (text.toString ().matches (regex.toString ()), m (p, WILDCARD, text.toString ()), p + " / " + text);
		}
	}

	@Test
	public void test_camelCase () {
		assertTrue (m ("NPE", CAMEL_CASE, "NullPointerException"));
		assertTrue (m ("NPE", CAMEL_CASE, "java.lang.NullPointerException: x"));
		assertTrue (m ("NuPoEx", CAMEL_CASE, "NullPointerException"));
		assertTrue (m ("NE", CAMEL_CASE, "NullPointerException"));
		assertTrue (m ("XMLP", CAMEL_CASE, "XMLParser"));
		assertTrue (m ("XP", CAMEL_CASE, "XMLParser"));
		assertTrue (m ("UTF8E", CAMEL_CASE, "UTF8Encoder"));
		assertTrue (m ("nPE", CAMEL_CASE, "NullPointerException"));
		assertTrue (m ("AbC", CAMEL_CASE, "AxBdAbBcC"));
		assertFalse (m ("NPF", CAMEL_CASE, "NullPointerException"));
		assertFalse (m ("NuX", CAMEL_CASE, "NullPointerException"));
		assertFalse (m ("PN", CAMEL_CASE, "NullPointerException"));
		assertFalse (m ("NPE", CAMEL_CASE, null));
		assertTrue (m ("NPE", CAMEL_CASE | NEGATE, "IllegalStateException"));
	}

	@Test
	public void test_fuzzySubsequence () {
		assertTrue (m ("nlpex", FUZZY | IGNORE_CASE, "NullPointerException"));
		assertFalse (m ("nlpex", FUZZY, "NullPointerException"));
		assertTrue (m ("NPE", FUZZY, "NullPointerException"));
		assertFalse (m ("NPEX", FUZZY, "NullPointerException"));
		assertTrue (m ("abc,xyz", FUZZY, "a-x-y-z"));
		M3TextMatch npe = M3TextMatch.compile ("npe", FUZZY | IGNORE_CASE);
		int humps = npe.score ("NullPointerException"), inside = npe.score ("unpaired");
		assertTrue (humps > inside, humps + " > " + inside);
		assertEquals (-1, npe.score ("plain"));
		assertEquals (0, M3TextMatch.compile ("np", CONTAINS).score ("np"));
		assertTrue (M3TextMatch.compile ("npe", FUZZY).score ("npe") > M3TextMatch.compile ("npe", FUZZY).score ("nxpxe"));
	}

	@Test
	public void test_highlights () {
		int [] ranges = new int [8];
		assertEquals (1, M3TextMatch.compile ("point", CONTAINS | IGNORE_CASE).highlights ("NullPointerException", ranges));
		assertArrayEquals (new int [] {4, 9}, Arrays.copyOf (ranges, 2));
		assertEquals (3, M3TextMatch.compile ("npe", FUZZY | IGNORE_CASE).highlights ("NullPointerException", ranges));
		assertArrayEquals (new int [] {0, 1, 4, 5, 9, 10}, Arrays.copyOf (ranges, 6));
		assertEquals (3, M3TextMatch.compile ("NuPoEx", CAMEL_CASE).highlights ("NullPointerException", ranges));
		assertArrayEquals (new int [] {0, 2, 4, 6, 11, 13}, Arrays.copyOf (ranges, 6));
		assertEquals (1, M3TextMatch.compile ("ab", FUZZY).highlights ("xxab", ranges));
		assertArrayEquals (new int [] {2, 4}, Arrays.copyOf (ranges, 2));
		assertEquals (1, M3TextMatch.compile ("Nu", STARTS_WITH).highlights ("Null", ranges));
		assertEquals (1, M3TextMatch.compile ("N*", WILDCARD).highlights ("Null", ranges));
		assertArrayEquals (new int [] {0, 4}, Arrays.copyOf (ranges, 2));
		assertEquals (0, M3TextMatch.compile ("x", CONTAINS | NEGATE).highlights ("Null", ranges));
		assertEquals (-1, M3TextMatch.compile ("x", CONTAINS).highlights ("Null", ranges));
		assertEquals (1, M3TextMatch.compile ("abc", FUZZY).highlights ("a1b2c", new int [2]));
		assertThrows (IllegalArgumentException.class, () -> M3TextMatch.compile ("x", CONTAINS).highlights ("x", null));
		assertThrows (IllegalArgumentException.class, () -> M3TextMatch.compile ("x", 7));
	}

	/* fuzzy against a plain subsequence check, random texts and tokens */
	@Test
	public void test_fuzzyAgainstASubsequenceCheck () {
		Random random = new Random (5);
		for (int k = 0; k < 20000; k++) {
			String text = word (random, 12), token = word (random, 4);
			int t = 0;
			for (int i = 0; i < text.length () && t < token.length (); i++) {
				if (text.charAt (i) == token.charAt (t)) t++;
			}
			M3TextMatch match = M3TextMatch.compile (token, FUZZY);
			assertEquals (t == token.length (), match.matches (text), token + " / " + text);
			int [] ranges = new int [2 * token.length ()];
			int pairs = match.highlights (text, ranges), covered = 0;
			for (int p = 0; p < pairs; p++) covered += ranges [2 * p + 1] - ranges [2 * p];
			if (match.matches (text)) assertEquals (token.length (), covered, token + " / " + text);
		}
	}

	private static String word (Random random, int max) {
		StringBuilder word = new StringBuilder ();
		for (int k = 1 + random.nextInt (max); k > 0; k--) word.append ((char) ('a' + random.nextInt (3)));
		return word.toString ();
	}
}
