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
 * com.m3.collections.M3ListModelLazyTest (same author; the SWT copy of the viewport collections).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/** Direct tests of the flat list model: lazy widths and slots, argument errors, search and prefetch epochs. */
public class M3ListModelLazyTest {
	@Test
	public void lazySlotsAndWidths () {
		M3ListModel model = new M3ListModel (String::length, true);
		model.add ("abc");
		model.add ("a", 0);
		assertEquals (2, model.getPendingWidthCount (), "lazy add measures nothing");
		assertEquals (0, model.getMeasuredWidestWidth ());
		model.setItem (1, "abcdef");
		assertEquals (6, model.getWidestWidth (), "measures every pending slot");
		assertEquals (0, model.getPendingWidthCount ());
		model.setItemCount (5);
		assertEquals (5, model.getItemCount ());
		assertFalse (model.isMaterialized (4));
		assertTrue (model.isMaterialized (0));
		assertEquals (0, model.measureItem (4), "an unmaterialized slot measures as empty text");
		model.setItemCount (5);
		long epoch = model.prefetchEpoch ();
		model.clear (0);
		assertFalse (model.isMaterialized (0));
		assertEquals (epoch + 1, model.prefetchEpoch ());
		model.clearAll ();
		assertFalse (model.isMaterialized (1));
		model.invalidateWidths ();
		assertEquals (5, model.getPendingWidthCount ());
		model.setItemCount (2);
		assertEquals (2, model.getItemCount ());
		model.setItems (new String [] {"x", "yy"});
		assertEquals (2, model.getPendingWidthCount ());
		assertThrows (IllegalArgumentException.class, () -> model.setItemCount (-1));
		M3ListModel empty = new M3ListModel (String::length, true);
		long before = empty.prefetchEpoch ();
		empty.clearAll ();
		assertEquals (before, empty.prefetchEpoch (), "clearing an empty model is no structural change");
	}

	@Test
	public void eagerModelRejectsLazyOperations () {
		M3ListModel model = new M3ListModel (String::length);
		model.add ("a");
		assertThrows (IllegalStateException.class, () -> model.setItemCount (3));
		assertThrows (IllegalStateException.class, () -> model.clear (0));
		assertThrows (IllegalStateException.class, model::clearAll);
		assertThrows (IllegalStateException.class, model::invalidateWidths);
		assertThrows (IllegalArgumentException.class, () -> model.setItem (0, null));
		assertThrows (IllegalArgumentException.class, () -> model.setItems (null));
		assertThrows (IllegalArgumentException.class, () -> model.setItems (new String [] {"a", null}));
		assertTrue (model.isMaterialized (0));
	}

	@Test
	public void searchAndSelectionBounds () {
		M3ListModel model = new M3ListModel (String::length);
		model.setItems (new String [] {"a", "b", "a", "c"});
		assertEquals (0, model.indexOf ("a"));
		assertEquals (2, model.indexOf ("a", 1));
		assertEquals (-1, model.indexOf ("z"));
		assertEquals (-1, model.indexOf ("a", -1));
		assertEquals (-1, model.indexOf ("a", 4));
		assertThrows (IllegalArgumentException.class, () -> model.indexOf (null, 0));
		model.select (1);
		assertTrue (model.isSelected (1));
		assertFalse (model.isSelected (2));
		assertFalse (model.isSelected (-1));
		assertFalse (model.isSelected (4));
	}

	@Test
	public void prefetchEpochExhaustion () throws ReflectiveOperationException {
		M3ListModel model = new M3ListModel (String::length);
		Field epoch = M3ListModel.class.getDeclaredField ("prefetchEpoch");
		epoch.setAccessible (true);
		epoch.setLong (model, Long.MAX_VALUE);
		assertThrows (IllegalStateException.class, () -> model.add ("a"));
		assertEquals (0, model.getItemCount (), "an exhausted epoch changes nothing");
	}
}
