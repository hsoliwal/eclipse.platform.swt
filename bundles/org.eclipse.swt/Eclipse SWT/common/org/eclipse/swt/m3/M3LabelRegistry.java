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
package org.eclipse.swt.m3;

import java.util.*;
import java.util.List;
import java.util.function.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;

/*
 * The registered labels of one display (M3Language.register): three parallel lanes (widget, message key id, kind) and one
 * interned key table; no object per registration. Disposed widgets are dropped when the lanes grow and on every relabel.
 */
//FLLDCIM: DOOP — lanes, not a map per widget; a relabel is one pass over the lanes
final class M3LabelRegistry {
	private Widget [] widgets = new Widget [16];
	private int [] keys = new int [16];
	private byte [] kinds = new byte [16];
	private int count;
	private final List<String> keyTable = new ArrayList<> (), fallbackTable = new ArrayList<> ();
	private final Map<String, Integer> keyIds = new HashMap<> ();

	static M3LabelRegistry of (Display display, boolean create) {
		String name = M3LabelRegistry.class.getName ();
		M3LabelRegistry registry = (M3LabelRegistry) display.getData (name);
		if (registry == null && create) display.setData (name, registry = new M3LabelRegistry ());
		return registry;
	}

	/* the fallback of a key is the text when no bundle has the key (the latest registration's fallback wins) */
	void add (Widget widget, byte kind, String key, String fallback) {
		int id = keyIds.computeIfAbsent (key, k -> { keyTable.add (k); fallbackTable.add (k); return keyTable.size () - 1; });
		fallbackTable.set (id, fallback);
		int at = IntStream.iterate (count - 1, i -> i >= 0, i -> i - 1).filter (i -> widgets [i] == widget && kinds [i] == kind).findFirst ().orElse (-1);
		if (at < 0) at = append (widget, kind);
		keys [at] = id;
	}

	private int append (Widget widget, byte kind) {
		if (count == widgets.length) compact (w -> w.isDisposed ());
		if (count == widgets.length) grow ();
		widgets [count] = widget;
		kinds [count] = kind;
		return count++;
	}

	private void grow () {
		widgets = Arrays.copyOf (widgets, count * 2);
		keys = Arrays.copyOf (keys, count * 2);
		kinds = Arrays.copyOf (kinds, count * 2);
	}

	void remove (Widget widget) {
		compact (w -> w == widget);
	}

	/* keeps the entries whose widget does not match, in order */
	private void compact (Predicate<Widget> drop) {
		int kept = 0;
		for (int i = 0; i < count; i++) {
			if (drop.test (widgets [i])) continue;
			widgets [kept] = widgets [i];
			keys [kept] = keys [i];
			kinds [kept++] = kinds [i];
		}
		Arrays.fill (widgets, kept, count, null);
		count = kept;
	}

	int count () {
		compact (w -> w.isDisposed ());
		return count;
	}

	/* sets every registered label of the widgets that follow the language from the lookup; answers the number set */
	int relabel (Function<String, Optional<String>> lookup, Predicate<Widget> follows) {
		compact (w -> w.isDisposed ());
		String [] texts = IntStream.range (0, keyTable.size ()).mapToObj (id -> lookup.apply (keyTable.get (id)).orElse (fallbackTable.get (id))).toArray (String []::new);
		int [] set = IntStream.range (0, count).filter (i -> follows.test (widgets [i])).toArray ();
		Arrays.stream (set).forEach (i -> apply (widgets [i], kinds [i], texts [keys [i]]));
		Arrays.stream (set).forEach (i -> layout (widgets [i]));
		return set.length;
	}

	/* the text, tooltip or message of a widget; refused for a widget that has none of that kind */
	static void apply (Widget widget, byte kind, String text) {
		switch (kind) {
			case M3Language.TEXT -> text (widget, text);
			case M3Language.TOOLTIP -> toolTip (widget, text);
			case M3Language.MESSAGE -> message (widget, text);
			default -> SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		}
	}

	private static void text (Widget widget, String text) {
		switch (widget) {
			case Item item -> item.setText (text);
			case Decorations shell -> shell.setText (text);
			case Button button -> button.setText (text);
			case Label label -> label.setText (text);
			case Link link -> link.setText (text);
			case Group group -> group.setText (text);
			case CLabel label -> label.setText (text);
			default -> SWT.error (SWT.ERROR_INVALID_ARGUMENT, null, " no text label: " + widget);
		}
	}

	private static void toolTip (Widget widget, String text) {
		switch (widget) {
			case Control control -> control.setToolTipText (text);
			case ToolItem item -> item.setToolTipText (text);
			case TabItem item -> item.setToolTipText (text);
			case CTabItem item -> item.setToolTipText (text);
			case TableColumn column -> column.setToolTipText (text);
			case TreeColumn column -> column.setToolTipText (text);
			case MenuItem item -> item.setToolTipText (text);
			case TrayItem item -> item.setToolTipText (text);
			default -> SWT.error (SWT.ERROR_INVALID_ARGUMENT, null, " no tooltip: " + widget);
		}
	}

	private static void message (Widget widget, String text) {
		if (widget instanceof Text field) {
			field.setMessage (text);
		} else {
			SWT.error (SWT.ERROR_INVALID_ARGUMENT, null, " no message: " + widget);
		}
	}

	/* a relabelled control or the control of a relabelled tool or tab item lays out again (text widths change) */
	private static void layout (Widget widget) {
		switch (widget) {
			case Control control -> control.requestLayout ();
			case ToolItem item -> item.getParent ().requestLayout ();
			case CTabItem item -> item.getParent ().requestLayout ();
			case TabItem item -> item.getParent ().requestLayout ();
			default -> { /* menus and columns size themselves */ }
		}
	}
}
