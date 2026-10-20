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
 *     Hitesh Soliwal (Synexia) - adapted from TV_5.0 treeviewer IViewerTextFilter,
 *         AbstractRegexFilter and AbstractWildCardFilter (docs/m3/sort-filter/PROVENANCE.tsv)
 *     Hitesh Soliwal (Synexia) - match types and match case adapted from x86core jsonviewer
 *         MatcherFactory and its matchers (docs/m3/sort-filter/PROVENANCE.tsv)
 *******************************************************************************/
package org.eclipse.swt.m3;

import java.util.*;
import java.util.regex.*;

import org.eclipse.swt.*;

/**
 * A compiled text criterion of {@link M3ColumnFilter} (enhanced M3 API).
 * <p>
 * The pattern text is split at unescaped commas into tokens (<code>\,</code> is a literal comma;
 * empty tokens are ignored). A text matches when any token matches it; with {@link #NEGATE} it
 * matches when no token matches it. Token modes:
 * </p>
 * <ul>
 * <li>{@link #CONTAINS}: the token occurs in the text;</li>
 * <li>{@link #STARTS_WITH}: the text starts with the token;</li>
 * <li>{@link #EXACT}: the text equals the token;</li>
 * <li>{@link #WILDCARD}: the whole text matches the token, where <code>*</code> is any sequence,
 *     <code>?</code> any one character, and <code>\</code> escapes <code>*</code>, <code>?</code> and <code>\</code>;</li>
 * <li>{@link #REGEX}: the whole text matches the token as a <code>java.util.regex</code> pattern;</li>
 * <li>{@link #FUZZY}: the token's characters occur in the text in order (gaps allowed);</li>
 * <li>{@link #CAMEL_CASE}: the token's humps start humps of the text in order (<code>NPE</code> finds
 *     <code>NullPointerException</code>).</li>
 * </ul>
 * <p>
 * {@link #IGNORE_CASE} compares without case. A <code>null</code> text is the empty text. A match
 * object reuses its regex matchers, so it is used by one thread at a time.
 * </p>
 *
 * @since 3.138
 */
public final class M3TextMatch {

	/** Mode: the token occurs in the text. */
	public static final int CONTAINS = 0;

	/** Mode: the text starts with the token. */
	public static final int STARTS_WITH = 1;

	/** Mode: the text equals the token. */
	public static final int EXACT = 2;

	/** Mode: the whole text matches the token as a wildcard pattern (<code>*</code>, <code>?</code>). */
	public static final int WILDCARD = 3;

	/** Mode: the whole text matches the token as a regular expression. */
	public static final int REGEX = 4;

	/**
	 * Mode: the token's characters occur in the text in order, with gaps allowed (fuzzy subsequence, as in fzf);
	 * {@link #score(String)} ranks the matches.
	 *
	 * @since 3.138
	 */
	public static final int FUZZY = 5;

	/**
	 * Mode: camel case: each hump of the token (an upper-case letter or a digit, then lower-case letters) starts a
	 * hump of the text, in order (humps of the text may be skipped), so <code>NPE</code> and <code>NuPoEx</code>
	 * match <code>NullPointerException</code>; the first hump may start at any hump of the text.
	 *
	 * @since 3.138
	 */
	public static final int CAMEL_CASE = 6;

	/** Flag: a text matches when no token matches it ("doesn't match"). */
	public static final int NEGATE = 1 << 8;

	/** Flag: compare without case. */
	public static final int IGNORE_CASE = 1 << 9;

	private static final int MODE_MASK = 0xFF;
	private static final int STAR = -1, ANY = -2;

	private final String pattern;
	private final int mode;
	private final String [] tokens;
	private final int [][] wildcards;
	private final Matcher [] matchers;

	private M3TextMatch (String pattern, int mode) {
		this.pattern = pattern;
		this.mode = mode;
		tokens = tokens (pattern);
		wildcards = new int [tokens.length][];
		matchers = new Matcher [tokens.length];
		boolean ignoreCase = (mode & IGNORE_CASE) != 0;
		for (int i = 0; i < tokens.length; i++) {
			if (kind () == WILDCARD) wildcards [i] = wildcard (tokens [i]);
			if (kind () == REGEX) matchers [i] = Pattern.compile (tokens [i], ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0).matcher (""); //$NON-NLS-1$
		}
	}

	/**
	 * Compiles a pattern text.
	 *
	 * @param pattern the pattern text
	 * @param mode one of the modes, optionally or-ed with {@link #NEGATE} and {@link #IGNORE_CASE}
	 * @return the match
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the pattern is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the mode is not a mode, or a regular expression token is not valid</li>
	 * </ul>
	 */
	public static M3TextMatch compile (String pattern, int mode) {
		String problem = check (pattern, mode);
		if (!problem.isEmpty ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT, null, problem);
		return new M3TextMatch (pattern, mode);
	}

	/**
	 * Checks a pattern text: returns the empty string when {@link #compile(String, int)} accepts
	 * it, or the reason why not.
	 *
	 * @param pattern the pattern text
	 * @param mode one of the modes, optionally or-ed with {@link #NEGATE} and {@link #IGNORE_CASE}
	 * @return the empty string, or a message
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the pattern is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the mode is not a mode</li>
	 * </ul>
	 */
	public static String check (String pattern, int mode) {
		M3Checks.nonNull (pattern);
		M3Checks.argument ((mode & MODE_MASK) <= CAMEL_CASE && (mode & ~(MODE_MASK | NEGATE | IGNORE_CASE)) == 0);
		try {
			new M3TextMatch (pattern, mode);
			return ""; //$NON-NLS-1$
		} catch (PatternSyntaxException e) {
			return e.getMessage ();
		}
	}

	/**
	 * Splits a pattern text into its tokens: at every comma not preceded by a backslash; a
	 * backslash before a comma is dropped, other backslashes are kept; empty tokens are dropped.
	 *
	 * @param pattern the pattern text
	 * @return the tokens
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the pattern is null</li>
	 * </ul>
	 */
	public static String [] tokens (String pattern) {
		M3Checks.nonNull (pattern);
		List<String> tokens = new ArrayList<> ();
		StringBuilder token = new StringBuilder ();
		for (int i = 0; i < pattern.length (); i++) {
			char c = pattern.charAt (i);
			if (c == '\\' && i + 1 < pattern.length () && pattern.charAt (i + 1) == ',') {
				token.append (',');
				i++;
			} else if (c == ',') {
				flush (token, tokens);
			} else {
				token.append (c);
			}
		}
		flush (token, tokens);
		return tokens.toArray (new String [tokens.size ()]);
	}

	private static void flush (StringBuilder token, List<String> tokens) {
		if (token.length () > 0) tokens.add (token.toString ());
		token.setLength (0);
	}

	/**
	 * Returns the pattern text.
	 *
	 * @return the pattern
	 */
	public String pattern () {
		return pattern;
	}

	/**
	 * Returns the mode with its flags.
	 *
	 * @return the mode
	 */
	public int mode () {
		return mode;
	}

	/**
	 * Returns whether the pattern has a token. A match without tokens matches every text and
	 * {@link M3ColumnFilter} treats it as no criterion.
	 *
	 * @return whether there is a token
	 */
	public boolean isActive () {
		return tokens.length > 0;
	}

	/**
	 * Returns whether a text matches.
	 *
	 * @param text the text, <code>null</code> for the empty text
	 * @return whether it matches
	 */
	public boolean matches (String text) {
		return tokens.length == 0 || any (text) != negated ();
	}

	/* whether any token matches the text, ignoring NEGATE */
	boolean any (String text) {
		String value = text == null ? "" : text; //$NON-NLS-1$
		for (int i = 0; i < tokens.length; i++) {
			if (token (i, value)) return true;
		}
		return false;
	}

	boolean negated () {
		return (mode & NEGATE) != 0;
	}

	/*
	 * whether every text this match accepts is also accepted by previous (typing more characters of a contains or
	 * starts-with filter): then only the rows previous kept need testing. The previous match tests this match's
	 * token itself, so the relation uses exactly the matching rules; with IGNORE_CASE only ASCII tokens qualify.
	 */
	boolean narrows (M3TextMatch previous) {
		if (previous == null || previous.mode != mode || negated () || tokens.length != 1 || previous.tokens.length != 1) return false;
		if (kind () != CONTAINS && kind () != STARTS_WITH && kind () != FUZZY) return false;
		if ((mode & IGNORE_CASE) != 0 && !(ascii (tokens [0]) && ascii (previous.tokens [0]))) return false;
		return previous.any (tokens [0]);
	}

	private static boolean ascii (String token) {
		for (int i = 0; i < token.length (); i++) {
			if (token.charAt (i) > 127) return false;
		}
		return true;
	}

	private int kind () {
		return mode & MODE_MASK;
	}

	private boolean token (int i, String text) {
		boolean ignoreCase = (mode & IGNORE_CASE) != 0;
		String token = tokens [i];
		switch (kind ()) {
			case CONTAINS: return contains (text, token, ignoreCase);
			case STARTS_WITH: return text.regionMatches (ignoreCase, 0, token, 0, token.length ());
			case EXACT: return ignoreCase ? text.equalsIgnoreCase (token) : text.equals (token);
			case WILDCARD: return wildcard (wildcards [i], text, ignoreCase);
			case FUZZY: return fuzzy (token, text, ignoreCase, null) >= 0;
			case CAMEL_CASE: return camel (token, 0, text, 0, null) >= 0;
			default: return matchers [i].reset (text).matches ();
		}
	}

	private static boolean contains (String text, String token, boolean ignoreCase) {
		if (!ignoreCase) return text.contains (token);
		for (int i = 0, last = text.length () - token.length (); i <= last; i++) {
			if (text.regionMatches (true, i, token, 0, token.length ())) return true;
		}
		return false;
	}


	/**
	 * Returns how well a text matches: -1 when it does not match (see {@link #matches(String)}), otherwise a score
	 * that is higher for a better match. For {@link #FUZZY} and {@link #CAMEL_CASE} it is the best token's score: 16
	 * per matched character, more at the start of a word or a hump and for consecutive characters, less for gaps.
	 * For the other modes, and for a negated match, it is 0.
	 *
	 * @param text the text, <code>null</code> for the empty text
	 * @return the score, or -1
	 */
	public int score (String text) {
		if (!matches (text)) return -1;
		if (negated () || kind () != FUZZY && kind () != CAMEL_CASE) return 0;
		String value = text == null ? "" : text; //$NON-NLS-1$
		int best = 0;
		for (String token : tokens) {
			int [] positions = positions (token, value);
			if (positions != null) best = Math.max (best, points (value, positions));
		}
		return best;
	}

	/**
	 * Writes the character ranges of a text that the first matching token covers, as pairs of start (inclusive) and
	 * end (exclusive) indexes, for highlighting: the occurrence for {@link #CONTAINS}, the prefix for
	 * {@link #STARTS_WITH}, the matched characters (adjacent ones joined) for {@link #FUZZY} and
	 * {@link #CAMEL_CASE}, the whole text for the other modes. Writes nothing for a negated match.
	 *
	 * @param text the text, <code>null</code> for the empty text
	 * @param ranges receives the pairs; pairs that do not fit are dropped
	 * @return the number of pairs written, or -1 when the text does not match
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the ranges are null</li>
	 * </ul>
	 */
	public int highlights (String text, int [] ranges) {
		M3Checks.nonNull (ranges);
		if (!matches (text)) return -1;
		if (negated ()) return 0;
		String value = text == null ? "" : text; //$NON-NLS-1$
		for (int i = 0; i < tokens.length; i++) {
			if (!token (i, value)) continue;
			String token = tokens [i];
			switch (kind ()) {
				case CONTAINS: {
					int at = indexOf (value, token, (mode & IGNORE_CASE) != 0);
					return pair (ranges, 0, at, at + token.length ());
				}
				case STARTS_WITH: return pair (ranges, 0, 0, token.length ());
				case FUZZY:
				case CAMEL_CASE: {
					int [] positions = positions (token, value);
					int pairs = 0;
					for (int k = 0; k < positions.length; k++) {
						int start = positions [k];
						while (k + 1 < positions.length && positions [k + 1] == positions [k] + 1) k++;
						pairs += pair (ranges, pairs, start, positions [k] + 1);
					}
					return pairs;
				}
				default: return pair (ranges, 0, 0, value.length ());
			}
		}
		return 0;
	}

	private static int pair (int [] ranges, int index, int start, int end) {
		if (index * 2 + 1 >= ranges.length) return 0;
		ranges [index * 2] = start;
		ranges [index * 2 + 1] = end;
		return 1;
	}

	private static int indexOf (String text, String token, boolean ignoreCase) {
		for (int i = 0, last = text.length () - token.length (); i <= last; i++) {
			if (text.regionMatches (ignoreCase, i, token, 0, token.length ())) return i;
		}
		return -1;
	}

	/* the text index of each token character for a FUZZY or CAMEL_CASE token, or null */
	private int [] positions (String token, String text) {
		int [] positions = new int [token.length ()];
		boolean found = kind () == FUZZY ? fuzzy (token, text, (mode & IGNORE_CASE) != 0, positions) >= 0 : camel (token, 0, text, 0, positions) >= 0;
		return found ? positions : null;
	}

	/*
	 * fuzzy subsequence (the fzf v1 pattern, written fresh): a forward scan finds where the token first ends in the
	 * text; with positions wanted, a backward scan from there finds the shortest window and a forward scan inside it
	 * records the positions. Returns 0, or -1 when the token is not a subsequence of the text.
	 */
	private static int fuzzy (String token, String text, boolean ignoreCase, int [] positions) {
		int n = token.length (), t = 0, p = 0;
		for (; t < text.length () && p < n; t++) {
			if (same (token.charAt (p), text.charAt (t), ignoreCase)) p++;
		}
		if (p < n) return -1;
		if (positions == null || n == 0) return 0;
		int start = t;
		for (p = n - 1; p >= 0;) {
			if (same (token.charAt (p), text.charAt (--start), ignoreCase)) p--;
		}
		for (int i = start; p < n - 1; i++) {
			if (same (token.charAt (p + 1), text.charAt (i), ignoreCase)) positions [++p] = i;
		}
		return 0;
	}

	/*
	 * camel case from token index p and text index t: the token hump starting at p begins at a hump of the text at or
	 * after t and its lower-case tail continues there; later humps are tried when the rest does not match
	 * (backtracking). Positions (when not null) receive the text index of each token character. Returns 0 or -1.
	 */
	private static int camel (String token, int p, String text, int t, int [] positions) {
		if (p == token.length ()) return 0;
		for (int h = t; h < text.length (); h++) {
			if (!hump (text, h) || !same (token.charAt (p), text.charAt (h), true)) continue;
			int q = p + 1, u = h + 1;
			while (q < token.length () && !humpChar (token.charAt (q)) && u < text.length () && same (token.charAt (q), text.charAt (u), true)) {
				q++;
				u++;
			}
			if (q < token.length () && !humpChar (token.charAt (q))) continue;
			if (camel (token, q, text, u, positions) < 0) continue;
			if (positions != null) {
				for (int k = p; k < q; k++) positions [k] = h + k - p;
			}
			return 0;
		}
		return -1;
	}

	private static boolean humpChar (char c) {
		return Character.isUpperCase (c) || Character.isDigit (c);
	}

	/*
	 * whether a hump of the text starts at i: a word start, an upper-case letter (each letter of XML is a hump, as in
	 * JDT camel case), or the start of a digit run
	 */
	private static boolean hump (String text, int i) {
		char c = text.charAt (i);
		if (!Character.isLetterOrDigit (c)) return false;
		if (i == 0) return true;
		char before = text.charAt (i - 1);
		if (!Character.isLetterOrDigit (before)) return true;
		if (Character.isUpperCase (c)) return true;
		return Character.isDigit (c) && !Character.isDigit (before);
	}

	/*
	 * the score of matched positions: 16 each, +8 at a word start or +7 at a hump (doubled for the first character),
	 * +4 right after a matched character, -3 for a gap and -1 per further gap character; at least 0
	 */
	private static int points (String text, int [] positions) {
		int score = 0;
		for (int k = 0; k < positions.length; k++) {
			int i = positions [k], bonus = 0;
			if (i == 0 || !Character.isLetterOrDigit (text.charAt (i - 1))) bonus = 8;
			else if (hump (text, i)) bonus = 7;
			score += 16 + (k == 0 ? 2 * bonus : bonus);
			if (k > 0) score += positions [k - 1] == i - 1 ? 4 : -3 - (i - positions [k - 1] - 2);
		}
		return Math.max (0, score);
	}
	/* a wildcard token as codes: STAR, ANY, or a literal character */
	private static int [] wildcard (String token) {
		int [] codes = new int [token.length ()];
		int n = 0;
		for (int i = 0; i < token.length (); i++) {
			char c = token.charAt (i);
			if (c == '\\' && i + 1 < token.length () && "*?\\".indexOf (token.charAt (i + 1)) >= 0) { //$NON-NLS-1$
				codes [n++] = token.charAt (++i);
			} else {
				codes [n++] = c == '*' ? STAR : c == '?' ? ANY : c;
			}
		}
		return Arrays.copyOf (codes, n);
	}

	/* greedy two-pointer wildcard match with backtracking to the last star (LeetCode 44 category) */
	private static boolean wildcard (int [] codes, String text, boolean ignoreCase) {
		int t = 0, p = 0, star = -1, resume = 0;
		while (t < text.length ()) {
			if (p < codes.length && codes [p] != STAR && (codes [p] == ANY || same (codes [p], text.charAt (t), ignoreCase))) {
				t++;
				p++;
			} else if (p < codes.length && codes [p] == STAR) {
				star = p++;
				resume = t;
			} else if (star >= 0) {
				p = star + 1;
				t = ++resume;
			} else {
				return false;
			}
		}
		while (p < codes.length && codes [p] == STAR) p++;
		return p == codes.length;
	}

	private static boolean same (int code, char c, boolean ignoreCase) {
		if (code == c) return true;
		if (!ignoreCase) return false;
		char a = Character.toUpperCase ((char) code), b = Character.toUpperCase (c);
		return a == b || Character.toLowerCase (a) == Character.toLowerCase (b);
	}

	@Override
	public String toString () {
		return "M3TextMatch [" + pattern + ", mode " + Integer.toHexString (mode) + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
