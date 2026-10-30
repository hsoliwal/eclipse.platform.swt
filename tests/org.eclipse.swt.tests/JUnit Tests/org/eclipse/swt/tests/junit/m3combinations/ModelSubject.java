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
package org.eclipse.swt.tests.junit.m3combinations;

import java.util.*;
import java.util.List;

import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/**
 * The widget-free M3 model layer (headless: no Display): rows are the order, the selection is an M3ItemRange, the
 * sort/filter result is cross-checked with an M3ProxyModel over the same source.
 */
final class ModelSubject extends Subject {
	M3ItemRange selection = M3ItemRange.EMPTY;
	final Map<Integer, String> updated = new HashMap<> ();
	boolean released;

	@Override
	void create (Shell shell, int style) {
		released = false;
	}

	@Override
	Widget widget () {
		return null;
	}

	@Override
	void show (int [] order) {
		selection = M3ItemRange.EMPTY;
		updated.clear ();
	}

	@Override
	void scrollTo (int row) {
		throw new UnsupportedOperationException ("the model layer has no scroll position");
	}

	@Override
	void select (int start, int end) {
		selection = M3ItemRange.of (start, end);
	}

	@Override
	void update (int row, String text) {
		updated.put (row, text);
	}

	@Override
	String rowText (int row) {
		return updated.getOrDefault (row, super.rowText (row));
	}

	/** The proxy model's view of the same reversed, filtered source of n rows (the order a sort/filter must give). */
	static int [] proxyOrder (int n) {
		M3ProxyModel proxy = new M3ProxyModel (n).addFilter (id -> id % 3 != 0).addSort ((a, b) -> Integer.compare (b, a)).refresh ();
		int [] rows = new int [proxy.size ()];
		for (int i = 0; i < rows.length; i++) rows [i] = proxy.map (i);
		return rows;
	}

	@Override
	String state () {
		if (released) return "disposed";
		int n = order.length;
		boolean sorted = n > 1 && order [0] > order [1];
		String proxy = sorted ? " proxy-agrees=" + Arrays.equals (order, proxyOrder (order [0] + 1)) : "";
		return "count=" + n + " sel=" + selection + " text=" + (n == 0 ? "-" : rowText (0) + "/" + rowText (n / 2)) + proxy;
	}

	@Override
	String dispose () {
		released = true;
		order = new int [0];
		return "disposed";
	}

	@Override
	boolean disposed () {
		return released;
	}

	@Override
	List<Widget> items () {
		return new ArrayList<> ();
	}
}
