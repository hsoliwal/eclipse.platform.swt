/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.mobile.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Public SWT contract tests. The recording platform qualifies Java semantics only, not native
 * pixels.
 */
public class MobileDisplayTest {
	private RecordingPlatform platform;
	private Display display;
	private Shell shell;

	@BeforeEach
	void create() {
		platform = new RecordingPlatform();
		MobilePlatform.bind(platform);
		display = new Display();
		shell = new Shell(display);
		shell.open();
		drain();
	}

	@AfterEach
	void cleanup() {
		if (display != null && !display.isDisposed()) display.dispose();
	}

	private void drain() {
		for (int i = 0; i < 5000; i++) {
			if (display.isDisposed() || !display.readAndDispatch()) return;
		}
		fail("Event queue failed to quiesce");
	}

	private Canvas canvas() {
		Canvas c = new Canvas(shell, SWT.NONE);
		c.setBounds(20, 30, 80, 60);
		return c;
	}

	@Test
	void ownerThreadAndNativeQueueAreSeparate() throws Exception {
		Canvas c = canvas();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread t =
				new Thread(
						() -> {
							assertNull(Display.getCurrent());
							try {
								c.getBounds();
							} catch (Throwable e) {
								failure.set(e);
							}
							platform.sink.resize(360, 640);
						});
		t.start();
		t.join(2000);
		assertFalse(t.isAlive());
		assertInstanceOf(SWTException.class, failure.get());
		assertEquals(SWT.ERROR_THREAD_INVALID_ACCESS, ((SWTException) failure.get()).code);
		assertEquals(320, shell.getSize().x);
		drain();
		assertEquals(new Point(360, 640), shell.getSize());
		assertSame(display, Display.getCurrent());
	}

	@Test
	void queuedCallsKeepFifoAndDoNotExecuteInline() {
		List<Integer> calls = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			final int n = i;
			display.asyncExec(() -> calls.add(n));
		}
		assertTrue(calls.isEmpty());
		drain();
		assertEquals(java.util.stream.IntStream.range(0, 50).boxed().toList(), calls);
	}

	@Test
	void timerReplacementAndCancellationUseRunnableIdentity() {
		AtomicInteger calls = new AtomicInteger();
		Runnable r = calls::incrementAndGet;
		display.timerExec(0, r);
		display.timerExec(0, r);
		drain();
		assertEquals(1, calls.get());
		display.timerExec(0, r);
		display.timerExec(-1, r);
		drain();
		assertEquals(1, calls.get());
	}

	@Test
	void queuedWorkCannotStarveFramesOrDueTimers() {
		AtomicInteger calls = new AtomicInteger();
		AtomicInteger timer = new AtomicInteger();
		Runnable[] producer = new Runnable[1];
		producer[0] =
				() -> {
					calls.incrementAndGet();
					if (calls.get() < 100) display.asyncExec(producer[0]);
				};
		display.asyncExec(producer[0]);
		display.timerExec(0, timer::incrementAndGet);
		int frames = platform.frames;
		shell.redraw();
		for (int i = 0; i < 34; i++) display.readAndDispatch();
		assertTrue(platform.frames > frames);
		assertEquals(1, timer.get());
		drain();
		assertEquals(100, calls.get());
	}

	@Test
	void inputCoordinatesAndFocusUseOriginalControlOwners() {
		Canvas c = canvas();
		AtomicReference<Event> event = new AtomicReference<>();
		c.addListener(SWT.MouseDown, event::set);
		platform.sink.pointer(SWT.MouseDown, 25, 37, 1, 0, 42);
		assertNull(event.get());
		drain();
		assertSame(c, event.get().widget);
		assertEquals(5, event.get().x);
		assertEquals(7, event.get().y);
		assertSame(c, display.getFocusControl());
	}

	@Test
	void nativeCancellationNeverSynthesizesSelectionOrMouseUp() {
		Canvas c = canvas();
		AtomicInteger ups = new AtomicInteger();
		c.addListener(SWT.MouseUp, e -> ups.incrementAndGet());
		platform.sink.pointer(SWT.MouseDown, 25, 37, 1, 0, 1);
		platform.sink.pointer(SWT.MouseExit, 25, 37, 0, 0, 2);
		platform.sink.pointer(SWT.MouseUp, 300, 400, 1, 0, 3);
		drain();
		assertEquals(0, ups.get());
	}

	@Test
	void nativeHoverEnterIsDeliveredOnce() {
		Canvas c = canvas();
		AtomicInteger enters = new AtomicInteger();
		c.addListener(SWT.MouseEnter, e -> enters.incrementAndGet());
		platform.sink.pointer(SWT.MouseEnter, 25, 37, 0, 0, 1);
		platform.sink.pointer(SWT.MouseDown, 25, 37, 1, 0, 2);
		drain();
		assertEquals(1, enters.get());
	}

	@Test
	void namedSystemPrimariesUseTheirSwtValues() {
		assertEquals(new RGB(0, 0, 255), display.getSystemColor(SWT.COLOR_BLUE).getRGB());
		assertEquals(new RGB(255, 0, 0), display.getSystemColor(SWT.COLOR_RED).getRGB());
		assertEquals(new RGB(0, 255, 0), display.getSystemColor(SWT.COLOR_GREEN).getRGB());
	}

	@Test
	void disabledAncestorBlocksNativePointerAndKeyboard() {
		Composite p = new Composite(shell, SWT.NONE);
		p.setBounds(0, 0, 100, 100);
		Canvas c = new Canvas(p, SWT.NONE);
		c.setBounds(0, 0, 80, 80);
		AtomicInteger count = new AtomicInteger();
		c.addListener(SWT.MouseUp, e -> count.incrementAndGet());
		c.addListener(SWT.KeyUp, e -> count.incrementAndGet());
		c.setFocus();
		p.setEnabled(false);
		platform.sink.pointer(SWT.MouseUp, 10, 10, 1, 0, 1);
		platform.sink.key(SWT.KeyUp, ' ', ' ', 0, 2);
		drain();
		assertEquals(0, count.get());
		assertNull(display.getFocusControl());
	}

	@Test
	void selectionVetoTravelsThroughExistingTypedListener() {
		Canvas c = canvas();
		SelectionListener listener = SelectionListener.widgetSelectedAdapter(e -> e.doit = false);
		c.addTypedListener(listener, SWT.Selection);
		Event e = new Event();
		c.notifyListeners(SWT.Selection, e);
		assertFalse(e.doit);
		assertEquals(1, c.getTypedListeners(SWT.Selection, SelectionListener.class).count());
		c.removeTypedListener(SWT.Selection, listener);
		assertEquals(0, c.getListeners(SWT.Selection).length);
	}

	@Test
	void commonEventTablePreservesRemovalDuringDispatch() {
		Canvas c = canvas();
		List<Integer> calls = new ArrayList<>();
		Listener second = e -> calls.add(2);
		c.addListener(
				SWT.Selection,
				e -> {
					calls.add(1);
					c.removeListener(SWT.Selection, second);
				});
		c.addListener(SWT.Selection, second);
		c.notifyListeners(SWT.Selection, new Event());
		assertEquals(List.of(1), calls);
	}

	@Test
	void listenerErrorsDoNotSkipRemainingDisposeCleanup() {
		Canvas c = canvas();
		AtomicInteger completed = new AtomicInteger();
		c.addDisposeListener(
				e -> {
					throw new IllegalStateException("failure");
				});
		c.addDisposeListener(e -> completed.incrementAndGet());
		assertThrows(IllegalStateException.class, c::dispose);
		assertTrue(c.isDisposed());
		assertEquals(1, completed.get());
		assertEquals(0, shell.getChildren().length);
	}

	@Test
	void failingFocusOutCannotRetainDisposedChild() {
		Canvas c = canvas();
		c.setFocus();
		c.addListener(
				SWT.FocusOut,
				e -> {
					throw new IllegalArgumentException("focus");
				});
		assertThrows(IllegalArgumentException.class, c::dispose);
		assertTrue(c.isDisposed());
		assertEquals(0, shell.getChildren().length);
	}

	@Test
	void childCannotEscapeDisposingParent() {
		Composite p = new Composite(shell, SWT.NONE);
		Canvas c = new Canvas(p, SWT.NONE);
		AtomicReference<Canvas> escaped = new AtomicReference<>();
		c.addDisposeListener(e -> escaped.set(new Canvas(p, SWT.NONE)));
		assertThrows(SWTException.class, p::dispose);
		assertNull(escaped.get());
		assertTrue(p.isDisposed());
		assertTrue(c.isDisposed());
		assertEquals(0, shell.getChildren().length);
	}

	@Test
	void failingDisplayCallbacksStillDisposeUnbindAndReleaseNativeHost() {
		display.disposeExec(() -> display.disposeExec(() -> {}));
		assertThrows(SWTException.class, display::dispose);
		assertTrue(display.isDisposed());
		assertTrue(shell.isDisposed());
		assertNull(Display.getCurrent());
		assertEquals(1, platform.disposals);
		assertThrows(IllegalStateException.class, MobilePlatform::current);
	}

	@Test
	void shellCannotEscapeDisplayDisposal() {
		display.disposeExec(() -> new Shell(display));
		assertThrows(SWTException.class, display::dispose);
		assertTrue(display.isDisposed());
		assertNull(Display.getCurrent());
	}

	@Test
	void showListenerMayDisposeShellWithoutUseAfterDispose() {
		Shell other = new Shell(display);
		other.addListener(SWT.Show, e -> other.dispose());
		assertDoesNotThrow(other::open);
		assertTrue(other.isDisposed());
	}

	@Test
	void focusListenerMayDisposeOnDisable() {
		Canvas c = canvas();
		c.setFocus();
		c.addListener(SWT.FocusOut, e -> c.dispose());
		assertDoesNotThrow(() -> c.setEnabled(false));
		assertTrue(c.isDisposed());
	}

	@Test
	void parentLaneCompactionPreservesExposedIdentityAndBounds() {
		Canvas a = canvas(), b = canvas(), c = canvas();
		b.setBounds(100, 110, 120, 130);
		c.setData("stable");
		a.dispose();
		assertSame(b, shell.getChildren()[0]);
		assertSame(c, shell.getChildren()[1]);
		assertEquals(new Rectangle(100, 110, 120, 130), b.getBounds());
		assertEquals("stable", c.getData());
	}

	@Test
	void borderClientCoordinatesAreConsistent() {
		Composite p = new Composite(shell, SWT.BORDER);
		p.setBounds(10, 20, 100, 80);
		Canvas c = new Canvas(p, SWT.BORDER);
		c.setBounds(4, 5, 40, 30);
		assertEquals(new Rectangle(0, 0, 98, 78), p.getClientArea());
		assertEquals(new Point(16, 27), c.toDisplay(0, 0));
		assertEquals(new Point(0, 0), c.toControl(16, 27));
		assertEquals(new Rectangle(-1, -1, 42, 32), c.computeTrim(0, 0, 40, 30));
	}

	@Test
	void paintGcExpiresAndRepaintLockDefersCallbacks() {
		Canvas c = canvas();
		AtomicInteger paints = new AtomicInteger();
		AtomicReference<GC> gc = new AtomicReference<>();
		c.addPaintListener(
				e -> {
					paints.incrementAndGet();
					gc.set(e.gc);
					e.gc.fillOval(0, 0, 10, 10);
				});
		drain();
		assertEquals(1, paints.get());
		assertTrue(gc.get().isDisposed());
		c.setRedraw(false);
		c.redraw();
		drain();
		assertEquals(1, paints.get());
		c.setRedraw(true);
		drain();
		assertEquals(2, paints.get());
	}

	@Test
	void hiddenControlsReceiveNoPaint() {
		Canvas c = canvas();
		AtomicInteger paints = new AtomicInteger();
		c.addPaintListener(e -> paints.incrementAndGet());
		c.setVisible(false);
		drain();
		assertEquals(0, paints.get());
		c.setVisible(true);
		drain();
		assertEquals(1, paints.get());
	}

	@Test
	void gcClippingAlphaAndAntialiasAreTransported() {
		Canvas c = canvas();
		c.addPaintListener(
				e -> {
					e.gc.setClipping(2, 3, 5, 7);
					assertTrue(e.gc.isClipped());
					e.gc.setAlpha(128);
					e.gc.setAntialias(SWT.OFF);
					e.gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
					e.gc.fillRectangle(0, 0, 20, 20);
					e.gc.setClipping((Rectangle) null);
					assertFalse(e.gc.isClipped());
				});
		drain();
		MobileFrame f = platform.last;
		boolean found = false;
		for (int i = 0; i < f.size(); i++)
			if (f.operation(i) == MobileFrame.FILL_RECT && f.color(i) == 0x80ff0000) {
				assertEquals(SWT.OFF, f.antialias(i));
				found = true;
			}
		assertTrue(found);
	}

	@Test
	void externalGcCommandsPersistUntilInvalidation() {
		Canvas c = canvas();
		drain();
		GC gc = new GC(c);
		gc.setBackground(display.getSystemColor(SWT.COLOR_GREEN));
		gc.fillRectangle(1, 1, 7, 9);
		gc.dispose();
		drain();
		assertTrue(hasColor(platform.last, 0xff00ff00));
		c.redraw();
		drain();
		assertFalse(hasColor(platform.last, 0xff00ff00));
	}

	@Test
	void immutableFrameRejectsMutationAndUnbalancedCommands() {
		MobileFrame.Builder b = new MobileFrame.Builder();
		b.shape(MobileFrame.FILL_RECT, 0xff112233, 0, 1, 2, 3, 4, 0, 0);
		MobileFrame f = b.build(20, 20);
		f.colors()[0] = 0;
		f.coordinates()[0] = 99;
		b.shape(MobileFrame.FILL_RECT, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(1, f.size());
		assertEquals(0xff112233, f.color(0));
		assertEquals(1, f.coordinate(0, 0));
		b.save();
		assertThrows(IllegalStateException.class, () -> b.build(20, 20));
		assertThrows(IndexOutOfBoundsException.class, () -> f.coordinate(0, 8));
	}

	@Test
	void nativeCloseReleasesDisplayAndSubtree() {
		Canvas c = canvas();
		platform.sink.close();
		drain();
		assertTrue(display.isDisposed());
		assertTrue(shell.isDisposed());
		assertTrue(c.isDisposed());
		assertEquals(1, platform.disposals);
		platform.sink.pointer(SWT.MouseUp, 25, 37, 1, 0, 1);
		assertTrue(display.isDisposed());
	}

	@Test
	void displayThreadSleepAndSyncExecHaveNoLostWake() throws Exception {
		CompletableFuture<Display> ready = new CompletableFuture<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		RecordingPlatform p = new RecordingPlatform();
		Thread loop =
				new Thread(
						() -> {
							MobilePlatform.bind(p);
							try {
								Display d = new Display();
								ready.complete(d);
								while (!d.isDisposed()) {
									if (!d.readAndDispatch()) d.sleep();
								}
							} catch (Throwable e) {
								failure.set(e);
							}
						},
						"SWT-Test-Display");
		loop.start();
		Display d = ready.get(3, TimeUnit.SECONDS);
		try {
			AtomicReference<Thread> executor = new AtomicReference<>();
			assertTimeoutPreemptively(
					Duration.ofSeconds(3),
					() -> d.syncExec(() -> executor.set(Thread.currentThread())));
			assertSame(loop, executor.get());
			assertThrows(
					SWTException.class,
					() ->
							d.syncExec(
									() -> {
										throw new IllegalArgumentException("callback");
									}));
			d.syncExec(() -> {});
			d.asyncExec(d::dispose);
			loop.join(3000);
			assertFalse(loop.isAlive());
			assertNull(failure.get());
		} finally {
			if (!d.isDisposed()) d.asyncExec(d::dispose);
			loop.join(3000);
		}
	}

	@Test
	void queuedSyncCallerIsReleasedWhenDisplayIsDisposed() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		AtomicReference<Throwable> result = new AtomicReference<>();
		Thread caller =
				new Thread(
						() -> {
							started.countDown();
							try {
								display.syncExec(
										() ->
												fail(
														"Disposed Display must not run queued sync"
															+ " callback"));
							} catch (Throwable e) {
								result.set(e);
							}
						});
		caller.start();
		assertTrue(started.await(2, TimeUnit.SECONDS));
		long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (caller.getState() != Thread.State.WAITING && System.nanoTime() < end)
			Thread.onSpinWait();
		assertEquals(Thread.State.WAITING, caller.getState());
		display.dispose();
		caller.join(2000);
		assertFalse(caller.isAlive());
		assertInstanceOf(SWTException.class, result.get());
	}

	private static boolean hasColor(MobileFrame f, int argb) {
		for (int i = 0; i < f.size(); i++) if (f.color(i) == argb) return true;
		return false;
	}

	static final class RecordingPlatform extends MobilePlatform {
		MobileEventSink sink;
		MobileFrame last;
		int frames, disposals;

		public String name() {
			return "recording-contract-test";
		}

		public int width() {
			return 320;
		}

		public int height() {
			return 480;
		}

		public void setEventSink(MobileEventSink sink) {
			this.sink = sink;
		}

		public void submit(MobileFrame frame) {
			last = frame;
			frames++;
		}

		public int[] measureText(String text, String font, int pixels, int style) {
			return new int[] {text.length() * pixels / 2, pixels, pixels * 3 / 4};
		}

		public void setVisible(boolean visible) {}

		public void dispose() {
			disposals++;
		}
	}
}
