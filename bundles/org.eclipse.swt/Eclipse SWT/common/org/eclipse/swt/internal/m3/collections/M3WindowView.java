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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 118b3cb7e59ea4d4cc32468dccfc6f32e5715dcb,
 * com.m3.collections.M3WindowView (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A sliding window {@code [first, first + size)} over any indexed sequence, itself a read-only {@link List}, so
 * windows, filtered views and flattening views compose (a view of a view).
 * <p>Each window entry carries an {@code int} state mask (for example resident, bound, dirty) in one lane that
 * follows its row when the window moves. A move is planned with {@link M3WindowPlan}: only the rows that leave at the
 * head or tail and the rows that enter there are touched, and the plan of the last move stays readable through
 * {@link #lastMove()}. The state lane is a ring, so a move costs the entries that enter and leave, not the window
 * (brief rule 24, viewport deltas: resize and layout are window moves). The window is clamped to the source's current
 * size on every read.</p>
 * <p>Structural changes of the source reach the window only as {@link M3Patch} runs ({@link M3Patch.Target}: remove
 * runs, then insert runs, in source rows). Runs before the window shift it, runs after it are free, and runs inside
 * it become entry-level removes and inserts for the bound {@link #host(M3Patch.Target) host}: O(1) per run plus the
 * entries it touches. While the window is {@link #hide() hidden} no host call is made: runs only update the lanes and
 * fold the touched entries into one dirty span (O(1) per run), and {@link #show()} answers one window patch that
 * brings the host from what it showed when hidden to the current window, to apply and paint once.</p>
 * <p>Cost (w entries in the window, n source rows): {@link #size()}, {@link #rowOf}, {@link #entryOf}, state and flag
 * O(1); {@link #get} O(1) plus the source's {@code get}; {@link #move} O(entries entering and leaving), the ring growing
 * amortized; {@link #remove}, {@link #insert}: O(1) plus the shorter side of the window moved in the lane, never
 * O(n); {@link #update}, {@link #hide()}, {@link #show()} O(1); {@link #count(int)} O(w). {@link #work()} counts
 * these steps.</p>
 * <p>Threads: not thread-safe and holds no lock. A window, its source and its host are confined to one thread (the
 * thread that applies the model's patches, the UI thread for a widget); the host is called on that thread. The
 * source changes only together with the patch that describes the change.</p>
 *
 * @param <E> element type of the source
 */
public final class M3WindowView<E> extends AbstractList<E> implements RandomAccess, M3Patch.Target {
	private static final int [] NONE = new int [0];

	private final List<E> source;
	private final M3WindowPlan plan = new M3WindowPlan ();
	/* the state lane of the committed window as a ring: entry i lives at (head + i) & (states.length - 1) */
	private int [] states = new int [0];
	private int head;
	private M3Patch.Target host;
	private boolean hidden, dirty;
	/* while hidden: the entries the host still shows, and the span of current entries that differ from them */
	private int hostSize, dirtyLow, dirtyHigh;
	private long work;

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
	 * them; entering rows start with mask 0. Answers the plan of this move (the window delta: rows leaving and entering
	 * at the head and the tail). O(entries entering or leaving). While hidden the move is folded into the dirty span.
	 */
	public M3WindowPlan move (int first, int size) {
		int oldSize = plan.size ();
		plan.plan (first, size);
		if (plan.replaced ()) {
			ensureRing (size, 0);
			head = 0;
			clearEntries (0, size);
		} else {
			int retiredHead = plan.retiredHead (), retiredTail = plan.retiredTail ();
			int insertedHead = plan.insertedHead (), insertedTail = plan.insertedTail ();
			int kept = oldSize - retiredHead - retiredTail;
			ensureRing (size, retiredHead);
			head = (head + retiredHead - insertedHead) & mask ();
			clearEntries (0, insertedHead);
			clearEntries (insertedHead + kept, insertedTail);
		}
		if (hidden) foldMove (oldSize);
		plan.commit ();
		return plan;
	}

	/** The plan of the last {@link #move(int, int)} (head/tail rows that left and entered). */
	public M3WindowPlan lastMove () {
		return plan;
	}

	/** Binds the receiver of the entry-level removes and inserts of patches that land in the window (a native plane). */
	public void host (M3Patch.Target host) {
		this.host = host;
	}

	/** Applies a structural patch of the source (old snapshot to new): see {@link #remove(int, int)}, {@link #insert(int, int)}. */
	public void apply (M3Patch patch) {
		patch.applyTo (this);
	}

	/**
	 * Source rows {@code [row, row + count)} were removed. Rows before the window shift it up; the resident rows among
	 * them leave the window, and the host is told to remove those entries (or, hidden, the span is folded).
	 */
	@Override
	public void remove (int row, int count) {
		work++;
		int resident = plan.removed (row, count);
		if (resident == 0) return;
		int entry = plan.removedEntry (row);
		removeEntries (entry, resident, plan.size () + resident);
		if (hidden) foldRemove (entry, resident);
		else if (host != null) host.remove (entry, resident);
		modCount++;
	}

	/**
	 * Source rows {@code [row, row + count)} were inserted. Rows before the window shift it down; rows inside it become
	 * entries (state 0), and the host is told to insert them (or, hidden, the span is folded).
	 */
	@Override
	public void insert (int row, int count) {
		work++;
		int entry = plan.inserted (row, count);
		if (entry < 0) return;
		insertEntries (entry, count, plan.size () - count);
		if (hidden) foldInsert (entry, count);
		else if (host != null) host.insert (entry, count);
		modCount++;
	}

	/**
	 * The contents of source rows {@code [row, row + count)} changed in place. The resident ones are told to the host as
	 * an entry update (or, hidden, folded into the dirty span); their state masks stay. O(1).
	 */
	@Override
	public void update (int row, int count) {
		work++;
		int from = Math.max (row, plan.first ()), to = Math.min (row + count, plan.first () + plan.size ());
		if (from >= to) return;
		int entry = from - plan.first ();
		if (hidden) foldUpdate (entry, to - from);
		else if (host != null) host.update (entry, to - from);
	}

	/** Stops host calls: from now on patches only update the lanes and the dirty span. */
	public void hide () {
		if (hidden) return;
		hidden = true;
		dirty = false;
		hostSize = size ();
	}

	public boolean isHidden () {
		return hidden;
	}

	/**
	 * Resumes host calls and answers one patch, over the entries the host showed when hidden, that brings it to the
	 * current window: one remove run and one insert run at the start of the dirty span (empty when nothing changed).
	 * O(1); the caller applies it once and paints.
	 */
	public M3Patch show () {
		if (!hidden) return M3Patch.of (size (), NONE, NONE, NONE, NONE);
		hidden = false;
		int current = size ();
		if (!dirty) {
			if (current == hostSize) return M3Patch.of (hostSize, NONE, NONE, NONE, NONE);
			// only the clamp to the source's end moved: the tail differs
			dirtyLow = dirtyHigh = Math.min (current, hostSize);
		}
		dirty = false;
		// entries before low and the last `suffix` entries are what the host already shows
		int low = Math.min (dirtyLow, Math.min (current, hostSize));
		int suffix = Math.min (current - Math.min (Math.max (dirtyHigh, low), current), hostSize - low);
		int high = current - suffix, hostMiddle = hostSize - low - suffix;
		int [] removeAt = hostMiddle > 0 ? new int [] {low} : NONE, removeCount = hostMiddle > 0 ? new int [] {hostMiddle} : NONE;
		int [] insertAt = high > low ? new int [] {low} : NONE, insertCount = high > low ? new int [] {high - low} : NONE;
		return M3Patch.of (hostSize, removeAt, removeCount, insertAt, insertCount);
	}

	/** Steps taken by moves and patch runs (runs plus entries entering, leaving or shifting in the lane). */
	public long work () {
		return work;
	}

	/** State mask of window entry {@code index}. */
	public int state (int index) {
		Objects.checkIndex (index, plan.size ());
		return states [slot (index)];
	}

	/** Replaces the state mask of window entry {@code index}. */
	public void state (int index, int mask) {
		Objects.checkIndex (index, plan.size ());
		states [slot (index)] = mask;
	}

	/** Sets or clears {@code flag} on window entry {@code index}. */
	public void flag (int index, int flag, boolean value) {
		Objects.checkIndex (index, plan.size ());
		int s = slot (index);
		states [s] = value ? states [s] | flag : states [s] & ~flag;
	}

	/** Whether {@code flag} is set on window entry {@code index}. */
	public boolean flag (int index, int flag) {
		return (state (index) & flag) != 0;
	}

	/** Number of window entries with any bit of {@code mask} set. */
	public int count (int mask) {
		int count = 0;
		for (int i = 0; i < plan.size (); i++) if ((states [slot (i)] & mask) != 0) count++;
		return count;
	}

	private int mask () {
		return states.length - 1;
	}

	private int slot (int index) {
		return (head + index) & (states.length - 1);
	}

	/* grows the ring to hold size entries; skip entries at the head are about to leave and are not kept */
	private void ensureRing (int size, int skip) {
		if (size <= states.length) return;
		int capacity = Integer.highestOneBit (Math.max (4, size - 1)) << 1;
		int [] next = new int [capacity];
		int keep = Math.max (0, Math.min (plan.size (), states.length) - skip);
		for (int i = 0; i < keep; i++) next [skip + i] = states [slot (skip + i)];
		work += keep;
		states = next;
		head = 0;
	}

	private void clearEntries (int from, int count) {
		for (int i = 0; i < count; i++) states [slot (from + i)] = 0;
		work += count;
	}

	/* the lane held total entries; entries [entry, entry + count) leave: the shorter side moves */
	private void removeEntries (int entry, int count, int total) {
		int after = total - entry - count;
		if (entry <= after) {
			for (int i = entry - 1; i >= 0; i--) states [slot (i + count)] = states [slot (i)];
			head = (head + count) & mask ();
			work += entry;
		} else {
			for (int i = entry; i < entry + after; i++) states [slot (i)] = states [slot (i + count)];
			work += after;
		}
	}

	/* the lane held total entries; count entries open at entry with state 0: the shorter side moves */
	private void insertEntries (int entry, int count, int total) {
		if (total + count > states.length) {
			int capacity = Integer.highestOneBit (Math.max (4, total + count - 1)) << 1;
			int [] next = new int [capacity];
			for (int i = 0; i < entry; i++) next [i] = states [slot (i)];
			for (int i = entry; i < total; i++) next [i + count] = states [slot (i)];
			work += total;
			states = next;
			head = 0;
			return;
		}
		int after = total - entry;
		if (entry <= after) {
			head = (head - count) & mask ();
			for (int i = 0; i < entry; i++) states [slot (i)] = states [slot (i + count)];
			work += entry;
		} else {
			for (int i = total - 1; i >= entry; i--) states [slot (i + count)] = states [slot (i)];
			work += after;
		}
		clearEntries (entry, count);
	}

	/* the dirty span after count current entries were inserted at entry */
	private void foldInsert (int entry, int count) {
		if (!dirty) {
			dirty = true;
			dirtyLow = entry;
			dirtyHigh = entry + count;
			return;
		}
		if (dirtyLow >= entry) dirtyLow += count;
		if (dirtyHigh > entry) dirtyHigh += count;
		dirtyLow = Math.min (dirtyLow, entry);
		dirtyHigh = Math.max (dirtyHigh, entry + count);
	}

	/* the dirty span after current entries [entry, entry + count) changed in place */
	private void foldUpdate (int entry, int count) {
		if (!dirty) {
			dirty = true;
			dirtyLow = entry;
			dirtyHigh = entry + count;
			return;
		}
		dirtyLow = Math.min (dirtyLow, entry);
		dirtyHigh = Math.max (dirtyHigh, entry + count);
	}

	/* the dirty span after current entries [entry, entry + count) were removed (a seam at entry) */
	private void foldRemove (int entry, int count) {
		if (!dirty) {
			dirty = true;
			dirtyLow = dirtyHigh = entry;
			return;
		}
		dirtyLow = dirtyLow >= entry + count ? dirtyLow - count : Math.min (dirtyLow, entry);
		dirtyHigh = dirtyHigh >= entry + count ? dirtyHigh - count : Math.min (dirtyHigh, entry);
		dirtyLow = Math.min (dirtyLow, entry);
		dirtyHigh = Math.max (dirtyHigh, entry);
	}

	/* a move while hidden, as the plan's edits in entry coordinates: head and tail removals, then head and tail inserts */
	private void foldMove (int oldSize) {
		if (plan.replaced ()) {
			foldRemove (0, oldSize);
			foldInsert (0, plan.plannedSize ());
			return;
		}
		int size = oldSize;
		if (plan.retiredHead () > 0) {
			foldRemove (0, plan.retiredHead ());
			size -= plan.retiredHead ();
		}
		if (plan.retiredTail () > 0) {
			foldRemove (size - plan.retiredTail (), plan.retiredTail ());
			size -= plan.retiredTail ();
		}
		if (plan.insertedHead () > 0) {
			foldInsert (0, plan.insertedHead ());
			size += plan.insertedHead ();
		}
		if (plan.insertedTail () > 0) foldInsert (size, plan.insertedTail ());
	}
}
