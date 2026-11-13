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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every-widget D leaf of StyledText: setTabs and setTabStops with the value in use reset no line cache and repaint
 * nothing (before: a cache reset over the whole document and a full repaint, as an editor re-applying its preferences
 * does); a new value repaints to the pixels of a full repaint.
 */
public class Test_org_eclipse_swt_custom_StyledText_SameTabs {

	private Display display;
	private Shell shell;
	private StyledText text;
	private int paints;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
		shell.setSize(420, 320);
		text = new StyledText(shell, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		text.setBounds(10, 10, 380, 260);
		StringBuilder content = new StringBuilder();
		for (int i = 0; i < 5000; i++) {
			content.append("line\t").append(i).append("\tcolumn\tend\n");
		}
		text.setText(content.toString());
		text.addListener(SWT.Paint, e -> paints++);
		shell.open();
		spin();
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	@Test
	public void test_theTabsInUseRepaintNothing() {
		paints = 0;
		text.setTabs(text.getTabs());
		spin();
		assertEquals(0, paints, "the tab width in use repaints nothing");

		text.setTabs(8);
		spin();
		assertSameAsFullRepaint("new tab width");

		text.setTabStops(new int[] { 40, 90 });
		spin();
		assertSameAsFullRepaint("new tab stops");
		paints = 0;
		text.setTabStops(new int[] { 40, 90 });
		spin();
		assertEquals(0, paints, "the tab stops in use repaint nothing");
		assertArrayEquals(new int[] { 40, 90 }, text.getTabStops());

		text.setTabStops(null);
		spin();
		paints = 0;
		text.setTabStops(null);
		spin();
		assertEquals(0, paints, "no tab stops again repaints nothing");
		assertEquals(8, text.getTabs());
	}

	private void assertSameAsFullRepaint(String step) {
		int[] incremental = pixels();
		text.redraw();
		text.update();
		spin();
		assertArrayEquals(pixels(), incremental, step + ": the pixels equal a full repaint");
		assertTrue(text.getLineCount() > 1000);
	}

	private int[] pixels() {
		Rectangle size = text.getBounds();
		Image shot = new Image(display, size.width, size.height);
		GC gc = new GC(text);
		gc.copyArea(shot, 0, 0);
		gc.dispose();
		ImageData data = shot.getImageData();
		shot.dispose();
		int[] all = new int[size.width * size.height];
		for (int y = 0; y < size.height; y++) {
			data.getPixels(0, y, size.width, all, y * size.width);
		}
		return all;
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
