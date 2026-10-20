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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 24, "all delta based view", in the drawn modes: a model delta repaints only the rows it damages. Each
 * Tree, Table and List delta below (text, colour and check of a visible row; insert, remove and count changes outside
 * the visible rows) is applied to a widget of 10^4 rows and the paints it causes are drained; the plane's frame log
 * must count no full repaint of the rows area for it. Each delta's damaged pixels are logged, and every delta that
 * repainted all the rows is listed in the failure.
 */
public class Test_org_eclipse_swt_widgets_DrawnDeltaRepaint {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_DrawnDeltaRepaint.class.getName ());
	static final int ROWS = 10_000;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (display.getBounds ().x, display.getBounds ().y, 360, 420);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	static FrameLog log (Control control) {
		try {
			Field plane = control.getClass ().getDeclaredField ("drawnPlane");
			plane.setAccessible (true);
			Object drawn = plane.get (control);
			if (drawn == null) return null;
			Field frames = drawn.getClass ().getSuperclass ().getDeclaredField ("frames");
			frames.setAccessible (true);
			return (FrameLog) frames.get (drawn);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException (e);
		}
	}

	/* applies each delta, drains its paints, and answers the deltas that repainted all the rows */
	List<String> deltas (Control control, Map<String, Runnable> deltas) {
		Assumptions.assumeTrue (log (control) != null, "a drawn mode (custom, m3, gpu) only");
		shell.open ();
		ViewportDifferential.settle (display, 300);
		control.update ();
		List<String> full = new ArrayList<> ();
		for (Map.Entry<String, Runnable> delta : deltas.entrySet ()) {
			/* the plane in effect now: a widget that makes its handle again installs a new plane */
			FrameLog log = log (control);
			long fullBefore = log.fullRepaints (), pixelsBefore = log.paintedPixels (), framesBefore = log.frames (), drawnBefore = log.rowsDrawn (), keptBefore = log.rowsKept ();
			delta.getValue ().run ();
			control.update ();
			ViewportDifferential.settle (display, 60);
			control.update ();
			if (log (control) != log) LOG.warning (control.getClass ().getSimpleName () + " " + delta.getKey () + ": the widget installed a new drawn plane");
			long fulls = log.fullRepaints () - fullBefore, pixels = log.paintedPixels () - pixelsBefore, frames = log.frames () - framesBefore;
			String line = control.getClass ().getSimpleName () + " " + delta.getKey () + ": " + frames + " frames, " + pixels + " pixels, " + (log.rowsDrawn () - drawnBefore) + " rows drawn, " + (log.rowsKept () - keptBefore) + " kept, " + fulls + " full";
			LOG.info (line);
			if (fulls > 0) full.add (line);
		}
		keptRowsAreTheDrawnRows (control);
		return full;
	}

	/*
	 * The rows the plane kept from its retained frame are the rows it would draw: the capture after the deltas equals the
	 * capture after the plane forgot every row (a font set again) and drew them all.
	 */
	void keptRowsAreTheDrawnRows (Control control) {
		ImageData kept = ViewportDifferential.render (control);
		control.setFont (control.getFont ());
		control.redraw ();
		control.update ();
		ViewportDifferential.settle (display, 60);
		ImageData drawn = ViewportDifferential.render (control);
		String name = control.getClass ().getSimpleName () + "-" + ViewportDifferential.reference (control.getClass ().getSimpleName ());
		if (!Arrays.equals (kept.data, drawn.data)) {
			ViewportDifferential.save ("delta-kept-" + name, kept);
			ViewportDifferential.save ("delta-drawn-" + name, drawn);
			fail ("rule 24: a kept row differs from the drawn row (delta-kept-/delta-drawn-" + name + ".png)");
		}
	}

	@Test
	public void treeDeltasRepaintOnlyTheirRows () {
		Tree tree = new Tree (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER);
		tree.setBounds (5, 5, 340, 380);
		for (int i = 0; i < ROWS; i++) new TreeItem (tree, SWT.NONE).setText ("row " + i);
		Map<String, Runnable> deltas = new LinkedHashMap<> ();
		deltas.put ("setText visible", () -> tree.getItem (3).setText ("changed"));
		deltas.put ("setForeground visible", () -> tree.getItem (4).setForeground (display.getSystemColor (SWT.COLOR_RED)));
		deltas.put ("setChecked visible", () -> tree.getItem (5).setChecked (true));
		deltas.put ("insert below the window", () -> new TreeItem (tree, SWT.NONE, 5000).setText ("inserted"));
		deltas.put ("dispose below the window", () -> tree.getItem (9000).dispose ());
		deltas.put ("add child to a collapsed row", () -> new TreeItem (tree.getItem (6), SWT.NONE).setText ("child"));
		deltas.put ("setItemCount growth", () -> tree.setItemCount (tree.getItemCount () + 10));
		List<String> full = deltas (tree, deltas);
		assertTrue (full.isEmpty (), "rule 24: deltas repainted all the rows: " + full);
	}

	@Test
	public void tableDeltasRepaintOnlyTheirRows () {
		Table table = new Table (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 340, 380);
		for (int i = 0; i < ROWS; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		Map<String, Runnable> deltas = new LinkedHashMap<> ();
		deltas.put ("setText visible", () -> table.getItem (3).setText ("changed"));
		deltas.put ("setForeground visible", () -> table.getItem (4).setForeground (display.getSystemColor (SWT.COLOR_RED)));
		deltas.put ("setChecked visible", () -> table.getItem (5).setChecked (true));
		deltas.put ("insert below the window", () -> new TableItem (table, SWT.NONE, 5000).setText ("inserted"));
		deltas.put ("remove below the window", () -> table.remove (9000));
		deltas.put ("setItemCount growth", () -> table.setItemCount (table.getItemCount () + 10));
		List<String> full = deltas (table, deltas);
		assertTrue (full.isEmpty (), "rule 24: deltas repainted all the rows: " + full);
	}

	@Test
	public void listDeltasRepaintOnlyTheirRows () {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		list.setBounds (5, 5, 340, 380);
		String [] items = new String [ROWS];
		Arrays.setAll (items, i -> "row " + i);
		list.setItems (items);
		Map<String, Runnable> deltas = new LinkedHashMap<> ();
		deltas.put ("setItem visible", () -> list.setItem (3, "changed"));
		deltas.put ("add below the window", () -> list.add ("inserted", 5000));
		deltas.put ("remove below the window", () -> list.remove (9000));
		deltas.put ("add at the end", () -> list.add ("last"));
		List<String> full = deltas (list, deltas);
		assertTrue (full.isEmpty (), "rule 24: deltas repainted all the rows: " + full);
	}
}
