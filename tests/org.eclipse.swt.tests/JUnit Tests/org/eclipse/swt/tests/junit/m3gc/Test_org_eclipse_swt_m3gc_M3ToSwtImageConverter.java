package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.internal.render.m3.gc.M3ToSwtImageConverter;
import org.eclipse.swt.internal.render.m3.gc.IM3Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3ColorAlphaType;
import jdk.m3.graphics.M3ColorType;
import jdk.m3.graphics.M3Image;
import jdk.m3.graphics.M3ImageInfo;
import jdk.m3.graphics.M3Surface;

public class Test_org_eclipse_swt_m3gc_M3ToSwtImageConverter {

	@BeforeAll
	static void checkPlatform() {
	    assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}
	
	@Test
	public void testConvertSimpleRGBAImage() {
		// Create a 2x2 Skija image with RGBA_8888
		try (M3Surface surface = M3Surface.makeRaster(new M3ImageInfo(2, 2, M3ColorType.RGBA_8888, M3ColorAlphaType.PREMUL))) {
			surface.getCanvas().clear(0xFF0000FF); // Blue
			final M3Image m3Image = surface.makeImageSnapshot();
			final ImageData data = M3ToSwtImageConverter.convertM3ImageToImageData(m3Image);
			assertNotNull(data);
			assertEquals(2, data.width);
			assertEquals(2, data.height);
			// Check if the data is blue (0,0,255)
			final int r = data.data[0] & 0xFF;
			final int g = data.data[1] & 0xFF;
			final int b = data.data[2] & 0xFF;
			// Print actual values for debugging
			System.out.printf("Pixel (0,0): R=%d G=%d B=%d\n", r, g, b);
			assertEquals(0, r, "Red channel should be 0");
			assertEquals(0, g, "Green channel should be 0");
			assertEquals(255, b, "Blue channel should be 255");
		}
	}

	@Test
	public void testConvertAlphaHandling() {
		// Create a 1x1 Skija image with alpha
		try (M3Surface surface = M3Surface.makeRaster(new M3ImageInfo(1, 1, M3ColorType.RGBA_8888, M3ColorAlphaType.PREMUL))) {
			surface.getCanvas().clear(0x80000000); // Half-transparent black
			final M3Image m3Image = surface.makeImageSnapshot();
			final ImageData data = M3ToSwtImageConverter.convertM3ImageToImageData(m3Image);
			assertNotNull(data);
			assertEquals(1, data.width);
			assertEquals(1, data.height);
			// Print actual alpha value for debugging
			final int alpha = data.alphaData[0] & 0xFF;
			System.out.printf("Pixel (0,0) alpha: %d\n", alpha);
			// Check if alpha is between 1 and 254 (exclusive)
			assertTrue(alpha > 0 && alpha < 255, "Alpha should be between 1 and 254, but was: " + alpha);
		}
	}

	@Test
	public void testNullInput() {
		// Should throw AssertionError or NullPointerException on null input
		final Throwable thrown = assertThrows(Throwable.class, () -> {
			M3ToSwtImageConverter.convertM3ImageToImageData((IM3Image)null);
		});
		assertTrue(thrown instanceof NullPointerException || thrown instanceof AssertionError,
				"Expected NullPointerException or AssertionError, but got: " + thrown.getClass().getName());
	}
}