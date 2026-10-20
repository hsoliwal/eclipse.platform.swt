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

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Mouse tracking of the CTabFolder (optimization leaf C2, docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): after
 * each mouse event every tab is in the state the stock loop gives it (hot under the mouse, the close button hot over
 * it, every other tab at rest), through moves, the close button, exit, a click, a disposed tab and a tab created
 * after the layout. The same expectations hold in the original mode (<code>-Dswt.widget.mode.CTabFolder=original</code>).
 */
public class Test_org_eclipse_swt_custom_CTabFolder_Hover {
	Display display;
	Shell shell;
	CTabFolder folder;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
		shell.setBounds (40, 40, 600, 200);
		shell.setLayout (new FillLayout ());
		folder = new CTabFolder (shell, SWT.CLOSE | SWT.BORDER);
		for (int i = 0; i < 40; i++) {
			CTabItem item = new CTabItem (folder, SWT.NONE);
			item.setText ("tab " + i);
		}
		folder.setSelection (2);
		shell.open ();
		ViewportDifferential.drain (display);
	}

	@AfterEach
	public void tearDown () {
		if (shell != null) shell.dispose ();
	}

	static int intField (Object item, String name) throws ReflectiveOperationException {
		Field field = CTabItem.class.getDeclaredField (name);
		field.setAccessible (true);
		return field.getInt (item);
	}

	static Rectangle closeRect (CTabItem item) throws ReflectiveOperationException {
		Field field = CTabItem.class.getDeclaredField ("closeRect");
		field.setAccessible (true);
		return (Rectangle) field.get (item);
	}

	void mouse (int type, int x, int y) {
		Event event = new Event ();
		event.x = x;
		event.y = y;
		event.button = type == SWT.MouseMove || type == SWT.MouseExit ? 0 : 1;
		folder.notifyListeners (type, event);
	}

	/* every tab against the stock expectation: hover is the tab under (x, y), or none */
	void check (String what, int hover, boolean overClose) throws ReflectiveOperationException {
		int selected = folder.getSelectionIndex ();
		for (int i = 0; i < folder.getItemCount (); i++) {
			CTabItem item = folder.getItem (i);
			boolean hot = (intField (item, "state") & SWT.HOT) != 0;
			int close = intField (item, "closeImageState");
			if (i == hover) {
				assertTrue (hot, what + ": tab " + i + " hot");
				assertEquals (overClose ? SWT.HOT : SWT.NONE, close, what + ": tab " + i + " close button");
			} else {
				assertFalse (hot, what + ": tab " + i + " not hot");
				assertEquals (i == selected ? SWT.NONE : SWT.BACKGROUND, close, what + ": tab " + i + " close button at rest");
			}
		}
	}

	Point middle (int index) {
		Rectangle bounds = folder.getItem (index).getBounds ();
		return new Point (bounds.x + 4, bounds.y + bounds.height / 2);
	}

	@Test
	public void everyTabEndsInTheStockState () throws ReflectiveOperationException {
		Point p = middle (3);
		mouse (SWT.MouseMove, p.x, p.y);
		check ("over tab 3", 3, false);
		p = middle (5);
		mouse (SWT.MouseMove, p.x, p.y);
		check ("over tab 5", 5, false);
		Rectangle close = closeRect (folder.getItem (5));
		if (close.width > 0) {
			mouse (SWT.MouseMove, close.x + 1, close.y + 1);
			check ("over the close button of tab 5", 5, true);
		}
		p = middle (2);
		mouse (SWT.MouseMove, p.x, p.y);
		check ("over the selected tab", 2, false);
		mouse (SWT.MouseMove, 3, folder.getClientArea ().y + folder.getClientArea ().height - 3);
		check ("over the page", -1, false);
		p = middle (4);
		mouse (SWT.MouseMove, p.x, p.y);
		mouse (SWT.MouseExit, -1, -1);
		check ("exit", -1, false);
		p = middle (6);
		mouse (SWT.MouseMove, p.x, p.y);
		mouse (SWT.MouseDown, p.x, p.y);
		mouse (SWT.MouseUp, p.x, p.y);
		ViewportDifferential.drain (display);
		assertEquals (6, folder.getSelectionIndex (), "the click selected the tab under the mouse");
		mouse (SWT.MouseMove, p.x, p.y);
		check ("over the new selection", 6, false);
	}

	@Test
	public void disposedAndCreatedTabsAfterTheLayout () throws ReflectiveOperationException {
		Point p = middle (4);
		mouse (SWT.MouseMove, p.x, p.y);
		folder.getItem (4).dispose ();
		ViewportDifferential.drain (display);
		p = middle (3);
		mouse (SWT.MouseMove, p.x, p.y);
		check ("after a dispose", 3, false);
		new CTabItem (folder, SWT.NONE, 0).setText ("first");
		/* a move before the layout ran uses the stale positions in both modes (the indexes are stale: the stock scan) */
		mouse (SWT.MouseMove, p.x, p.y);
		ViewportDifferential.drain (display);
		p = middle (5);
		mouse (SWT.MouseMove, p.x, p.y);
		check ("after the layout", 5, false);
	}
}
