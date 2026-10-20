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

import java.awt.*;
import java.beans.*;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.event.*;

/**
 * Row geometry of a <code>JList</code> without a renderer call per row. Swing measures every row with the cell
 * renderer on each layout of a list whose cells have no fixed size (<code>BasicListUI.updateLayoutState</code>), so
 * adding one row to a million-row list renders a million cells. This class gives the list a fixed cell height (one
 * text line, as SWT rows) and a fixed cell width: the widest row, kept from the model's change events.
 * <ul>
 * <li>Widths live in a packed int lane parallel to the rows, measured with the font metrics only for the rows a
 * change event names (catalogue: delta application).</li>
 * <li>The widest row comes from a histogram of widths: adding is O(1); removing the last row of the widest width
 * walks down to the next used width (catalogue: counting histogram maximum).</li>
 * <li>A new font, renderer or look and feel measures every row again.</li>
 * </ul>
 * Runs on the thread that changes the model (the SWT thread), as the original model events do.
 * <p>
 * IMPORTANT: This class is <em>not</em> part of the SWT public API.
 * </p>
 */
public final class M3ListRows implements ListDataListener, PropertyChangeListener {
	private final JList<?> list;
	private ListModel<?> model;
	/** Width of each row's text, in pixels. */
	private int [] widths = new int [16];
	private int size;
	/** counts [w]: the rows whose text is w pixels wide. */
	private int [] counts = new int [256];
	private int widest;
	/** What the renderer adds to the text width (insets, gaps), and the row height, for the current font. */
	private int extra, height;
	private FontMetrics metrics;
	/** Cell sizes changed while the list was hidden. */
	private boolean pending;

	private M3ListRows (JList<?> list) {
		this.list = list;
	}

	/**
	 * Gives a list fixed cell sizes kept by this class.
	 *
	 * @param list the list
	 * @return the geometry of the list
	 */
	public static M3ListRows install (JList<?> list) {
		M3ListRows rows = new M3ListRows (list);
		list.addPropertyChangeListener (rows);
		rows.attach (list.getModel ());
		return rows;
	}

	private void attach (ListModel<?> next) {
		if (model != null) model.removeListDataListener (this);
		model = next;
		model.addListDataListener (this);
		measureAll ();
	}

	/** Measures the font and every row again (a new font, renderer, look and feel or model). */
	public void measureAll () {
		Font font = list.getFont ();
		JLabel probe = new DefaultListCellRenderer ();
		probe.setFont (font);
		probe.setText ("Xg");
		metrics = probe.getFontMetrics (font);
		Dimension sample = probe.getPreferredSize ();
		extra = sample.width - width ("Xg");
		height = sample.height;
		size = 0;
		Arrays.fill (counts, 0);
		widest = 0;
		int count = model.getSize ();
		open (0, count);
		for (int i = 0; i < count; i++) set (i, width (model.getElementAt (i)));
		apply ();
	}

	/** The width of the widest row, without the renderer's extra. */
	public int widest () {
		return widest;
	}

	@Override
	public void intervalAdded (ListDataEvent e) {
		int from = Math.min (e.getIndex0 (), e.getIndex1 ()), to = Math.max (e.getIndex0 (), e.getIndex1 ());
		open (from, to - from + 1);
		for (int i = from; i <= to; i++) set (i, width (model.getElementAt (i)));
		apply ();
	}

	@Override
	public void intervalRemoved (ListDataEvent e) {
		int from = Math.min (e.getIndex0 (), e.getIndex1 ()), to = Math.min (Math.max (e.getIndex0 (), e.getIndex1 ()), size - 1);
		if (from < 0 || from > to) return;
		for (int i = from; i <= to; i++) uncount (widths [i]);
		System.arraycopy (widths, to + 1, widths, from, size - to - 1);
		size -= to - from + 1;
		apply ();
	}

	@Override
	public void contentsChanged (ListDataEvent e) {
		if (e.getIndex0 () < 0 || model.getSize () != size) {
			measureAll ();
			return;
		}
		int from = Math.min (e.getIndex0 (), e.getIndex1 ()), to = Math.min (Math.max (e.getIndex0 (), e.getIndex1 ()), size - 1);
		for (int i = from; i <= to; i++) {
			uncount (widths [i]);
			set (i, width (model.getElementAt (i)));
		}
		apply ();
	}

	@Override
	public void propertyChange (PropertyChangeEvent e) {
		switch (e.getPropertyName ()) {
			case "model": attach (list.getModel ()); break;
			case "font": case "UI": case "cellRenderer": measureAll (); break;
			default: break;
		}
	}

	private int width (Object value) {
		String text = value == null ? "" : value.toString ();
		return text.isEmpty () ? 0 : SwingUtilities.computeStringWidth (metrics, text);
	}

	/** Makes room for <code>count</code> rows at <code>index</code> (their widths are set next). */
	private void open (int index, int count) {
		if (size + count > widths.length) widths = Arrays.copyOf (widths, Math.max (size + count, widths.length * 2));
		System.arraycopy (widths, index, widths, index + count, size - index);
		size += count;
	}

	private void set (int index, int width) {
		widths [index] = width;
		if (width >= counts.length) counts = Arrays.copyOf (counts, Math.max (width + 1, counts.length * 2));
		counts [width]++;
		if (width > widest) widest = width;
	}

	private void uncount (int width) {
		counts [width]--;
		while (widest > 0 && counts [widest] == 0) widest--;
	}

	/**
	 * Gives the list the cell sizes, when they changed (each change makes Swing lay the list out again). While the
	 * list is displayable but not showing (brief rule 24, hidden viewports), the sizes wait for {@link #shown()}.
	 */
	private void apply () {
		if (list.isDisplayable () && !list.isShowing ()) {
			pending = true;
			return;
		}
		pending = false;
		int cellWidth = widest + extra;
		if (list.getFixedCellHeight () != height) list.setFixedCellHeight (height);
		if (list.getFixedCellWidth () != cellWidth) list.setFixedCellWidth (cellWidth);
	}

	/** The list shows: the cell sizes that changed while it was hidden reach Swing. */
	public void shown () {
		if (pending) apply ();
	}
}
