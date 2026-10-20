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
package org.eclipse.swt.widgets;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.m3.*;

/*
 * The tree part of M3Model: one id lane per parent whose children have ids. The roots have positional
 * ids until a patch or setIds gives them; deeper children get ids from setIds or from the patch that
 * inserts them. A parent id finds its lane, and a child id finds the lane that holds it, through
 * M3LongIndex (no boxing), so ids are unique in the whole tree.
 */
final class M3TreeModel {

	/* the children ids of one parent; positions are built on demand */
	static final class Lane {
		final long parent;
		long [] ids;
		private M3LongIndex positions;

		Lane (long parent, long [] ids) {
			this.parent = parent;
			this.ids = ids;
		}

		int indexOf (long id) {
			if (positions == null) {
				positions = new M3LongIndex (ids.length);
				for (int i = 0; i < ids.length; i++) positions.put (ids [i], i);
			}
			return positions.get (id);
		}
	}

	private final Tree tree;
	private final ArrayList<Lane> lanes = new ArrayList<> ();
	private final M3LongIndex parentSlot = new M3LongIndex (16);
	private final M3LongIndex childSlot = new M3LongIndex (64);

	M3TreeModel (Tree tree) {
		this.tree = tree;
	}

	/* the lane of a parent, or null; the roots get a positional lane, and a lane whose parent's child count changed outside the model is forgotten */
	Lane lane (long parent) {
		int slot = parentSlot.get (parent);
		Lane lane = slot < 0 ? null : lanes.get (slot);
		if (lane != null && lane.ids.length != count (parent)) {
			dropLane (parent);
			lane = null;
		}
		if (lane != null || parent != M3Model.ROOT) return lane;
		long [] ids = new long [tree.getItemCount ()];
		for (int i = 0; i < ids.length; i++) {
			ids [i] = i;
			if (childSlot.get (i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		return setLane (parent, ids);
	}

	/* gives a parent's children their ids; ids must be unused elsewhere in the tree */
	Lane setLane (long parent, long [] ids) {
		Lane old = parentSlot.get (parent) < 0 ? null : lanes.get (parentSlot.get (parent));
		if (old != null) {
			for (long id : old.ids) childSlot.remove (id);
		}
		for (long id : ids) {
			if (childSlot.get (id) >= 0) {
				if (old != null) for (long kept : old.ids) childSlot.put (kept, parentSlot.get (parent));
				SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			}
		}
		Lane lane = new Lane (parent, ids);
		int slot = parentSlot.get (parent);
		if (slot < 0) {
			lanes.add (lane);
			slot = lanes.size () - 1;
			parentSlot.put (parent, slot);
		} else {
			lanes.set (slot, lane);
		}
		for (long id : ids) childSlot.put (id, slot);
		return lane;
	}

	/* forgets the lane of a parent and, recursively, of its children */
	void dropLane (long parent) {
		int slot = parentSlot.remove (parent);
		if (slot < 0) return;
		Lane lane = lanes.get (slot);
		lanes.set (slot, null);
		for (long id : lane.ids) {
			if (childSlot.get (id) == slot) childSlot.remove (id);
			dropLane (id);
		}
	}

	/* the lane holding an id, or null */
	Lane laneOf (long id) {
		lane (M3Model.ROOT);
		int slot = childSlot.get (id);
		return slot < 0 ? null : lanes.get (slot);
	}

	/* the item of an id, or null */
	TreeItem item (long id) {
		Lane lane = laneOf (id);
		if (lane == null) return null;
		int index = lane.indexOf (id);
		if (lane.parent == M3Model.ROOT) return index < tree.getItemCount () ? tree.getItem (index) : null;
		TreeItem parent = item (lane.parent);
		return parent != null && index < parent.getItemCount () ? parent.getItem (index) : null;
	}

	/* the id of an item, or the root id for null; -2 when the item has no id */
	long idOf (TreeItem item) {
		if (item == null) return M3Model.ROOT;
		TreeItem parentItem = item.getParentItem ();
		long parent = idOf (parentItem);
		if (parent == -2) return -2;
		Lane lane = lane (parent);
		if (lane == null) return -2;
		int index = parentItem == null ? tree.indexOf (item) : parentItem.indexOf (item);
		return index < 0 || index >= lane.ids.length ? -2 : lane.ids [index];
	}

	/* children of a parent id, or -1 when the parent has no item */
	int count (long parent) {
		if (parent == M3Model.ROOT) return tree.getItemCount ();
		TreeItem item = item (parent);
		return item == null ? -1 : item.getItemCount ();
	}

	/* ------------------------------------------------------------------ M3Model operations */

	int size (long parent) {
		int count = count (parent);
		if (count < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		return count;
	}

	long id (long parent, int index) {
		Lane lane = knownLane (parent);
		if (index < 0 || index >= lane.ids.length) SWT.error (SWT.ERROR_INVALID_RANGE);
		return lane.ids [index];
	}

	int indexOf (long id) {
		Lane lane = laneOf (id);
		return lane == null ? -1 : lane.indexOf (id);
	}

	long parentOf (long id) {
		Lane lane = laneOf (id);
		return lane == null ? -2 : lane.parent;
	}

	void setIds (long parent, long [] ids) {
		if (ids == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (ids.length != size (parent)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		M3LongIndex check = new M3LongIndex (ids.length);
		for (int i = 0; i < ids.length; i++) {
			if (check.put (ids [i], i) >= 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		Lane old = parentSlot.get (parent) < 0 ? null : lanes.get (parentSlot.get (parent));
		if (old != null) {
			for (long id : old.ids) dropLane (id);
		}
		setLane (parent, ids.clone ());
	}

	TreeItem knownItem (long id) {
		TreeItem item = item (id);
		if (item == null) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		return item;
	}

	/* the lane of a parent with an item; a parent without children gets an empty lane */
	private Lane knownLane (long parent) {
		Lane lane = lane (parent);
		if (lane != null) return lane;
		if (count (parent) == 0) return setLane (parent, new long [0]);
		SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		return null;
	}

	void apply (M3Patch patch, M3Model owner) {
		long parent = patch.parent ();
		Lane lane = knownLane (parent);
		long [] before = lane.ids, after;
		try {
			after = patch.apply (before);
		} catch (IllegalArgumentException e) {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT, e);
			return;
		}
		M3LongIndex old = new M3LongIndex (before.length), next = new M3LongIndex (after.length);
		for (int i = 0; i < before.length; i++) old.put (before [i], i);
		for (int i = 0; i < after.length; i++) {
			if (next.put (after [i], i) >= 0 || (old.get (after [i]) < 0 && childSlot.get (after [i]) >= 0)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
		TreeItem parentItem = parent == M3Model.ROOT ? null : item (parent);
		boolean virtual = (tree.getStyle () & SWT.VIRTUAL) != 0;
		long anchor = idOf (tree.getTopItem ());
		M3BulkUpdates.bulk (tree, () -> {
			patch.applyTo (new M3Patch.Target () {
				int run;

				@Override
				public void remove (int index, int count) {
					for (int k = count - 1; k >= 0; k--) {
						TreeItem item = parentItem == null ? tree.getItem (index + k) : parentItem.getItem (index + k);
						item.dispose ();
					}
				}

				@Override
				public void insert (int index, int count) {
					for (int k = 0; k < count; k++) {
						TreeItem item = parentItem == null ? new TreeItem (tree, SWT.NONE, index + k) : new TreeItem (parentItem, SWT.NONE, index + k);
						if (virtual) {
							clear (parentItem, index + k);
						} else {
							owner.fillTreeItem (item, patch.insertedId (run, k));
						}
					}
					run++;
				}

				@Override
				public void update (int index, int count) {
					for (int k = 0; k < count; k++) {
						if (virtual) {
							clear (parentItem, index + k);
						} else {
							owner.fillTreeItem (parentItem == null ? tree.getItem (index + k) : parentItem.getItem (index + k), after [index + k]);
						}
					}
				}
			});
			/* removed and moved rows lost their items, and with them their children */
			for (int i = 0; i < patch.removeRuns (); i++) {
				for (int k = 0; k < patch.removeCount (i); k++) dropLane (before [patch.removeAt (i) + k]);
			}
			setLane (parent, after);
			TreeItem top = anchor == -2 || anchor == M3Model.ROOT ? null : item (anchor);
			if (top != null) tree.setTopItem (top);
		});
	}

	private void clear (TreeItem parentItem, int index) {
		if (parentItem == null) tree.clear (index, false); else parentItem.clear (index, false);
	}
}
