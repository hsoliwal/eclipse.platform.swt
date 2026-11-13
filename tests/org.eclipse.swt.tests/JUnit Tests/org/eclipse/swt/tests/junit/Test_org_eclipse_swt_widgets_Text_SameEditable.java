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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every-widget D leaf of the win32 Text: setEditable with the value in use leaves the control alone (before:
 * EM_SETREADONLY invalidated it on every call, as a form re-applying its enablement does); a new value still repaints
 * and changes getEditable.
 */
public class Test_org_eclipse_swt_widgets_Text_SameEditable {

	private Display display;
	private Shell shell;
	private Text text;

	@BeforeEach
	public void setUp() {
		assumeTrue("win32".equals(SWT.getPlatform()), "the invalidation is in the win32 Text");
		display = Display.getDefault();
		shell = new Shell(display);
		shell.setSize(320, 160);
		text = new Text(shell, SWT.BORDER);
		text.setBounds(10, 10, 240, 28);
		text.setText("some text");
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
	public void test_theEditableFlagInUseLeavesTheControlAlone() throws Exception {
		assertTrue(text.getEditable());
		assertFalse(needsPaint(), "the text is painted");
		text.setEditable(true);
		assertFalse(needsPaint(), "the editable flag in use invalidates nothing");

		text.setEditable(false);
		assertTrue(needsPaint(), "a new editable flag repaints");
		assertEquals(false, text.getEditable());
		assertTrue((text.getStyle() & SWT.READ_ONLY) != 0);
		spin();
		text.setEditable(false);
		assertFalse(needsPaint(), "the editable flag in use invalidates nothing");
		text.setEditable(true);
		assertTrue(text.getEditable());
		assertTrue((text.getStyle() & SWT.READ_ONLY) == 0);
	}

	/* whether Windows holds an update region for the control */
	private boolean needsPaint() throws Exception {
		Field field = Control.class.getDeclaredField("handle");
		field.setAccessible(true);
		long handle = field.getLong(text);
		Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
		Class<?> rect = Class.forName("org.eclipse.swt.internal.win32.RECT");
		Method getUpdateRect = os.getMethod("GetUpdateRect", long.class, rect, boolean.class);
		return (Boolean) getUpdateRect.invoke(null, handle, null, false);
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
