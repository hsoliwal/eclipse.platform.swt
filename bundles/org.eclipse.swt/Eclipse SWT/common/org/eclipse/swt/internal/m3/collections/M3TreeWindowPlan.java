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
 * com.m3.collections.M3TreeWindowPlan (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/eclipse.platform.swt 553eefd41d9961d303c41e0e4e181712e64a1e45,
 * VirtualTreeResidentPlan.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.*;

/**
 * Native window of a viewport Tree: which native rows survive a window move, which are
 * deleted and where the new rows go.
 *
 * <p>A row is identified by its logical coordinate (parent topology id, child index), never
 * by its native position: a native parent holds a contiguous sub-range of its logical
 * children. Rows present before and after a move keep their native handle, so native
 * focus, hot and selection state survive. Only top-most retired rows are deleted, because
 * deleting a native parent deletes its subtree. New rows are inserted in preorder, each
 * after its previous physical sibling, which keeps the logical order of every native
 * parent.</p>
 *
 * <p>Inputs are the dense preorder of {@link M3TreeProjection.Residency}:
 * logical parent id, child index and physical parent entry ({@code -1} for a root row).
 * The plan allocates nothing per logical row; its arrays are sized by the window.</p>
 */
public final class M3TreeWindowPlan {
	private long [] keys = new long [0], handles = new long [0];
	private int [] parentSlots = new int [0];
	private int size;
	private final M3LongLongHashMap slotByKey = new M3LongLongHashMap ();
	private final M3LongLongHashMap slotByHandle = new M3LongLongHashMap ();

	private long [] planned = new long [0], retired = new long [0];
	private int [] previousSiblings = new int [0];
	private boolean [] kept = new boolean [0];
	private int plannedCount, retiredCount;

	public M3TreeWindowPlan () {
	}

	public static long key (int parentId, int childIndex) {
		return ((long) parentId << 32) | (childIndex & 0xFFFFFFFFL);
	}

	/** Number of native rows of the committed window. */
	public int size () {
		return size;
	}

	/** Native handle of the committed row at this coordinate, or 0 when it is not resident. */
	public long handleOf (int parentId, int childIndex) {
		int slot = (int) slotByKey.getOrDefault (key (parentId, childIndex), -1L);
		return slot < 0 ? 0 : handles [slot];
	}

	/** Entry (dense preorder index) of a committed native handle, or -1. */
	public int entryOf (long handle) {
		int slot = (int) slotByHandle.getOrDefault (handle, -1L);
		return slot < 0 ? -1 : slot;
	}

	/** Native handle of a committed entry. */
	public long handle (int entry) {
		requireEntry (entry);
		return handles [entry];
	}

	/** Logical coordinate key of a committed entry. */
	public long keyOf (int entry) {
		requireEntry (entry);
		return keys [entry];
	}

	/**
	 * Plans a move to the next window. Afterwards {@link #plannedHandle(int)} is the reused
	 * native handle of each next entry (0 when it must be inserted) and
	 * {@link #retired(int)} lists the top-most native rows to delete.
	 */
	public void plan (int count, int [] parentIds, int [] childIndices, int [] physicalParents) {
		if (count < 0 || parentIds.length < count || childIndices.length < count || physicalParents.length < count) {
			throw new IllegalArgumentException ("window arrays");
		}
		if (planned.length < count) {
			planned = new long [capacity (planned.length, count)];
			previousSiblings = new int [planned.length];
		}
		if (kept.length < size) kept = new boolean [capacity (kept.length, size)];
		Arrays.fill (kept, 0, size, false);
		int [] lastChild = new int [count + 1];
		Arrays.fill (lastChild, -1);
		for (int entry = 0; entry < count; entry++) {
			int physicalParent = physicalParents [entry];
			if (physicalParent < -1 || physicalParent >= entry) throw new IllegalArgumentException ("preorder");
			previousSiblings [entry] = lastChild [physicalParent + 1];
			lastChild [physicalParent + 1] = entry;
			int slot = (int) slotByKey.getOrDefault (key (parentIds [entry], childIndices [entry]), -1L);
			if (slot >= 0 && !kept [slot]) {
				kept [slot] = true;
				planned [entry] = handles [slot];
			} else {
				planned [entry] = 0;
			}
		}
		plannedCount = count;
		retiredCount = 0;
		for (int slot = 0; slot < size; slot++) {
			int parent = parentSlots [slot];
			if (!kept [slot] && (parent == -1 || kept [parent])) {
				if (retiredCount == retired.length) retired = Arrays.copyOf (retired, capacity (retired.length, retiredCount + 1));
				retired [retiredCount++] = handles [slot];
			}
		}
	}

	/** Convenience form of {@link #plan(int, int[], int[], int[])} over a residency buffer. */
	public void plan (M3TreeProjection.Residency next) {
		int count = next.size ();
		int [] parentIds = new int [count], childIndices = new int [count], physicalParents = new int [count];
		for (int entry = 0; entry < count; entry++) {
			parentIds [entry] = next.parentId (entry);
			childIndices [entry] = next.childIndex (entry);
			physicalParents [entry] = next.physicalParent (entry);
		}
		plan (count, parentIds, childIndices, physicalParents);
		lastParentIds = parentIds;
		lastChildIndices = childIndices;
		lastPhysicalParents = physicalParents;
	}

	private int [] lastParentIds, lastChildIndices, lastPhysicalParents;

	public int plannedCount () {
		return plannedCount;
	}

	/** Reused native handle of a planned entry, or 0 when the entry must be inserted. */
	public long plannedHandle (int entry) {
		if (entry < 0 || entry >= plannedCount) throw new IllegalArgumentException ("planned entry");
		return planned [entry];
	}

	/** Previous physical sibling of a planned entry in the next window, or -1 for a first child. */
	public int previousSibling (int entry) {
		if (entry < 0 || entry >= plannedCount) throw new IllegalArgumentException ("planned entry");
		return previousSiblings [entry];
	}

	public int retiredCount () {
		return retiredCount;
	}

	/** Top-most native row to delete; deleting it deletes its native subtree. */
	public long retired (int index) {
		if (index < 0 || index >= retiredCount) throw new IllegalArgumentException ("retired index");
		return retired [index];
	}

	/** Records the native handles of the planned window (reused and inserted) as the committed window. */
	public void commit (long [] nativeHandles) {
		if (lastParentIds == null) throw new IllegalStateException ("commit without residency plan");
		commit (plannedCount, lastParentIds, lastChildIndices, lastPhysicalParents, nativeHandles);
	}

	public void commit (int count, int [] parentIds, int [] childIndices, int [] physicalParents, long [] nativeHandles) {
		if (count != plannedCount || nativeHandles.length < count) throw new IllegalArgumentException ("commit arrays");
		if (keys.length < count) {
			int next = capacity (keys.length, count);
			keys = new long [next];
			handles = new long [next];
			parentSlots = new int [next];
		}
		slotByKey.clear ();
		slotByHandle.clear ();
		for (int entry = 0; entry < count; entry++) {
			long handle = nativeHandles [entry];
			if (handle == 0) throw new IllegalArgumentException ("uncommitted native row " + entry);
			keys [entry] = key (parentIds [entry], childIndices [entry]);
			handles [entry] = handle;
			parentSlots [entry] = physicalParents [entry];
			slotByKey.put (keys [entry], entry);
			slotByHandle.put (handle, entry);
		}
		size = count;
		lastParentIds = lastChildIndices = lastPhysicalParents = null;
	}

	/**
	 * Siblings at or after <code>fromIndex</code> under <code>parentId</code> moved by <code>delta</code>
	 * (an insert or a removal before them). Their committed rows keep their native handles and only
	 * change keys, so the next plan reuses them instead of rebuilding the window.
	 */
	public void shiftSiblings (int parentId, int fromIndex, int delta) {
		if (delta == 0) return;
		boolean moved = false;
		for (int entry = 0; entry < size; entry++) {
			long key = keys [entry];
			int index = (int) key;
			if ((int) (key >> 32) == parentId && index >= fromIndex) {
				if (index + delta < 0) throw new IllegalArgumentException ("negative child index");
				keys [entry] = key (parentId, index + delta);
				moved = true;
			}
		}
		if (moved) reindex ();
	}

	/**
	 * The committed row of <code>entry</code> and its native subtree were deleted by the caller. In
	 * preorder the subtree is the entry and the following entries whose physical parent lies inside it.
	 */
	public void removeSubtree (int entry) {
		requireEntry (entry);
		int end = entry + 1;
		while (end < size && parentSlots [end] >= entry) end++;
		int removed = end - entry, tail = size - end;
		System.arraycopy (keys, end, keys, entry, tail);
		System.arraycopy (handles, end, handles, entry, tail);
		System.arraycopy (parentSlots, end, parentSlots, entry, tail);
		size -= removed;
		for (int next = entry; next < size; next++) {
			if (parentSlots [next] >= end) parentSlots [next] -= removed;
		}
		reindex ();
	}

	private void reindex () {
		slotByKey.clear ();
		slotByHandle.clear ();
		for (int entry = 0; entry < size; entry++) {
			slotByKey.put (keys [entry], entry);
			slotByHandle.put (handles [entry], entry);
		}
		plannedCount = retiredCount = 0;
		lastParentIds = lastChildIndices = lastPhysicalParents = null;
	}

	/** Forgets the committed window (all native rows were deleted by the caller). */
	public void clear () {
		size = plannedCount = retiredCount = 0;
		slotByKey.clear ();
		slotByHandle.clear ();
		lastParentIds = lastChildIndices = lastPhysicalParents = null;
	}

	private void requireEntry (int entry) {
		if (entry < 0 || entry >= size) throw new IllegalArgumentException ("resident entry");
	}

	private static int capacity (int current, int required) {
		return (int) Math.min (Integer.MAX_VALUE - 8, Math.max ((long) required, Math.max (8L, current + (long) current / 2)));
	}
}
