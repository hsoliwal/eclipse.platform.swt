/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

/** Native GTK visibility callbacks may dispose or reenter either owning widget. */
public class Test_org_eclipse_swt_widgets_ScrollBar_CallbackDisposalGtk {

	@Test
	public void showListenerMayDisposeOwnerBeforeFirstPaint () throws Exception {
		assertOwnerDisposal (false);
	}

	@Test
	public void hideListenerMayDisposeOwner () throws Exception {
		assertOwnerDisposal (true);
	}

	private static void assertOwnerDisposal (boolean initiallyVisible) throws Exception {
		try (Fixture fixture = new Fixture (initiallyVisible, SWT.VERTICAL)) {
			fixture.bar.addListener (initiallyVisible ? SWT.Hide : SWT.Show, event -> fixture.canvas.dispose ());
			assertDoesNotThrow (() -> fixture.bar.setVisible (!initiallyVisible));
			assertTrue (fixture.canvas.isDisposed ());
			assertTrue (fixture.bar.isDisposed ());
			assertEquals (List.of (initiallyVisible ? "hide" : "show"), fixture.events);
		}
	}

	@Test
	public void showListenerMayDisposeOnlyScrollBarAndOwnerStillReceivesResize () throws Exception {
		assertScrollBarDisposal (false);
	}

	@Test
	public void hideListenerMayDisposeOnlyScrollBarAndOwnerStillReceivesResize () throws Exception {
		assertScrollBarDisposal (true);
	}

	private static void assertScrollBarDisposal (boolean initiallyVisible) throws Exception {
		try (Fixture fixture = new Fixture (initiallyVisible, SWT.VERTICAL)) {
			fixture.bar.addListener (initiallyVisible ? SWT.Hide : SWT.Show, event -> fixture.bar.dispose ());
			assertDoesNotThrow (() -> fixture.bar.setVisible (!initiallyVisible));
			assertTrue (fixture.bar.isDisposed ());
			assertFalse (fixture.canvas.isDisposed ());
			assertNull (fixture.canvas.getVerticalBar ());
			assertEquals (List.of (initiallyVisible ? "hide" : "show", "resize"), fixture.events,
					"the surviving owner still receives the changed-client-area notification");
		}
	}

	@Test
	public void nestedVisibilityDisposalStopsEveryOutstandingOwnerResize () throws Exception {
		for (boolean initiallyVisible : new boolean [] {false, true}) {
			try (Fixture fixture = new Fixture (initiallyVisible, SWT.VERTICAL)) {
				fixture.bar.addListener (initiallyVisible ? SWT.Hide : SWT.Show,
						event -> fixture.bar.setVisible (initiallyVisible));
				fixture.bar.addListener (initiallyVisible ? SWT.Show : SWT.Hide, event -> fixture.canvas.dispose ());
				assertDoesNotThrow (() -> fixture.bar.setVisible (!initiallyVisible));
				assertTrue (fixture.canvas.isDisposed ());
				assertEquals (initiallyVisible ? List.of ("hide", "show") : List.of ("show", "hide"), fixture.events);
			}
		}
	}

	@Test
	public void nestedResizeDisposalStopsTheOuterOwnerResize () throws Exception {
		try (Fixture fixture = new Fixture (false, SWT.VERTICAL)) {
			fixture.bar.addListener (SWT.Show, event -> fixture.bar.setVisible (false));
			fixture.canvas.addListener (SWT.Resize, event -> fixture.canvas.dispose ());
			assertDoesNotThrow (() -> fixture.bar.setVisible (true));
			assertTrue (fixture.canvas.isDisposed ());
			assertEquals (List.of ("show", "hide", "resize"), fixture.events);
		}
	}

	@Test
	public void nestedScrollBarDisposalRetainsBothLiveOwnerResizeNotifications () throws Exception {
		try (Fixture fixture = new Fixture (false, SWT.VERTICAL)) {
			fixture.bar.addListener (SWT.Show, event -> fixture.bar.setVisible (false));
			fixture.bar.addListener (SWT.Hide, event -> fixture.bar.dispose ());
			assertDoesNotThrow (() -> fixture.bar.setVisible (true));
			assertTrue (fixture.bar.isDisposed ());
			assertFalse (fixture.canvas.isDisposed ());
			assertEquals (List.of ("show", "hide", "resize", "resize"), fixture.events);
		}
	}

	@Test
	public void liveNestedVisibilityRetainsEventOrderAndTheLatestVisibility () throws Exception {
		try (Fixture fixture = new Fixture (false, SWT.VERTICAL)) {
			fixture.bar.addListener (SWT.Show, event -> fixture.bar.setVisible (false));
			fixture.bar.setVisible (true);
			assertFalse (fixture.bar.getVisible (), "outer call must not restore its superseded native visibility");
			assertEquals (List.of ("show", "hide", "resize", "resize"), fixture.events);
		}
	}

	@Test
	public void normalVisibilityIsSynchronousIdempotentAndSelectionNeutral () throws Exception {
		for (int orientation : new int [] {SWT.HORIZONTAL, SWT.VERTICAL}) {
			try (Fixture fixture = new Fixture (false, orientation)) {
				int [] selections = {0};
				fixture.bar.addListener (SWT.Selection, event -> selections [0]++);
				fixture.bar.setVisible (false);
				assertEquals (List.of (), fixture.events);
				fixture.bar.setVisible (true);
				assertTrue (fixture.bar.getVisible ());
				assertEquals (List.of ("show", "resize"), fixture.events);
				fixture.bar.setVisible (true);
				assertEquals (List.of ("show", "resize"), fixture.events);
				fixture.bar.setVisible (false);
				assertFalse (fixture.bar.getVisible ());
				assertEquals (List.of ("show", "resize", "hide", "resize"), fixture.events);
				assertEquals (0, selections [0]);
			}
		}
	}

	@Test
	public void resizeListenerMayDisposeOwnerAfterNormalVisibilityNotification () throws Exception {
		try (Fixture fixture = new Fixture (false, SWT.HORIZONTAL)) {
			fixture.canvas.addListener (SWT.Resize, event -> fixture.canvas.dispose ());
			assertDoesNotThrow (() -> fixture.bar.setVisible (true));
			assertTrue (fixture.canvas.isDisposed ());
			assertEquals (List.of ("show", "resize"), fixture.events);
		}
	}

	@Test
	public void displayFilterMayDisposeOwnerBeforeTheVisibilityListener () throws Exception {
		try (Fixture fixture = new Fixture (false, SWT.HORIZONTAL)) {
			Listener filter = event -> {
				if (event.widget == fixture.bar) {
					fixture.events.add ("filter");
					fixture.canvas.dispose ();
				}
			};
			fixture.display.addFilter (SWT.Show, filter);
			try {
				assertDoesNotThrow (() -> fixture.bar.setVisible (true));
				assertTrue (fixture.canvas.isDisposed ());
				assertEquals (List.of ("filter"), fixture.events);
			} finally {
				fixture.display.removeFilter (SWT.Show, filter);
			}
		}
	}

	@Test
	public void visibilityListenerFailurePropagatesWithoutFollowingResize () throws Exception {
		try (Fixture fixture = new Fixture (false, SWT.VERTICAL)) {
			RuntimeException expected = new RuntimeException ("application visibility listener");
			fixture.bar.addListener (SWT.Show, event -> { throw expected; });
			assertSame (expected, assertThrows (RuntimeException.class, () -> fixture.bar.setVisible (true)));
			assertEquals (List.of ("show"), fixture.events);
		}
	}

	private static final class Fixture implements AutoCloseable {
		final Display display;
		final boolean ownDisplay;
		final Shell shell;
		final Canvas canvas;
		final ScrollBar bar;
		final List<String> events = new ArrayList<> ();

		Fixture (boolean initiallyVisible, int orientation) throws Exception {
			assumeTrue ("gtk".equals (SWT.getPlatform ()));
			assumeTrue (!Class.forName ("org.eclipse.swt.internal.gtk.GTK").getField ("GTK4").getBoolean (null));
			Display current = Display.getCurrent ();
			ownDisplay = current == null;
			display = ownDisplay ? new Display () : current;
			shell = new Shell (display);
			canvas = new Canvas (shell, SWT.H_SCROLL | SWT.V_SCROLL);
			canvas.setBounds (0, 0, 240, 160);
			bar = orientation == SWT.HORIZONTAL ? canvas.getHorizontalBar () : canvas.getVerticalBar ();
			bar.setVisible (initiallyVisible);
			bar.addListener (SWT.Show, event -> events.add ("show"));
			bar.addListener (SWT.Hide, event -> events.add ("hide"));
			canvas.addListener (SWT.Resize, event -> events.add ("resize"));
		}

		@Override
		public void close () {
			if (!shell.isDisposed ()) shell.dispose ();
			if (ownDisplay && !display.isDisposed ()) display.dispose ();
		}
	}
}
