/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.*;

/**
 * Flat visible-row projection over {@link VirtualTreeTopology}.
 *
 * <p>Cold logical children are arithmetic coordinates and require no topology
 * slot. Only materialized expanded nodes contribute descendant rows. The
 * projection therefore scales with the materialized/expanded frontier rather
 * than total logical tree cardinality.</p>
 */
final class VirtualTreeVisibleProjection {

	record Row (int parentId, int childIndex, int materializedId, int depth) {
		boolean materialized () {
			return materializedId >= 0;
		}
	}

	/**
	 * Caller-owned native demand buffer. Entries are a dense physical preorder of
	 * paint rows and their ancestor closure. Logical sibling indices remain
	 * separate from dense physical indices; no Item is materialized by planning.
	 */
	static final class Residency {
		private int [] parentIds = new int [0], childIndices = new int [0];
		private int [] materializedIds = new int [0], depths = new int [0];
		private int [] physicalParents = new int [0], physicalIndices = new int [0];
		private int [] childCounts = new int [0], firstChildren = new int [0];
		private int [] lastChildren = new int [0], nextSiblings = new int [0];
		private long [] visibleRows = new long [0];
		private int [] paintEntries = new int [0];
		private int [] pathIds = new int [0], pathEntries = new int [0];
		private int size, paintCount, rootCount, firstRoot = -1, lastRoot = -1;
		private long firstPaintRow, sourceGeneration = -1;

		int size () { return size; }
		int paintCount () { return paintCount; }
		int rootCount () { return rootCount; }
		long firstPaintRow () { return firstPaintRow; }
		long sourceGeneration () { return sourceGeneration; }

		int parentId (int entry) { requireEntry (entry); return parentIds [entry]; }
		int childIndex (int entry) { requireEntry (entry); return childIndices [entry]; }
		int materializedId (int entry) { requireEntry (entry); return materializedIds [entry]; }
		int depth (int entry) { requireEntry (entry); return depths [entry]; }
		int physicalParent (int entry) { requireEntry (entry); return physicalParents [entry]; }
		int physicalIndex (int entry) { requireEntry (entry); return physicalIndices [entry]; }
		int childCount (int entry) { requireEntry (entry); return childCounts [entry]; }
		long visibleRow (int entry) { requireEntry (entry); return visibleRows [entry]; }

		int paintEntry (int offset) {
			if (offset < 0 || offset >= paintCount) throw new IllegalArgumentException ("paint offset");
			return paintEntries [offset];
		}

		int entryForMaterializedId (int id) {
			if (id < 0) return -1;
			for (int entry = 0; entry < size; entry++) {
				if (materializedIds [entry] == id) return entry;
			}
			return -1;
		}

		int entryForCoordinate (int parentId, int childIndex) {
			for (int entry = 0; entry < size; entry++) {
				if (parentIds [entry] == parentId && childIndices [entry] == childIndex) return entry;
			}
			return -1;
		}

		int physicalChild (int parentEntry, int index) {
			if (parentEntry != -1) requireEntry (parentEntry);
			int count = parentEntry == -1 ? rootCount : childCounts [parentEntry];
			if (index < 0 || index >= count) return -1;
			int entry = parentEntry == -1 ? firstRoot : firstChildren [parentEntry];
			for (int i = 0; i < index; i++) entry = nextSiblings [entry];
			return entry;
		}

		int entryAtPhysicalPath (int [] path, int length) {
			if (path == null || length <= 0 || length > path.length) {
				throw new IllegalArgumentException ("physical path");
			}
			int entry = -1;
			for (int level = 0; level < length; level++) {
				entry = physicalChild (entry, path [level]);
				if (entry < 0) return -1;
			}
			return entry;
		}

		/** Writes a physical path into caller-owned storage; returns its length. */
		int physicalPath (int entry, int [] path) {
			requireEntry (entry);
			int length = Math.incrementExact (depths [entry]);
			if (path == null || path.length < length) throw new IllegalArgumentException ("path capacity");
			for (int level = length - 1; level >= 0; level--) {
				path [level] = physicalIndices [entry];
				entry = physicalParents [entry];
			}
			return length;
		}

		/**
		 * Rebuildable native packet: entry/root counts, then physical parent,
		 * physical sibling index, physical child count and SWT facade-id lanes.
		 * Native child counts describe this bounded projection, never logical N.
		 */
		int [] nativeModelSnapshot () {
			int [] snapshot = new int [Math.addExact (2, Math.multiplyExact (size, 4))];
			snapshot [0] = size;
			snapshot [1] = rootCount;
			System.arraycopy (physicalParents, 0, snapshot, 2, size);
			System.arraycopy (physicalIndices, 0, snapshot, 2 + size, size);
			System.arraycopy (childCounts, 0, snapshot, 2 + size * 2, size);
			System.arraycopy (materializedIds, 0, snapshot, 2 + size * 3, size);
			return snapshot;
		}

		private void reset (long firstRow) {
			size = paintCount = rootCount = 0;
			firstRoot = lastRoot = -1;
			firstPaintRow = firstRow;
			sourceGeneration = -1;
		}

		private void appendPaintRow (VirtualTreeVisibleProjection projection,
				int parentId, int childIndex, int id, int depth, long visibleRow) {
			ensurePathCapacity (Math.incrementExact (depth));
			if (paintCount == 0) {
				VirtualTreeTopology topology = projection.topology;
				int ancestor = parentId;
				for (int level = depth - 1; level >= 0; level--) {
					pathIds [level] = ancestor;
					ancestor = topology.parentId (ancestor);
				}
				if (ancestor != VirtualTreeTopology.ROOT) {
					throw new IllegalStateException ("projection ancestry mismatch");
				}
				for (int level = 0; level < depth; level++) {
					ancestor = pathIds [level];
					pathEntries [level] = appendEntry (topology.parentId (ancestor),
							topology.childIndex (ancestor), ancestor, level,
							level == 0 ? -1 : pathEntries [level - 1], projection.visibleIndexOf (ancestor));
				}
			}
			int parentEntry = depth == 0 ? -1 : pathEntries [depth - 1];
			if (depth > 0 && materializedIds [parentEntry] != parentId) {
				throw new IllegalStateException ("non-contiguous projection window");
			}
			int entry = appendEntry (parentId, childIndex, id, depth, parentEntry, visibleRow);
			pathEntries [depth] = entry;
			if (paintCount == paintEntries.length) {
				paintEntries = Arrays.copyOf (paintEntries, capacity (paintEntries.length, paintCount + 1));
			}
			paintEntries [paintCount++] = entry;
		}

		private int appendEntry (int parentId, int childIndex, int id,
				int depth, int parentEntry, long visibleRow) {
			ensureCapacity (Math.incrementExact (size));
			int entry = size++;
			parentIds [entry] = parentId;
			childIndices [entry] = childIndex;
			materializedIds [entry] = id;
			depths [entry] = depth;
			visibleRows [entry] = visibleRow;
			physicalParents [entry] = parentEntry;
			childCounts [entry] = 0;
			firstChildren [entry] = lastChildren [entry] = nextSiblings [entry] = -1;
			if (parentEntry == -1) {
				physicalIndices [entry] = rootCount++;
				if (lastRoot == -1) firstRoot = entry;
				else nextSiblings [lastRoot] = entry;
				lastRoot = entry;
			} else {
				physicalIndices [entry] = childCounts [parentEntry]++;
				int previous = lastChildren [parentEntry];
				if (previous == -1) firstChildren [parentEntry] = entry;
				else nextSiblings [previous] = entry;
				lastChildren [parentEntry] = entry;
			}
			return entry;
		}

		private void ensureCapacity (int required) {
			if (required <= parentIds.length) return;
			int next = capacity (parentIds.length, required);
			parentIds = Arrays.copyOf (parentIds, next);
			childIndices = Arrays.copyOf (childIndices, next);
			materializedIds = Arrays.copyOf (materializedIds, next);
			depths = Arrays.copyOf (depths, next);
			physicalParents = Arrays.copyOf (physicalParents, next);
			physicalIndices = Arrays.copyOf (physicalIndices, next);
			childCounts = Arrays.copyOf (childCounts, next);
			firstChildren = Arrays.copyOf (firstChildren, next);
			lastChildren = Arrays.copyOf (lastChildren, next);
			nextSiblings = Arrays.copyOf (nextSiblings, next);
			visibleRows = Arrays.copyOf (visibleRows, next);
		}

		private void ensurePathCapacity (int required) {
			if (required <= pathIds.length) return;
			int next = capacity (pathIds.length, required);
			pathIds = Arrays.copyOf (pathIds, next);
			pathEntries = Arrays.copyOf (pathEntries, next);
		}

		private static int capacity (int current, int required) {
			return (int)Math.min (Integer.MAX_VALUE,
					Math.max ((long)required, Math.max (4L, current + (long)current / 2)));
		}

		private void requireEntry (int entry) {
			if (entry < 0 || entry >= size) throw new IllegalArgumentException ("resident entry");
		}
	}

	private final VirtualTreeTopology topology;

	VirtualTreeVisibleProjection (VirtualTreeTopology topology) {
        if (topology == null) {
            throw new IllegalArgumentException("topology");
        }
		this.topology = topology;
	}

	long visibleRowCount () {
		return visibleChildren (VirtualTreeTopology.ROOT);
	}

	long generation () {
		return topology.generation ();
	}

	long visibleChildren (int parentId) {
        if (!topology.childCountKnown(parentId)) {
            return 0;
        }
		return topology.visibleChildrenRowCount (parentId);
	}

	Row rowAt (long visibleIndex) {
		long total = visibleRowCount ();
		if (visibleIndex < 0 || visibleIndex >= total) {
			throw new IllegalArgumentException ("visible row outside projection");
		}
		return rowAt (VirtualTreeTopology.ROOT, visibleIndex, 0, null, visibleIndex);
	}

	Row [] window (long firstVisible, int rowCount) {
        if (firstVisible < 0 || rowCount < 0) {
            throw new IllegalArgumentException("negative window");
        }
		long total = visibleRowCount ();
        if (firstVisible >= total || rowCount == 0) {
            return new Row [0];
        }
		int length = (int)Math.min ((long)rowCount, total - firstVisible);
		Row [] rows = new Row [length];
        for (int i = 0; i < length; i++) {
            rows [i] = rowAt(firstVisible + i);
        }
		return rows;
	}

	/** Fills bounded native demand using the same sparse traversal as {@link #rowAt(long)}. */
	void residencyWindow (long firstVisible, int rowCount, Residency target) {
		if (firstVisible < 0 || rowCount < 0 || target == null) {
			throw new IllegalArgumentException ("residency window");
		}
		long total = visibleRowCount ();
		target.reset (firstVisible);
		int length = firstVisible >= total ? 0 : (int)Math.min ((long)rowCount, total - firstVisible);
		for (int i = 0; i < length; i++) {
			long row = firstVisible + i;
			rowAt (VirtualTreeTopology.ROOT, row, 0, target, row);
		}
		target.sourceGeneration = generation ();
	}

	long visibleIndexOf (int materializedId) {
        if (!topology.contains(materializedId)) {
            return -1;
        }
		long offset = 0;
		int id = materializedId;
		while (true) {
			int parentId = topology.parentId (id);
			offset = Math.addExact (offset, offsetWithinParent (parentId, topology.childIndex (id)));
            if (parentId == VirtualTreeTopology.ROOT) {
                return offset;
            }
            if (!topology.flag(parentId, VirtualItemState.EXPANDED)) {
                return -1;
            }
			offset = Math.addExact (offset, 1);
			id = parentId;
		}
	}

	private Row rowAt (int parentId, long row, int depth, Residency target, long visibleRow) {
		while (true) {
			int logicalCount = topology.childCount (parentId);
			int coordinate = 0;
			int descendantId = -1;
			for (int id = topology.firstMaterializedChildId (parentId);
					id >= 0; id = topology.nextMaterializedSiblingId (id)) {
				int index = topology.childIndex (id);
                if (index >= logicalCount) {
                    break;
                }
				int coldGap = index - coordinate;
				if (row < coldGap) {
					return emitRow (parentId, Math.addExact (coordinate, (int)row), -1, depth, target, visibleRow);
				}
				row -= coldGap;
                if (row == 0) {
                    return emitRow(parentId, index, id, depth, target, visibleRow);
                }
				row--;
				if (topology.flag (id, VirtualItemState.EXPANDED)
						&& topology.childCountKnown (id)) {
					long descendants = visibleChildren (id);
					if (row < descendants) {
						descendantId = id;
						break;
					}
					row -= descendants;
				}
				coordinate = index + 1;
			}
			if (descendantId >= 0) {
				parentId = descendantId;
				depth = Math.incrementExact (depth);
				continue;
			}
			long childIndex = Math.addExact ((long)coordinate, row);
            if (childIndex >= logicalCount) {
                throw new IllegalStateException("projection overflow");
            }
			return emitRow (parentId, Math.toIntExact (childIndex), -1, depth, target, visibleRow);
		}
	}

	private Row emitRow (int parentId, int childIndex, int id, int depth, Residency target, long visibleRow) {
		if (target == null) return new Row (parentId, childIndex, id, depth);
		target.appendPaintRow (this, parentId, childIndex, id, depth, visibleRow);
		return null;
	}

	private long offsetWithinParent (int parentId, int targetChildIndex) {
		long offset = targetChildIndex;
		for (int id = topology.firstMaterializedChildId (parentId);
				id >= 0; id = topology.nextMaterializedSiblingId (id)) {
			int index = topology.childIndex (id);
            if (index >= targetChildIndex) {
                break;
            }
			if (topology.flag (id, VirtualItemState.EXPANDED)
					&& topology.childCountKnown (id)) {
				offset = Math.addExact (offset, visibleChildren (id));
			}
		}
		return offset;
	}
}
