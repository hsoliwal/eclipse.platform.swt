/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.Display;

/** SWT owner contracts. This test transport does not qualify Android or UIKit. */
public final class MobileGraphicsContractTest {

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	/* Reflection makes missing public methods executable preimage regressions. */
	private static Object invoke(GC gc, String name, int... arguments) {
		Class<?>[] types = new Class<?>[arguments.length];
		Arrays.fill(types, int.class);
		Object[] values = Arrays.stream(arguments).boxed().toArray();
		try {
			return GC.class.getMethod(name, types).invoke(gc, values);
		} catch (InvocationTargetException failure) {
			Throwable cause = failure.getCause();
			if (cause instanceof RuntimeException exception) throw exception;
			if (cause instanceof Error error) throw error;
			throw new AssertionError(cause);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("Missing public mobile GC method: " + name, failure);
		}
	}

	private static final class Fixture implements AutoCloseable {
		final ContractPlatform platform = new ContractPlatform();
		final Display display;
		final Image image;
		final GC gc;

		Fixture(int width, int height) {
			MobileRuntime.install(platform);
			display = new Display();
			image = new Image(display, width, height);
			gc = new GC(image);
			gc.setForeground(display.getSystemColor(SWT.COLOR_BLUE));
			gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			gc.setAntialias(SWT.OFF);
		}

		@Override public void close() {
			try {
				gc.dispose();
				image.dispose();
				display.dispose();
			} finally {
				MobileRuntime.uninstall(platform);
			}
			require(platform.controls.isEmpty() && platform.images.isEmpty(), "Graphics tests release every native owner");
			require(platform.openedGraphics == platform.closedGraphics, "Graphics tests balance native GC leases");
		}
	}

	private static int rgb(Image image, int x, int y) {
		ImageData data = image.getImageData();
		RGB rgb = data.palette.getRGB(data.getPixel(x, y));
		return rgb.red << 16 | rgb.green << 8 | rgb.blue;
	}

	private static int[] pixels(Image image) {
		ImageData data = image.getImageData();
		int[] result = new int[data.width * data.height];
		for (int y = 0; y < data.height; y++) {
			for (int x = 0; x < data.width; x++) {
				RGB rgb = data.palette.getRGB(data.getPixel(x, y));
				result[y * data.width + x] = rgb.red << 16 | rgb.green << 8 | rgb.blue;
			}
		}
		return result;
	}

	static void testRoundedRectangles() {
		try (Fixture f = new Fixture(64, 64)) {
			invoke(f.gc, "fillRoundRectangle", 24, 28, -20, -16, -12, -10);
			require(rgb(f.image, 14, 20) == 0xff0000, "Negative dimensions normalize to the same filled rounded rectangle");
			require(rgb(f.image, 4, 12) == 0xffffff, "Rounded corner excludes its bounding-box corner");
			require(rgb(f.image, 4, 20) == 0xff0000, "Rounded edge reaches the side midpoint");
			invoke(f.gc, "drawRoundRectangle", 30, 10, 22, 20, 8, 8);
			require(rgb(f.image, 41, 10) == 0x0000ff, "Rounded outline uses the foreground");
			require(rgb(f.image, 41, 20) == 0xffffff, "Rounded outline leaves its interior unfilled");
			invoke(f.gc, "fillRoundRectangle", 4, 36, 20, 16, Integer.MIN_VALUE, Integer.MAX_VALUE);
			require(rgb(f.image, 14, 44) == 0xff0000 && rgb(f.image, 4, 36) == 0xffffff,
					"Extreme signed corner diameters clamp without integer overflow");
			invoke(f.gc, "fillRoundRectangle", 30, 36, 10, 10, 0, 4);
			require(rgb(f.image, 30, 36) == 0xff0000, "A zero corner diameter produces an ordinary rectangle");
		}
	}

	static void testArcs() {
		try (Fixture f = new Fixture(64, 64)) {
			invoke(f.gc, "fillArc", 28, 28, -24, -24, 0, 90);
			require(rgb(f.image, 21, 10) == 0xff0000, "Positive SWT arc fills the upper-right quadrant");
			require(rgb(f.image, 21, 22) == 0xffffff && rgb(f.image, 10, 10) == 0xffffff,
					"A positive quarter arc fills neither the lower-right nor upper-left quadrant");
			invoke(f.gc, "fillArc", 34, 4, 24, 24, 0, -90);
			require(rgb(f.image, 51, 22) == 0xff0000 && rgb(f.image, 51, 10) == 0xffffff,
					"Negative SWT arc sweeps clockwise into the lower-right quadrant");
			invoke(f.gc, "drawArc", 4, 34, 24, 24, 0, 90);
			require(rgb(f.image, 16, 34) == 0x0000ff && rgb(f.image, 16, 46) == 0xffffff,
					"Arc outline uses the foreground and does not draw a pie wedge");
			invoke(f.gc, "fillArc", 34, 34, 24, 24, 72, -720);
			require(rgb(f.image, 46, 46) == 0xff0000 && rgb(f.image, 36, 46) == 0xff0000
					&& rgb(f.image, 46, 36) == 0xff0000, "A full negative sweep fills an entire ellipse");
			int[] before = pixels(f.image);
			invoke(f.gc, "drawArc", 0, 0, 20, 20, 0, 0);
			invoke(f.gc, "fillArc", 0, 0, 0, 20, 0, 90);
			invoke(f.gc, "drawArc", 0, 0, 20, 0, 0, -90);
			require(Arrays.equals(before, pixels(f.image)), "Zero sweeps and empty arc bounds do not paint");
		}
		try (Fixture arc = new Fixture(32, 32)) {
			invoke(arc.gc, "drawArc", 4, 4, 24, 24, Integer.MAX_VALUE, Integer.MIN_VALUE);
			int[] full = pixels(arc.image);
			arc.gc.setBackground(arc.display.getSystemColor(SWT.COLOR_WHITE));
			arc.gc.fillRectangle(0, 0, 32, 32);
			arc.gc.drawOval(4, 4, 24, 24);
			require(Arrays.equals(full, pixels(arc.image)), "Full signed arc extremes match the existing oval owner");
		}
	}

	static void testTextAntialias() {
		try (Fixture f = new Fixture(128, 32)) {
			require((Integer) invoke(f.gc, "getTextAntialias") == SWT.DEFAULT, "Text antialias starts at SWT.DEFAULT");
			invoke(f.gc, "setTextAntialias", SWT.OFF);
			f.gc.setAntialias(SWT.ON);
			require((Integer) invoke(f.gc, "getTextAntialias") == SWT.OFF, "Shape antialias does not replace text antialias");
			invoke(f.gc, "setTextAntialias", SWT.ON);
			require(f.gc.getAntialias() == SWT.ON, "Text antialias preserves shape antialias");
			try { invoke(f.gc, "setTextAntialias", 99); throw new AssertionError("Invalid text antialias accepted"); }
			catch (IllegalArgumentException expected) { }
			require((Integer) invoke(f.gc, "getTextAntialias") == SWT.ON, "Rejected text antialias leaves state intact");
			f.gc.setAdvanced(false);
			require((Integer) invoke(f.gc, "getTextAntialias") == SWT.DEFAULT && f.gc.getAntialias() == SWT.DEFAULT,
					"Disabling advanced graphics resets both independent antialias settings");
		}
		int[] plain = textPixels(SWT.OFF, SWT.OFF);
		require(Arrays.equals(plain, textPixels(SWT.ON, SWT.OFF)), "Shape antialias cannot change text-only pixels");
		int[] smooth = textPixels(SWT.OFF, SWT.ON);
		require(!Arrays.equals(plain, smooth), "Text antialias changes actual glyph edge pixels");
		require(Arrays.equals(smooth, textPixels(SWT.ON, SWT.ON)), "Antialiased text remains independent of shape mode");
	}

	private static int[] textPixels(int shapeMode, int textMode) {
		try (Fixture f = new Fixture(128, 32)) {
			f.gc.setForeground(f.display.getSystemColor(SWT.COLOR_BLACK));
			f.gc.setAntialias(shapeMode);
			invoke(f.gc, "setTextAntialias", textMode);
			f.gc.drawText("Ag/", 3, 3, true);
			return pixels(f.image);
		}
	}

	static void testAlphaMasking() {
		try (Fixture f = new Fixture(24, 8)) {
			f.gc.setAlpha(-1);
			require(f.gc.getAlpha() == 255, "Desktop SWT masks alpha -1 to 255");
			f.gc.fillRectangle(0, 0, 8, 8);
			f.gc.setAlpha(256);
			require(f.gc.getAlpha() == 0, "Desktop SWT masks alpha 256 to zero");
			f.gc.fillRectangle(8, 0, 8, 8);
			f.gc.setAlpha(511);
			require(f.gc.getAlpha() == 255, "Desktop SWT masks alpha 511 to 255");
			f.gc.fillRectangle(16, 0, 8, 8);
			require(rgb(f.image, 4, 4) == 0xff0000 && rgb(f.image, 12, 4) == 0xffffff
					&& rgb(f.image, 20, 4) == 0xff0000, "Masked alpha reaches the existing native drawing owner");
		}
	}

	static void testEffectiveClippingAndCopy() {
		try (Fixture f = new Fixture(40, 40)) {
			f.gc.setClipping(-4, 6, 20, 80);
			require(f.gc.getClipping().equals(new Rectangle(0, 6, 16, 34)), "Clipping query intersects the image bounds");
			invoke(f.gc, "fillRoundRectangle", -8, -8, 60, 60, 12, 12);
			require(rgb(f.image, 4, 8) == 0xff0000 && rgb(f.image, 20, 8) == 0xffffff
					&& rgb(f.image, 4, 4) == 0xffffff, "Rounded drawing respects effective clipping");
			Image copy = new Image(f.display, 8, 8);
			try {
				f.gc.copyArea(copy, 0, 6);
				require(rgb(copy, 3, 3) == 0xff0000, "Image-GC copy preserves the rendered clipped pixels");
				GC screen = new GC(f.display);
				try {
					try { screen.copyArea(copy, 0, 0); throw new AssertionError("Fixture screen capture fabricated a result"); }
					catch (UnsupportedOperationException expected) { }
				} finally { screen.dispose(); }
			} finally { copy.dispose(); }
			Transform transform = new Transform(f.display);
			try {
				transform.translate(10, 6);
				transform.scale(2, 2);
				f.gc.setTransform(transform);
				f.gc.setClipping((Rectangle) null);
				require(f.gc.getClipping().equals(new Rectangle(-5, -3, 20, 20)), "Clipping bounds are reported in current user coordinates");
				f.gc.setClipping(0, 0, 30, 30);
				require(f.gc.getClipping().equals(new Rectangle(0, 0, 15, 17)), "Transformed clipping includes the finite native image bounds");
				f.gc.setClipping(100, 100, 4, 4);
				require(f.gc.getClipping().isEmpty(), "Disjoint clipping has empty effective bounds");
				f.gc.setAdvanced(false);
				require(f.gc.getClipping().equals(new Rectangle(0, 0, 40, 40)) && !f.gc.isClipped(), "Advanced reset clears transform and explicit clipping");
			} finally { transform.dispose(); }
		}
	}

	static void testPublicHandleAndGuards() throws Exception {
		try (Fixture f = new Fixture(32, 32)) {
			var field = GC.class.getField("handle");
			require(field.getType() == long.class && field.getLong(f.gc) != 0, "Retained internal public handle names the live GC lease");
			Runnable[] operations = {
				() -> invoke(f.gc, "drawRoundRectangle", 0, 0, 10, 10, 2, 2),
				() -> invoke(f.gc, "fillRoundRectangle", 0, 0, 10, 10, 2, 2),
				() -> invoke(f.gc, "drawArc", 0, 0, 0, 0, 0, 0),
				() -> invoke(f.gc, "fillArc", 0, 0, 0, 0, 0, 0),
				() -> invoke(f.gc, "getTextAntialias"),
				() -> invoke(f.gc, "setTextAntialias", SWT.ON),
				f.gc::getClipping
			};
			AtomicReference<Throwable> failure = new AtomicReference<>();
			Thread worker = new Thread(() -> {
				try { for (Runnable operation : operations) expectSwt(SWT.ERROR_THREAD_INVALID_ACCESS, operation); }
				catch (Throwable error) { failure.set(error); }
			}, "mobile graphics wrong-thread control");
			worker.start(); worker.join(3000);
			require(!worker.isAlive(), "Wrong-thread graphics checks do not block");
			if (failure.get() != null) throw new AssertionError("Wrong-thread graphics guard", failure.get());
			f.gc.dispose();
			require(field.getLong(f.gc) == 0, "Disposal clears the retained internal public handle");
			for (Runnable operation : operations) expectSwt(SWT.ERROR_GRAPHIC_DISPOSED, operation);
		}
	}

	private static void expectSwt(int code, Runnable operation) {
		try { operation.run(); throw new AssertionError("Expected SWT error " + code); }
		catch (SWTException failure) { require(failure.code == code, "Expected SWT error " + code + ", got " + failure.code); }
	}

	public static void main(String[] arguments) throws Exception {
		testRoundedRectangles();
		testArcs();
		testTextAntialias();
		testAlphaMasking();
		testEffectiveClippingAndCopy();
		testPublicHandleAndGuards();
		System.out.println("PASS: six mobile graphics owner contracts; native platform qualification is separate.");
	}
}
