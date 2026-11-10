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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Color;
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
 * Every-widget D leaf of CTabFolder: setting the background or foreground colour the folder already uses (as a CSS
 * engine re-applying a style does) repaints nothing; a new colour repaints to the pixels of a full repaint.
 */
public class Test_org_eclipse_swt_custom_CTabFolder_SameColour {

	private Display display;
	private Shell shell;
	private CTabFolder folder;
	private int paints;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
		shell.setSize(400, 200);
		folder = new CTabFolder(shell, SWT.BORDER);
		folder.setBounds(10, 10, 360, 140);
		for (int i = 0; i < 3; i++) {
			new CTabItem(folder, SWT.NONE).setText("Tab " + i);
		}
		folder.setSelection(0);
		folder.addListener(SWT.Paint, e -> paints++);
		shell.open();
		spin();
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	@Test
	public void test_theColourInUseRepaintsNothing() {
		Color red = display.getSystemColor(SWT.COLOR_RED);
		Color blue = display.getSystemColor(SWT.COLOR_BLUE);
		Color green = display.getSystemColor(SWT.COLOR_DARK_GREEN);
		paints = 0;
		folder.setBackground(red);
		folder.setForeground(blue);
		spin();
		assertTrue(paints > 0, "a new background and foreground repaint");
		assertSameAsFullRepaint("new background and foreground");

		paints = 0;
		folder.setBackground(red);
		spin();
		int backgroundPaints = paints;
		paints = 0;
		folder.setForeground(blue);
		spin();
		assertEquals("0/0", backgroundPaints + "/" + paints, "the background / foreground in use repaint nothing");
		assertEquals(red, folder.getBackground());

		paints = 0;
		folder.setForeground(green);
		spin();
		assertTrue(paints > 0, "a new foreground repaints");
		assertEquals(green, folder.getForeground());
		assertSameAsFullRepaint("new foreground");
	}

	private void assertSameAsFullRepaint(String step) {
		int[] incremental = pixels();
		folder.redraw();
		folder.update();
		spin();
		assertTrue(Arrays.equals(incremental, pixels()), step + ": the pixels equal a full repaint");
	}

	private int[] pixels() {
		Rectangle bounds = folder.getBounds();
		Image shot = new Image(display, bounds.width, bounds.height);
		GC gc = new GC(folder);
		gc.copyArea(shot, 0, 0);
		gc.dispose();
		ImageData data = shot.getImageData();
		shot.dispose();
		int[] all = new int[bounds.width * bounds.height];
		for (int y = 0; y < bounds.height; y++) {
			data.getPixels(0, y, bounds.width, all, y * bounds.width);
		}
		return all;
	}

	private void spin() {
		long end = System.currentTimeMillis() + 150;
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
