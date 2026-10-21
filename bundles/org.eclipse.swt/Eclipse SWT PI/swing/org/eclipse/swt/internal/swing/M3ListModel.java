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
package org.eclipse.swt.internal.swing;

import javax.swing.*;
import javax.swing.event.*;

/**
 * The rows of the <code>JList</code> behind an SWT List in the viewport mode, with hidden viewports (brief rule 24):
 * while the list is not showing, a change reaches only the row geometry ({@link M3ListRows}) and the selection, which
 * moves as <code>BasicListUI</code> would move it; the list is marked dirty. When the list shows, Swing gets one
 * <code>contentsChanged</code> for all rows, so it lays the list out and paints it once.
 * <p>
 * IMPORTANT: This class is <em>not</em> part of the SWT public API.
 * </p>
 */
public final class M3ListModel extends DefaultListModel<Object> {
	private static final long serialVersionUID = 1L;
	private final JList<?> list;
	private boolean dirty;

	private M3ListModel (JList<?> list) {
		this.list = list;
	}

	/**
	 * Gives a list this model (empty); the owner calls {@link #shown()} when the list starts showing.
	 *
	 * @param list the list, before its first row
	 * @return the model
	 */
	@SuppressWarnings ("unchecked")
	public static M3ListModel install (JList<?> list) {
		M3ListModel model = new M3ListModel (list);
		((JList<Object>) list).setModel (model);
		return model;
	}

	/** Whether rows changed while the list was hidden and Swing has not been told yet. */
	public boolean isDirty () {
		return dirty;
	}

	/** The list shows: one <code>contentsChanged</code> over all rows for what changed while it was hidden. */
	public void shown () {
		for (ListDataListener listener : getListDataListeners ()) {
			if (listener instanceof M3ListRows) ((M3ListRows) listener).shown ();
		}
		if (!dirty) return;
		dirty = false;
		ListDataEvent e = new ListDataEvent (this, ListDataEvent.CONTENTS_CHANGED, 0, Math.max (0, getSize () - 1));
		ListDataListener [] listeners = getListDataListeners ();
		for (int i = listeners.length - 1; i >= 0; i--) {
			// the geometry already followed every change
			if (!(listeners [i] instanceof M3ListRows)) listeners [i].contentsChanged (e);
		}
	}

	@Override
	protected void fireIntervalAdded (Object source, int index0, int index1) {
		deliver (new ListDataEvent (source, ListDataEvent.INTERVAL_ADDED, index0, index1));
	}

	@Override
	protected void fireIntervalRemoved (Object source, int index0, int index1) {
		deliver (new ListDataEvent (source, ListDataEvent.INTERVAL_REMOVED, index0, index1));
	}

	@Override
	protected void fireContentsChanged (Object source, int index0, int index1) {
		deliver (new ListDataEvent (source, ListDataEvent.CONTENTS_CHANGED, index0, index1));
	}

	private void deliver (ListDataEvent e) {
		boolean showing = list.isShowing ();
		if (!showing) {
			hiddenSelection (e);
			dirty = true;
		}
		ListDataListener [] listeners = getListDataListeners ();
		for (int i = listeners.length - 1; i >= 0; i--) {
			if (showing || listeners [i] instanceof M3ListRows) dispatch (listeners [i], e);
		}
	}

	/** The selection moves as <code>BasicListUI</code> moves it for the event Swing does not get. */
	private void hiddenSelection (ListDataEvent e) {
		ListSelectionModel selection = list.getSelectionModel ();
		int min = Math.min (e.getIndex0 (), e.getIndex1 ()), max = Math.max (e.getIndex0 (), e.getIndex1 ());
		if (selection == null || min < 0) return;
		if (e.getType () == ListDataEvent.INTERVAL_ADDED) selection.insertIndexInterval (min, max - min + 1, true);
		if (e.getType () == ListDataEvent.INTERVAL_REMOVED) selection.removeIndexInterval (min, max);
	}

	private static void dispatch (ListDataListener listener, ListDataEvent e) {
		switch (e.getType ()) {
			case ListDataEvent.INTERVAL_ADDED: listener.intervalAdded (e); break;
			case ListDataEvent.INTERVAL_REMOVED: listener.intervalRemoved (e); break;
			default: listener.contentsChanged (e); break;
		}
	}
}
