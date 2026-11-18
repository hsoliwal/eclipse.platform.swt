/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.Test;

/** Portable owner contracts; these do not qualify Android or iOS native pixels. */
public class MobileBorderContractJUnitTest {
	@Test void legacyTransportAndAbsentBordersRefuseWithoutChanges() {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Canvas bordered = new Canvas(shell, SWT.BORDER);
			Canvas plain = new Canvas(shell, SWT.NONE);
			assertEquals(SWT.DEFAULT, bordered.getBorderStyle());
			bordered.setBorderStyle(SWT.DEFAULT);
			plain.setBorderStyle(SWT.DEFAULT);
			assertEquals(0, platform.borderStyleAttempts);
			assertEquals(SWT.ERROR_NOT_IMPLEMENTED,
					assertThrows(SWTError.class, () -> bordered.setBorderStyle(SWT.SHADOW_IN)).code);
			assertEquals(1, platform.borderStyleAttempts);
			assertEquals(SWT.ERROR_NOT_IMPLEMENTED,
					assertThrows(SWTError.class, () -> plain.setBorderStyle(SWT.SHADOW_OUT)).code);
			assertThrows(IllegalArgumentException.class, () -> bordered.setBorderStyle(SWT.BORDER));
			assertEquals(1, platform.borderStyleAttempts);
			assertEquals(SWT.DEFAULT, bordered.getBorderStyle());
			assertEquals(SWT.DEFAULT, plain.getBorderStyle());
			assertEquals(0, platform.borderChanges);
		} finally {
			try { display.dispose(); } finally { MobileRuntime.uninstall(platform); }
		}
	}

	@Test void nativeBorderChangesPreservePeerGeometryAndFailureState() {
		ContractPlatform platform = new ContractPlatform();
		platform.borderStylesSupported = true;
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Canvas canvas = new Canvas(shell, SWT.BORDER);
			long peer = platform.lastCreated;
			canvas.setBounds(7, 9, 31, 19);
			Rectangle bounds = canvas.getBounds(), client = canvas.getClientArea();
			Rectangle trim = canvas.computeTrim(0, 0, 13, 11);
			Point origin = canvas.toDisplay(0, 0);
			int style = canvas.getStyle(), border = canvas.getBorderWidth();
			AtomicInteger resized = new AtomicInteger(), painted = new AtomicInteger();
			canvas.addListener(SWT.Resize, event -> resized.incrementAndGet());
			canvas.addListener(SWT.Paint, event -> painted.incrementAndGet());
			for (int appearance : new int[] {SWT.SHADOW_IN, SWT.SHADOW_OUT, SWT.DEFAULT}) {
				canvas.setBorderStyle(appearance);
				assertEquals(appearance, canvas.getBorderStyle());
				assertEquals(appearance, platform.controls.get(peer).borderStyle);
				assertEquals(bounds, canvas.getBounds());
				assertEquals(client, canvas.getClientArea());
				assertEquals(trim, canvas.computeTrim(0, 0, 13, 11));
				assertEquals(origin, canvas.toDisplay(0, 0));
				assertEquals(border, canvas.getBorderWidth());
				assertEquals(style, canvas.getStyle());
				assertEquals(peer, platform.lastCreated);
				canvas.setBorderStyle(appearance);
			}
			assertEquals(3, platform.borderChanges);
			canvas.setBorderStyle(SWT.SHADOW_OUT);
			int attempts = platform.borderStyleAttempts;
			for (int etched : new int[] {SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT}) {
				assertEquals(SWT.ERROR_NOT_IMPLEMENTED,
						assertThrows(SWTError.class, () -> canvas.setBorderStyle(etched)).code);
				assertEquals(SWT.SHADOW_OUT, canvas.getBorderStyle());
			}
			assertEquals(attempts, platform.borderStyleAttempts);
			platform.failNextBorderChange = true;
			assertThrows(IllegalStateException.class, () -> canvas.setBorderStyle(SWT.SHADOW_IN));
			assertEquals(SWT.SHADOW_OUT, canvas.getBorderStyle());
			assertEquals(SWT.SHADOW_OUT, platform.controls.get(peer).borderStyle);
			assertEquals(0, resized.get());
			assertEquals(0, painted.get());
			assertEquals(0, platform.createdImages);
		} finally {
			try { display.dispose(); } finally { MobileRuntime.uninstall(platform); }
		}
	}

	@Test void printingUsesSelectedBorderWithoutChangingClientPixels() {
		ContractPlatform platform = new ContractPlatform();
		platform.borderStylesSupported = true;
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Canvas canvas = new Canvas(shell, SWT.BORDER);
			canvas.setSize(9, 7);
			canvas.addPaintListener(event -> {
				event.gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
				event.gc.fillRectangle(canvas.getClientArea());
			});
			shell.open();
			while (display.readAndDispatch()) { }
			Image output = new Image(display, 9, 7);
			GC gc = new GC(output);
			try {
				for (int appearance : new int[] {SWT.DEFAULT, SWT.SHADOW_IN, SWT.SHADOW_OUT, SWT.DEFAULT}) {
					canvas.setBorderStyle(appearance);
					Color original = gc.getBackground();
					Rectangle clip = gc.getClipping();
					assertTrue(canvas.print(gc));
					assertEquals(original, gc.getBackground());
					assertEquals(clip, gc.getClipping());
					ImageData pixels = output.getImageData();
					for (int y = 0; y < 7; y++) for (int x = 0; x < 9; x++) {
						boolean leading = y == 0 || x == 0 && y != 6;
						boolean trailing = y == 6 || x == 8;
						RGB expected = !leading && !trailing ? new RGB(255, 0, 0)
								: appearance == SWT.SHADOW_OUT && leading || appearance == SWT.SHADOW_IN && !leading
										? new RGB(255, 255, 255) : new RGB(128, 128, 128);
						assertEquals(expected, pixels.palette.getRGB(pixels.getPixel(x, y)),
								"appearance=" + appearance + " pixel=" + x + "," + y);
					}
				}
			} finally {
				gc.dispose(); output.dispose();
			}
		} finally {
			try { display.dispose(); } finally { MobileRuntime.uninstall(platform); }
		}
	}

	@Test void borderAccessUsesOriginalThreadAndLifetimeChecks() throws Exception {
		ContractPlatform platform = new ContractPlatform();
		MobileRuntime.install(platform);
		Display display = new Display();
		try {
			Canvas canvas = new Canvas(new Shell(display), SWT.BORDER);
			AtomicReference<Throwable> getFailure = new AtomicReference<>(), setFailure = new AtomicReference<>();
			Thread worker = new Thread(() -> {
				try { canvas.getBorderStyle(); } catch (Throwable failure) { getFailure.set(failure); }
				try { canvas.setBorderStyle(SWT.DEFAULT); } catch (Throwable failure) { setFailure.set(failure); }
			});
			worker.start(); worker.join(3000);
			assertFalse(worker.isAlive());
			assertEquals(SWT.ERROR_THREAD_INVALID_ACCESS, assertInstanceOf(SWTException.class, getFailure.get()).code);
			assertEquals(SWT.ERROR_THREAD_INVALID_ACCESS, assertInstanceOf(SWTException.class, setFailure.get()).code);
			canvas.dispose();
			assertEquals(SWT.ERROR_WIDGET_DISPOSED,
					assertThrows(SWTException.class, canvas::getBorderStyle).code);
			assertEquals(SWT.ERROR_WIDGET_DISPOSED,
					assertThrows(SWTException.class, () -> canvas.setBorderStyle(SWT.DEFAULT)).code);
		} finally {
			try { display.dispose(); } finally { MobileRuntime.uninstall(platform); }
		}
	}
}
