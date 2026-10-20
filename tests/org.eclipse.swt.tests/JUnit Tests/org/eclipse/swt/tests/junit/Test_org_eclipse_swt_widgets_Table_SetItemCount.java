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

import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * eclipse.platform.swt#208, "1.3 second UI freezes in Table.setItemCount": the refresh of a viewer whose input
 * changes is <code>setItemCount (0)</code> and then <code>setItemCount (n)</code>
 * (<code>AbstractTableViewer.internalVirtualRefreshAll</code>, <code>TableViewer.doSetItemCount</code>). Each of these
 * calls must stay under {@link #BUDGET_MILLIS} for a million rows, plain or <code>SWT.VIRTUAL</code>.
 * <p>
 * The VIRTUAL test runs in every mode and on any SWT; the plain test runs where the plain Table is the win32 viewport.
 * A Table that makes and deletes its rows one by one (the stock plain Table) would block a test for minutes at a million
 * rows, so each test first times the same calls on {@link #PILOT} rows and fails at once when they project past ten
 * budgets (a linear projection, so the real cost is higher): run against the official jar, the plain test fails at the
 * pilot (evidence/table_setitemcount-swt208-tt20261020b-win11-hidden-run.txt). On win32 the Table viewport
 * (docs/m3/TABLE_MEDIATED_VIEWPORT.md, leaf T-Table-O1) passes. Evidence: docs/m3/table-viewport/evidence/SetItemCountProbe.java.txt.
 * </p>
 */
public class Test_org_eclipse_swt_widgets_Table_SetItemCount {
	static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Table_SetItemCount.class.getName ());
	static final int MILLION = 1_000_000, PILOT = 20_000;
	/** Budget of one setItemCount call of a million rows: well under the 1.3 s freeze of the issue. */
	static final long BUDGET_MILLIS = 250;

	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 320, 420);
		shell.open ();
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	private Table table (int style) {
		Table table = new Table (shell, style | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		if ((style & SWT.VIRTUAL) != 0) table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText ("row " + event.index));
		return table;
	}

	private static long millis (Runnable operation) {
		long t0 = System.nanoTime ();
		operation.run ();
		return (System.nanoTime () - t0) / 1_000_000;
	}

	/* the pilot: grow to PILOT rows, clear, grow again; a linear projection to a million rows must stay within ten budgets (after a warm-up of a tenth of the rows: class loading and compilation are not timed) */
	private void pilot (Table table, String name) {
		table.setItemCount (PILOT / 10);
		table.setItemCount (0);
		long grow = millis (() -> table.setItemCount (PILOT));
		long clear = millis (() -> table.setItemCount (0));
		long reload = millis (() -> table.setItemCount (PILOT));
		long scale = MILLION / PILOT, limit = 10 * BUDGET_MILLIS;
		LOG.info (() -> name + " pilot of " + PILOT + " rows: grow " + grow + " ms, clear " + clear + " ms, reload " + reload + " ms");
		assertTrue (grow * scale <= limit, name + ": setItemCount (" + PILOT + ") took " + grow + " ms, " + grow * scale + " ms projected for " + MILLION + " rows (budget " + BUDGET_MILLIS + " ms)");
		assertTrue (clear * scale <= limit, name + ": setItemCount (0) of " + PILOT + " rows took " + clear + " ms, " + clear * scale + " ms projected for " + MILLION + " rows (budget " + BUDGET_MILLIS + " ms)");
		assertTrue (reload * scale <= limit, name + ": setItemCount (" + PILOT + ") after the clear took " + reload + " ms, " + reload * scale + " ms projected for " + MILLION + " rows (budget " + BUDGET_MILLIS + " ms)");
		table.setItemCount (0);
	}

	/* the refresh of the issue at a million rows: grow, clear and reload, each under the budget, with the rows of the reload in place */
	private void refreshAMillionRows (int style, String name) {
		Table table = table (style);
		pilot (table, name);
		long grow = millis (() -> table.setItemCount (MILLION));
		assertEquals (MILLION, table.getItemCount ());
		long clear = millis (() -> table.setItemCount (0));
		assertEquals (0, table.getItemCount ());
		long reload = millis (() -> table.setItemCount (MILLION));
		assertEquals (MILLION, table.getItemCount ());
		assertEquals (MILLION - 1, table.indexOf (table.getItem (MILLION - 1)), "the last row answers its item");
		LOG.info (() -> name + " of " + MILLION + " rows: grow " + grow + " ms, clear " + clear + " ms, reload " + reload + " ms");
		assertTrue (grow <= BUDGET_MILLIS, name + ": setItemCount (" + MILLION + ") took " + grow + " ms (budget " + BUDGET_MILLIS + " ms)");
		assertTrue (clear <= BUDGET_MILLIS, name + ": setItemCount (0) of " + MILLION + " rows took " + clear + " ms (budget " + BUDGET_MILLIS + " ms)");
		assertTrue (reload <= BUDGET_MILLIS, name + ": setItemCount (" + MILLION + ") after the clear took " + reload + " ms (budget " + BUDGET_MILLIS + " ms)");
	}

	@Test
	@Timeout (120)
	public void refreshOfAMillionPlainRowsStaysUnderBudget () {
		/* the stock plain Table (the official jar, the original mode) makes and deletes its rows one by one: minutes at a million rows (evidence) */
		Assumptions.assumeTrue (Test_org_eclipse_swt_widgets_Table_Splice.splices (new Table (shell, SWT.MULTI)), "the plain Table is the win32 viewport's");
		refreshAMillionRows (SWT.MULTI, "plain Table");
	}

	@Test
	@Timeout (120)
	public void refreshOfAMillionVirtualRowsStaysUnderBudget () {
		refreshAMillionRows (SWT.MULTI | SWT.VIRTUAL, "VIRTUAL Table");
	}
}
