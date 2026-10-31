package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.internal.render.m3.gc.IM3Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jdk.m3.graphics.M3BlendMode;
import jdk.m3.graphics.M3Rect;

public class Test_org_eclipse_swt_m3gc_drawPoint {

	@BeforeAll
	static void checkPlatform() {
		assumeTrue(SupportedTestPlatforms.isSupported(), "Test skipped: Platform not supported");
	}

	@Test
	public void testDrawPoint() {
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

		gc.drawPoint(10, 20);

		var calls = surface.calls;
		assertEquals(1, calls.size(), "Expected 1 method call");
		
		System.out.println(calls);

		
		PaintData epd = new PaintData();
		epd.color = -1;
		epd.strokeWidth = 0.0f;
		epd.strokeMiter = 4.0f;
		epd.strokeCap = 0;
		epd.strokeJoin = 0;
		epd.style = 0;
		epd.alpha = 255;
		epd.antiAlias = false;
		epd.dither = false;
		epd.shader = null;
		epd.blendMode = M3BlendMode.SRC_OVER;
		epd.pathEffect = null;
		epd.imageFilter = null;
		epd.colorFilter = null;
		epd.maskFilter = null;

		// drawPoint creates a drawRect call with 1x1 pixel at the given position
		assertEquals(MethodCall.get("surface-canvas", "drawRect", new M3Rect(10.0f, 20.0f, 11.0f, 21.0f), epd),
				surface.calls.get(0));

		surface.close();
	}

	@Test
	public void testDrawPoint_Zoom150() {
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

		gc.drawPoint(10, 20);

		var calls = surface.calls;
		assertEquals(1, calls.size(), "Expected 1 method call");

		System.out.println(calls);


		PaintData epd = new PaintData();
		epd.color = -1;
		epd.strokeWidth = 0.0f;
		epd.strokeMiter = 4.0f;
		epd.strokeCap = 0;
		epd.strokeJoin = 0;
		epd.style = 0;
		epd.alpha = 255;
		epd.antiAlias = false;
		epd.dither = false;
		epd.shader = null;
		epd.blendMode = M3BlendMode.SRC_OVER;
		epd.pathEffect = null;
		epd.imageFilter = null;
		epd.colorFilter = null;
		epd.maskFilter = null;

		// At 150% zoom: x=10*1.5=15, y=20*1.5=30, size scales to 1.0 pixel (1x1 stays 1x1 not scaled)
		// Rect: left=15.0, top=30.0, right=16.0, bottom=31.0
		assertEquals(MethodCall.get("surface-canvas", "drawRect", new M3Rect(15.0f, 30.0f, 16.0f, 31.0f), epd),
				surface.calls.get(0));

		surface.close();
	}

	@Test
	public void testDrawPoint_Zoom200() {
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

		gc.drawPoint(10, 20);

		var calls = surface.calls;
		assertEquals(1, calls.size(), "Expected 1 method call");

		System.out.println(calls);


		PaintData epd = new PaintData();
		epd.color = -1;
		epd.strokeWidth = 0.0f;
		epd.strokeMiter = 4.0f;
		epd.strokeCap = 0;
		epd.strokeJoin = 0;
		epd.style = 0;
		epd.alpha = 255;
		epd.antiAlias = false;
		epd.dither = false;
		epd.shader = null;
		epd.blendMode = M3BlendMode.SRC_OVER;
		epd.pathEffect = null;
		epd.imageFilter = null;
		epd.colorFilter = null;
		epd.maskFilter = null;

		// At 200% zoom: x=10*2.0=20, y=20*2.0=40, size stays 1x1
		// Rect: left=20.0, top=40.0, right=21.0, bottom=41.0
		assertEquals(MethodCall.get("surface-canvas", "drawRect", new M3Rect(20.0f, 40.0f, 21.0f, 41.0f), epd),
				surface.calls.get(0));

		surface.close();
	}
}