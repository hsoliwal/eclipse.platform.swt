/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.gpu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.SplittableRandom;

import org.eclipse.swt.internal.maya.Bitmap;
import org.eclipse.swt.internal.maya.ColorAlphaType;
import org.eclipse.swt.internal.maya.FontMgr;
import org.eclipse.swt.internal.maya.ImageInfo;
import org.eclipse.swt.internal.maya.Surface;
import org.eclipse.swt.internal.maya.TextBlob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * M3Canvas (one native call per OpStream) against OpStreamReplay's command-by-command Java replay: random
 * streams of every command kind draw the same pixels both ways, on raster surfaces (no OpenGL needed).
 */
public class Test_org_eclipse_swt_maya_M3Canvas {

	private static final int W = 160, H = 120, STREAMS = 200, COMMANDS = 60, WHITE = 0xFFFFFFFF;

	private static final int[] KINDS = { OpStream.CLIP, OpStream.CLIP_RESET, OpStream.FILL_RECT, OpStream.DRAW_RECT,
			OpStream.DRAW_LINE, OpStream.DRAW_POINT, OpStream.FILL_OVAL, OpStream.DRAW_OVAL, OpStream.FILL_GRADIENT,
			OpStream.DRAW_TEXT, OpStream.FILL_POLY, OpStream.DRAW_POLY, OpStream.GLYPH_RUN, OpStream.CLIP_PUSH,
			OpStream.CLIP_POP, OpStream.TRANSFORM };

	private static GlPlaneBackend backend;

	private static int fontId;

	@BeforeAll
	public static void setUp() {
		backend = new GlPlaneBackend();
		backend.fontMgr = FontMgr.getDefault();
		char[] face = "Arial".toCharArray();
		fontId = FontTable.INSTANCE.define(face, face.length, -14, 400, 0, 14, 11, 16);
		int[] widths = new int[256];
		Arrays.fill(widths, 7);
		FontTable.INSTANCE.setPage(fontId, 0, widths);
	}

	/* no dispose: it releases the OpenGL context this test never makes (and needs swt-wgl) */
	@AfterAll
	public static void tearDown() {
		backend.endFrame();
	}

	@Test
	public void randomStreamsDrawTheSamePixels() {
		SplittableRandom random = new SplittableRandom(20261117);
		int painted = 0;
		for (int s = 0; s < STREAMS; s++) {
			OpStream ops = stream(random);
			int[] perCommand = draw(ops, false), frame = draw(ops, true);
			assertArrayEquals(perCommand, frame, "stream " + s);
			if (Arrays.stream(frame).anyMatch(p -> p != WHITE)) painted++;
		}
		assertTrue(painted > STREAMS * 9 / 10, "streams that paint: " + painted);
	}

	@Test
	public void m3CanvasCountsTheCommandsItDraws() {
		OpStream ops = new OpStream(64, 1 << 16);
		ops.rect(OpStream.FILL_RECT, 1, 1, 5, 5, 0xFF0000FF, 0);
		ops.clipPush(0, 0, 50, 50);
		ops.line(0, 0, 9, 9, 0xFF00FF00, stroke(1, 1, 1));
		ops.clipPop();
		ops.plane(1);
		try (Surface surface = surface()) {
			surface.getCanvas().clear(WHITE);
			assertEquals(4, OpStreamReplay.runFrame(backend, surface.getCanvas()._ptr, ops), "PLANE is not drawn");
		}
	}

	/* a blob evicted while a frame's blobs are looked up stays open until the frame ends */
	@Test
	public void blobsEvictedDuringAFrameCloseAfterIt() {
		TextBlob blob = TextBlob.makeFromPosH(new short[] { 36 }, new float[] { 0 }, 0, backend.font(fontId));
		backend.beginFrame();
		backend.retire(blob);
		assertNotEquals(0L, blob._ptr, "open during the frame");
		backend.endFrame();
		assertEquals(0L, blob._ptr, "closed after the frame");
	}

	private static int[] draw(OpStream ops, boolean frame) {
		try (Surface surface = surface()) {
			surface.getCanvas().clear(WHITE);
			long canvas = surface.getCanvas()._ptr;
			if (frame) {
				OpStreamReplay.runFrame(backend, canvas, ops);
			} else {
				OpStreamReplay.runPerCommand(backend, canvas, ops);
			}
			Bitmap bitmap = new Bitmap();
			bitmap.allocN32Pixels(W, H);
			surface.readPixels(bitmap, 0, 0);
			byte[] bytes = bitmap.readPixels();
			bitmap.close();
			int[] pixels = new int[W * H];
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = (bytes[4 * i] & 0xFF) | (bytes[4 * i + 1] & 0xFF) << 8 | (bytes[4 * i + 2] & 0xFF) << 16 | (bytes[4 * i + 3] & 0xFF) << 24;
			}
			return pixels;
		}
	}

	private static Surface surface() {
		return Surface.makeRaster(ImageInfo.makeN32(W, H, ColorAlphaType.PREMUL));
	}

	private static int stroke(float width, int cap, int join) {
		return PaintKeys.mask(PaintKeys.KIND_STROKE, 1, cap, join, -1, 0, width, PaintKeys.BACKEND_MAYA);
	}

	private static OpStream stream(SplittableRandom r) {
		OpStream ops = new OpStream(256, 1 << 16);
		int depth = 0;
		for (int c = 0; c < COMMANDS; c++) {
			int kind = KINDS[r.nextInt(KINDS.length)], x = r.nextInt(-20, W), y = r.nextInt(-20, H);
			int w = r.nextInt(-30, 90), h = r.nextInt(-30, 70), argb = r.nextInt() | 0x40000000;
			int mask = r.nextBoolean() ? 0 : stroke(r.nextInt(5) * 0.75f, 1 + r.nextInt(3), 1 + r.nextInt(3));
			switch (kind) {
				case OpStream.CLIP -> ops.clip(x, y, Math.abs(w), Math.abs(h));
				case OpStream.CLIP_RESET -> ops.clipReset();
				case OpStream.CLIP_PUSH -> { ops.clipPush(x, y, Math.abs(w), Math.abs(h)); depth++; }
				case OpStream.CLIP_POP -> { if (depth > 0) { ops.clipPop(); depth--; } }
				case OpStream.TRANSFORM -> {
					if (r.nextInt(3) == 0) ops.transformIdentity();
					else ops.transform(1f + r.nextInt(-2, 3) * 0.25f, r.nextInt(-1, 2) * 0.2f, r.nextInt(-1, 2) * 0.2f, 1f, r.nextInt(-10, 10), r.nextInt(-10, 10));
				}
				case OpStream.DRAW_LINE -> ops.line(x, y, x + w, y + h, argb, mask == 0 ? stroke(1, 1, 1) : mask);
				case OpStream.FILL_GRADIENT -> ops.gradient(x, y, w, h, argb, r.nextInt() | 0xFF000000, r.nextInt(2));
				case OpStream.DRAW_TEXT -> ops.text("Maya frame " + c, 0, 5 + r.nextInt(6), fontId, x, y, argb, r.nextInt() | 0xFF000000, r.nextInt(2), 60, 16, 11);
				case OpStream.FILL_POLY, OpStream.DRAW_POLY -> {
					int[] points = new int[2 * (2 + r.nextInt(5))];
					for (int i = 0; i < points.length; i++) points[i] = i % 2 == 0 ? r.nextInt(-10, W + 10) : r.nextInt(-10, H + 10);
					ops.poly(kind | (kind == OpStream.DRAW_POLY && r.nextBoolean() ? OpStream.OPEN : 0), points, argb, kind == OpStream.DRAW_POLY ? stroke(r.nextInt(4), 1, 1 + r.nextInt(3)) : 0);
				}
				case OpStream.GLYPH_RUN -> glyphRun(ops, r, x, y, argb);
				case OpStream.DRAW_RECT, OpStream.DRAW_OVAL -> ops.rect(kind, x, y, w, h, argb, mask == 0 ? stroke(1, 1, 1) : mask);
				default -> ops.rect(kind, x, y, w, h, argb, kind == OpStream.DRAW_POINT ? mask : 0);
			}
		}
		return ops;
	}

	private static void glyphRun(OpStream ops, SplittableRandom r, int x, int y, int argb) {
		int count = 1 + r.nextInt(8), o = ops.begin(OpStream.GLYPH_RUN, 5 + 2 * count);
		int[] a = ops.ops();
		a[o] = fontId;
		a[o + 1] = x;
		a[o + 2] = y + 12;
		a[o + 3] = argb | 0xFF000000;
		a[o + 4] = count;
		for (int i = 0; i < count; i++) {
			a[o + 5 + i] = 20 + r.nextInt(60);
			a[o + 5 + count + i] = 6 + r.nextInt(4);
		}
	}
}
