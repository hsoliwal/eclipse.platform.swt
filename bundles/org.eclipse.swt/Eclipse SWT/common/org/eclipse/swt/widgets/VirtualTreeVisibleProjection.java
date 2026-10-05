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
		return rowAt (VirtualTreeTopology.ROOT, visibleIndex, 0);
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
		fillWindow (firstVisible, rows);
		return rows;
	}


	/**
	 * Seek by cached subtree weights, then emit one continuous preorder range.
	 * Existing parent/sibling lanes supply the return path, so no traversal
	 * stack or retained cursor is needed. Cold siblings are skipped arithmetically.
	 */
	private void fillWindow (long skip, Row [] rows) {
		int parentId = VirtualTreeTopology.ROOT, coordinate = 0, depth = 0, written = 0;
		int nextId = topology.firstMaterializedChildId (parentId);
		while (written < rows.length) {
			int logicalCount = topology.childCount (parentId);
			int coldEnd = nextId >= 0 ? Math.min (logicalCount, topology.childIndex (nextId)) : logicalCount;
			if (coordinate < coldEnd) {
				int omitted = (int)Math.min (skip, (long)coldEnd - coordinate);
				coordinate += omitted;
				skip -= omitted;
				int emit = Math.min (coldEnd - coordinate, rows.length - written);
				for (int i = 0; i < emit; i++) {
					rows [written++] = new Row (parentId, coordinate++, -1, depth);
				}
				if (written == rows.length) {
					return;
				}
			}
			if (nextId >= 0 && coordinate < logicalCount) {
				int id = nextId;
				nextId = topology.nextMaterializedSiblingId (id);
				int index = coordinate++;
				if (skip > 0) {
					skip--;
				} else {
					rows [written++] = new Row (parentId, index, id, depth);
					if (written == rows.length) {
						return;
					}
				}
				if (topology.flag (id, VirtualItemState.EXPANDED) && topology.childCountKnown (id)) {
					long descendants = visibleChildren (id);
					if (skip >= descendants) {
						skip -= descendants;
					} else {
						depth++;
						parentId = id;
						coordinate = 0;
						nextId = topology.firstMaterializedChildId (id);
					}
				}
				continue;
			}
			if (depth == 0) {
				throw new IllegalStateException ("projection overflow");
			}
			depth--;
			coordinate = topology.childIndex (parentId) + 1;
			nextId = topology.nextMaterializedSiblingId (parentId);
			parentId = topology.parentId (parentId);
		}
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

	private Row rowAt (int parentId, long row, int depth) {
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
					return new Row (parentId, Math.addExact (coordinate, (int)row), -1, depth);
				}
				row -= coldGap;
                if (row == 0) {
                    return new Row(parentId, index, id, depth);
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
			return new Row (parentId, Math.toIntExact (childIndex), -1, depth);
		}
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
