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
 * com.m3.collections.M3FlatteningView (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A tree flattened into a read-only {@link List}: the roots, and below every expanded node its children, in preorder.
 * Collapsed subtrees are filtered out, so this is also a filtered view; it composes with {@link M3WindowView} and the
 * other views.
 * <p>Only expanded nodes are materialized: each gets an int id in an {@link M3TreeTopology} (lanes, with the
 * order-statistic sibling forest), and {@link M3TreeProjection} maps a row to its (parent, child index) coordinate in
 * O(depth x log k). Cold children are arithmetic: {@link #get(int)} asks the {@link Source} for them only when read.
 * Child counts are read when a node is expanded.</p>
 * <p>Cost (k materialized siblings, d depth): {@link #size()} O(1); {@link #get}, {@link #depth}, {@link #isExpanded}
 * O(d log k); {@link #expand}, {@link #collapse} O(d log k); {@link #apply} O(runs log k + discarded nodes + d log k),
 * never a rebuild (brief rule 24). {@link #work()} counts the sibling-index steps.</p>
 * <p>Threads: not thread-safe and holds no lock; the view and its {@link Source} are confined to the thread that
 * applies the model's patches.</p>
 *
 * @param <N> node type
 */
public final class M3FlatteningView<N> extends AbstractList<N> implements RandomAccess {
	/** The tree being flattened; {@code parent == null} stands for the invisible root. */
	public interface Source<N> {
		int childCount (N parent);

		N child (N parent, int index);
	}

	private final Source<N> source;
	private final M3TreeTopology topology = new M3TreeTopology ();
	private final M3TreeProjection projection = new M3TreeProjection (topology);
	private Object [] nodes = new Object [8];
	private int nextId;

	/** Flattens {@code source}; every node starts collapsed. */
	public M3FlatteningView (Source<N> source) {
		this.source = Objects.requireNonNull (source, "source");
		topology.setChildCount (M3TreeTopology.ROOT, source.childCount (null));
		// a discarded subtree releases its node objects (ids are never reused)
		topology.onDiscard (id -> nodes [id] = null);
	}

	@Override
	public int size () {
		return Math.toIntExact (projection.visibleRowCount ());
	}

	@Override
	public N get (int row) {
		M3TreeProjection.Row r = rowAt (row);
		if (r.materializedId () >= 0) return node (r.materializedId ());
		return source.child (r.parentId () == M3TreeTopology.ROOT ? null : node (r.parentId ()), r.childIndex ());
	}

	/** Depth of {@code row}: 0 for a root. */
	public int depth (int row) {
		return rowAt (row).depth ();
	}

	/** Whether the node at {@code row} is expanded. */
	public boolean isExpanded (int row) {
		int id = rowAt (row).materializedId ();
		return id >= 0 && topology.flag (id, M3ItemState.EXPANDED);
	}

	/** Expands the node at {@code row}: its children (read from the source now) follow it. */
	public void expand (int row) {
		M3TreeProjection.Row r = rowAt (row);
		int id = r.materializedId ();
		if (id < 0) id = materialize (r, get (row));
		topology.setChildCount (id, source.childCount (node (id)));
		topology.flag (id, M3ItemState.EXPANDED, true);
	}

	/** Collapses the node at {@code row}; its subtree stays materialized and keeps its own expansion state. */
	public void collapse (int row) {
		int id = rowAt (row).materializedId ();
		if (id >= 0) topology.flag (id, M3ItemState.EXPANDED, false);
	}

	/** Number of materialized (once expanded) nodes. */
	public int materializedCount () {
		return topology.materializedCount ();
	}

	/**
	 * Applies a patch of the children of the node at {@code row} (old child list to new; row -1 stands for the roots):
	 * the only structural update path of this view (brief rule 24). Later siblings shift lazily, removed subtrees are
	 * dropped, child counts and row counts are refreshed along the ancestors: O(runs log k + discarded nodes), no
	 * rebuild and no re-read of unchanged children. A moved child that was expanded keeps its subtree and expansion.
	 * The node at {@code row} must be expanded (or have been: a collapsed node with known children takes the patch too).
	 */
	public void apply (int row, M3Patch patch) {
		int parentId = M3TreeTopology.ROOT;
		if (row >= 0) {
			parentId = rowAt (row).materializedId ();
			if (parentId < 0 || !topology.childCountKnown (parentId)) {
				throw new IllegalArgumentException ("the node at row " + row + " was never expanded");
			}
		}
		topology.apply (parentId, patch);
		modCount++;
	}

	/** Nodes visited by the sibling index since this view was created (the cost counter of brief rule 24). */
	public long work () {
		return topology.indexWork ();
	}

	private M3TreeProjection.Row rowAt (int row) {
		Objects.checkIndex (row, size ());
		return projection.rowAt (row);
	}

	private int materialize (M3TreeProjection.Row r, N node) {
		int id = nextId++;
		if (id == nodes.length) nodes = Arrays.copyOf (nodes, M3LaneGrowth.capacity (nodes.length, id + 1, 8));
		nodes [id] = node;
		topology.bind (id, r.parentId (), r.childIndex ());
		return id;
	}

	@SuppressWarnings ("unchecked")
	private N node (int id) {
		return (N) nodes [id];
	}
}
