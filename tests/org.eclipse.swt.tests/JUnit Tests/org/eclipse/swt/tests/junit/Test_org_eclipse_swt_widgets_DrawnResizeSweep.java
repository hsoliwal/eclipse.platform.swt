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
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Brief rule 24, viewport deltas (resize, layout): in a drawn mode a resize is a delta of the viewport. A Tree, a Table
 * (header and columns) and a List of 10^4 rows go through 50 sizes (25 growing, 25 shrinking, both axes); after each
 * step the paints are drained. Per step the plane's frame log gives the full repaints of the rows area and the back
 * buffer its re-creations. Gate: no full repaint after the first step. Reported (log and, with
 * <code>-Dswt.drawn.resize=&lt;tsv&gt;</code>, one TSV row per widget): full repaints after the first step, back buffer
 * re-creations, frames, pixels painted and the frame time p50/p99 of the sweep.
 */
public class Test_org_eclipse_swt_widgets_DrawnResizeSweep {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_DrawnResizeSweep.class.getName ());
	static final int ROWS = 10_000, STEPS = 50;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (display.getBounds ().x, display.getBounds ().y, 700, 700);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	static Object plane (Control control) throws ReflectiveOperationException {
		Field plane = control.getClass ().getDeclaredField ("drawnPlane");
		plane.setAccessible (true);
		return plane.get (control);
	}

	static int creations (Object plane) throws ReflectiveOperationException {
		Field buffer = Class.forName ("org.eclipse.swt.widgets.DrawnPlane").getDeclaredField ("buffer");
		buffer.setAccessible (true);
		return ((BackBuffer) buffer.get (plane)).creations;
	}

	void sweep (Control control) throws Exception {
		Assumptions.assumeTrue (plane (control) != null, "a drawn mode (custom, m3, gpu) only");
		control.setBounds (5, 5, 300, 300);
		shell.open ();
		ViewportDifferential.settle (display, 300);
		control.update ();
		/* the plane in effect once the widget is shown: a widget that makes its handle again installs a new plane */
		Object plane = plane (control);
		FrameLog log = Test_org_eclipse_swt_widgets_DrawnDeltaRepaint.log (control);
		long framesStart = log.frames (), pixelsStart = log.paintedPixels (), drawnStart = log.rowsDrawn (), keptStart = log.rowsKept ();
		int creationsStart = creations (plane);
		long [] full = new long [STEPS];
		int [] made = new int [STEPS];
		for (int step = 0; step < STEPS; step++) {
			int i = step < STEPS / 2 ? step : STEPS - 1 - step;
			long fullBefore = log.fullRepaints ();
			int creationsBefore = creations (plane);
			control.setSize (300 + 13 * i, 300 + 9 * i);
			control.update ();
			ViewportDifferential.settle (display, 10);
			control.update ();
			full [step] = log.fullRepaints () - fullBefore;
			made [step] = creations (plane) - creationsBefore;
			if (plane (control) != plane) fail ("step " + step + ": the widget installed a new drawn plane");
		}
		/* the rows kept or widened over the sweep are the rows the plane would draw */
		Test_org_eclipse_swt_widgets_DrawnHiddenViewport.keptRowsAreTheDrawnRows (display, control);
		long fullAfterFirst = Arrays.stream (full, 1, STEPS).sum ();
		int creations = creations (plane) - creationsStart;
		String mode = ViewportDifferential.reference (control.getClass ().getSimpleName ());
		String row = String.format (Locale.ROOT, "%s\t%s\tsteps %d\tfull repaints after the first step %d\tback buffer re-creations %d\tframes %d\tpixels %d\trows drawn %d\tkept %d\tframe p50 %.3f ms\tp99 %.3f ms\tfull per step %s",
			control.getClass ().getSimpleName (), mode, STEPS, fullAfterFirst, creations, log.frames () - framesStart, log.paintedPixels () - pixelsStart,
			log.rowsDrawn () - drawnStart, log.rowsKept () - keptStart,
			log.frameTime (50) / 1e6, log.frameTime (99) / 1e6, Arrays.toString (full));
		LOG.info ("drawn resize sweep: " + row);
		String output = System.getProperty ("swt.drawn.resize");
		if (output != null) Files.write (Paths.get (output), List.of (row), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		assertEquals (0, fullAfterFirst, "rule 24: resize steps repainted all the rows: " + row);
	}

	@Test
	public void treeResizeIsADelta () throws Exception {
		Tree tree = new Tree (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER);
		for (int i = 0; i < ROWS; i++) new TreeItem (tree, SWT.NONE).setText ("row " + i);
		sweep (tree);
	}

	@Test
	public void tableResizeIsADelta () throws Exception {
		Table table = new Table (shell, SWT.CHECK | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		for (int c = 0; c < 3; c++) new TableColumn (table, SWT.NONE).setWidth (c == 2 ? 180 : 100);
		for (int i = 0; i < ROWS; i++) new TableItem (table, SWT.NONE).setText (new String [] {"row " + i, "v" + i % 97, "a longer cell " + i});
		sweep (table);
	}

	@Test
	public void listResizeIsADelta () throws Exception {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		String [] items = new String [ROWS];
		Arrays.setAll (items, i -> "row " + i);
		list.setItems (items);
		sweep (list);
	}
}
