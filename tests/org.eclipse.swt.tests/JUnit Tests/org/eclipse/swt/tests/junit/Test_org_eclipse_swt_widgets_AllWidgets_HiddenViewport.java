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

import java.util.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The hidden-viewport gate of brief rule 24 (docs/m3/allwidgets-viewport/DELTA_GATE.tsv) for the containers of this
 * track: a probe viewport (a canvas over 10^4 model rows that asks {@link VisibilityGate#defer} before invalidating,
 * as the drawn viewports do) sits where a container hides it (an unselected CTabFolder tab, a collapsed ExpandItem, an
 * ExpandItem scrolled out of the bar, a StackLayout page under the top one, a ScrolledComposite child scrolled out,
 * a minimized shell, zero size). While hidden it receives 10^4 deltas; then the container shows it. Gate: no
 * invalidation and no paint while hidden, one {@link VisibilityGate#CATCH_UP} and exactly one paint pass after the
 * show, and the pixels equal a probe of the same model that was never hidden. Win32 (the gate's platform port).
 */
public class Test_org_eclipse_swt_widgets_AllWidgets_HiddenViewport {
	static final Logger LOG = Logger.getLogger ("org.eclipse.swt.tests.junit.HiddenViewport");
	static final int ROWS = 10_000, ROW = 16, DELTAS = 10_000;
	Display display;
	Shell shell;

	/* a viewport over int rows: invalidates a changed visible row unless the gate defers it */
	static final class Probe {
		final Canvas canvas;
		final int [] rows = new int [ROWS];
		int invalidations, paints, catchUps, deferred;

		Probe (Composite parent) {
			canvas = new Canvas (parent, SWT.NO_BACKGROUND);
			canvas.addListener (SWT.Paint, event -> {
				paints++;
				paint (event.gc, rows, canvas.getClientArea ());
			});
			canvas.addListener (VisibilityGate.CATCH_UP, event -> catchUps++);
		}

		void delta (int row, int value) {
			rows [row] = value;
			if (VisibilityGate.defer (canvas, VisibilityGate.PAINT)) {
				deferred++;
				return;
			}
			int y = row * ROW;
			Rectangle area = canvas.getClientArea ();
			if (y < area.height) {
				invalidations++;
				canvas.redraw (0, y, area.width, ROW, false);
			}
		}

		static void paint (GC gc, int [] rows, Rectangle area) {
			for (int row = 0, y = 0; y < area.height && row < rows.length; row++, y += ROW) {
				int value = rows [row];
				gc.setBackground (gc.getDevice ().getSystemColor (SWT.COLOR_WHITE + (value & 7)));
				gc.fillRectangle (0, y, area.width, ROW);
				gc.setForeground (gc.getDevice ().getSystemColor (SWT.COLOR_BLACK));
				gc.drawText (Integer.toString (value), 4, y, true);
			}
		}
	}

	@BeforeEach
	public void setUp () {
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "the visibility gate's port is win32's");
		display = Display.getDefault ();
		ViewportDifferential.parkCursor (display);
		shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
		shell.setBounds (40, 40, 400, 500);
		shell.setLayout (new FillLayout ());
	}

	@AfterEach
	public void tearDown () {
		if (shell != null) shell.dispose ();
	}

	@Test
	public void unselectedCTabFolderTab () {
		CTabFolder folder = new CTabFolder (shell, SWT.BORDER);
		CTabItem first = new CTabItem (folder, SWT.NONE), second = new CTabItem (folder, SWT.NONE);
		first.setText ("first");
		second.setText ("second");
		first.setControl (new Label (folder, SWT.NONE));
		Composite page = new Composite (folder, SWT.NONE);
		page.setLayout (new FillLayout ());
		Probe probe = new Probe (page);
		second.setControl (page);
		folder.setSelection (first);
		gate ("ctabfolder-tab", probe, () -> folder.setSelection (second));
	}

	@Test
	public void collapsedExpandItem () {
		ExpandBar bar = new ExpandBar (shell, SWT.V_SCROLL);
		ExpandItem item = new ExpandItem (bar, SWT.NONE);
		item.setText ("probe");
		item.setHeight (200);
		Probe probe = new Probe (bar);
		item.setControl (probe.canvas);
		item.setExpanded (false);
		gate ("expandbar-collapsed", probe, () -> item.setExpanded (true));
	}

	@Test
	public void expandItemScrolledOut () {
		ExpandBar bar = new ExpandBar (shell, SWT.V_SCROLL);
		ExpandItem target = null;
		for (int i = 0; i < 40; i++) {
			ExpandItem item = new ExpandItem (bar, SWT.NONE);
			item.setText ("item " + i);
			item.setHeight (i == 30 ? 200 : 40);
			item.setExpanded (true);
			if (i == 30) target = item;
		}
		Probe probe = new Probe (bar);
		target.setControl (probe.canvas);
		gate ("expandbar-scrolled", probe, () -> {
			for (int k = 0; k < 400 && !inView (probe.canvas, bar); k++) ViewportDifferential.send (bar, 0x115, 1, 0); // WM_VSCROLL SB_LINEDOWN
		});
	}

	@Test
	public void stackLayoutPageUnderTheTop () {
		Composite stack = new Composite (shell, SWT.NONE);
		StackLayout layout = new StackLayout ();
		stack.setLayout (layout);
		Composite top = new Composite (stack, SWT.NONE), page = new Composite (stack, SWT.NONE);
		page.setLayout (new FillLayout ());
		Probe probe = new Probe (page);
		layout.topControl = top;
		gate ("stacklayout", probe, () -> {
			layout.topControl = page;
			stack.layout ();
		});
	}

	@Test
	public void scrolledCompositeChildScrolledOut () {
		ScrolledComposite scrolled = new ScrolledComposite (shell, SWT.V_SCROLL);
		Composite content = new Composite (scrolled, SWT.NONE);
		Probe probe = new Probe (content);
		probe.canvas.setBounds (0, 3000, 380, 200);
		content.setSize (380, 3300);
		scrolled.setContent (content);
		gate ("scrolledcomposite", probe, () -> scrolled.setOrigin (0, 2900));
	}

	@Test
	public void minimizedShell () {
		shell.dispose ();
		shell = new Shell (display, SWT.SHELL_TRIM);
		shell.setBounds (40, 40, 400, 500);
		shell.setLayout (new FillLayout ());
		Probe probe = new Probe (shell);
		shell.open ();
		ViewportDifferential.drain (display);
		shell.setMinimized (true);
		gate ("minimized-shell", probe, () -> shell.setMinimized (false));
	}

	@Test
	public void zeroSize () {
		Composite holder = new Composite (shell, SWT.NONE);
		Probe probe = new Probe (holder);
		probe.canvas.setBounds (0, 0, 0, 0);
		gate ("zero-size", probe, () -> probe.canvas.setBounds (0, 0, 380, 200));
	}

	static boolean inView (Control control, Composite viewport) {
		Rectangle bounds = control.getBounds (), area = viewport.getClientArea ();
		return bounds.y >= 0 && bounds.y + 20 <= area.height;
	}

	/* hide state set up by the caller, 10^4 deltas, the container's show, then the gate's counts and the pixels */
	void gate (String container, Probe probe, Runnable show) {
		if (!shell.isVisible ()) shell.open ();
		ViewportDifferential.drain (display);
		assertTrue (VisibilityGate.isOffscreen (probe.canvas, true), container + ": the probe must be hidden before the deltas");
		probe.invalidations = probe.paints = probe.catchUps = probe.deferred = 0;
		Random random = new Random (20261020);
		for (int i = 0; i < DELTAS; i++) probe.delta (random.nextInt (64), random.nextInt (1 << 20));
		ViewportDifferential.drain (display);
		int hiddenInvalidations = probe.invalidations, hiddenPaints = probe.paints, deferred = probe.deferred;
		show.run ();
		ViewportDifferential.drain (display);
		probe.canvas.update ();
		int paints = probe.paints, catchUps = probe.catchUps;
		LOG.info ("hidden-viewport " + container + " deltas " + DELTAS + " deferred " + deferred + " invalidations-while-hidden " + hiddenInvalidations
			+ " paints-while-hidden " + hiddenPaints + " catch-ups " + catchUps + " paints-after-show " + paints);
		assertEquals (0, hiddenInvalidations, container + ": invalidations while hidden");
		assertEquals (0, hiddenPaints, container + ": paints while hidden");
		assertEquals (1, catchUps, container + ": catch-up events after the show");
		assertEquals (1, paints, container + ": paint passes after the show");
		assertPixelsEqualAFreshProbe (container, probe);
	}

	void assertPixelsEqualAFreshProbe (String container, Probe probe) {
		Rectangle area = probe.canvas.getClientArea ();
		Image shown = new Image (display, area.width, area.height), fresh = new Image (display, area.width, area.height);
		GC gc = new GC (probe.canvas);
		gc.copyArea (shown, 0, 0);
		gc.dispose ();
		gc = new GC (fresh);
		Probe.paint (gc, probe.rows, area);
		gc.dispose ();
		ImageData a = shown.getImageData (), b = fresh.getImageData ();
		shown.dispose ();
		fresh.dispose ();
		for (int y = 0; y < area.height; y++) {
			for (int x = 0; x < area.width; x++) {
				assertEquals (b.palette.getRGB (b.getPixel (x, y)), a.palette.getRGB (a.getPixel (x, y)), container + ": pixel " + x + "," + y);
			}
		}
	}
}
