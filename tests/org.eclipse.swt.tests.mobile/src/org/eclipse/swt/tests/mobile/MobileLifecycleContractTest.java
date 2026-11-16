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
package org.eclipse.swt.tests.mobile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/** Portable regressions for the original mobile widget and display owners. */
final class MobileLifecycleContractTest {

	private static final class Fixture implements AutoCloseable {
		final ContractPlatform platform = new ContractPlatform();
		final Display display;
		final Shell shell;

		Fixture() {
			MobileRuntime.install(platform);
			display = new Display();
			shell = new Shell(display);
			shell.open();
			drain(display);
		}

		Canvas canvas(Composite parent) {
			Canvas canvas = new Canvas(parent, SWT.NONE);
			canvas.setBounds(10, 10, 80, 60);
			return canvas;
		}

		@Override public void close() {
			try {
				if (!display.isDisposed()) display.dispose();
			} finally {
				MobileRuntime.uninstall(platform);
			}
		}
	}

	private static void drain(Display display) {
		for (int i = 0; i < 10000; i++) {
			if (display.isDisposed() || !display.readAndDispatch()) return;
		}
		fail("Mobile event queue did not quiesce");
	}

	static void childrenCannotEscapeDisposingParent() {
		try (Fixture f = new Fixture()) {
			Composite parent = new Composite(f.shell, SWT.NONE);
			Canvas child = f.canvas(parent);
			AtomicReference<Canvas> escaped = new AtomicReference<>();
			child.addDisposeListener(event -> escaped.set(new Canvas(parent, SWT.NONE)));
			SWTException failure = assertThrows(SWTException.class, parent::dispose);
			assertEquals(SWT.ERROR_WIDGET_DISPOSED, failure.code);
			assertNull(escaped.get());
			assertTrue(parent.isDisposed());
			assertTrue(child.isDisposed());
			assertEquals(0, f.shell.getChildren().length);
			assertEquals(1, f.platform.controls.size(), "Only the original Shell peer remains");
		}
	}

	static void shellsCannotEscapeDisposingDisplay() {
		try (Fixture f = new Fixture()) {
			AtomicReference<Shell> escaped = new AtomicReference<>();
			f.display.disposeExec(() -> escaped.set(new Shell(f.display)));
			SWTException failure = assertThrows(SWTException.class, f.display::dispose);
			assertEquals(SWT.ERROR_DEVICE_DISPOSED, failure.code);
			assertNull(escaped.get());
			assertTrue(f.display.isDisposed());
			assertTrue(f.shell.isDisposed());
			assertNull(Display.getCurrent());
			assertTrue(f.platform.controls.isEmpty());
			assertTrue(f.platform.images.isEmpty());
			assertEquals(1, f.platform.disconnects);
		}
	}

	static void lateDisposeCallbacksCannotEscapeCleanup() {
		try (Fixture f = new Fixture()) {
			AtomicInteger completed = new AtomicInteger();
			assertDoesNotThrow(() -> f.display.disposeExec(null));
			f.display.disposeExec(() -> {
				assertDoesNotThrow(() -> f.display.disposeExec(null));
				f.display.disposeExec(() -> fail("Late callback admitted"));
			});
			f.display.disposeExec(completed::incrementAndGet);
			SWTException failure = assertThrows(SWTException.class, f.display::dispose);
			assertEquals(SWT.ERROR_DEVICE_DISPOSED, failure.code);
			assertEquals(1, completed.get(), "An earlier failing callback cannot skip later cleanup");
			assertTrue(f.display.isDisposed());
			assertTrue(f.platform.controls.isEmpty());
			assertTrue(f.platform.images.isEmpty());
			assertEquals(1, f.platform.disconnects);
		}
	}

	static void throwingFocusOutStillReleasesOwner() {
		try (Fixture f = new Fixture()) {
			Canvas canvas = f.canvas(f.shell);
			drain(f.display);
			assertTrue(canvas.setFocus());
			canvas.addListener(SWT.FocusOut, event -> { throw new IllegalArgumentException("focus callback"); });
			IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, canvas::dispose);
			assertEquals("focus callback", failure.getMessage());
			assertTrue(canvas.isDisposed());
			assertNull(f.display.getFocusControl());
			assertEquals(0, f.shell.getChildren().length);
			assertEquals(1, f.platform.controls.size());
			assertEquals(f.platform.openedGraphics, f.platform.closedGraphics);
		}
	}

	static void disabledAndHiddenAncestorsClearFocusAndRejectInput() {
		try (Fixture f = new Fixture()) {
			Composite parent = new Composite(f.shell, SWT.NONE);
			parent.setBounds(0, 0, 120, 100);
			Canvas canvas = f.canvas(parent);
			long peer = f.platform.lastCreated;
			AtomicInteger focusOut = new AtomicInteger(), input = new AtomicInteger();
			canvas.addListener(SWT.FocusOut, event -> focusOut.incrementAndGet());
			canvas.addListener(SWT.MouseUp, event -> input.incrementAndGet());
			canvas.addListener(SWT.KeyUp, event -> input.incrementAndGet());
			assertTrue(canvas.setFocus());
			parent.setEnabled(false);
			assertNull(f.display.getFocusControl());
			assertEquals(1, focusOut.get());
			f.platform.sink.input(peer, SWT.MouseUp, 1, 1, 1, 0, '\0', 0, 1, 1);
			f.platform.sink.input(peer, SWT.KeyUp, 0, 0, 0, ' ', ' ', 0, 0, 2);
			drain(f.display);
			assertEquals(0, input.get());
			parent.setEnabled(true);
			assertTrue(canvas.setFocus());
			parent.setVisible(false);
			assertNull(f.display.getFocusControl());
			assertEquals(2, focusOut.get());
			f.platform.sink.input(peer, SWT.MouseUp, 1, 1, 1, 0, '\0', 0, 1, 3);
			f.platform.sink.input(peer, SWT.KeyUp, 0, 0, 0, ' ', ' ', 0, 0, 4);
			f.platform.sink.input(peer, SWT.FocusIn, 0, 0, 0, 0, '\0', 0, 0, 5);
			drain(f.display);
			assertEquals(0, input.get());
			assertNull(f.display.getFocusControl());
			parent.setVisible(true);
			assertTrue(canvas.setFocus(), "Restoring visibility preserves the original Canvas");
		}
	}

	static void focusOutMayDisposeDuringDisableAndHide() {
		try (Fixture f = new Fixture()) {
			Canvas disabled = f.canvas(f.shell);
			assertTrue(disabled.setFocus());
			disabled.addListener(SWT.FocusOut, event -> disabled.dispose());
			assertDoesNotThrow(() -> disabled.setEnabled(false));
			assertTrue(disabled.isDisposed());
			Composite hidden = new Composite(f.shell, SWT.NONE);
			hidden.setBounds(0, 0, 120, 100);
			Canvas child = f.canvas(hidden);
			assertTrue(child.setFocus());
			child.addListener(SWT.FocusOut, event -> hidden.dispose());
			assertDoesNotThrow(() -> hidden.setVisible(false));
			assertTrue(hidden.isDisposed());
			assertTrue(child.isDisposed());
			assertNull(f.display.getFocusControl());
			assertEquals(0, f.shell.getChildren().length);
		}
	}

	static void reentrantFocusRedirectionKeepsFinalOwner() {
		try (Fixture f = new Fixture()) {
			Canvas first = f.canvas(f.shell), requested = f.canvas(f.shell), redirected = f.canvas(f.shell);
			AtomicInteger requestedFocusIn = new AtomicInteger(), redirectedFocusIn = new AtomicInteger();
			requested.addListener(SWT.FocusIn, event -> requestedFocusIn.incrementAndGet());
			redirected.addListener(SWT.FocusIn, event -> redirectedFocusIn.incrementAndGet());
			assertTrue(first.setFocus());
			first.addListener(SWT.FocusOut, event -> redirected.setFocus());
			assertFalse(requested.setFocus());
			assertSame(redirected, f.display.getFocusControl());
			assertEquals(0, requestedFocusIn.get());
			assertEquals(1, redirectedFocusIn.get());
		}
	}

	static void showMayDisposeShellWithoutStaleAccess() {
		try (Fixture f = new Fixture()) {
			Shell transientShell = new Shell(f.display);
			transientShell.addListener(SWT.Show, event -> transientShell.dispose());
			assertDoesNotThrow(transientShell::open);
			assertTrue(transientShell.isDisposed());
			assertArrayEquals(new Shell[] {f.shell}, f.display.getShells());
			assertEquals(1, f.platform.controls.size());
		}
	}

	static void throwingDisposeListenersStillReleaseOwner() {
		try (Fixture f = new Fixture()) {
			Canvas canvas = f.canvas(f.shell);
			AtomicInteger completed = new AtomicInteger();
			canvas.addDisposeListener(event -> { throw new IllegalStateException("dispose callback"); });
			canvas.addDisposeListener(event -> completed.incrementAndGet());
			IllegalStateException failure = assertThrows(IllegalStateException.class, canvas::dispose);
			assertEquals("dispose callback", failure.getMessage());
			assertEquals(1, completed.get());
			assertTrue(canvas.isDisposed());
			assertEquals(0, f.shell.getChildren().length);
			assertEquals(1, f.platform.controls.size());
		}
	}

	static void borderedClientCoordinatesMatchPaintingAndInput() {
		try (Fixture f = new Fixture()) {
			Composite parent = new Composite(f.shell, SWT.BORDER);
			parent.setBounds(10, 20, 100, 80);
			Canvas child = new Canvas(parent, SWT.BORDER);
			child.setBounds(4, 5, 40, 30);
			long peer = f.platform.lastCreated;
			assertEquals(new Rectangle(0, 0, 98, 78), parent.getClientArea());
			assertEquals(new Rectangle(0, 0, 38, 28), child.getClientArea());
			assertEquals(new Point(16, 27), child.toDisplay(0, 0));
			assertEquals(new Point(0, 0), child.toControl(16, 27));
			assertEquals(new Point(5, 6), f.display.map(child, parent, 0, 0));
			assertEquals(new Rectangle(-1, -1, 42, 32), child.computeTrim(0, 0, 40, 30));
			AtomicInteger paints = new AtomicInteger();
			child.addPaintListener(event -> {
				paints.incrementAndGet();
				assertEquals(new Rectangle(0, 0, 38, 28), event.gc.getClipping());
				assertEquals(38, event.width);
				assertEquals(28, event.height);
				event.gc.setBackground(f.display.getSystemColor(SWT.COLOR_RED));
				event.gc.fillRectangle(0, 0, 38, 28);
			});
			drain(f.display);
			assertEquals(1, paints.get());
			int[] frame = f.platform.controls.get(peer).frame;
			assertEquals(38 * 28, frame.length, "The native presentation seam receives client pixels");
			assertEquals(0xffff0000, frame[0]);
			assertEquals(0xffff0000, frame[frame.length - 1]);
			AtomicReference<Event> pointer = new AtomicReference<>();
			child.addListener(SWT.MouseDown, pointer::set);
			f.platform.sink.input(peer, SWT.MouseDown, 5, 7, 1, 0, '\0', 0, 1, 1);
			drain(f.display);
			assertEquals(5, pointer.get().x);
			assertEquals(7, pointer.get().y);
			Image printed = new Image(f.display, 100, 80);
			GC printer = new GC(printed);
			try {
				assertTrue(parent.print(printer));
				ImageData pixels = printed.getImageData();
				assertEquals(f.display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW).getRGB(), pixels.palette.getRGB(pixels.getPixel(0, 0)));
				assertEquals(f.display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW).getRGB(), pixels.palette.getRGB(pixels.getPixel(5, 6)));
				assertEquals(new RGB(255, 0, 0), pixels.palette.getRGB(pixels.getPixel(6, 7)));
			} finally {
				printer.dispose();
				printed.dispose();
			}
			GC direct = new GC(child);
			try {
				direct.setBackground(f.display.getSystemColor(SWT.COLOR_GREEN));
				direct.fillRectangle(0, 0, 1, 1);
			} finally {
				direct.dispose();
			}
			frame = f.platform.controls.get(peer).frame;
			assertEquals(0xff00ff00, frame[0]);
			assertEquals(0xffff0000, frame[frame.length - 1]);
			assertEquals(f.platform.openedGraphics, f.platform.closedGraphics);
		}
	}

	static void modifierKeysReportStateBeforeTheirTransition() {
		try (Fixture f = new Fixture()) {
			Canvas canvas = f.canvas(f.shell);
			long peer = f.platform.lastCreated;
			AtomicReference<Event> received = new AtomicReference<>();
			canvas.addListener(SWT.KeyDown, received::set);
			canvas.addListener(SWT.KeyUp, received::set);
			int[] modifiers = {SWT.SHIFT, SWT.CTRL, SWT.ALT, SWT.COMMAND};
			for (int bits = 0; bits < 16; bits++) {
				int state = SWT.BUTTON1;
				for (int i = 0; i < modifiers.length; i++) if ((bits & (1 << i)) != 0) state |= modifiers[i];
				for (int key : modifiers) {
					f.platform.sink.input(peer, SWT.KeyDown, 0, 0, 0, key, '\0', state, 1, 1);
					drain(f.display);
					assertEquals(key, received.get().keyCode);
					assertEquals(state & ~key, received.get().stateMask);
					f.platform.sink.input(peer, SWT.KeyUp, 0, 0, 0, key, '\0', state, 1, 2);
					drain(f.display);
					assertEquals(key, received.get().keyCode);
					assertEquals(state | key, received.get().stateMask);
				}
				f.platform.sink.input(peer, SWT.KeyDown, 0, 0, 0, 'a', 'A', state, 1, 3);
				drain(f.display);
				assertEquals('a', received.get().keyCode);
				assertEquals('A', received.get().character);
				assertEquals(state, received.get().stateMask);
				f.platform.sink.input(peer, SWT.KeyUp, 0, 0, 0, 'a', 'A', state, 1, 4);
				drain(f.display);
				assertEquals(state, received.get().stateMask);
			}
		}
	}

	static void hideListenerMayRestoreVisibleFocus() {
		try (Fixture f = new Fixture()) {
			f.platform.queueNativeFocusAcknowledgements = true;
			Composite parent = new Composite(f.shell, SWT.NONE);
			parent.setBounds(0, 0, 120, 100);
			Canvas child = f.canvas(parent);
			assertTrue(child.setFocus());
			drain(f.display);
			java.util.List<Integer> events = new java.util.ArrayList<>();
			child.addListener(SWT.FocusOut, event -> events.add(SWT.FocusOut));
			child.addListener(SWT.FocusIn, event -> events.add(SWT.FocusIn));
			parent.addListener(SWT.Show, event -> events.add(SWT.Show));
			parent.addListener(SWT.Hide, event -> {
				events.add(SWT.Hide);
				parent.setVisible(true);
				assertTrue(child.setFocus());
			});
			parent.setVisible(false);
			assertTrue(parent.getVisible());
			assertSame(child, f.display.getFocusControl());
			java.util.List<Integer> expected = java.util.List.of(SWT.FocusOut, SWT.Hide, SWT.Show, SWT.FocusIn);
			assertEquals(expected, events, "Live mobile focus loss precedes Hide; restored focus must survive");
			drain(f.display);
			assertSame(child, f.display.getFocusControl());
			assertEquals(expected, events, "Queued acknowledgements cannot clear the restored owner");
		}
	}





	static void queuedWorkRetainsFifoAndPaintFairness() {
		try (Fixture f = new Fixture()) {
			java.util.List<Integer> calls = new java.util.ArrayList<>();
			for (int i = 0; i < 50; i++) {
				int value = i;
				f.display.asyncExec(() -> calls.add(value));
			}
			assertTrue(calls.isEmpty());
			drain(f.display);
			assertEquals(java.util.stream.IntStream.range(0, 50).boxed().toList(), calls);
			Canvas canvas = f.canvas(f.shell);
			drain(f.display);
			AtomicInteger paints = new AtomicInteger(), queued = new AtomicInteger(), timers = new AtomicInteger();
			canvas.addPaintListener(event -> paints.incrementAndGet());
			Runnable[] producer = new Runnable[1];
			producer[0] = () -> { if (queued.incrementAndGet() < 100) f.display.asyncExec(producer[0]); };
			f.display.asyncExec(producer[0]);
			f.display.timerExec(0, timers::incrementAndGet);
			canvas.redraw();
			for (int i = 0; i < 34; i++) assertTrue(f.display.readAndDispatch());
			assertTrue(paints.get() > 0, "A replenishing event queue cannot strand an earlier repaint");
			assertEquals(1, timers.get());
			drain(f.display);
			assertEquals(100, queued.get());
		}
	}

	static void paintingVisibilityLocksAndExternalGcRemainOwned() {
		try (Fixture f = new Fixture()) {
			Canvas canvas = f.canvas(f.shell);
			long peer = f.platform.lastCreated;
			AtomicInteger paints = new AtomicInteger();
			AtomicReference<GC> paintGc = new AtomicReference<>();
			canvas.addPaintListener(event -> { paints.incrementAndGet(); paintGc.set(event.gc); });
			drain(f.display);
			assertEquals(1, paints.get());
			assertTrue(paintGc.get().isDisposed());
			canvas.setRedraw(false); canvas.redraw(); drain(f.display);
			assertEquals(1, paints.get());
			canvas.setVisible(false); canvas.setRedraw(true); drain(f.display);
			assertEquals(1, paints.get(), "Unlocking redraw cannot paint a hidden owner");
			canvas.setVisible(true); drain(f.display);
			assertEquals(2, paints.get());
			GC external = new GC(canvas);
			try {
				external.setBackground(f.display.getSystemColor(SWT.COLOR_GREEN));
				external.fillRectangle(1, 1, 7, 9);
			} finally { external.dispose(); }
			drain(f.display);
			assertTrue(java.util.Arrays.stream(f.platform.controls.get(peer).frame).anyMatch(pixel -> pixel == 0xff00ff00));
			canvas.redraw(); drain(f.display);
			assertFalse(java.util.Arrays.stream(f.platform.controls.get(peer).frame).anyMatch(pixel -> pixel == 0xff00ff00));
			assertEquals(f.platform.openedGraphics, f.platform.closedGraphics);
		}
	}

	static void childRemovalKeepsIdentityAndCommonEventContracts() {
		try (Fixture f = new Fixture()) {
			Canvas first = f.canvas(f.shell), second = f.canvas(f.shell), third = f.canvas(f.shell);
			second.setBounds(100, 110, 120, 130); third.setData("stable");
			first.dispose();
			assertArrayEquals(new org.eclipse.swt.widgets.Control[] {second, third}, f.shell.getChildren());
			assertEquals(new Rectangle(100, 110, 120, 130), second.getBounds());
			assertEquals("stable", third.getData());
			java.util.List<Integer> calls = new java.util.ArrayList<>();
			org.eclipse.swt.widgets.Listener removed = event -> calls.add(2);
			second.addListener(SWT.Selection, event -> { calls.add(1); second.removeListener(SWT.Selection, removed); });
			second.addListener(SWT.Selection, removed);
			second.notifyListeners(SWT.Selection, new Event());
			assertEquals(java.util.List.of(1), calls);
			assertEquals(new RGB(0, 0, 255), f.display.getSystemColor(SWT.COLOR_BLUE).getRGB());
			assertEquals(new RGB(255, 0, 0), f.display.getSystemColor(SWT.COLOR_RED).getRGB());
			assertEquals(new RGB(0, 255, 0), f.display.getSystemColor(SWT.COLOR_GREEN).getRGB());
		}
	}

	static void graphicsClippingAlphaAndImageSnapshotsRemainOwned() {
		try (Fixture f = new Fixture()) {
			Image image = new Image(f.display, 20, 20);
			GC gc = new GC(image);
			try {
				gc.setBackground(f.display.getSystemColor(SWT.COLOR_WHITE)); gc.fillRectangle(0, 0, 20, 20);
				gc.setClipping(2, 3, 5, 7); assertTrue(gc.isClipped());
				gc.setAlpha(128); gc.setAntialias(SWT.OFF);
				gc.setBackground(f.display.getSystemColor(SWT.COLOR_RED)); gc.fillRectangle(0, 0, 20, 20);
				ImageData pixels = image.getImageData();
				assertEquals(new RGB(255, 127, 127), pixels.palette.getRGB(pixels.getPixel(3, 4)));
				assertEquals(new RGB(255, 255, 255), pixels.palette.getRGB(pixels.getPixel(0, 0)));
				assertEquals(SWT.OFF, gc.getAntialias());
				gc.setClipping((Rectangle) null); assertFalse(gc.isClipped());
				pixels.setPixel(3, 4, pixels.palette.getPixel(new RGB(0, 0, 0)));
				ImageData retained = image.getImageData();
				assertEquals(new RGB(255, 127, 127), retained.palette.getRGB(retained.getPixel(3, 4)),
						"Mutating an exposed ImageData snapshot cannot mutate the retained image");
			} finally {
				try { gc.dispose(); } finally { image.dispose(); }
			}
		}
	}

	static void queuedNativeFocusCannotOverrideReentrantOwner() {
		try (Fixture f = new Fixture()) {
			f.platform.queueNativeFocusAcknowledgements = true;
			Canvas previous = f.canvas(f.shell);
			Canvas requested = f.canvas(f.shell); long requestedPeer = f.platform.lastCreated;
			Canvas redirected = f.canvas(f.shell); long redirectedPeer = f.platform.lastCreated;
			Canvas refused = f.canvas(f.shell);
			AtomicInteger requestedIns = new AtomicInteger(), redirectedIns = new AtomicInteger();
			requested.addListener(SWT.FocusIn, event -> requestedIns.incrementAndGet());
			redirected.addListener(SWT.FocusIn, event -> redirectedIns.incrementAndGet());
			assertTrue(previous.setFocus()); drain(f.display);
			previous.addListener(SWT.FocusOut, event -> assertTrue(redirected.setFocus()));
			assertFalse(requested.setFocus());
			assertSame(redirected, f.display.getFocusControl());
			assertEquals(0, requestedIns.get()); assertEquals(1, redirectedIns.get());
			drain(f.display);
			assertSame(redirected, f.display.getFocusControl());
			assertEquals(0, requestedIns.get(), "A superseded native acknowledgement cannot emit stale FocusIn");
			assertEquals(1, redirectedIns.get(), "Native acknowledgement cannot duplicate the final owner FocusIn");

			// A failed synchronous request cannot invalidate genuine queued OS focus.
			f.platform.nativeFocus(requestedPeer);
			f.platform.rejectFocusRequests = true;
			assertFalse(refused.setFocus());
			f.platform.rejectFocusRequests = false;
			drain(f.display);
			assertSame(requested, f.display.getFocusControl());
			assertEquals(1, requestedIns.get());
			assertTrue(requested.setFocus()); drain(f.display);
			assertEquals(1, requestedIns.get(), "Requesting the current native owner adds no focus event");

			// Clear happens before FocusOut callbacks; their replacement owner wins.
			requested.addListener(SWT.FocusOut, event -> assertTrue(redirected.setFocus()));
			requested.setVisible(false); drain(f.display);
			assertSame(redirected, f.display.getFocusControl());
			assertEquals(2, redirectedIns.get());
			f.platform.nativeFocus(0); drain(f.display);
			assertNull(f.display.getFocusControl(), "An unsolicited current native FocusOut still clears focus");
			f.platform.nativeFocus(redirectedPeer); drain(f.display);
			assertSame(redirected, f.display.getFocusControl());
			assertEquals(3, redirectedIns.get(), "An unsolicited current native FocusIn still reaches its owner");
		}
		try (Fixture f = new Fixture()) {
			f.platform.queueNativeFocusAcknowledgements = true;
			Canvas requested = f.canvas(f.shell), following = f.canvas(f.shell);
			long followingPeer = f.platform.lastCreated;
			f.platform.nativeFocusAfterRequest = followingPeer;
			assertTrue(requested.setFocus()); drain(f.display);
			assertSame(following, f.display.getFocusControl(),
					"A genuine OS focus change after the native request cannot be lost before the owner resumes");
		}
		try (Fixture f = new Fixture()) {
			f.platform.queueNativeFocusAcknowledgements = true;
			Canvas previous = f.canvas(f.shell), following = f.canvas(f.shell);
			long followingPeer = f.platform.lastCreated;
			assertTrue(previous.setFocus()); drain(f.display);
			f.platform.nativeFocus(followingPeer);
			previous.setVisible(false); drain(f.display);
			assertSame(following, f.display.getFocusControl(),
					"Hiding the previous logical owner cannot discard queued genuine focus outside its subtree");
		}
		try (Fixture f = new Fixture()) {
			f.platform.queueNativeFocusAcknowledgements = true;
			Composite parent = new Composite(f.shell, SWT.NONE); parent.setBounds(0, 0, 100, 100);
			Canvas child = f.canvas(parent); long childPeer = f.platform.lastCreated;
			assertTrue(child.setFocus()); drain(f.display);
			f.platform.nativeFocus(0); f.platform.nativeFocus(childPeer);
			child.addListener(SWT.FocusOut, event -> parent.setEnabled(true));
			parent.setEnabled(false); drain(f.display);
			assertTrue(parent.getEnabled());
			assertNull(f.display.getFocusControl(),
					"Re-enabling a cleared ancestor without refocusing cannot resurrect an earlier native FocusIn");
		}
		try (Fixture f = new Fixture()) {
			f.platform.queueNativeFocusAcknowledgements = true;
			Canvas pending = f.canvas(f.shell); long pendingPeer = f.platform.lastCreated;
			assertTrue(pending.setFocus()); drain(f.display);
			f.platform.nativeFocus(0); drain(f.display);
			assertNull(f.display.getFocusControl());
			f.platform.nativeFocus(pendingPeer);
			pending.setVisible(false); pending.setVisible(true); drain(f.display);
			assertNull(f.display.getFocusControl(),
					"A hide/show clear rejects pending old FocusIn even without a current logical focus owner");
		}

	}

	static void findDisplayRetainsOriginalThreadLookup() {
		try (Fixture f = new Fixture()) {
			Thread owner = Thread.currentThread();
			assertSame(f.display, Display.findDisplay(owner));
			assertNull(Display.findDisplay(new Thread()));
			assertNull(Display.findDisplay(null));
			f.display.dispose();
			assertNull(Display.findDisplay(owner));
		}
	}
}
