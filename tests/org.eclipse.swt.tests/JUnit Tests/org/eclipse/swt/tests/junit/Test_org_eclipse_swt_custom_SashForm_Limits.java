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

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Split panels leaf SF1 (docs/m3/split-panels/SPLIT_PANELS.md): pane minimum and maximum sizes in points and percent,
 * honoured by the layout (weighted shares inside the limits, the last panes giving up space when the minimums do not
 * fit) and by a sash drag (both neighbours stay inside their limits; the drag is clamped and refused past them).
 */
public class Test_org_eclipse_swt_custom_SashForm_Limits {
	Display display;
	Shell shell;
	SashForm form;
	Control [] panes;

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display, SWT.NO_TRIM);
		shell.setLayout (new FillLayout ());
		form = new SashForm (shell, SWT.HORIZONTAL);
		panes = new Control [3];
		for (int i = 0; i < panes.length; i++) panes [i] = new Canvas (form, SWT.NONE);
		shell.setSize (603 + 2 * form.SASH_WIDTH, 200);
		shell.open ();
		form.setWeights (1, 1, 1);
		form.layout (true);
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
	}

	int width (int pane) {
		return panes [pane].getBounds ().width;
	}

	int total () {
		return width (0) + width (1) + width (2);
	}

	@Test
	public void withoutLimitsTheStockLayout () {
		assertArrayEquals (new int [0], form.getMinimumSizes ());
		assertArrayEquals (new int [0], form.getMaximumSizes ());
		int available = form.getClientArea ().width - 2 * form.getSashWidth ();
		assertEquals (available, total ());
		assertTrue (Math.abs (width (0) - width (1)) <= 1 && Math.abs (width (1) - width (2)) <= 2, width (0) + " " + width (1) + " " + width (2));
	}

	@Test
	public void minimumsAndMaximumsInPointsAndPercent () {
		int available = total ();
		form.setMinimumSizes (SashForm.POINTS, 0, 300, 0);
		assertEquals (300, width (1), "the middle pane at its minimum");
		assertEquals (available, total ());
		assertTrue (Math.abs (width (0) - width (2)) <= 1, "the rest shared by weight");
		form.setMaximumSizes (SashForm.POINTS, 100, 0, 0);
		assertEquals (100, width (0), "the first pane at its maximum");
		assertEquals (available - 400, width (2), "the space the first pane cannot take goes to the free pane");
		form.setMinimumSizes (SashForm.PERCENT, 0, 0, 50);
		form.setMaximumSizes (SashForm.POINTS, 0, 0, 0);
		int area = form.getClientArea ().width;
		assertTrue (width (2) >= area / 2, "50 percent of the area: " + width (2));
		assertArrayEquals (new int [] {0, 0, 50}, form.getMinimumSizes ());
		form.setWeights (10, 1, 1);
		assertTrue (width (2) >= area / 2, "setWeights stops at the minimum too");
		assertEquals (available, total ());
	}

	@Test
	public void minimumsThatDoNotFitShrinkTheLastPanesFirst () {
		int available = total ();
		form.setMinimumSizes (SashForm.POINTS, available / 2, available / 2, available / 2);
		assertEquals (available / 2, width (0));
		assertEquals (available - available / 2, width (1));
		assertEquals (0, width (2));
	}

	@Test
	public void aDragStopsAtTheLimitsOfBothNeighbours () {
		form.setMinimumSizes (SashForm.POINTS, 150, 120, 0);
		form.setMaximumSizes (SashForm.POINTS, 260, 0, 0);
		Sash sash = sash (0);
		Rectangle bounds = sash.getBounds ();
		int first = width (0), second = width (1);
		Event event = drag (sash, bounds.x + 500);
		assertFalse (event.doit, "clamped: refused past the limit");
		assertEquals (bounds.x + Math.min (260 - first, second - 120), event.x, "the sash stops at the tighter limit");
		event = drag (sash, bounds.x - 400);
		assertFalse (event.doit);
		assertEquals (bounds.x - (first - 150), event.x, "the first pane's minimum");
		event = drag (sash, bounds.x + 10);
		assertTrue (event.doit);
		assertEquals (first + 10, width (0), "a drag inside the limits is applied on release");
		assertEquals (second - 10, width (1));
	}

	Sash sash (int index) {
		int found = 0;
		for (Control child : form.getChildren ()) {
			if (child instanceof Sash sash && found++ == index) return sash;
		}
		throw new AssertionError ("no sash " + index);
	}

	/* the Selection event a sash sends on release at x */
	Event drag (Sash sash, int x) {
		Rectangle bounds = sash.getBounds ();
		Event event = new Event ();
		event.x = x;
		event.y = bounds.y;
		event.width = bounds.width;
		event.height = bounds.height;
		event.doit = true;
		sash.notifyListeners (SWT.Selection, event);
		return event;
	}
}
