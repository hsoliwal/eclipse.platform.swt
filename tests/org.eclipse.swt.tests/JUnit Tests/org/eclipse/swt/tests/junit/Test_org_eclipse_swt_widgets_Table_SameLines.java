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
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every-widget D leaf of the win32 Table: setLinesVisible with the value in use leaves the native header alone
 * (before: it invalidated the whole header on every call, as a CSS engine re-applying a style causes); a new value
 * still repaints the header and changes the lines.
 */
public class Test_org_eclipse_swt_widgets_Table_SameLines {

	private Display display;
	private Shell shell;
	private Table table;

	@BeforeEach
	public void setUp() {
		assumeTrue("win32".equals(SWT.getPlatform()), "the header invalidation is in the win32 Table");
		display = Display.getDefault();
		shell = new Shell(display);
		shell.setSize(420, 300);
		table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION);
		table.setBounds(10, 10, 380, 230);
		table.setHeaderVisible(true);
		for (int c = 0; c < 3; c++) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText("column " + c);
			column.setWidth(110);
		}
		for (int r = 0; r < 8; r++) {
			new TableItem(table, SWT.NONE).setText(new String[] { "a" + r, "b" + r, "c" + r });
		}
		shell.open();
		spin();
	}

	@AfterEach
	public void tearDown() {
		if (shell != null) {
			shell.dispose();
		}
	}

	@Test
	public void test_theLinesFlagInUseLeavesTheHeaderAlone() throws Exception {
		table.setLinesVisible(true);
		spin();
		assertFalse(headerNeedsPaint(), "the header is painted");

		table.setLinesVisible(true);
		assertFalse(headerNeedsPaint(), "the lines flag in use invalidates no header");
		assertTrue(table.getLinesVisible());

		table.setLinesVisible(false);
		assertTrue(headerNeedsPaint(), "a new lines flag repaints the header");
		assertEquals(false, table.getLinesVisible());
		spin();
		table.setLinesVisible(false);
		assertFalse(headerNeedsPaint(), "the lines flag in use invalidates no header");
	}

	/* whether Windows holds an update region for the native header control */
	private boolean headerNeedsPaint() throws Exception {
		Field field = Table.class.getDeclaredField("hwndHeader");
		field.setAccessible(true);
		long header = field.getLong(table);
		Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
		Class<?> rect = Class.forName("org.eclipse.swt.internal.win32.RECT");
		Method getUpdateRect = os.getMethod("GetUpdateRect", long.class, rect, boolean.class);
		return (Boolean) getUpdateRect.invoke(null, header, null, false);
	}

	private void spin() {
		long end = System.currentTimeMillis() + 200;
		while (System.currentTimeMillis() < end) {
			if (!display.readAndDispatch()) {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}
}
