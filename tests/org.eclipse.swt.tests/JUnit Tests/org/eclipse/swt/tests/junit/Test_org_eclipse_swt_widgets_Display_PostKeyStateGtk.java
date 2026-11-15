/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Native key posting carries modifier state and retains separately held keys. */
@Tag("gtk4-todo")
@Tag("gtk3-wayland-todo")
public class Test_org_eclipse_swt_widgets_Display_PostKeyStateGtk {

	private record Key(int type, int code, int state) { }
	private final List<Key> keys = new ArrayList<>();
	private final Listener recordKey = event -> keys.add(new Key(event.type, event.keyCode, event.stateMask));
	private Display display;
	private Shell shell;
	private boolean ownDisplay;

	@BeforeEach
	void openReceiver() throws Exception {
		assumeTrue("gtk".equals(SWT.getPlatform()));
		assumeTrue(!Class.forName("org.eclipse.swt.internal.gtk.GTK").getField("GTK4").getBoolean(null));
		Display current = Display.getCurrent();
		ownDisplay = current == null;
		display = ownDisplay ? new Display() : current;
		assumeTrue("x11".equals(System.getProperty("org.eclipse.swt.internal.gdk.backend")));
		shell = new Shell(display);
		shell.setLayout(new FillLayout());
		Button receiver = new Button(shell, SWT.PUSH);
		receiver.setText("Native key input receiver");
		shell.setBounds(50, 50, 300, 200);
		shell.open(); shell.forceActive(); receiver.forceFocus();
		pump();
		assertSame(receiver, display.getFocusControl());
		display.addFilter(SWT.KeyDown, recordKey);
		display.addFilter(SWT.KeyUp, recordKey);
	}

	@AfterEach
	void closeReceiver() {
		if (display == null || display.isDisposed()) return;
		display.removeFilter(SWT.KeyDown, recordKey);
		display.removeFilter(SWT.KeyUp, recordKey);
		if (shell != null && !shell.isDisposed()) shell.dispose();
		if (ownDisplay) display.dispose();
	}

	private void pump() throws InterruptedException {
		long end = System.nanoTime() + 100_000_000L;
		while (System.nanoTime() < end) {
			if (!display.readAndDispatch()) Thread.sleep(5);
		}
	}

	private void post(int type, int keyCode, char character, int stateMask) throws InterruptedException {
		Event event = new Event();
		event.type = type; event.keyCode = keyCode; event.character = character; event.stateMask = stateMask;
		assertTrue(display.post(event), "Native key event must be queued for the focused test shell");
		pump();
	}

	@Test
	void unmodifiedKeyHasNoMouseButtonState() throws Exception {
		post(SWT.KeyDown, 'a', 'a', 0); post(SWT.KeyUp, 'a', 'a', 0);
		assertEquals(List.of(new Key(SWT.KeyDown, 'a', 0), new Key(SWT.KeyUp, 'a', 0)), keys);
	}

	@Test
	void controlSpaceHasOnlyControlState() throws Exception {
		post(SWT.KeyDown, SWT.CTRL, '\0', 0);
		post(SWT.KeyDown, ' ', ' ', 0); post(SWT.KeyUp, ' ', ' ', 0);
		post(SWT.KeyUp, SWT.CTRL, '\0', 0);
		assertEquals(List.of(new Key(SWT.KeyDown, SWT.CTRL, 0), new Key(SWT.KeyDown, ' ', SWT.CTRL),
				new Key(SWT.KeyUp, ' ', SWT.CTRL), new Key(SWT.KeyUp, SWT.CTRL, SWT.CTRL)), keys);
	}

	@Test
	void heldShiftSurvivesOrdinaryKeyRelease() throws Exception {
		post(SWT.KeyDown, SWT.SHIFT, '\0', 0);
		post(SWT.KeyDown, 'a', 'a', 0); post(SWT.KeyUp, 'a', 'a', 0);
		post(SWT.KeyDown, 'b', 'b', 0); post(SWT.KeyUp, 'b', 'b', 0);
		post(SWT.KeyUp, SWT.SHIFT, '\0', 0);
		assertEquals(List.of(new Key(SWT.KeyDown, SWT.SHIFT, 0), new Key(SWT.KeyDown, 'a', SWT.SHIFT),
				new Key(SWT.KeyUp, 'a', SWT.SHIFT), new Key(SWT.KeyDown, 'b', SWT.SHIFT),
				new Key(SWT.KeyUp, 'b', SWT.SHIFT), new Key(SWT.KeyUp, SWT.SHIFT, SWT.SHIFT)), keys);
	}

	@Test
	void modifierEventsDescribeTheStateBeforeEachTransition() throws Exception {
		post(SWT.KeyDown, SWT.SHIFT, '\0', 0); post(SWT.KeyUp, SWT.SHIFT, '\0', 0);
		assertEquals(List.of(new Key(SWT.KeyDown, SWT.SHIFT, 0), new Key(SWT.KeyUp, SWT.SHIFT, SWT.SHIFT)), keys);
	}

	@Test
	void explicitCombinedModifiersArePreserved() throws Exception {
		post(SWT.KeyDown, 'a', 'a', SWT.CTRL | SWT.SHIFT); post(SWT.KeyUp, 'a', 'a', SWT.CTRL | SWT.SHIFT);
		assertEquals(List.of(new Key(SWT.KeyDown, 'a', SWT.CTRL | SWT.SHIFT), new Key(SWT.KeyUp, 'a', SWT.CTRL | SWT.SHIFT)), keys);
	}
}
