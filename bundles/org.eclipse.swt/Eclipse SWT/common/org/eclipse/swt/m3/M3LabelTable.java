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

import java.text.*;
import java.util.*;

import org.eclipse.swt.*;

/**
 * Interned cell text of a lane model (public model API, brief rule 26): one <code>int</code> label id per distinct
 * text, so a text cell is an <code>int</code> lane and a filter or sort works once per distinct label.
 * <p>
 * Id 0 is the empty label. Each label holds a reference count: {@link #intern(CharSequence)} and
 * {@link #acquire(int)} add one, {@link #release(int)} takes one away. A label whose count reaches 0 stays readable
 * and is revived by the next intern of the same text; its id is freed, and may then be given to another text, only
 * at the next {@link #recycle()}, which a model calls at the end of each apply. So a released id is never reused
 * before the listeners have seen the patch that released it, and caches keyed by label id drop their entries when
 * {@link #reuseEpoch()} moves.
 * </p>
 * <p>
 * Lanes and an open-addressing table of ids (linear probing, backward-shift deletion); one <code>String</code> per
 * distinct text, nothing per cell. Not thread-safe: it belongs to the thread that applies its model.
 * </p>
 *
 * @since 3.138
 */
public final class M3LabelTable {

	private static final String EMPTY = ""; //$NON-NLS-1$

	private String [] texts = new String [64];
	private int [] hashes = new int [64];
	private int [] refs = new int [64];
	private CollationKey [] keys;
	private Collator collator;
	/* ids 1 .. next-1 have been handed out; entries = ids present in the table (live or waiting for recycle) */
	private int next = 1, live, entries;
	private int [] table = new int [128];
	private int [] free = new int [16], pending = new int [16];
	private int freeCount, pendingCount;
	private long reuseEpoch;

	/**
	 * Creates an empty table.
	 */
	public M3LabelTable () {
		texts [0] = EMPTY;
	}

	/**
	 * Returns the id of a text and adds one reference to it.
	 *
	 * @param text the text
	 * @return the label id; 0 for <code>null</code> or an empty text (no reference is counted for 0)
	 */
	public int intern (CharSequence text) {
		if (text == null || text.length () == 0) return 0;
		int hash = text instanceof String s ? s.hashCode () : hash (text);
		int mask = table.length - 1;
		int index = home (hash, mask);
		for (int id; (id = table [index]) != 0; index = (index + 1) & mask) {
			if (hashes [id] == hash && texts [id].contentEquals (text)) return reference (id);
		}
		return add (text.toString (), hash, index);
	}

	/**
	 * Returns the id of the text in a range of a char array and adds one reference to it.
	 *
	 * @param buffer the chars
	 * @param offset the first char
	 * @param length the number of chars
	 * @return the label id; 0 for an empty range
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the buffer is null</li>
	 *    <li>ERROR_INVALID_RANGE - if the range is outside the buffer</li>
	 * </ul>
	 */
	public int intern (char [] buffer, int offset, int length) {
		if (buffer == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (offset < 0 || length < 0 || offset + length > buffer.length) SWT.error (SWT.ERROR_INVALID_RANGE);
		if (length == 0) return 0;
		int hash = 0;
		for (int i = 0; i < length; i++) hash = 31 * hash + buffer [offset + i];
		int mask = table.length - 1;
		int index = home (hash, mask);
		for (int id; (id = table [index]) != 0; index = (index + 1) & mask) {
			if (hashes [id] == hash && equals (texts [id], buffer, offset, length)) return reference (id);
		}
		return add (new String (buffer, offset, length), hash, index);
	}

	/**
	 * Adds one reference to a label.
	 *
	 * @param labelId the label id; 0 is ignored
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has the id</li>
	 * </ul>
	 */
	public void acquire (int labelId) {
		if (labelId == 0) return;
		check (labelId);
		reference (labelId);
	}

	/**
	 * Takes one reference away from a label. At 0 the label stays readable until the next {@link #recycle()}.
	 *
	 * @param labelId the label id; 0 is ignored
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has the id or it holds no reference</li>
	 * </ul>
	 */
	public void release (int labelId) {
		if (labelId == 0) return;
		check (labelId);
		if (refs [labelId] == 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		if (--refs [labelId] > 0) return;
		live--;
		if (pendingCount == pending.length) pending = Arrays.copyOf (pending, pendingCount * 2);
		pending [pendingCount++] = labelId;
	}

	/**
	 * Frees the labels whose count is still 0, so their ids may be reused, and moves {@link #reuseEpoch()} when any
	 * was freed. A model calls it at the end of each apply.
	 */
	public void recycle () {
		int freed = 0;
		for (int i = 0; i < pendingCount; i++) {
			int id = pending [i];
			if (refs [id] != 0 || texts [id] == null) continue;
			delete (id);
			texts [id] = null;
			if (keys != null) keys [id] = null;
			if (freeCount == free.length) free = Arrays.copyOf (free, freeCount * 2);
			free [freeCount++] = id;
			freed++;
		}
		pendingCount = 0;
		if (freed > 0) reuseEpoch++;
	}

	/**
	 * Returns the text of a label: the same object for as long as the label lives.
	 *
	 * @param labelId the label id
	 * @return the text, empty for 0
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has the id</li>
	 * </ul>
	 */
	public CharSequence text (int labelId) {
		if (labelId != 0) check (labelId);
		return texts [labelId];
	}

	/**
	 * Returns the length of a label.
	 *
	 * @param labelId the label id
	 * @return the length
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has the id</li>
	 * </ul>
	 */
	public int length (int labelId) {
		return text (labelId).length ();
	}

	/**
	 * Copies the chars of a label.
	 *
	 * @param labelId the label id
	 * @param buffer the receiver
	 * @param offset where the text starts in the buffer
	 * @return the length; when the buffer is too short nothing is copied and the result is the negative length
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the buffer is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has the id</li>
	 *    <li>ERROR_INVALID_RANGE - if the offset is outside the buffer</li>
	 * </ul>
	 */
	public int getChars (int labelId, char [] buffer, int offset) {
		if (buffer == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (offset < 0 || offset > buffer.length) SWT.error (SWT.ERROR_INVALID_RANGE);
		String text = (String) text (labelId);
		int length = text.length ();
		if (buffer.length - offset < length) return -length;
		text.getChars (0, length, buffer, offset);
		return length;
	}

	/**
	 * Returns the collation key of a label, computed once per label and collator.
	 *
	 * @param labelId the label id
	 * @param collator the collator; a different collator than the last one drops the cached keys
	 * @return the key
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the collator is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has the id</li>
	 * </ul>
	 */
	public CollationKey collationKey (int labelId, Collator collator) {
		if (collator == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		String text = (String) text (labelId);
		if (collator != this.collator || keys == null) {
			this.collator = collator;
			keys = new CollationKey [texts.length];
		} else if (keys.length < texts.length) {
			keys = Arrays.copyOf (keys, texts.length);
		}
		CollationKey key = keys [labelId];
		if (key == null) keys [labelId] = key = collator.getCollationKey (text);
		return key;
	}

	/**
	 * Compares two labels by a collator, through their cached collation keys.
	 *
	 * @param a a label id
	 * @param b a label id
	 * @param collator the collator
	 * @return negative, 0 or positive
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the collator is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has one of the ids</li>
	 * </ul>
	 */
	public int collate (int a, int b, Collator collator) {
		if (a == b) {
			if (collator == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
			if (a != 0) check (a);
			return 0;
		}
		return collationKey (a, collator).compareTo (collationKey (b, collator));
	}

	/**
	 * Compares two labels by their chars (binary order, as <code>String.compareTo</code>).
	 *
	 * @param a a label id
	 * @param b a label id
	 * @return negative, 0 or positive
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has one of the ids</li>
	 * </ul>
	 */
	public int compare (int a, int b) {
		return ((String) text (a)).compareTo ((String) text (b));
	}

	/**
	 * Returns the number of labels that hold a reference.
	 *
	 * @return the live labels
	 */
	public int size () {
		return live;
	}

	/**
	 * Returns the highest id handed out so far: every label id is in <code>0 .. maxId ()</code>.
	 *
	 * @return the highest id, 0 for a new table
	 */
	public int maxId () {
		return next - 1;
	}

	/**
	 * Returns the reuse epoch: it moves when a {@link #recycle()} freed ids, after which an id may name another text.
	 *
	 * @return the epoch
	 */
	public long reuseEpoch () {
		return reuseEpoch;
	}

	/**
	 * Returns the number of references a label holds.
	 *
	 * @param labelId the label id
	 * @return the references, 0 for the empty label and for a released label waiting for recycle
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if no label has the id</li>
	 * </ul>
	 */
	public int references (int labelId) {
		if (labelId == 0) return 0;
		check (labelId);
		return refs [labelId];
	}

	/* ------------------------------------------------------------------ helpers */

	private int reference (int id) {
		if (refs [id]++ == 0) live++;
		return id;
	}

	private int add (String text, int hash, int index) {
		int id;
		if (freeCount > 0) {
			id = free [--freeCount];
		} else {
			id = next++;
			if (id == texts.length) {
				int capacity = id * 2;
				texts = Arrays.copyOf (texts, capacity);
				hashes = Arrays.copyOf (hashes, capacity);
				refs = Arrays.copyOf (refs, capacity);
			}
		}
		texts [id] = text;
		hashes [id] = hash;
		refs [id] = 1;
		live++;
		table [index] = id;
		if (++entries * 2 > table.length) rehash (table.length * 2);
		return id;
	}

	private void check (int id) {
		if (id <= 0 || id >= next || texts [id] == null) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
	}

	private void rehash (int capacity) {
		int [] grown = new int [capacity];
		int mask = capacity - 1;
		for (int id : table) {
			if (id == 0) continue;
			int index = home (hashes [id], mask);
			while (grown [index] != 0) index = (index + 1) & mask;
			grown [index] = id;
		}
		table = grown;
	}

	/* backward-shift deletion of linear probing: no tombstones */
	private void delete (int id) {
		int mask = table.length - 1;
		int hole = home (hashes [id], mask);
		while (table [hole] != id) hole = (hole + 1) & mask;
		for (int index = (hole + 1) & mask; table [index] != 0; index = (index + 1) & mask) {
			int home = home (hashes [table [index]], mask);
			/* the entry may move back into the hole unless its home lies cyclically in (hole, index] */
			boolean stays = hole <= index ? hole < home && home <= index : hole < home || home <= index;
			if (stays) continue;
			table [hole] = table [index];
			hole = index;
		}
		table [hole] = 0;
		entries--;
	}

	private static int home (int hash, int mask) {
		return (hash ^ (hash >>> 16)) * 0x9E3779B9 >>> 7 & mask;
	}

	private static int hash (CharSequence text) {
		int hash = 0;
		for (int i = 0, n = text.length (); i < n; i++) hash = 31 * hash + text.charAt (i);
		return hash;
	}

	private static boolean equals (String text, char [] buffer, int offset, int length) {
		if (text.length () != length) return false;
		for (int i = 0; i < length; i++) {
			if (text.charAt (i) != buffer [offset + i]) return false;
		}
		return true;
	}
}
