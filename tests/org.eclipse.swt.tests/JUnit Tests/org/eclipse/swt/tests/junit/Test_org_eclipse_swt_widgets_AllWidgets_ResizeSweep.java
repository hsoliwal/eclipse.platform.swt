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

import java.lang.reflect.*;
import java.util.function.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The resize gate of brief rule 24 ("viewport deltas: resize and layout", docs/m3/allwidgets-viewport/DELTA_GATE.tsv):
 * a sweep of 50 shell sizes per widget, and per step the number of child windows whose native rectangle changed
 * (laid out natively) against the number that are visible after the step. A viewport delta lays out only children that
 * are or come into view, so per step the changed count stays within the visible count plus the ones leaving. One JUL
 * line per widget (logger <code>org.eclipse.swt.tests.junit.ResizeSweep</code>): steps, max and total of changed,
 * max visible, and the children created during the sweep. Logs only; <code>-Dswt.allwidgets.gate.enforce=true</code>
 * asserts the bound. Win32 (native rectangles).
 */
public class Test_org_eclipse_swt_widgets_AllWidgets_ResizeSweep {
	static final Logger LOG = Logger.getLogger ("org.eclipse.swt.tests.junit.ResizeSweep");
	static final int STEPS = 50;
	Display display;
	Shell shell;

	@BeforeEach
	public void setUp () {
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "native rectangles are read with win32 calls");
		display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
		shell.setBounds (40, 40, 400, 600);
		shell.setLayout (new FillLayout ());
	}

	@AfterEach
	public void tearDown () {
		if (shell != null) shell.dispose ();
	}

	@Test
	public void expandBar () throws ReflectiveOperationException {
		ExpandBar bar = new ExpandBar (shell, SWT.V_SCROLL);
		for (int i = 0; i < 2000; i++) {
			ExpandItem item = new ExpandItem (bar, SWT.NONE);
			item.setText ("item " + i);
			item.setHeight (40);
			if (i % 10 == 0) {
				Label label = new Label (bar, SWT.NONE);
				label.setText ("control " + i);
				item.setControl (label);
			}
			item.setExpanded ((i & 1) == 0);
		}
		sweep ("expandbar", bar, bar::getChildren);
	}

	@Test
	public void scrolledCompositeWithLayout () throws ReflectiveOperationException {
		ScrolledComposite scrolled = new ScrolledComposite (shell, SWT.V_SCROLL);
		Composite content = new Composite (scrolled, SWT.NONE);
		content.setLayout (new GridLayout (1, false));
		for (int i = 0; i < 2000; i++) {
			Label label = new Label (content, SWT.NONE);
			label.setText ("label " + i);
			label.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		}
		scrolled.setContent (content);
		scrolled.setExpandHorizontal (true);
		scrolled.setMinHeight (content.computeSize (SWT.DEFAULT, SWT.DEFAULT).y);
		scrolled.setExpandVertical (true);
		sweep ("scrolled-layout", content, content::getChildren);
	}

	@Test
	public void cTabFolder () throws ReflectiveOperationException {
		CTabFolder folder = new CTabFolder (shell, SWT.CLOSE | SWT.BORDER);
		for (int i = 0; i < 500; i++) {
			CTabItem item = new CTabItem (folder, SWT.NONE);
			item.setText ("tab " + i);
			Label label = new Label (folder, SWT.NONE);
			label.setText ("page " + i);
			item.setControl (label);
		}
		folder.setSelection (250);
		sweep ("ctabfolder", folder, folder::getChildren);
	}

	@Test
	public void toolBar () throws ReflectiveOperationException {
		ToolBar bar = new ToolBar (shell, SWT.FLAT | SWT.WRAP | SWT.RIGHT);
		for (int i = 0; i < 1000; i++) {
			if (i % 20 == 0) {
				ToolItem separator = new ToolItem (bar, SWT.SEPARATOR);
				Text text = new Text (bar, SWT.BORDER);
				separator.setControl (text);
				separator.setWidth (60);
			} else {
				new ToolItem (bar, SWT.PUSH).setText ("item " + i);
			}
		}
		sweep ("toolbar", bar, bar::getChildren);
	}

	/* 50 sizes; per step the children whose native rectangle changed and those visible in the parent afterwards */
	void sweep (String widget, Composite parent, Supplier<Control []> children) throws ReflectiveOperationException {
		shell.open ();
		ViewportDifferential.drain (display);
		int createdBefore = children.get ().length;
		int maxChanged = 0, totalChanged = 0, maxVisible = 0, worst = 0;
		long start = System.nanoTime ();
		for (int step = 0; step < STEPS; step++) {
			Control [] before = children.get ();
			int [][] rects = new int [before.length][];
			for (int i = 0; i < before.length; i++) rects [i] = nativeRect (before [i]);
			shell.setSize (400 + (step % 10) * 7, 600 - (step / 10) * 11);
			ViewportDifferential.drain (display);
			int changed = 0, visible = 0;
			int [] client = nativeRect (parent);
			for (int i = 0; i < before.length; i++) {
				if (before [i].isDisposed ()) continue;
				int [] rect = nativeRect (before [i]);
				if (!java.util.Arrays.equals (rect, rects [i])) changed++;
				if (before [i].isVisible () && rect [0] < client [2] && rect [2] > client [0] && rect [1] < client [3] && rect [3] > client [1]) visible++;
			}
			maxChanged = Math.max (maxChanged, changed);
			totalChanged += changed;
			maxVisible = Math.max (maxVisible, visible);
			worst = Math.max (worst, changed - visible);
		}
		long micros = (System.nanoTime () - start) / 1000 / STEPS;
		int created = children.get ().length - createdBefore;
		LOG.info ("resize-sweep " + widget + " " + ViewportDifferential.reference (parent.getClass ().getSimpleName ()) + " children " + createdBefore + " steps " + STEPS
			+ " changed max " + maxChanged + " total " + totalChanged + " visible max " + maxVisible + " changed-minus-visible max " + worst
			+ " created " + created + " us/step " + micros);
		if (Boolean.getBoolean ("swt.allwidgets.gate.enforce")) {
			Assertions.assertTrue (worst <= 2, widget + ": a resize step laid out " + worst + " children beyond the visible ones");
			Assertions.assertEquals (0, created, widget + ": children created by the sweep");
		}
	}

	/* the native window rectangle in screen pixels: left, top, right, bottom */
	static int [] nativeRect (Control control) throws ReflectiveOperationException {
		Class<?> os = Class.forName ("org.eclipse.swt.internal.win32.OS"), rectType = Class.forName ("org.eclipse.swt.internal.win32.RECT");
		Object rect = rectType.getConstructor ().newInstance ();
		os.getMethod ("GetWindowRect", long.class, rectType).invoke (null, Control.class.getField ("handle").getLong (control), rect);
		return new int [] {rectType.getField ("left").getInt (rect), rectType.getField ("top").getInt (rect), rectType.getField ("right").getInt (rect), rectType.getField ("bottom").getInt (rect)};
	}
}
