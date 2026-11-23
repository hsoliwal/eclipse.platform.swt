/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit.maya;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.internal.maya.MayaComposition;
import org.eclipse.swt.internal.maya.MayaFramePacket;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Layers composited by the window system (DirectComposition) above a shell's own pixels: Z order, offset, opacity
 * and visibility change in one {@code _nApply} each, and the shell is never asked to repaint. Pixels are read from
 * the screen, where the system composites them.
 */
public class Test_org_eclipse_swt_maya_Composition {

	private static final int SIZE = 100, OPAQUE_RED = 0xFFFF0000, OPAQUE_BLUE = 0xFF0000FF;
	private static final RGB WHITE = new RGB(255, 255, 255), RED = new RGB(255, 0, 0), BLUE = new RGB(0, 0, 255);

	private Display display;
	private Shell shell;
	private long composition;
	private int paints;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
		shell.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		shell.setBounds(60, 60, 240, 240);
		shell.open();
		pump(200);
		shell.addListener(SWT.Paint, e -> paints++);
		composition = MayaComposition._nCreate(shell.handle);
		assertNotEquals(0, composition, "a DirectComposition target for the shell");
	}

	@AfterEach
	public void tearDown() {
		if (composition != 0) MayaComposition._nDispose(composition);
		shell.dispose();
	}

	private void pump(long millis) {
		long end = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < end) {
			while (display.readAndDispatch()) {
				// drain
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private long layer(int argb) {
		long layer = MayaComposition._nAddLayer(composition, SIZE, SIZE);
		assertNotEquals(0, layer);
		int[] pixels = new int[SIZE * SIZE];
		Arrays.fill(pixels, argb);
		assertEquals(0, MayaComposition._nUpload(layer, pixels));
		return layer;
	}

	/* one frame: the layers bottom to top, x, y, opacity and visible per layer */
	private void frame(long[] layers, float... props) {
		assertEquals(0, MayaComposition._nApply(composition, layers, props, layers.length));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
	}

	/* the pixel the screen shows at the shell's (x, y) */
	private RGB screen(int x, int y) {
		Point at = shell.toDisplay(x, y);
		Image pixel = new Image(display, 1, 1);
		GC gc = new GC(display);
		try {
			gc.copyArea(pixel, at.x, at.y);
		} finally {
			gc.dispose();
		}
		ImageData data = pixel.getImageData();
		pixel.dispose();
		return data.palette.getRGB(data.getPixel(0, 0));
	}

	private static void assertNear(RGB expected, RGB actual) {
		assertTrue(Math.abs(expected.red - actual.red) <= 2 && Math.abs(expected.green - actual.green) <= 2
				&& Math.abs(expected.blue - actual.blue) <= 2, "expected " + expected + ", was " + actual);
	}

	@Test
	public void orderOffsetOpacityAndVisibilityAreCompositedWithoutRepainting() {
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		paints = 0;
		frame(new long[] { red, blue }, 20, 20, 1, 1, 60, 60, 0.5f, 1);
		assertEquals(RED, screen(30, 30));
		assertNear(new RGB(128, 0, 127), screen(80, 80)); // blue at half over red, blended once
		assertNear(new RGB(128, 128, 255), screen(140, 140)); // blue at half over the shell's white
		assertEquals(WHITE, screen(200, 200));

		frame(new long[] { blue, red }, 60, 60, 0.5f, 1, 20, 20, 1, 1); // the same layers, red now on top
		assertEquals(RED, screen(80, 80));
		assertNear(new RGB(128, 128, 255), screen(140, 140));

		frame(new long[] { blue, red }, 110, 60, 1, 1, 20, 20, 1, 1); // blue moved and opaque
		assertEquals(BLUE, screen(140, 140));
		assertEquals(RED, screen(80, 80));

		frame(new long[] { red, blue }, 20, 20, 1, 1, 110, 60, 1, 0); // blue hidden
		assertEquals(WHITE, screen(140, 140));
		assertEquals(0, paints, "the window system composites the layers: the shell never repaints");
	}

	@Test
	public void aRemovedLayerAndADisposedCompositionLeaveTheShellsPixels() {
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		frame(new long[] { red, blue }, 20, 20, 1, 1, 110, 110, 1, 1);
		assertEquals(BLUE, screen(140, 140));
		MayaComposition._nRemoveLayer(composition, blue);
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(WHITE, screen(140, 140));
		assertEquals(RED, screen(30, 30));
		MayaComposition._nDispose(composition);
		composition = 0;
		pump(250);
		assertEquals(WHITE, screen(30, 30), "disposing removes every layer from the window");
	}

	@Test
	public void identicalAndPropertyOnlyFramesPreserveNativePresentation() {
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		long[] order = { red, blue };
		float[] props = { 20, 20, 1, 1, 60, 60, 0.5f, 1 };
		frame(order, props);
		assertNear(new RGB(128, 0, 127), screen(80, 80));
		for (int i = 0; i < 3; i++) {
			frame(order, props); // identical frames: no native visual edits required
			assertNear(new RGB(128, 0, 127), screen(80, 80));
		}
		props[6] = 1; // opacity changes, without changing visible order
		frame(order, props);
		assertEquals(BLUE, screen(80, 80));
		props[4] = 110; // translate the same native visual
		frame(order, props);
		assertEquals(RED, screen(80, 80));
		assertEquals(BLUE, screen(140, 140));
		props[3] = 0; // visible membership now changes
		frame(order, props);
		assertEquals(WHITE, screen(30, 30));
		props[3] = 1;
		frame(order, props);
		assertEquals(RED, screen(30, 30));
		MayaComposition._nRemoveLayer(composition, blue);
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(WHITE, screen(140, 140), "removal invalidates the cached scene order");
		assertEquals(RED, screen(30, 30));
	}

	@Test
	public void packedDirtyUpdatesOnlySpecifiedNativePixels() {
		// The installed Maya binary may predate the optional _nUploadDirty export.
		// Ordinary suites preserve legacy compatibility, while release qualification
		// explicitly sets -Dswt.maya.dirty.required=true and cannot skip this gate.
		int abiProbe = MayaComposition.uploadDirty(0, new int[0], new int[0], 0);
		if (Boolean.getBoolean("swt.maya.dirty.required")) {
			assertNotEquals(-1, abiProbe, "required Maya dirty JNI ABI is missing");
		}
		assumeTrue(abiProbe != -1, "optional native dirty-upload ABI is unavailable");
		long red = layer(OPAQUE_RED); // initializes the whole native surface
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(RED, screen(24, 24)); // first commit before dirty-only frame
		int[] patches = new int[16 + 9];
		Arrays.fill(patches, 0, 16, OPAQUE_BLUE);
		Arrays.fill(patches, 16, 25, 0xFF00FF00);
		int[] rects = { 3, 3, 4, 4, 30, 30, 3, 3 };
		assertEquals(0, MayaComposition.uploadDirty(red, patches, rects, 2));
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(RED, screen(21, 21));
		assertEquals(BLUE, screen(24, 24));
		assertEquals(new RGB(0, 255, 0), screen(51, 51));
		assertEquals(RED, screen(60, 60));
		assertNotEquals(0, MayaComposition.uploadDirty(red, new int[2], rects, 2));
		assertNotEquals(0, MayaComposition.uploadDirty(red, patches, new int[] { 99, 99, 4, 4 }, 1));
		assertNotEquals(0, MayaComposition.uploadDirty(red, patches, rects, Integer.MAX_VALUE));
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(BLUE, screen(24, 24), "invalid batched frames must preserve the prior compositor surface");
	}

	private void requireSceneAbi() {
		long required = MayaComposition.CAP_AFFINE_2D | MayaComposition.CAP_LOCAL_RECT_CLIP;
		long available = MayaComposition.capabilities(composition);
		if (Boolean.getBoolean("swt.maya.scene.required")) {
			assertEquals(required, available & required, "required Maya affine/clip JNI ABI is missing");
		}
		assumeTrue((available & required) == required, "older Maya native ABI has no affine/clip scene");
	}

	private void scene(long[] layers, float[] properties, int[] flags) {
		assertEquals(0, MayaComposition.applyScene(composition, layers, properties, flags, layers.length));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
	}

	@Test
	public void nativeSceneAppliesLocalClipAndLegacyFrameResetsAffineState() {
		requireSceneAbi();
		long red = layer(OPAQUE_RED);
		long[] handles = { red };
		float[] properties = { 0, 0, 1, 1, 2, 0, 0, 1, 20, 20, 0, 0, 25, 60 };
		int[] flags = { MayaComposition.SCENE_CLIP };
		paints = 0;
		scene(handles, properties, flags);
		assertEquals(RED, screen(30, 30));
		assertEquals(WHITE, screen(80, 30), "source-local clip transforms with the image");
		properties[12] = 60;
		scene(handles, properties, flags);
		assertEquals(RED, screen(80, 30), "clip-only change reaches the retained native visual");
		scene(handles, properties, flags); // no-op scene must retain the same pixels
		assertEquals(RED, screen(80, 30));
		assertEquals(0, paints, "native affine and clip properties do not repaint the shell");

		frame(handles, 20, 20, 1, 1);
		assertEquals(RED, screen(80, 30));
		assertEquals(WHITE, screen(140, 30), "legacy frame clears the old affine scale");
	}

	@Test
	public void packedLegacyFrameClearsSceneClipAndSceneOnlyUploadCommitsPixels() {
		requireSceneAbi();
		long red = layer(OPAQUE_RED);
		long[] handles = { red };
		float[] properties = { 0, 0, 1, 1, 2, 0, 0, 1, 20, 20, 0, 0, 25, 60 };
		int[] flags = { MayaComposition.SCENE_CLIP };
		scene(handles, properties, flags);
		assertEquals(RED, screen(30, 30));
		assertEquals(WHITE, screen(80, 30));
		int[] blue = new int[SIZE * SIZE];
		Arrays.fill(blue, OPAQUE_BLUE);
		assertEquals(0, MayaComposition._nUpload(red, blue));
		scene(handles, properties, flags); // same properties, pending EndDraw must still commit
		assertEquals(BLUE, screen(30, 30));
		assertEquals(WHITE, screen(80, 30));
		assertEquals(MayaFramePacket.VERSION, MayaComposition._nFramePacketVersion());
		ByteBuffer packet = new MayaFramePacket.Encoder()
				.encode(handles, new float[] { 20, 20, 1, 1 }, 1, 1L);
		assertEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(BLUE, screen(80, 30), "packed v1 clears the old source clip");
		assertEquals(WHITE, screen(140, 30), "packed v1 clears the old scale");
	}

	@Test
	public void sceneValidationRejectsTheWholeFrameBeforeAnyVisualChanges() {
		requireSceneAbi();
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		long[] handles = { red, blue };
		float[] properties = {
				20, 20, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 100, 100,
				120, 20, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 100, 100 };
		int[] flags = { 0, MayaComposition.SCENE_CLIP };
		scene(handles, properties, flags);
		assertEquals(RED, screen(30, 30));
		assertEquals(BLUE, screen(130, 30));
		properties[0] = 60; // valid first record must not move if record two is invalid
		properties[18] = Float.NaN;
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, properties, flags, 2));
		properties[18] = 1;
		for (int invalid : new int[] { -1, 2, Integer.MAX_VALUE }) {
			flags[1] = invalid;
			assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, properties, flags, 2));
		}
		flags[1] = MayaComposition.SCENE_CLIP;
		properties[26] = -1; // right edge precedes left
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, properties, flags, 2));
		properties[26] = 100;
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, new float[27], flags, 2));
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, properties, new int[1], 2));
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, properties, flags, Integer.MAX_VALUE));
		assertNotEquals(0, MayaComposition._nApplyScene(composition, new long[] { red, red }, properties, flags, 2));
		assertNotEquals(0, MayaComposition._nApplyScene(composition, new long[] { red, Long.MAX_VALUE }, properties, flags, 2));
		assertNotEquals(0, MayaComposition._nApplyScene(composition, null, properties, flags, 2));
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, null, flags, 2));
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, properties, null, 2));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(30, 30), "invalid later record did not move the first visual");
		assertEquals(BLUE, screen(130, 30));
		properties[0] = -20;
		properties[1] = -20;
		properties[14] = 120;
		scene(handles, properties, flags);
		assertEquals(RED, screen(10, 10), "negative finite physical offsets are supported");
	}

	@Test
	public void capabilitiesAndSceneHandlesFollowNativeLifetime() {
		requireSceneAbi();
		long extensions = MayaComposition._nCapabilities(composition);
		assertEquals(MayaComposition.CAP_AFFINE_2D | MayaComposition.CAP_LOCAL_RECT_CLIP
				| MayaComposition.CAP_DIRTY_UPLOAD, extensions);
		assertEquals(0, MayaComposition._nCapabilities(0));
		assertEquals(0, MayaComposition._nCapabilities(Long.MAX_VALUE));
		long red = layer(OPAQUE_RED);
		float[] properties = { 20, 20, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 100, 100 };
		assertEquals(0, MayaComposition._nCapabilities(red), "a layer token is not a composition");
		MayaComposition._nRemoveLayer(composition, red);
		assertNotEquals(0, MayaComposition._nApplyScene(composition,
				new long[] { red }, properties, new int[] { 0 }, 1));
		long disposed = composition;
		MayaComposition._nDispose(disposed);
		composition = 0;
		assertEquals(0, MayaComposition._nCapabilities(disposed));
		assertNotEquals(0, MayaComposition._nApplyScene(disposed,
				new long[0], new float[0], new int[0], 0));
	}

	@Test
	public void directPacketAndLegacyFramesHaveIdenticalNativePixels() {
		// Older fragments did not export _nApplyPacked. Ordinary SWT tests
		// may skip that new route; native release qualification must set
		// -Dswt.maya.packed.required=true and must not accept a skipped gate.
		int version;
		try {
			version = MayaComposition._nFramePacketVersion();
		} catch (UnsatisfiedLinkError oldNativeLibrary) {
			version = -1;
		}
		if (Boolean.getBoolean("swt.maya.packed.required")) {
			assertEquals(MayaFramePacket.VERSION, version, "required Maya packet JNI ABI is missing");
		}
		assumeTrue(version == MayaFramePacket.VERSION, "older Maya native ABI has no packed frame");
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		long[] order = { red, blue };
		float[] props = { 20, 20, 1, 1, 60, 60, 0.5f, 1 };
		frame(order, props); // established legacy native pixel oracle
		RGB redOnly = screen(30, 30);
		RGB overlap = screen(80, 80);

		MayaFramePacket.Encoder encoder = new MayaFramePacket.Encoder();
		ByteBuffer packet = encoder.encode(order, props, order.length, 1L);
		paints = 0;
		assertEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(redOnly, screen(30, 30));
		assertNear(overlap, screen(80, 80));
		assertEquals(0, paints, "the packed transaction still composites native pixels");

		// Property-only packet: same Z, changed opacity and X offset.
		props[6] = 1;
		props[4] = 110;
		packet = encoder.encode(order, props, order.length, 2L);
		assertEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(80, 80));
		assertEquals(BLUE, screen(140, 140));

		// Pixel-only upload MUST be published by a packed frame even though
		// all layer properties and Z-order are unchanged.
		int[] green = new int[SIZE * SIZE];
		Arrays.fill(green, 0xFF00FF00);
		assertEquals(0, MayaComposition._nUpload(red, green));
		assertEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(new RGB(0, 255, 0), screen(30, 30));
		RGB committed = screen(30, 30);

		// Invalid packets, stale/foreign handles, and duplicate IDs may
		// never change the last successfully committed native frame.
		packet.putInt(0, 0);
		assertNotEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(committed, screen(30, 30));
		packet = encoder.encode(order, props, order.length, 3L);
		packet.putLong(MayaFramePacket.HEADER_BYTES + MayaFramePacket.RECORD_BYTES, red);
		assertNotEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(committed, screen(30, 30));
		packet = encoder.encode(order, props, order.length, 4L);
		packet.putLong(MayaFramePacket.HEADER_BYTES, Long.MAX_VALUE);
		assertNotEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(committed, screen(30, 30));

		// A normal legacy frame must still work after a packed transaction.
		frame(order, props);
		assertEquals(committed, screen(30, 30));
	}

	@Test
	public void staleNativeHandlesCannotAliasRecycledCompositorObjects() {
		long oldComposition = composition;
		long oldLayer = layer(OPAQUE_RED);
		frame(new long[] { oldLayer }, 20, 20, 1, 1);
		MayaComposition._nRemoveLayer(composition, oldLayer);
		assertNotEquals(0, MayaComposition._nUpload(oldLayer, new int[SIZE * SIZE]),
				"removed token can never address a later native layer");
		long replacementLayer = layer(OPAQUE_BLUE);
		assertNotEquals(oldLayer, replacementLayer,
				"fresh native memory may reuse an address, but never the token");
		frame(new long[] { replacementLayer }, 20, 20, 1, 1);
		assertEquals(BLUE, screen(30, 30));
		assertNotEquals(0, MayaComposition._nApply(composition,
				new long[] { oldLayer }, new float[] { 20, 20, 1, 1 }, 1));
		// The versioned packed transport uses the same native token registry.
		// A retired layer remains invalid after the allocator creates a new one.
		int packetVersion = -1;
		try {
			packetVersion = MayaComposition._nFramePacketVersion();
		} catch (UnsatisfiedLinkError oldMayaBinary) {
			// Optional ABI: the ordinary legacy tests still run.
		}
		if (packetVersion == MayaFramePacket.VERSION) {
			ByteBuffer staleFrame = new MayaFramePacket.Encoder().encode(
					new long[] { oldLayer }, new float[] { 20, 20, 1, 1 }, 1, 8L);
			assertNotEquals(0, MayaComposition._nApplyPacked(
					composition, staleFrame, staleFrame.remaining()));
			assertEquals(BLUE, screen(30, 30), "stale packed handle did not change committed pixels");
		}
		// Tokens are type-tagged. A layer token is never a composition,
		// and a composition token is never a layer.
		assertEquals(0, MayaComposition._nAddLayer(replacementLayer, SIZE, SIZE));
		assertNotEquals(0, MayaComposition._nUpload(oldComposition, new int[SIZE * SIZE]));
		assertNotEquals(0, MayaComposition._nApply(composition,
				new long[] { oldComposition }, new float[] { 20, 20, 1, 1 }, 1));
		assertEquals(BLUE, screen(30, 30), "invalid tokens preserve committed pixels");

		MayaComposition._nDispose(composition);
		composition = 0;
		assertEquals(0, MayaComposition._nAddLayer(oldComposition, SIZE, SIZE),
				"disposed composition token is permanently retired");
		composition = MayaComposition._nCreate(shell.handle);
		assertNotEquals(0, composition);
		assertNotEquals(oldComposition, composition);
		assertEquals(0, MayaComposition._nAddLayer(oldComposition, SIZE, SIZE));
		long newLayer = layer(OPAQUE_RED);
		assertNotEquals(oldLayer, newLayer);
		assertNotEquals(replacementLayer, newLayer);
		frame(new long[] { newLayer }, 25, 25, 1, 1);
		assertEquals(RED, screen(30, 30));
		assertNotEquals(0, MayaComposition._nUpload(replacementLayer, new int[SIZE * SIZE]),
				"old composition's layer cannot access a new native composition");
	}

    @Test
    public void nativeMetricsExplainCommitNoopAndDirtyPixelPublication() {
        long[] initial = MayaComposition.readMetrics(composition);
        if (Boolean.getBoolean("swt.maya.metrics.required")) {
            assertTrue(initial != null, "required Maya metrics ABI is unavailable");
        }
        assumeTrue(initial != null, "optional native metrics ABI not packaged");
        assertEquals(MayaComposition.METRIC_COUNT, initial.length);
        assertEquals(1L, initial[MayaComposition.METRIC_VERSION]);

        long red = layer(OPAQUE_RED);
        long[] uploaded = MayaComposition.readMetrics(composition);
        assertEquals(initial[MayaComposition.METRIC_LAYERS_CREATED] + 1,
                uploaded[MayaComposition.METRIC_LAYERS_CREATED]);
        assertEquals(1L, uploaded[MayaComposition.METRIC_LIVE_LAYERS]);
        assertEquals(initial[MayaComposition.METRIC_FULL_UPLOADS] + 1,
                uploaded[MayaComposition.METRIC_FULL_UPLOADS]);
        assertEquals(initial[MayaComposition.METRIC_UPLOAD_BYTES] + SIZE * SIZE * 4L,
                uploaded[MayaComposition.METRIC_UPLOAD_BYTES]);
        assertEquals(1L, uploaded[MayaComposition.METRIC_PENDING_SURFACE]);

        long[] order = { red };
        float[] props = { 20, 20, 1, 1 };
        frame(order, props);
        long[] committed = MayaComposition.readMetrics(composition);
        assertEquals(uploaded[MayaComposition.METRIC_FRAME_ATTEMPTS] + 1,
                committed[MayaComposition.METRIC_FRAME_ATTEMPTS]);
        assertEquals(uploaded[MayaComposition.METRIC_FRAME_COMMITS] + 1,
                committed[MayaComposition.METRIC_FRAME_COMMITS]);
        assertEquals(0L, committed[MayaComposition.METRIC_PENDING_SURFACE]);
        assertEquals(RED, screen(30, 30));

        frame(order, props);
        long[] noop = MayaComposition.readMetrics(composition);
        assertEquals(committed[MayaComposition.METRIC_FRAME_ATTEMPTS] + 1,
                noop[MayaComposition.METRIC_FRAME_ATTEMPTS]);
        assertEquals(committed[MayaComposition.METRIC_FRAME_COMMITS],
                noop[MayaComposition.METRIC_FRAME_COMMITS],
                "unchanged native frame must not submit another Commit");
        assertEquals(committed[MayaComposition.METRIC_FRAME_NOOPS] + 1,
                noop[MayaComposition.METRIC_FRAME_NOOPS]);

        // A successful full-image EndDraw is pending even with identical
        // offsets, opacity, native Z order and visibility.
        int[] blue = new int[SIZE * SIZE];
        Arrays.fill(blue, OPAQUE_BLUE);
        assertEquals(0, MayaComposition._nUpload(red, blue));
        long[] pending = MayaComposition.readMetrics(composition);
        assertEquals(1L, pending[MayaComposition.METRIC_PENDING_SURFACE]);
        assertEquals(noop[MayaComposition.METRIC_FULL_UPLOADS] + 1,
                pending[MayaComposition.METRIC_FULL_UPLOADS]);
        frame(order, props);
        long[] pixelCommit = MayaComposition.readMetrics(composition);
        assertEquals(noop[MayaComposition.METRIC_FRAME_COMMITS] + 1,
                pixelCommit[MayaComposition.METRIC_FRAME_COMMITS]);
        assertEquals(0L, pixelCommit[MayaComposition.METRIC_PENDING_SURFACE]);
        assertEquals(BLUE, screen(30, 30));

        assertNotEquals(0, MayaComposition._nApply(composition,
                new long[] { Long.MAX_VALUE }, props, 1));
        long[] rejected = MayaComposition.readMetrics(composition);
        assertEquals(pixelCommit[MayaComposition.METRIC_FRAME_REJECTIONS] + 1,
                rejected[MayaComposition.METRIC_FRAME_REJECTIONS]);
        assertEquals(pixelCommit[MayaComposition.METRIC_FRAME_COMMITS],
                rejected[MayaComposition.METRIC_FRAME_COMMITS]);

        long[] tooShort = new long[MayaComposition.METRIC_COUNT - 1];
        assertNotEquals(0, MayaComposition._nReadMetrics(composition, tooShort));
        long[] withTail = new long[MayaComposition.METRIC_COUNT + 1];
        withTail[withTail.length - 1] = -77;
        assertEquals(0, MayaComposition._nReadMetrics(composition, withTail));
        assertEquals(-77L, withTail[withTail.length - 1],
                "metrics JNI only writes its stable 16-field prefix");
        MayaComposition._nRemoveLayer(composition, red);
        long[] removed = MayaComposition.readMetrics(composition);
        assertEquals(0L, removed[MayaComposition.METRIC_LIVE_LAYERS]);
        assertEquals(rejected[MayaComposition.METRIC_LAYERS_RETIRED] + 1,
                removed[MayaComposition.METRIC_LAYERS_RETIRED]);
        assertNotEquals(0, MayaComposition._nReadMetrics(Long.MAX_VALUE,
                new long[MayaComposition.METRIC_COUNT]));
    }

	@Test
	public void oversizedFrameBuffersReadOnlyTheActivePrefix() {
		long red = layer(OPAQUE_RED), blue = layer(OPAQUE_BLUE);
		long[] handles = new long[8];
		float[] props = new float[32];
		Arrays.fill(props, 8, props.length, Float.NaN); // unused capacity must never influence a frame

		handles[0] = red;
		handles[1] = blue;
		System.arraycopy(new float[] { 20, 20, 1, 1, 110, 110, 1, 1 }, 0, props, 0, 8);
		assertEquals(0, MayaComposition._nApply(composition, handles, props, 2));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(30, 30));
		assertEquals(BLUE, screen(140, 140));

		// Reuse the same backing arrays. A shrinking active count must hide the old second layer.
		assertEquals(0, MayaComposition._nApply(composition, handles, props, 1));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(30, 30));
		assertEquals(WHITE, screen(140, 140));

		// Growing back into the existing capacity must preserve order, opacity and coordinates.
		handles[0] = blue;
		handles[1] = red;
		System.arraycopy(new float[] { 20, 20, 1, 1, 20, 20, 1, 1 }, 0, props, 0, 8);
		assertEquals(0, MayaComposition._nApply(composition, handles, props, 2));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(30, 30), "the second layer is above the first");
	}

	@Test
	public void imageUploadAfterIdenticalFrameMustCommitSurfacePixels() {
		long colored = layer(OPAQUE_RED);
		long[] order = { colored };
		float[] frame = { 20, 20, 1, 1 };
		frame(order, frame);
		assertEquals(RED, screen(30, 30));
		int[] next = new int[SIZE * SIZE];
		Arrays.fill(next, OPAQUE_BLUE);
		assertEquals(0, MayaComposition._nUpload(colored, next));
		frame(order, frame); // no property changed; EndDraw still requires Commit
		assertEquals(BLUE, screen(30, 30), "surface content changed even when visual properties did not");
		frame(order, frame); // identical and no pending upload remains a no-op
		assertEquals(BLUE, screen(30, 30));
	}

	@Test
	public void nativeSceneFrameAppliesAffineClipAndLegacyReset() {
		long red = layer(OPAQUE_RED);
		long[] handles = { red };
		float[] scene = {
				0, 0, 1, 1,
				2, 0, 0, 1, 20, 20,
				0, 0, 25, 60
		};
		assertEquals(MayaComposition.SCENE_FLOATS, scene.length);
		int[] flags = { MayaComposition.SCENE_CLIP };
		assertEquals(0, MayaComposition._nApplyScene(composition, handles, scene, flags, 1));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(30, 30));
		assertEquals(WHITE, screen(80, 30), "local clip is transformed with the visual");

		scene[12] = 60; // expand only the clip, not the image or native Z
		assertEquals(0, MayaComposition._nApplyScene(composition, handles, scene, flags, 1));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(80, 30));
		assertEquals(0, MayaComposition._nApplyScene(composition, handles, scene, flags, 1));
		assertEquals(0, MayaComposition._nApplyScene(composition, handles, scene, flags, 1));

		float previous = scene[4];
		scene[4] = Float.NaN;
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, scene, flags, 1));
		scene[4] = previous;
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, scene,
				new int[] { 2 }, 1), "unknown scene flags");
		assertNotEquals(0, MayaComposition._nApplyScene(composition, handles, new float[13], flags, 1));
		assertEquals(RED, screen(80, 30), "invalid requests retain the last committed pixels");

		frame(handles, 20, 20, 1, 1); // old ABI must remove the affine matrix and clip
		assertEquals(RED, screen(80, 30));
		assertEquals(WHITE, screen(140, 30));
	}

	@Test
	public void packedFrameAfterAffineSceneRestoresLegacyVisualState() {
		long red = layer(OPAQUE_RED);
		long[] handles = { red };
		float[] scene = {
				0, 0, 1, 1,
				2, 0, 0, 1, 20, 20,
				0, 0, 25, 60
		};
		assertEquals(0, MayaComposition._nApplyScene(composition, handles, scene,
				new int[] { MayaComposition.SCENE_CLIP }, 1));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(WHITE, screen(80, 30), "affine clip bounds the original image");
		var packet = new MayaFramePacket.Encoder()
				.encode(handles, new float[] { 20, 20, 1, 1 }, 1, 1);
		assertEquals(0, MayaComposition._nApplyPacked(composition, packet, packet.remaining()));
		assertEquals(0, MayaComposition._nWaitForCommit(composition));
		pump(250);
		assertEquals(RED, screen(80, 30), "the packed legacy API must restore identity and remove the clip");
		assertEquals(WHITE, screen(140, 30));
	}

    @Test
    public void nativeSurfaceQuotaIsOptInEnforcedAndReclaimedOnRetirement() {
        long[] initial = MayaComposition.readSurfaceBudget(composition);
        if (Boolean.getBoolean("swt.maya.surfaceBudget.required")) {
            assertTrue(initial != null, "required surface-budget JNI ABI is absent");
        }
        assumeTrue(initial != null, "older Maya native DLL lacks budget ABI");
        assertEquals(1L, initial[MayaComposition.BUDGET_VERSION]);
        assertEquals(0L, initial[MayaComposition.BUDGET_LIMIT_BYTES],
                "without opt-in the budget is unlimited");
        assertEquals(0L, initial[MayaComposition.BUDGET_RESERVED_BYTES]);
        final long oneSurface = SIZE * SIZE * 4L;
        assertEquals(0, MayaComposition.setSurfaceBudget(composition, oneSurface));
        long red = layer(OPAQUE_RED);
        assertEquals(0L, MayaComposition._nAddLayer(composition, SIZE, SIZE),
                "quota rejection happens before allocating the second native surface");
        long[] refused = MayaComposition.readSurfaceBudget(composition);
        assertEquals(oneSurface, refused[MayaComposition.BUDGET_RESERVED_BYTES]);
        assertEquals(oneSurface, refused[MayaComposition.BUDGET_LIMIT_BYTES]);
        assertEquals(oneSurface, refused[MayaComposition.BUDGET_PEAK_BYTES]);
        assertEquals(1L, refused[MayaComposition.BUDGET_DENIALS]);
        assertEquals(1L, refused[MayaComposition.BUDGET_ACCEPTED]);
        assertNotEquals(0, MayaComposition.setSurfaceBudget(composition, oneSurface - 1),
                "reducing the cap below a live reservation must preserve the old cap");
        assertEquals(oneSurface, MayaComposition.readSurfaceBudget(
                composition)[MayaComposition.BUDGET_LIMIT_BYTES]);

        frame(new long[] { red }, 20, 20, 1, 1);
        assertEquals(RED, screen(25, 25), "over-quota rejection preserved the valid scene");
        assertEquals(0, MayaComposition.setSurfaceBudget(composition, 2 * oneSurface));
        long blue = layer(OPAQUE_BLUE);
        assertNotEquals(red, blue);
        frame(new long[] { red, blue }, 20, 20, 1, 1, 120, 20, 1, 1);
        assertEquals(RED, screen(25, 25));
        assertEquals(BLUE, screen(125, 25));
        long[] both = MayaComposition.readSurfaceBudget(composition);
        assertEquals(2 * oneSurface, both[MayaComposition.BUDGET_RESERVED_BYTES]);
        assertEquals(2 * oneSurface, both[MayaComposition.BUDGET_PEAK_BYTES]);
        assertEquals(2L, both[MayaComposition.BUDGET_ACCEPTED]);

        MayaComposition._nRemoveLayer(composition, blue);
        frame(new long[] { red }, 20, 20, 1, 1);
        assertEquals(oneSurface, MayaComposition.readSurfaceBudget(
                composition)[MayaComposition.BUDGET_RESERVED_BYTES]);
        assertEquals(0, MayaComposition.setSurfaceBudget(composition, oneSurface));
        assertEquals(0L, MayaComposition._nAddLayer(composition, SIZE, SIZE));
        assertEquals(2L, MayaComposition.readSurfaceBudget(
                composition)[MayaComposition.BUDGET_DENIALS]);
        assertEquals(0, MayaComposition.setSurfaceBudget(composition, 0),
                "explicit zero restores default unlimited admission");
        long reopened = layer(OPAQUE_BLUE);
        frame(new long[] { red, reopened }, 20, 20, 1, 1, 120, 20, 1, 1);
        long[] unlimited = MayaComposition.readSurfaceBudget(composition);
        assertEquals(0, unlimited[MayaComposition.BUDGET_LIMIT_BYTES]);
        assertEquals(2 * oneSurface, unlimited[MayaComposition.BUDGET_RESERVED_BYTES]);

        long[] tooShort = new long[MayaComposition.SURFACE_BUDGET_FIELDS - 1];
        assertNotEquals(0, MayaComposition._nReadSurfaceBudget(composition, tooShort));
        long[] extra = new long[MayaComposition.SURFACE_BUDGET_FIELDS + 1];
        extra[extra.length - 1] = -73;
        assertEquals(0, MayaComposition._nReadSurfaceBudget(composition, extra));
        assertEquals(-73L, extra[extra.length - 1],
                "the native snapshot may write only its six-versioned-field prefix");
        assertNotEquals(0, MayaComposition._nReadSurfaceBudget(Long.MAX_VALUE,
                new long[MayaComposition.SURFACE_BUDGET_FIELDS]));

        MayaComposition._nRemoveLayer(composition, red);
        MayaComposition._nRemoveLayer(composition, reopened);
        assertEquals(0L, MayaComposition.readSurfaceBudget(
                composition)[MayaComposition.BUDGET_RESERVED_BYTES]);
        assertEquals(2 * oneSurface, MayaComposition.readSurfaceBudget(
                composition)[MayaComposition.BUDGET_PEAK_BYTES],
                "the high-water logical estimate is retained after all releases");
    }

	@Test
	public void invalidArgumentsAreRefused() {
		assertEquals(0, MayaComposition._nAddLayer(0, SIZE, SIZE));
		assertEquals(0, MayaComposition._nAddLayer(composition, 0, SIZE));
		long red = MayaComposition._nAddLayer(composition, SIZE, SIZE);
		assertNotEquals(0, MayaComposition._nUpload(red, new int[SIZE]), "too few pixels");
		assertNotEquals(0, MayaComposition._nUpload(0, new int[SIZE * SIZE]));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red }, new float[2], 1), "too few properties");
		assertNotEquals(0, MayaComposition._nApply(composition, null, new float[0], 0), "null handles");
		assertNotEquals(0, MayaComposition._nApply(composition, new long[0], null, 0), "null properties");
		assertNotEquals(0, MayaComposition._nApply(composition, new long[0], new float[0],
				Integer.MAX_VALUE), "the property count must never overflow");
		assertNotEquals(0, MayaComposition._nApply(0, new long[0], new float[0], 0));
	}

	@Test
	public void invalidNativeHandlesAndFrameArraysNeverReachTheCompositor() {
		long red = layer(OPAQUE_RED);
		frame(new long[] { red }, 20, 20, 1, 1);
		assertEquals(RED, screen(30, 30));
		assertNotEquals(0, MayaComposition._nApply(composition, null, new float[0], 0));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[0], null, 0));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, 1, 1 }, Integer.MAX_VALUE));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { Long.MAX_VALUE },
				new float[] { 20, 20, 1, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red, red },
				new float[] { 20, 20, 1, 1, 20, 20, 1, 1 }, 2));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { Float.NaN, 20, 1, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, Float.POSITIVE_INFINITY, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, -0.1f, 1 }, 1));
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 20, 20, 1, 0.5f }, 1));
		assertEquals(RED, screen(30, 30), "invalid frames must not alter the last committed frame");
		assertNotEquals(0, MayaComposition._nUpload(Long.MAX_VALUE, new int[SIZE * SIZE]));
		assertEquals(0, MayaComposition._nAddLayer(Long.MAX_VALUE, 2, 2));
		assertEquals(0, MayaComposition._nAddLayer(composition, Integer.MAX_VALUE, Integer.MAX_VALUE));
		assertNotEquals(0, MayaComposition._nWaitForCommit(Long.MAX_VALUE));
		MayaComposition._nRemoveLayer(composition, Long.MAX_VALUE);
		MayaComposition._nDispose(Long.MAX_VALUE);
		MayaComposition._nRemoveLayer(composition, red);
		assertNotEquals(0, MayaComposition._nUpload(red, new int[SIZE * SIZE]),
				"a removed layer handle must be refused");
		assertNotEquals(0, MayaComposition._nApply(composition, new long[] { red },
				new float[] { 0, 0, 1, 1 }, 1));
	}
}
