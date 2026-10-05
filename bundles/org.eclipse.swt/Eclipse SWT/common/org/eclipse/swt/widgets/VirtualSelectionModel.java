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

import org.eclipse.swt.internal.*;

/**
 * Compatibility adapter over the shared logical selection algebra.
 *
 * <p>The canonical range/complement representation lives in
 * {@link ViewportRuntime.Selection}; this type preserves existing widget call
 * sites while all viewport-driven controls converge on the same selection
 * semantics.</p>
 */
final class VirtualSelectionModel {
	private final ViewportRuntime.Selection selection = new ViewportRuntime.Selection ();

	int logicalCount () {
		return selection.logicalCount ();
	}

	void setLogicalCount (int count) {
		selection.setLogicalCount (count);
	}

	boolean isSelected (int index) {
		return selection.isSelected (index);
	}

	void setSelected (int index, boolean selected) {
		selection.setSelected (index, selected);
	}

	void selectRange (int start, int endExclusive) {
		selection.selectRange (start, endExclusive);
	}

	void deselectRange (int start, int endExclusive) {
		selection.deselectRange (start, endExclusive);
	}

	void clear () {
		selection.clear ();
	}

	void selectAll () {
		selection.selectAll ();
	}

	int selectedCount () {
		return selection.selectedCount ();
	}

	int [] toArray () {
		return selection.toArray ();
	}

	void insert (int index, int count) {
		selection.insert (index, count);
	}

	void remove (int index, int count) {
		selection.remove (index, count);
	}

	int rangeCount () {
		return selection.rangeCount ();
	}

	int rangeStart (int range) {
		return selection.rangeStart (range);
	}

	int rangeEndExclusive (int range) {
		return selection.rangeEndExclusive (range);
	}

	boolean complementMode () {
		return selection.complementMode ();
	}
}
