package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.render.m3.gc.RectangleConverter;
import org.eclipse.swt.internal.render.m3.gc.DpiScalerUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3RRect;
import jdk.m3.graphics.M3Rect;

public class Test_org_eclipse_swt_m3gc_RectangleConverter {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	private final DpiScalerUtil scaler100 = new DpiScalerUtil(100);
	private final DpiScalerUtil scaler150 = new DpiScalerUtil(150);

	@Test
	void testCreateScaledRectangle_Rectangle() {
		final Rectangle r = new Rectangle(10, 20, 30, 40);
		final M3Rect rect = RectangleConverter.createScaledRectangle(scaler100, r);
		assertEquals(10, rect.getLeft());
		assertEquals(20, rect.getTop());
		assertEquals(40, rect.getRight());
		assertEquals(60, rect.getBottom());
	}

	@Test
	void testScaleUpRectangle() {
		final Rectangle r = new Rectangle(10, 20, 30, 40);
		final Rectangle scaled = RectangleConverter.scaleUpRectangle(scaler150, r);
		assertEquals(15, scaled.x);
		assertEquals(30, scaled.y);
		assertEquals(45, scaled.width);
		assertEquals(60, scaled.height);
	}

	@Test
	void testCreateScaledRectangle_Ints() {
		final M3Rect rect = RectangleConverter.createScaledRectangle(scaler150, 10, 20, 30, 40);
		assertEquals(15, rect.getLeft());
		assertEquals(30, rect.getTop());
		assertEquals(60, rect.getRight());
		assertEquals(90, rect.getBottom());
	}

	@Test
	void testGetScaledOffsetValue() {
		assertEquals(0.5f, RectangleConverter.getScaledOffsetValue(scaler100, 0));
		assertEquals(0.5f, RectangleConverter.getScaledOffsetValue(scaler100, 1));
		assertEquals(0f, RectangleConverter.getScaledOffsetValue(scaler100, 2));
		assertEquals(0f, RectangleConverter.getScaledOffsetValue(scaler150, 1));
		assertEquals(0.75f, RectangleConverter.getScaledOffsetValue(scaler150, 2));
	}

	@Test
	void testOffsetRectangle_Rect() {
		final M3Rect rect = new M3Rect(1, 2, 11, 22);
		final M3Rect off = RectangleConverter.offsetRectangle(scaler100, 1, rect);
		assertEquals(1.5f, off.getLeft());
		assertEquals(2.5f, off.getTop());
		assertEquals(11.5f, off.getRight());
		assertEquals(22.5f, off.getBottom());
		// No offset for even lineWidth
		final M3Rect off2 = RectangleConverter.offsetRectangle(scaler100, 2, rect);
		assertEquals(rect, off2);
	}

	@Test
	void testOffsetRectangle_RRect() {
		final M3RRect rrect = new M3RRect(1, 2, 11, 22, new float[] { 3, 4 });
		final M3RRect off = RectangleConverter.offsetRectangle(scaler100, 1, rrect);
		assertEquals(1.5f, off.getLeft());
		assertEquals(2.5f, off.getTop());
		assertEquals(11.5f, off.getRight());
		assertEquals(22.5f, off.getBottom());
		assertArrayEquals(new float[] { 3, 4 }, off._radii);
		// No offset for even lineWidth
		final M3RRect off2 = RectangleConverter.offsetRectangle(scaler100, 2, rrect);
		assertEquals(rrect, off2);
	}

	@Test
	void testCreateScaledRectangleWithOffset() {
		final M3Rect rect = RectangleConverter.createScaledRectangleWithOffset(scaler100, 1, 10, 20, 30, 40);
		assertEquals(10.5f, rect.getLeft());
		assertEquals(20.5f, rect.getTop());
		assertEquals(40.5f, rect.getRight());
		assertEquals(60.5f, rect.getBottom());
	}

	@Test
	void testCreateScaledRoundRectangle() {
		final M3RRect rrect = RectangleConverter.createScaledRoundRectangle(scaler150, 10, 20, 30, 40, 5, 6);
		assertEquals(15, rrect.getLeft());
		assertEquals(30, rrect.getTop());
		assertEquals(60, rrect.getRight());
		assertEquals(90, rrect.getBottom());
		assertArrayEquals(new float[] { 7.5f, 9f }, rrect._radii);
	}
}