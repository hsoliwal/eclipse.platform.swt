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
import java.util.function.*;

import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;

/**
 * Combo and CCombo share one subject: they have the same item API (setItems, select, setItem, getText) but no common
 * type, so the subject holds the calls of one of them. Neither has a top index (the scroll rows are excluded).
 */
final class ComboSubject extends Subject {
	final BiFunction<Shell, Integer, Composite> factory;
	final BiConsumer<Composite, String []> setItems;
	final ObjIntConsumer<Composite> selectIndex;
	final BiConsumer<Composite, Object []> setItem;
	final Function<Composite, String> read;
	Composite combo;

	ComboSubject (BiFunction<Shell, Integer, Composite> factory, BiConsumer<Composite, String []> setItems, ObjIntConsumer<Composite> select,
		BiConsumer<Composite, Object []> setItem, Function<Composite, String> read) {
		this.factory = factory;
		this.setItems = setItems;
		this.selectIndex = select;
		this.setItem = setItem;
		this.read = read;
	}

	static ComboSubject combo () {
		return new ComboSubject (Combo::new, (c, items) -> ((Combo) c).setItems (items), (c, i) -> ((Combo) c).select (i),
			(c, a) -> ((Combo) c).setItem ((Integer) a [0], (String) a [1]),
			c -> "count=" + ((Combo) c).getItemCount () + " sel=" + ((Combo) c).getSelectionIndex () + " text=" + ((Combo) c).getText ());
	}

	static ComboSubject cCombo () {
		return new ComboSubject (CCombo::new, (c, items) -> ((CCombo) c).setItems (items), (c, i) -> ((CCombo) c).select (i),
			(c, a) -> ((CCombo) c).setItem ((Integer) a [0], (String) a [1]),
			c -> "count=" + ((CCombo) c).getItemCount () + " sel=" + ((CCombo) c).getSelectionIndex () + " text=" + ((CCombo) c).getText ());
	}

	@Override
	void create (Shell shell, int style) {
		combo = factory.apply (shell, style);
	}

	@Override
	Widget widget () {
		return combo;
	}

	@Override
	void show (int [] order) {
		setItems.accept (combo, texts (order));
	}

	@Override
	void scrollTo (int row) {
		selectIndex.accept (combo, row);
	}

	@Override
	void select (int start, int end) {
		selectIndex.accept (combo, start);
	}

	@Override
	void update (int row, String text) {
		setItem.accept (combo, new Object [] {row, text});
	}

	@Override
	String state () {
		return combo.isDisposed () ? "disposed" : read.apply (combo);
	}

	@Override
	List<Widget> items () {
		return new ArrayList<> ();
	}
}
