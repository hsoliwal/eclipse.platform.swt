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
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.TableCursor;
import org.eclipse.swt.custom.TreeCursor;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every-widget D leaf of the custom widgets: CLabel.setFont with the font in use and TableCursor / TreeCursor
 * setBackground / setForeground with the explicit colour in use repaint nothing (before: one repaint per call, as a CSS
 * engine re-applying a style causes); a new font or colour repaints to the pixels of a full repaint.
 */
public class Test_org_eclipse_swt_custom_SameFontAndColour {

	private Display display;
	private Shell shell;
	private Font font;
	private int paints;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
		shell.setSize(420, 320);
		font = new Font(display, "Tahoma", 13, SWT.BOLD);
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
		font.dispose();
	}

	@Test
	public void test_CLabel_theFontInUseRepaintsNothing() {
		CLabel label = new CLabel(shell, SWT.NONE);
		label.setText("label");
		label.setBounds(10, 10, 200, 40);
		label.addListener(SWT.Paint, e -> paints++);
		shell.open();
		spin();

		label.setFont(font);
		spin();
		assertSameAsFullRepaint(label, "new font");
		paints = 0;
		label.setFont(font);
		spin();
		assertEquals(0, paints, "the font in use repaints nothing");
		assertEquals(font, label.getFont());
	}

	@Test
	public void test_TableCursor_theColourInUseRepaintsNothing() {
		Table table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION);
		table.setBounds(10, 10, 360, 220);
		table.setHeaderVisible(true);
		for (int c = 0; c < 2; c++) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText("column " + c);
			column.setWidth(150);
		}
		for (int r = 0; r < 5; r++) {
			new TableItem(table, SWT.NONE).setText(new String[] { "cell " + r, "more " + r });
		}
		TableCursor cursor = new TableCursor(table, SWT.NONE);
		cursor.setSelection(1, 1);
		checkColours(cursor);
	}

	@Test
	public void test_TreeCursor_theColourInUseRepaintsNothing() {
		Tree tree = new Tree(shell, SWT.BORDER | SWT.FULL_SELECTION);
		tree.setBounds(10, 10, 360, 220);
		tree.setHeaderVisible(true);
		for (int c = 0; c < 2; c++) {
			TreeColumn column = new TreeColumn(tree, SWT.NONE);
			column.setText("column " + c);
			column.setWidth(150);
		}
		for (int r = 0; r < 5; r++) {
			new TreeItem(tree, SWT.NONE).setText(new String[] { "cell " + r, "more " + r });
		}
		TreeCursor cursor = new TreeCursor(tree, SWT.NONE);
		cursor.setSelection(1, 1);
		checkColours(cursor);
	}

	private void checkColours(Control cursor) {
		Color red = display.getSystemColor(SWT.COLOR_RED);
		Color yellow = display.getSystemColor(SWT.COLOR_YELLOW);
		cursor.addListener(SWT.Paint, e -> paints++);
		shell.open();
		spin();
		assertTrue(cursor.isVisible(), "the cursor shows");

		cursor.setBackground(red);
		cursor.setForeground(yellow);
		spin();
		assertSameAsFullRepaint(cursor, "new colours");
		paints = 0;
		cursor.setBackground(red);
		cursor.setForeground(yellow);
		spin();
		assertEquals(0, paints, "the colours in use repaint nothing");
		assertEquals(red, cursor.getBackground());
		assertEquals(yellow, cursor.getForeground());

		paints = 0;
		cursor.setBackground(null);
		spin();
		assertTrue(paints > 0, "back to the default colour repaints");
	}

	private void assertSameAsFullRepaint(Control control, String step) {
		int[] incremental = pixels(control);
		control.redraw();
		control.update();
		spin();
		assertArrayEquals(pixels(control), incremental, step + ": the pixels equal a full repaint");
	}

	private int[] pixels(Control control) {
		Rectangle size = control.getBounds();
		Image shot = new Image(display, size.width, size.height);
		GC gc = new GC(control);
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
