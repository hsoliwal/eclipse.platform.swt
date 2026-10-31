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
package org.eclipse.swt.tests.junit.m3fidelity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.List;
import org.junit.jupiter.api.*;

/**
 * MODE_TRUTH v1 (census L10, real-app fidelity leaf): {@link M3Mode#of} answers the route a widget is actually drawn by.
 * A Table, List or Tree drawn by a plane answers the plane's mode (the theme leaf found <code>viewport</code> for a Table
 * drawn in m3), a Canvas with an M3 handler answers m3 or gpu, and the stock paths answer viewport or original.
 */
public class Test_org_eclipse_swt_widgets_M3ModeTruth {
	static final String MODE = System.getProperty ("swt.widget.mode", "viewport").trim ().toLowerCase (Locale.ROOT);
	/* M3CanvasHandler.DATA_KEY: the data under which a handled canvas carries its handler */
	static final String HANDLER_KEY = "org.eclipse.swt.internal.render.m3.M3CanvasHandler";
	Display display;
	Shell shell;

	@BeforeEach
	void open () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new FillLayout ());
	}

	@AfterEach
	void close () {
		shell.dispose ();
	}

	/* the modes a drawn widget may report in this run: gpu falls back to m3 without a GL context, m3 to custom without the library */
	static Set<String> drawn () {
		return switch (MODE) {
			case "gpu" -> Set.of ("gpu", "m3", "custom");
			case "m3" -> Set.of ("m3", "custom");
			case "custom" -> Set.of ("custom");
			case "original" -> Set.of ("original");
			default -> Set.of ("viewport");
		};
	}

	@Test
	public void test_tableListTreeAnswerTheirDrawnRoute () {
		Control [] controls = {new Table (shell, SWT.BORDER), new List (shell, SWT.BORDER), new Tree (shell, SWT.BORDER)};
		shell.setSize (300, 200);
		shell.open ();
		for (Control control : controls) {
			String mode = M3Mode.name (M3Mode.of (control));
			assertTrue (drawn ().contains (mode), control.getClass ().getSimpleName () + " in " + MODE + " answers " + mode);
		}
	}

	@Test
	public void test_canvasAnswersItsHandlerRoute () {
		Canvas canvas = new Canvas (shell, SWT.NONE);
		shell.setSize (100, 100);
		shell.open ();
		boolean handled = canvas.getData (HANDLER_KEY) != null;
		String mode = M3Mode.name (M3Mode.of (canvas));
		if (MODE.equals ("m3") || MODE.equals ("gpu")) assertTrue (handled, "a Canvas in " + MODE + " has an M3 handler");
		if (handled) {
			assertTrue (Set.of ("m3", "gpu").contains (mode), "a Canvas drawn by its M3 handler answers " + mode);
		} else {
			assertEquals ("original", mode);
		}
	}

	/* BACKEND_REPORT v1: the backend each control reports agrees with its route */
	@Test
	public void test_backendAgreesWithRoute () {
		Control [] controls = {new Table (shell, SWT.BORDER), new List (shell, SWT.BORDER), new Tree (shell, SWT.BORDER), new Canvas (shell, SWT.NONE)};
		shell.setSize (300, 200);
		shell.open ();
		for (Control control : controls) {
			String mode = M3Mode.name (M3Mode.of (control)), backend = M3Mode.backend (control);
			String what = control.getClass ().getSimpleName () + " " + mode + " via " + backend;
			switch (mode) {
				case "gpu" -> assertTrue (backend.endsWith ("(gpu)"), what);
				case "m3" -> assertTrue (backend.endsWith ("(raster)"), what);
				case "custom" -> assertTrue (backend.contains ("drawn plane"), what);
				default -> assertEquals (M3Mode.NATIVE, backend, what);
			}
		}
	}
}
