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
package org.eclipse.swt.internal.m3.collections;

import java.util.Objects;

/**
 * A user-interface label carried in one {@code long}: either the whole text of
 * a short label (an immediate) or the id of a longer label in an
 * {@link M3LabelPool}.
 *
 * <p>Short labels (severities, units, numbers, file extensions, short names)
 * need no String, no pool entry and no reference count: their text is the
 * value, equality is {@code ==} and the hash is a mix of the value. The layout
 * of an immediate is the one of the M3 JDK's immediate String owner, so on the
 * M3 JDK the same text has the same 64-bit value:</p>
 * <pre>
 *   bits 63..61  tag: 0 = pool id (bits 30..0), 1 = LATIN1, 2 = IDENT; 3..7 never used
 *   LATIN1  1..7 units, each &lt;= 0xFF; bits 58..56 length; unit i at bits 8i..8i+7
 *   IDENT   8..10 units of [0-9A-Za-z_$]; bijective base 64 over bits 60..0,
 *           least significant digit first, digit = 1 + index in "0-9A-Za-z_$"
 * </pre>
 * <p>The profile is chosen from the content (LATIN1 first, IDENT only for the
 * lengths LATIN1 cannot hold), so equal text always packs to the same value.
 * The empty label is pool id 0, the value 0. Packing short text into a machine
 * word follows tools4j shortstring (MIT); its codec is not used here because it
 * spends the whole word and leaves no room for a tag.</p>
 *
 * <p>All methods are static and allocation-free except {@link #toString(long)}
 * and the pool's String facades.</p>
 */
public final class M3ShortLabel {
	/** The value of the empty label (pool id 0). */
	public static final long EMPTY = 0L;
	/** Never a label value; returned by {@link #pack(CharSequence)} for text that needs a pool entry. */
	public static final long NONE = -1L;

	static final int TAG_SHIFT = 61;
	static final long TAG_MASK = 7L << TAG_SHIFT;
	static final long TAG_LATIN1 = 1L << TAG_SHIFT;
	static final long TAG_IDENT = 2L << TAG_SHIFT;
	static final long PAYLOAD_MASK = (1L << TAG_SHIFT) - 1L;
	static final int LATIN1_MAX = 7;
	static final int LATIN1_LENGTH_SHIFT = 56;
	static final int IDENT_MAX = 10;
	static final int DIGIT_BITS = 6;
	private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$";
	private static final byte [] CODES = new byte [128];

	static {
		java.util.Arrays.fill (CODES, (byte) -1);
		for (int i = 0; i < ALPHABET.length (); i++) CODES [ALPHABET.charAt (i)] = (byte) i;
	}

	private M3ShortLabel () {
	}

	/** The immediate value of {@code text}, {@link #EMPTY} for "", or {@link #NONE} when it needs a pool entry. */
	public static long pack (CharSequence text) {
		int length = text.length ();
		if (length == 0) return EMPTY;
		if (length > IDENT_MAX) return NONE;
		boolean latin1 = length <= LATIN1_MAX;
		long bits = 0;
		for (int i = 0; i < length; i++) {
			char c = text.charAt (i);
			if (latin1) {
				if (c > 0xFF) return NONE;
				bits |= (long) c << (8 * i);
			} else {
				int code = c < 128 ? CODES [c] : -1;
				if (code < 0) return NONE;
				bits += (long) (code + 1) << (DIGIT_BITS * i);
			}
		}
		return latin1 ? TAG_LATIN1 | (long) length << LATIN1_LENGTH_SHIFT | bits : TAG_IDENT | bits;
	}

	/** The value of pool id {@code id}. */
	public static long ofPoolId (int id) {
		if (id < 0) throw new IllegalArgumentException ("negative pool id: " + id);
		return id;
	}

	/** Answers whether {@code label} carries its text itself. */
	public static boolean isImmediate (long label) {
		long tag = label & TAG_MASK;
		return tag == TAG_LATIN1 || tag == TAG_IDENT;
	}

	/** Answers whether {@code label} is a pool id (including {@link #EMPTY}). */
	public static boolean isPoolId (long label) {
		return label >= 0 && label <= Integer.MAX_VALUE;
	}

	/** The pool id of a label that {@link #isPoolId(long) is a pool id}. */
	public static int poolId (long label) {
		if (!isPoolId (label)) throw new IllegalArgumentException ("not a pool id: " + Long.toHexString (label));
		return (int) label;
	}

	/** Number of characters of an immediate label. */
	public static int length (long label) {
		if ((label & TAG_MASK) == TAG_LATIN1) return (int) (label >>> LATIN1_LENGTH_SHIFT) & 7;
		requireIdent (label);
		int length = 0;
		for (long digits = label & PAYLOAD_MASK; digits != 0; digits = (digits - 1) >>> DIGIT_BITS) length++;
		return length;
	}

	/** Character {@code index} of an immediate label. */
	public static char charAt (long label, int index) {
		Objects.checkIndex (index, length (label));
		if ((label & TAG_MASK) == TAG_LATIN1) return (char) ((label >>> (8 * index)) & 0xFF);
		long digits = label & PAYLOAD_MASK;
		for (int i = 0; i < index; i++) digits = (digits - 1) >>> DIGIT_BITS;
		return ALPHABET.charAt ((int) ((digits - 1) & 63));
	}

	/** Decodes an immediate label into {@code dst} at {@code offset}; returns its length. Allocates nothing. */
	public static int getChars (long label, char [] dst, int offset) {
		int length = length (label);
		Objects.checkFromIndexSize (offset, length, dst.length);
		if ((label & TAG_MASK) == TAG_LATIN1) {
			for (int i = 0; i < length; i++) dst [offset + i] = (char) ((label >>> (8 * i)) & 0xFF);
			return length;
		}
		long digits = label & PAYLOAD_MASK;
		for (int i = 0; i < length; i++, digits = (digits - 1) >>> DIGIT_BITS) dst [offset + i] = ALPHABET.charAt ((int) ((digits - 1) & 63));
		return length;
	}

	/** A new String with the text of an immediate label (for APIs that must return one). */
	public static String toString (long label) {
		char [] chars = new char [length (label)];
		getChars (label, chars, 0);
		return new String (chars);
	}

	/** {@link String#hashCode()} of the text of an immediate label. */
	public static int hash (long label) {
		int hash = 0;
		for (int i = 0, length = length (label); i < length; i++) hash = 31 * hash + charAt (label, i);
		return hash;
	}

	private static void requireIdent (long label) {
		if ((label & TAG_MASK) != TAG_IDENT) throw new IllegalArgumentException ("not an immediate label: " + Long.toHexString (label));
	}
}
