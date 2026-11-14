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
package org.eclipse.swt.internal;

import java.util.*;

/*
 * The shared part of the views: the listener lane, the change object, and the source edits. The
 * first view of a chain (no input) owns the source size and turns every edit into a change of
 * its identity source; the other views forward edits to their input, so an edit reported to any
 * view flows down the whole chain.
 */
abstract class RowViewBase implements RowView {

	private static final RowView.Listener [] NO_LISTENERS = {};

	final RowView input;
	final RowChange change = new RowChange (this);
	private final RowChange edit;
	private final RowView.Listener inputListener;
	private RowView.Listener [] listeners = NO_LISTENERS;
	private int rootSourceSize;
	/* input changes this view applied by a pass over all its rows (re-evaluation, fold or re-sort) instead of a delta */
	int fullPasses;
	private int visibleViewports, hiddenViewports;

	RowViewBase (RowView input, int sourceSize) {
		this.input = input;
		if (input == null) {
			RowChecks.argument (sourceSize >= 0);
			rootSourceSize = sourceSize;
			edit = new RowChange (null);
			inputListener = null;
		} else {
			edit = null;
			inputListener = this::inputChanged;
			input.addListener (inputListener);
		}
	}

	/* applies a change of the input (or of the identity source) and fires this view's change */
	abstract void inputChanged (RowChange change);

	@Override
	public int sourceSize () {
		return input == null ? rootSourceSize : input.sourceSize ();
	}

	@Override
	public void setSourceSize (int sourceSize) {
		if (input != null) {
			input.setSourceSize (sourceSize);
			return;
		}
		RowChecks.argument (sourceSize >= 0);
		rootSourceSize = sourceSize;
		edit.set (RowChange.RESET, 0, 0);
		inputChanged (edit);
	}

	@Override
	public void rowsInserted (int start, int count) {
		if (input != null) {
			input.rowsInserted (start, count);
			return;
		}
		RowChecks.span (start, 0, rootSourceSize);
		RowChecks.argument (count >= 0);
		rootSourceSize += count;
		edit.set (RowChange.INSERTED, start, count);
		for (int row = start; row < start + count; row++) edit.add (row);
		inputChanged (edit);
	}

	@Override
	public void rowsRemoved (int start, int count) {
		if (input != null) {
			input.rowsRemoved (start, count);
			return;
		}
		RowChecks.span (start, count, rootSourceSize);
		rootSourceSize -= count;
		edit.set (RowChange.REMOVED, start, count);
		inputChanged (edit);
	}

	@Override
	public void rowsChanged (int... sourceRows) {
		if (input != null) {
			input.rowsChanged (sourceRows);
			return;
		}
		RowChecks.nonNull (sourceRows);
		int [] rows = sourceRows.clone ();
		Arrays.sort (rows);
		edit.set (RowChange.UPDATED, 0, 0);
		for (int i = 0; i < rows.length; i++) {
			RowChecks.range (rows [i], rootSourceSize);
			if (i == 0 || rows [i] != rows [i - 1]) edit.add (rows [i]);
		}
		inputChanged (edit);
	}

	@Override
	public void addListener (RowView.Listener listener) {
		RowChecks.nonNull (listener);
		for (RowView.Listener l : listeners) {
			if (l == listener) return;
		}
		RowView.Listener [] grown = Arrays.copyOf (listeners, listeners.length + 1);
		grown [listeners.length] = listener;
		listeners = grown;
	}

	@Override
	public void removeListener (RowView.Listener listener) {
		RowChecks.nonNull (listener);
		for (int i = 0; i < listeners.length; i++) {
			if (listeners [i] == listener) {
				RowView.Listener [] shrunk = new RowView.Listener [listeners.length - 1];
				System.arraycopy (listeners, 0, shrunk, 0, i);
				System.arraycopy (listeners, i + 1, shrunk, i, shrunk.length - i);
				listeners = shrunk;
				return;
			}
		}
	}

	/**
	 * Detaches this view from its input view, so that it no longer follows the input. A view
	 * without an input is not affected.
	 */
	public void dispose () {
		if (input != null) input.removeListener (inputListener);
	}

	/*
	 * A bound viewport was added (+1) or removed (-1), or moved between visible and hidden: the chain counts them up to
	 * its first view, which holds the results it would post to the UI thread while every viewport is hidden.
	 */
	void viewports (int visible, int hidden) {
		visibleViewports += visible;
		hiddenViewports += hidden;
		if (input == null) viewportsChanged (); else if (input instanceof RowViewBase base) base.viewports (visible, hidden);
	}

	/* whether viewports are bound to this view and every one of them is hidden */
	boolean held () {
		return visibleViewports == 0 && hiddenViewports > 0;
	}

	/* the first view of a chain: the viewports changed visibility */
	void viewportsChanged () {
	}

	boolean hasListeners () {
		return listeners.length > 0;
	}

	void fire () {
		for (RowView.Listener listener : listeners) listener.viewChanged (change);
	}
}
