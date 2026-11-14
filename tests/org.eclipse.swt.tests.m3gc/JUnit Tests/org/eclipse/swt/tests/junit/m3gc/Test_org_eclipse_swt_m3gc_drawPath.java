package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.IM3Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.humbleui.skija.BlendMode;

public class Test_org_eclipse_swt_m3gc_drawPath {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testDrawPath() {
		IM3Resources resources = new M3ResourcesDouble();
		Color foregroundColor = new Color(null, 255, 255, 255);
		resources.setForeground(foregroundColor);
		Color backgroundColor = new Color(null, 0, 0, 0);
		resources.setBackground(backgroundColor);

		final M3SurfaceDouble surface = new M3SurfaceDouble(null);
		var canvas = surface.canvas;
		final CanvasExtensionDouble ext = new CanvasExtensionDouble();
		ext.surface = surface;
		ext.resources = resources;
		final M3ExternalGC gc = new M3ExternalGC(ext, SWT.NONE);

		org.eclipse.swt.graphics.Path path = new Path(null);
		path.moveTo(10, 20);
		path.lineTo(30, 40);
		gc.drawPath(path);

		var calls = surface.calls;
		assertEquals(1, calls.size(), "Expected 1 method call");
		
		System.out.println(calls);

		
		PaintData epd = new PaintData();
		epd.color = -1;
		epd.strokeWidth = 1.0f;
		epd.strokeMiter = 1000.0f;
		epd.strokeCap = 0;
		epd.strokeJoin = 0;
		epd.style = 1;
		epd.alpha = 255;
		epd.antiAlias = false;
		epd.dither = false;
		epd.shader = null;
		epd.blendMode = BlendMode.SRC_OVER;
		epd.pathEffect = null;
		epd.imageFilter = null;
		epd.colorFilter = null;
		epd.maskFilter = null;

		// drawPath is converted to M3Path, so we only check method name and PaintData
		assertEquals("drawPath", calls.get(0).methodName);
		assertEquals(epd, calls.get(0).params[1]);

		surface.close();
	}

	@Test
	public void testDrawPath_Zoom150() {
		IM3Resources resources = new M3ResourcesDouble(150);
		Color foregroundColor = new Color(null, 255, 255, 255);
		resources.setForeground(foregroundColor);
		Color backgroundColor = new Color(null, 0, 0, 0);
		resources.setBackground(backgroundColor);

		final M3SurfaceDouble surface = new M3SurfaceDouble(null);
		var canvas = surface.canvas;
		final CanvasExtensionDouble ext = new CanvasExtensionDouble();
		ext.surface = surface;
		ext.resources = resources;
		final M3ExternalGC gc = new M3ExternalGC(ext, SWT.NONE);

		org.eclipse.swt.graphics.Path path = new Path(null);
		path.moveTo(10, 20);
		path.lineTo(30, 40);
		gc.drawPath(path);

		var calls = surface.calls;
		assertEquals(1, calls.size(), "Expected 1 method call");

		System.out.println(calls);


		PaintData epd = new PaintData();
		epd.color = -1;
		epd.strokeWidth = 1.5f; // strokeWidth is scaled
		epd.strokeMiter = 1000.0f;
		epd.strokeCap = 0;
		epd.strokeJoin = 0;
		epd.style = 1;
		epd.alpha = 255;
		epd.antiAlias = false;
		epd.dither = false;
		epd.shader = null;
		epd.blendMode = BlendMode.SRC_OVER;
		epd.pathEffect = null;
		epd.imageFilter = null;
		epd.colorFilter = null;
		epd.maskFilter = null;

		// drawPath is converted to M3Path, so we only check method name and PaintData
		assertEquals("drawPath", calls.get(0).methodName);
		assertEquals(epd, calls.get(0).params[1]);

		surface.close();
	}

	@Test
	public void testDrawPath_Zoom200() {
		IM3Resources resources = new M3ResourcesDouble(200);
		Color foregroundColor = new Color(null, 255, 255, 255);
		resources.setForeground(foregroundColor);
		Color backgroundColor = new Color(null, 0, 0, 0);
		resources.setBackground(backgroundColor);

		final M3SurfaceDouble surface = new M3SurfaceDouble(null);
		var canvas = surface.canvas;
		final CanvasExtensionDouble ext = new CanvasExtensionDouble();
		ext.surface = surface;
		ext.resources = resources;
		final M3ExternalGC gc = new M3ExternalGC(ext, SWT.NONE);

		org.eclipse.swt.graphics.Path path = new Path(null);
		path.moveTo(10, 20);
		path.lineTo(30, 40);
		gc.drawPath(path);

		var calls = surface.calls;
		assertEquals(1, calls.size(), "Expected 1 method call");

		System.out.println(calls);


		PaintData epd = new PaintData();
		epd.color = -1;
		epd.strokeWidth = 2.0f; // 1.0 * 2.0
		epd.strokeMiter = 1000.0f;
		epd.strokeCap = 0;
		epd.strokeJoin = 0;
		epd.style = 1;
		epd.alpha = 255;
		epd.antiAlias = false;
		epd.dither = false;
		epd.shader = null;
		epd.blendMode = BlendMode.SRC_OVER;
		epd.pathEffect = null;
		epd.imageFilter = null;
		epd.colorFilter = null;
		epd.maskFilter = null;

		// drawPath is converted to M3Path, so we only check method name and PaintData
		assertEquals("drawPath", calls.get(0).methodName);
		assertEquals(epd, calls.get(0).params[1]);

		surface.close();
	}
}