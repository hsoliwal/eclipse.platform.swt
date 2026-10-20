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
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Mouse tracking of the ExpandBar (optimization leaf O4, docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): hover
 * follows the header under the mouse, one item at a time, through moves, gaps, leaving, disposal and focus clicks. The
 * expectations hold for the stock scan (<code>-Dswt.widget.mode.ExpandBar=original</code>) and the binary search with
 * the tracked hover item alike. Win32 (mouse messages are sent to the window).
 */
public class Test_org_eclipse_swt_widgets_ExpandBar_Hover {
	static final int WM_MOUSEMOVE = 0x200, WM_LBUTTONDOWN = 0x201, WM_LBUTTONUP = 0x202, WM_MOUSELEAVE = 0x2A3;
	Display display;
	Shell shell;
	ExpandBar bar;

	@BeforeEach
	public void setUp () {
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "mouse messages are win32's");
		display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
		shell.setBounds (40, 40, 300, 400);
		shell.setLayout (new FillLayout ());
		bar = new ExpandBar (shell, SWT.V_SCROLL);
		for (int i = 0; i < 30; i++) {
			ExpandItem item = new ExpandItem (bar, SWT.NONE);
			item.setText ("item " + i);
			item.setHeight (30);
			item.setExpanded (i % 3 == 0);
		}
		shell.open ();
		ViewportDifferential.drain (display);
	}

	@AfterEach
	public void tearDown () {
		if (shell != null) shell.dispose ();
	}

	static int field (Object object, String name) throws ReflectiveOperationException {
		Field field = ExpandItem.class.getDeclaredField (name);
		field.setAccessible (true);
		return field.getInt (object);
	}

	static boolean hover (ExpandItem item) throws ReflectiveOperationException {
		Field field = ExpandItem.class.getDeclaredField ("hover");
		field.setAccessible (true);
		return field.getBoolean (item);
	}

	static ExpandItem focus (ExpandBar bar) throws ReflectiveOperationException {
		Field field = ExpandBar.class.getDeclaredField ("focusItem");
		field.setAccessible (true);
		return (ExpandItem) field.get (bar);
	}

	/* the native point in the middle of an item's header row */
	int headerY (int index) throws ReflectiveOperationException {
		ExpandItem item = bar.getItem (index);
		return field (item, "y") + 3;
	}

	void send (int message, int y) {
		ViewportDifferential.send (bar, message, message == WM_LBUTTONDOWN ? 1 : 0, ViewportDifferential.makeLParam (50, y));
	}

	int hovered () throws ReflectiveOperationException {
		int found = -1, count = 0;
		for (int i = 0; i < bar.getItemCount (); i++) {
			if (hover (bar.getItem (i))) {
				found = i;
				count++;
			}
		}
		assertTrue (count <= 1, "more than one item hovered: " + count);
		return found;
	}

	@Test
	public void hoverFollowsTheHeaderUnderTheMouse () throws ReflectiveOperationException {
		send (WM_MOUSEMOVE, headerY (2));
		assertEquals (2, hovered ());
		send (WM_MOUSEMOVE, headerY (2) + 1);
		assertEquals (2, hovered (), "a move within the same header");
		send (WM_MOUSEMOVE, headerY (3));
		assertEquals (3, hovered ());
		/* the body of an expanded item and the spacing below it hover nothing */
		send (WM_MOUSEMOVE, headerY (0) + bar.getItem (0).getHeaderHeight () + 10);
		assertEquals (-1, hovered ());
		send (WM_MOUSEMOVE, headerY (1));
		assertEquals (1, hovered ());
		send (WM_MOUSEMOVE, 2);
		assertEquals (-1, hovered (), "above the first header");
		send (WM_MOUSEMOVE, headerY (4));
		assertEquals (4, hovered ());
		ViewportDifferential.send (bar, WM_MOUSELEAVE, 0, 0);
		assertEquals (-1, hovered (), "leaving clears the hover");
		send (WM_MOUSEMOVE, headerY (4));
		assertEquals (4, hovered (), "the same item hovers again after leaving");
		ViewportDifferential.send (bar, WM_MOUSELEAVE, 0, 0);
		ViewportDifferential.send (bar, WM_MOUSELEAVE, 0, 0);
		assertEquals (-1, hovered (), "leaving twice");
	}

	@Test
	public void aDisposedHoverItemIsForgotten () throws ReflectiveOperationException {
		send (WM_MOUSEMOVE, headerY (5));
		assertEquals (5, hovered ());
		bar.getItem (5).dispose ();
		ViewportDifferential.drain (display);
		send (WM_MOUSEMOVE, headerY (6));
		assertEquals (6, hovered ());
		bar.getItem (6).dispose ();
		ViewportDifferential.send (bar, WM_MOUSELEAVE, 0, 0);
		assertEquals (-1, hovered ());
	}

	@Test
	public void aClickOnAHeaderTakesTheFocus () throws ReflectiveOperationException {
		assertSame (bar.getItem (0), focus (bar));
		send (WM_LBUTTONDOWN, headerY (3));
		send (WM_LBUTTONUP, headerY (3));
		assertSame (bar.getItem (3), focus (bar));
		assertFalse (bar.getItem (3).getExpanded (), "the click toggles the item (expanded before: 3 % 3 == 0)");
		send (WM_LBUTTONDOWN, headerY (3));
		assertSame (bar.getItem (3), focus (bar), "a click on the focus item keeps it");
		send (WM_LBUTTONUP, headerY (3));
		send (WM_LBUTTONDOWN, 2);
		assertSame (bar.getItem (3), focus (bar), "a click above the first header changes nothing");
		send (WM_LBUTTONUP, 2);
		int body = headerY (0) + bar.getItem (0).getHeaderHeight () + 10; // inside the expanded body of item 0
		send (WM_LBUTTONDOWN, body);
		assertSame (bar.getItem (3), focus (bar), "a click outside every header changes nothing");
		send (WM_LBUTTONUP, body);
	}
}
