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
import static org.junit.jupiter.api.Assumptions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real GTK allocation must not turn a projected row boundary into a partial row. */
public class Test_ViewportTableAdjustment {

private Display display;
private Shell shell;
private Table table;
private Class<?> gtk;
private int callbacks;

@BeforeEach
public void setUp () throws Exception {
	assumeTrue ("gtk".equals (SWT.getPlatform ()));
	gtk = Class.forName ("org.eclipse.swt.internal.gtk.GTK");
	assumeFalse (gtk.getField ("GTK4").getBoolean (null));
	display = Display.getDefault ();
	shell = new Shell (display);
	shell.setLayout (new FillLayout ());
	shell.setSize (960, 640);
	String key = "org.eclipse.swt.internal.gtk.virtualTableBoundedView";
	String previous = System.getProperty (key);
	System.setProperty (key, "true");
	try {
		table = new Table (shell, SWT.VIRTUAL | SWT.CHECK | SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER);
	} finally {
		if (previous == null) System.clearProperty (key);
		else System.setProperty (key, previous);
	}
	table.setHeaderVisible (true);
	table.setLinesVisible (true);
	for (int i = 0; i < 3; i++) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		column.setText ("Column " + i);
		column.setWidth (200);
	}
	table.addListener (SWT.SetData, event -> {
		callbacks++;
		TableItem item = (TableItem)event.item;
		item.setText (new String[] {"row " + event.index, "", "payload " + event.index});
		item.setChecked ((event.index & 31) == 0);
	});
	table.setItemCount (2_000_000);
	table.setSelection (new int[] {2, 100_002, 900_002, 1_900_000});
	table.setTopIndex (0);
	shell.open ();
	drain (150);
}

@AfterEach
public void tearDown () {
	if (shell != null && !shell.isDisposed ()) shell.dispose ();
}

private Field field (String name) throws Exception {
	Field field = Table.class.getDeclaredField (name);
	field.setAccessible (true);
	return field;
}

private double adjustment (String owner, String property) throws Exception {
	return (Double)gtk.getMethod ("gtk_adjustment_get_" + property, long.class)
			.invoke (null, field (owner).getLong (table));
}

private void drain (int milliseconds) throws InterruptedException {
	long deadline = System.nanoTime () + milliseconds * 1_000_000L;
	while (System.nanoTime () < deadline) {
		if (!display.readAndDispatch ()) Thread.sleep (1);
	}
}

private void assertAligned (int top) throws Exception {
	int extent = field ("virtualRowExtent").getInt (table);
	assertEquals (top, table.getTopIndex ());
	assertEquals (Math.multiplyExact (top, extent), table.getVerticalBar ().getSelection (),
			"GTK allocation must preserve a requested whole-row logical position");
	assertEquals (0, field ("virtualPixelRemainder").getDouble (table), 0.000001);
	int count = field ("virtualViewCount").getInt (table);
	assertTrue (count >= 0 && count <= 512, "allocation must remain within the native viewport budget");
	assertEquals (Math.max (adjustment ("virtualViewAdjustment", "page_size"), count * (double)extent),
			adjustment ("virtualViewAdjustment", "upper"), 0.000001);
	assertFalse (field ("virtualViewRangeDirty").getBoolean (table), "settled native geometry must accept input");
}

@Test
public void distantRebindingKeepsExactRowBoundariesAndItemIdentity () throws Exception {
	TableItem pinned = table.getItem (1_900_000);
	pinned.setText ("pinned");
	pinned.setChecked (true);
	for (int top : new int[] {900_000, 100_000, 800_000, 250_000, 1_500_000, 0}) {
		table.setTopIndex (top);
		drain (150);
		assertAligned (top);
		assertSame (pinned, table.getItem (1_900_000));
		assertEquals ("pinned", pinned.getText ());
		assertTrue (pinned.getChecked ());
		assertArrayEquals (new int[] {2, 100_002, 900_002, 1_900_000}, table.getSelectionIndices ());
	}
	assertTrue (callbacks < 1_024, "a distant seek must not request the logical prefix");
}

@Test
public void settledPhysicalScrollingRetainsFractionalPixels () throws Exception {
	table.setTopIndex (900_000);
	drain (150);
	assertAligned (900_000);
	long physical = field ("virtualViewAdjustment").getLong (table);
	double origin = adjustment ("virtualViewAdjustment", "value");
	Method setValue = gtk.getMethod ("gtk_adjustment_set_value", long.class, double.class);
	setValue.invoke (null, physical, origin + 3.5);
	drain (150);
	assertEquals (900_000, table.getTopIndex ());
	assertEquals (3.5, field ("virtualPixelRemainder").getDouble (table), 0.000001,
			"valid physical scrolling must preserve the partial-row offset");
	assertEquals (900_000d * field ("virtualRowExtent").getInt (table) + 3.5,
			adjustment ("verticalAdjustment", "value"), 0.000001);
	table.setTopIndex (900_000);
	drain (150);
	assertAligned (900_000);
}

@Test
public void resizeAndShortOrEmptyModelsSettleWithReadyNativeGeometry () throws Exception {
	table.setTopIndex (100_000);
	shell.setSize (520, 400);
	shell.layout (true, true);
	drain (150);
	assertAligned (100_000);
	table.setItemCount (7);
	drain (150);
	assertAligned (0);
	table.setItemCount (0);
	drain (150);
	assertAligned (0);
	table.setItemCount (2_000_000);
	table.setTopIndex (900_000);
	drain (150);
	assertAligned (900_000);
}
}
