/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.mobile.MobilePlatform;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Original SWT lifecycle contracts, executed against the current mobile owners. */
public final class MobileLifecycleContractJUnitTest {

	private ContractPlatform platform;
	private Display display;
	private Shell shell;

	@BeforeEach void create() {
		platform = new ContractPlatform();
		MobileRuntime.install(platform);
		display = new Display();
		shell = new Shell(display);
		shell.open();
		drain();
	}

	@AfterEach void cleanup() {
		try {
			if (!display.isDisposed()) display.dispose();
		} finally {
			MobileRuntime.uninstall(platform);
		}
	}

	private void drain() {
		for (int i = 0; i < 10000; i++) {
			if (!display.readAndDispatch()) return;
		}
		fail("Event queue did not quiesce");
	}

	private Canvas canvas(Composite parent) {
		Canvas canvas = new Canvas(parent, SWT.NONE);
		canvas.setBounds(2, 3, 30, 40);
		return canvas;
	}

	@Test void showListenerMayDisposeShellDuringOpen() {
		Shell other = new Shell(display);
		other.addListener(SWT.Show, event -> other.dispose());
		assertDoesNotThrow(other::open);
		assertTrue(other.isDisposed());
	}

	@Test void layoutCallbackMayDisposeShellDuringOpen() {
		Shell other = new Shell(display);
		other.setLayout(new Layout() {
			@Override protected Point computeSize(Composite composite, int wHint, int hHint, boolean changed) {
				return new Point(40, 40);
			}
			@Override protected void layout(Composite composite, boolean changed) {
				composite.dispose();
			}
		});
		assertDoesNotThrow(other::open);
		assertTrue(other.isDisposed());
	}

	@Test void focusOutMayDisposeDisabledControl() {
		Canvas canvas = canvas(shell);
		assertTrue(canvas.setFocus());
		AtomicInteger focusOut = new AtomicInteger();
		canvas.addListener(SWT.FocusOut, event -> {
			focusOut.incrementAndGet();
			canvas.dispose();
		});
		assertDoesNotThrow(() -> canvas.setEnabled(false));
		assertTrue(canvas.isDisposed());
		assertEquals(1, focusOut.get());
		assertNull(display.getFocusControl());
	}

	@Test void focusOutMayDisposeHiddenControl() {
		Canvas canvas = canvas(shell);
		assertTrue(canvas.setFocus());
		canvas.addListener(SWT.FocusOut, event -> canvas.dispose());
		assertDoesNotThrow(() -> canvas.setVisible(false));
		assertTrue(canvas.isDisposed());
		assertNull(display.getFocusControl());
	}

	@Test void disabledAncestorClearsFocusAndBlocksNativeInput() {
		Composite parent = new Composite(shell, SWT.NONE);
		Canvas child = canvas(parent);
		long peer = platform.lastCreated;
		AtomicInteger input = new AtomicInteger();
		AtomicInteger focusOut = new AtomicInteger();
		child.addListener(SWT.MouseUp, event -> input.incrementAndGet());
		child.addListener(SWT.KeyUp, event -> input.incrementAndGet());
		child.addListener(SWT.FocusOut, event -> focusOut.incrementAndGet());
		assertTrue(child.setFocus());
		parent.setEnabled(false);
		platform.sink.input(peer, SWT.MouseUp, 3, 4, 1, 0, '\0', 0, 1, 1);
		platform.sink.input(peer, SWT.KeyUp, 0, 0, 0, ' ', ' ', 0, 1, 2);
		platform.sink.input(peer, SWT.FocusOut, 0, 0, 0, 0, '\0', 0, 0, 3);
		drain();
		assertEquals(0, input.get());
		assertEquals(1, focusOut.get());
		assertNull(display.getFocusControl());
	}

	@Test void hiddenAncestorClearsFocusAndBlocksNativeInput() {
		Composite parent = new Composite(shell, SWT.NONE);
		Canvas child = canvas(parent);
		long peer = platform.lastCreated;
		AtomicInteger input = new AtomicInteger();
		child.addListener(SWT.MouseUp, event -> input.incrementAndGet());
		child.addListener(SWT.KeyUp, event -> input.incrementAndGet());
		assertTrue(child.setFocus());
		parent.setVisible(false);
		platform.sink.input(peer, SWT.MouseUp, 3, 4, 1, 0, '\0', 0, 1, 1);
		platform.sink.input(peer, SWT.KeyUp, 0, 0, 0, ' ', ' ', 0, 1, 2);
		drain();
		assertEquals(0, input.get());
		assertNull(display.getFocusControl());
	}

	@Test void reentrantFocusTransferKeepsTheLastTarget() {
		Canvas first = canvas(shell);
		Canvas second = canvas(shell);
		Canvas third = canvas(shell);
		AtomicInteger secondFocusIn = new AtomicInteger();
		AtomicInteger thirdFocusIn = new AtomicInteger();
		assertTrue(first.setFocus());
		first.addListener(SWT.FocusOut, event -> third.setFocus());
		second.addListener(SWT.FocusIn, event -> secondFocusIn.incrementAndGet());
		third.addListener(SWT.FocusIn, event -> thirdFocusIn.incrementAndGet());
		assertFalse(second.setFocus());
		assertSame(third, display.getFocusControl());
		assertEquals(0, secondFocusIn.get());
		assertEquals(1, thirdFocusIn.get());
	}

	@Test void failingFocusOutStillReleasesTheChild() {
		Canvas canvas = canvas(shell);
		long peer = platform.lastCreated;
		assertTrue(canvas.setFocus());
		canvas.addListener(SWT.FocusOut, event -> { throw new IllegalArgumentException("focus"); });
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, canvas::dispose);
		assertEquals("focus", failure.getMessage());
		assertTrue(canvas.isDisposed());
		assertFalse(platform.controls.containsKey(peer));
		assertEquals(0, shell.getChildren().length);
		assertNull(display.getFocusControl());
	}

	@Test void queuedNativeFocusAcknowledgementsPreserveReentrantFocus() {
		platform.reportFocusChanges = true;
		Canvas first = canvas(shell);
		Canvas second = canvas(shell);
		long secondPeer = platform.lastCreated;
		Canvas third = canvas(shell);
		long thirdPeer = platform.lastCreated;
		AtomicInteger secondFocusIn = new AtomicInteger();
		AtomicInteger thirdFocusIn = new AtomicInteger();
		assertTrue(first.setFocus());
		drain();
		first.addListener(SWT.FocusOut, event -> third.setFocus());
		second.addListener(SWT.FocusIn, event -> secondFocusIn.incrementAndGet());
		third.addListener(SWT.FocusIn, event -> thirdFocusIn.incrementAndGet());
		assertFalse(second.setFocus());
		drain();
		assertSame(third, display.getFocusControl());
		assertEquals(0, secondFocusIn.get());
		assertEquals(1, thirdFocusIn.get());
		// Subsequent genuine native focus changes must still be delivered.
		platform.sink.input(thirdPeer, SWT.FocusOut, 0, 0, 0, 0, '\0', 0, 0, 3);
		platform.sink.input(secondPeer, SWT.FocusIn, 0, 0, 0, 0, '\0', 0, 0, 4);
		drain();
		assertSame(second, display.getFocusControl());
		assertEquals(1, secondFocusIn.get());
	}

	@Test void disposingParentCannotAcquireAnotherChild() {
		Composite parent = new Composite(shell, SWT.NONE);
		Canvas child = canvas(parent);
		AtomicReference<Canvas> escaped = new AtomicReference<>();
		child.addDisposeListener(event -> escaped.set(new Canvas(parent, SWT.NONE)));
		assertThrows(SWTException.class, parent::dispose);
		assertNull(escaped.get());
		assertTrue(parent.isDisposed());
		assertTrue(child.isDisposed());
		assertEquals(0, shell.getChildren().length);
		assertEquals(1, platform.controls.size());
	}

	@Test void disposingDisplayCannotAcquireAnotherShell() {
		AtomicReference<Shell> escaped = new AtomicReference<>();
		display.disposeExec(() -> escaped.set(new Shell(display)));
		SWTException failure = assertThrows(SWTException.class, display::dispose);
		assertEquals(SWT.ERROR_DEVICE_DISPOSED, failure.code);
		assertNull(escaped.get());
		assertTrue(display.isDisposed());
		assertNull(Display.getCurrent());
		assertEquals(1, platform.disconnects);
		assertTrue(platform.controls.isEmpty());
	}

	@Test void nullDisposeExecPreservesTheOriginalNoOpContract() {
		assertDoesNotThrow(() -> display.disposeExec(null));
		AtomicInteger calls = new AtomicInteger();
		display.disposeExec(calls::incrementAndGet);
		display.dispose();
		assertEquals(1, calls.get());
		SWTException failure = assertThrows(SWTException.class, () -> display.disposeExec(null));
		assertEquals(SWT.ERROR_DEVICE_DISPOSED, failure.code);
	}

	@Test void disposedParentUsesTheOriginalInvalidArgumentContract() {
		Composite parent = new Composite(shell, SWT.NONE);
		parent.dispose();
		assertThrows(IllegalArgumentException.class, () -> new Canvas(parent, SWT.NONE));
	}

	@Test void listenerFailureDoesNotSkipTheOtherDisposeListeners() {
		Canvas canvas = canvas(shell);
		AtomicInteger called = new AtomicInteger();
		canvas.addDisposeListener(event -> { throw new IllegalStateException("dispose"); });
		canvas.addDisposeListener(event -> called.incrementAndGet());
		assertThrows(IllegalStateException.class, canvas::dispose);
		assertTrue(canvas.isDisposed());
		assertEquals(1, called.get());
		assertEquals(0, shell.getChildren().length);
	}

	@Test void listenerRemovalDuringDispatchUsesTheOriginalEventTable() {
		Canvas canvas = canvas(shell);
		List<Integer> calls = new ArrayList<>();
		Listener second = event -> calls.add(2);
		canvas.addListener(SWT.Selection, event -> {
			calls.add(1);
			canvas.removeListener(SWT.Selection, second);
		});
		canvas.addListener(SWT.Selection, second);
		canvas.notifyListeners(SWT.Selection, null);
		assertEquals(List.of(1), calls);
	}

	@Test void removingAnEarlierChildPreservesIdentityAndBounds() {
		Canvas first = canvas(shell);
		Canvas second = canvas(shell);
		Canvas third = canvas(shell);
		second.setBounds(13, 17, 23, 29);
		Object data = new Object();
		second.setData("identity", data);
		first.dispose();
		assertArrayEquals(new Canvas[] {second, third}, shell.getChildren());
		assertEquals(new Point(13, 17), second.getLocation());
		assertEquals(new Point(23, 29), second.getSize());
		assertSame(data, second.getData("identity"));
	}

	@Test void paintGcExpiresAndNestedRedrawDeferralIsBalanced() {
		Canvas canvas = canvas(shell);
		AtomicInteger paints = new AtomicInteger();
		AtomicReference<GC> retained = new AtomicReference<>();
		canvas.addPaintListener(event -> { paints.incrementAndGet(); retained.set(event.gc); });
		drain();
		assertTrue(retained.get().isDisposed());
		int before = paints.get();
		canvas.setRedraw(false);
		canvas.setRedraw(false);
		canvas.redraw();
		drain();
		canvas.setRedraw(true);
		drain();
		assertEquals(before, paints.get());
		canvas.setRedraw(true);
		drain();
		assertEquals(before + 1, paints.get());
	}

	@Test void hiddenControlsRetainDamageUntilTheyBecomeVisible() {
		Canvas canvas = canvas(shell);
		AtomicInteger paints = new AtomicInteger();
		canvas.addPaintListener(event -> paints.incrementAndGet());
		drain();
		int before = paints.get();
		canvas.setVisible(false);
		canvas.redraw();
		drain();
		assertEquals(before, paints.get());
		canvas.setVisible(true);
		drain();
		assertEquals(before + 1, paints.get());
	}

	@Test void originalAndroidAndIosVirtualListDragDoesNotWrapSignedCoordinates() {
		final int logicalRows = 1_000_000_000;
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List(shell, SWT.VIRTUAL | SWT.MULTI);
		list.setBounds(0, 0, 120, 120);
		list.setItemCount(logicalRows);
		int nativePeerCount = platform.controls.size();

		pointerDrag(list, Integer.MAX_VALUE, Integer.MIN_VALUE);
		assertTrue(list.getTopIndex() > 0, "Upward drag must scroll forward even across int wrap");
		assertTrue(list.getTopIndex() < logicalRows);
		assertEquals(0, list.getSelectionCount());
		assertEquals(nativePeerCount, platform.controls.size(), "The original List owns one native peer");
		pointerDrag(list, Integer.MIN_VALUE, Integer.MAX_VALUE);
		assertTrue(list.getTopIndex() <= 1, "Reverse drag returns to the first row within quantization");
		assertEquals(0, list.getSelectionCount());
	}

	@Test void originalAndroidAndIosVirtualTableDragDoesNotWrapSignedCoordinates() {
		final int logicalRows = 1_000_000_000;
		org.eclipse.swt.widgets.Table table = new org.eclipse.swt.widgets.Table(shell, SWT.VIRTUAL | SWT.MULTI);
		table.setBounds(0, 0, 120, 120);
		table.setItemCount(logicalRows);
		int nativePeerCount = platform.controls.size();

		pointerDrag(table, Integer.MAX_VALUE, Integer.MIN_VALUE);
		assertTrue(table.getTopIndex() > 0, "Upward drag must scroll forward even across int wrap");
		assertTrue(table.getTopIndex() < logicalRows);
		assertEquals(0, table.getSelectionCount());
		assertEquals(nativePeerCount, platform.controls.size(), "The original Table owns one native peer");
		pointerDrag(table, Integer.MIN_VALUE, Integer.MAX_VALUE);
		assertTrue(table.getTopIndex() <= 1, "Reverse drag returns to the first row within quantization");
		assertEquals(0, table.getSelectionCount());
	}

	private static void pointerDrag(org.eclipse.swt.widgets.Control control, int fromY, int toY) {
		org.eclipse.swt.widgets.Event down = new org.eclipse.swt.widgets.Event();
		down.button = 1;
		down.y = fromY;
		control.notifyListeners(SWT.MouseDown, down);
		org.eclipse.swt.widgets.Event move = new org.eclipse.swt.widgets.Event();
		move.y = toY;
		control.notifyListeners(SWT.MouseMove, move);
		org.eclipse.swt.widgets.Event up = new org.eclipse.swt.widgets.Event();
		up.button = 1;
		up.y = toY;
		control.notifyListeners(SWT.MouseUp, up);
	}


	@Test void indexedNativePeersPreserveOriginalDispatchAndDisposal() {
		platform.next = 0x1000_0001_0000_0001L;
		final int size = 384;
		Canvas[] controls = new Canvas[size];
		long[] peers = new long[size];
		int[] notifications = new int[size];
		for (int i = 0; i < size; i++) {
			Canvas current = canvas(shell);
			int at = i;
			current.addListener(SWT.MouseUp, event -> notifications[at]++);
			controls[i] = current;
			peers[i] = platform.lastCreated;
			assertSame(current, display.findWidget(peers[i]), "Native token preserves original widget identity");
		}
		assertNull(display.findWidget(0));
		assertNull(display.findWidget(-1));
		for (long peer : peers)
			platform.sink.input(peer, SWT.MouseUp, 3, 4, 1, 0, '\0', 0, 1, 7);
		drain();
		for (int calls : notifications) assertEquals(1, calls);
		for (int i = 0; i < size; i += 3) {
			controls[i].dispose();
			assertNull(display.findWidget(peers[i]), "Disposed peer must not resolve");
			platform.sink.input(peers[i], SWT.MouseUp, 3, 4, 1, 0, '\0', 0, 1, 8);
		}
		drain();
		for (int calls : notifications) assertEquals(1, calls);
		for (int i = 0; i < size; i++)
			if (i % 3 != 0) assertSame(controls[i], display.findWidget(peers[i]));
	}

	@Test void recycledNativePeerSlotCannotReceiveStaleQueuedEvents() {
		platform.next = 0x1000_0001_0000_0029L;
		Canvas removed = canvas(shell);
		long oldPeer = platform.lastCreated;
		AtomicInteger removedCalls = new AtomicInteger();
		removed.addListener(SWT.MouseUp, event -> removedCalls.incrementAndGet());
		platform.sink.input(oldPeer, SWT.MouseUp, 1, 2, 1, 0, '\0', 0, 1, 1);
		removed.dispose();
		platform.next = oldPeer + (1L << 32);
		Canvas replacement = canvas(shell);
		long newPeer = platform.lastCreated;
		assertEquals(oldPeer & 0xffff_ffffL, newPeer & 0xffff_ffffL);
		AtomicInteger replacementCalls = new AtomicInteger();
		replacement.addListener(SWT.MouseUp, event -> replacementCalls.incrementAndGet());
		assertNull(display.findWidget(oldPeer));
		assertSame(replacement, display.findWidget(newPeer));
		platform.sink.input(oldPeer, SWT.MouseUp, 1, 2, 1, 0, '\0', 0, 1, 2);
		platform.sink.input(newPeer, SWT.MouseUp, 1, 2, 1, 0, '\0', 0, 1, 3);
		drain();
		assertEquals(0, removedCalls.get());
		assertEquals(1, replacementCalls.get());
	}

	@Test void staleNativePeerCannotActivateADisposedWidget() {
		Canvas canvas = canvas(shell);
		long peer = platform.lastCreated;
		AtomicInteger calls = new AtomicInteger();
		canvas.addListener(SWT.MouseUp, event -> calls.incrementAndGet());
		canvas.dispose();
		platform.sink.input(peer, SWT.MouseUp, 3, 4, 1, 0, '\0', 0, 1, 1);
		platform.sink.accessibility(peer, MobilePlatform.ACTION_ACTIVATE);
		drain();
		assertEquals(0, calls.get());
	}
}
