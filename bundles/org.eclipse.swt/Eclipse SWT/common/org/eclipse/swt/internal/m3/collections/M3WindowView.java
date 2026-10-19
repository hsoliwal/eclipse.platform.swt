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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 826e57b779174368fec5c4295eca9e3ad794c2e1,
 * com.m3.collections.M3WindowView (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A sliding window {@code [first, first + size)} over any indexed sequence, itself a read-only {@link List}, so
 * windows, filtered views and flattening views compose (a view of a view).
 * <p>Each window entry carries an {@code int} state mask (for example resident, bound, dirty) in one lane that
 * follows its row when the window moves. A move is planned with {@link M3WindowPlan}: only the rows that leave at the
 * head or tail and the rows that enter there are touched, and the plan of the last move stays readable through
 * {@link #lastMove()}. The window is clamped to the source's current size on every read.</p>
 *
 * @param <E> element type of the source
 */
public final class M3WindowView<E> extends AbstractList<E> implements RandomAccess {
	private final List<E> source;
	private final M3WindowPlan plan = new M3WindowPlan ();
	/* the state lane of the committed window and a spare of the same role; a move writes the spare and swaps */
	private int [] states = new int [0], spare = new int [0];

	/** A window of size 0 at row 0 over {@code source}. */
	public M3WindowView (List<E> source) {
		this.source = Objects.requireNonNull (source, "source");
	}

	/** The sequence this window looks at. */
	public List<E> source () {
		return source;
	}

	/** First row of the window in the source. */
	public int first () {
		return plan.first ();
	}

	/** Rows asked for by the last {@link #move(int, int)}; {@link #size()} may be smaller near the source's end. */
	public int requestedSize () {
		return plan.size ();
	}

	/** Rows visible through the window now: the requested size clamped to the source's size. */
	@Override
	public int size () {
		return Math.max (0, Math.min (plan.size (), source.size () - plan.first ()));
	}

	/** The window entry {@code index}: source row {@code first () + index}. */
	@Override
	public E get (int index) {
		Objects.checkIndex (index, size ());
		return source.get (plan.first () + index);
	}

	/** Source row of window entry {@code index}, or -1 when it is outside the window. */
	public int rowOf (int index) {
		return index >= 0 && index < size () ? plan.first () + index : -1;
	}

	/** Window entry of source row {@code row}, or -1 when the row is outside the window. */
	public int entryOf (int row) {
		int entry = plan.entryOf (row);
		return entry < size () ? entry : -1;
	}

	/**
	 * Moves the window to {@code [first, first + size)}. The state masks of rows that stay in the window move with
	 * them; entering rows start with mask 0. Answers the plan of this move.
	 */
	public M3WindowPlan move (int first, int size) {
		int oldFirst = plan.first (), oldSize = plan.size ();
		plan.plan (first, size);
		if (spare.length < size) spare = new int [M3LaneGrowth.capacity (spare.length, size, 4)];
		int [] next = spare;
		Arrays.fill (next, 0, size, 0);
		int overlapStart = Math.max (first, oldFirst), overlapEnd = Math.min (first + size, oldFirst + oldSize);
		if (overlapStart < overlapEnd) {
			System.arraycopy (states, overlapStart - oldFirst, next, overlapStart - first, overlapEnd - overlapStart);
		}
		spare = states;
		states = next;
		plan.commit ();
		return plan;
	}

	/** The plan of the last {@link #move(int, int)} (head/tail rows that left and entered). */
	public M3WindowPlan lastMove () {
		return plan;
	}

	/** State mask of window entry {@code index}. */
	public int state (int index) {
		Objects.checkIndex (index, plan.size ());
		return states [index];
	}

	/** Replaces the state mask of window entry {@code index}. */
	public void state (int index, int mask) {
		Objects.checkIndex (index, plan.size ());
		states [index] = mask;
	}

	/** Sets or clears {@code flag} on window entry {@code index}. */
	public void flag (int index, int flag, boolean value) {
		Objects.checkIndex (index, plan.size ());
		states [index] = value ? states [index] | flag : states [index] & ~flag;
	}

	/** Whether {@code flag} is set on window entry {@code index}. */
	public boolean flag (int index, int flag) {
		return (state (index) & flag) != 0;
	}

	/** Number of window entries with any bit of {@code mask} set. */
	public int count (int mask) {
		int count = 0;
		for (int i = 0; i < plan.size (); i++) if ((states [i] & mask) != 0) count++;
		return count;
	}
}
