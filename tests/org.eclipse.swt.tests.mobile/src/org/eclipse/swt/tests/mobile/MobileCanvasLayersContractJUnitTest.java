/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - Canvas layer presentation contract coverage
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.tests.junit.Test_CanvasLayers;
import org.eclipse.swt.widgets.Listener;
import org.junit.jupiter.api.Test;

/**
 * Executes original Canvas layer APIs through the existing Java2D test transport.
 * Presented pixels are transport evidence; Android/iOS and OS screen capture
 * qualification remain separate native gates.
 */
public final class MobileCanvasLayersContractJUnitTest extends Test_CanvasLayers {
	private ContractPlatform platform;

	@Override
	protected void beforeDisplay () {
		platform = new ContractPlatform ();
		MobileRuntime.install (platform);
	}

	@Override
	protected void afterDisplay () {
		try {
			assertEquals (platform.openedGraphics, platform.closedGraphics,
					"Every real GC opened by painting must be released");
			assertTrue (platform.images.isEmpty (), "Layer and backing images cannot outlive Display");
			assertEquals (platform.createdImages, platform.disposedImages,
					"Every cache upload must eventually release its transport image");
			assertTrue (platform.controls.isEmpty (), "Native peer ownership must be balanced");
		} finally {
			MobileRuntime.uninstall (platform);
		}
	}

	private void drain () {
		for (int i = 0; i < 10000; i++) if (!display.readAndDispatch ()) return;
		fail ("Canvas repaint queue did not quiesce");
	}

	private int presented (long peer, int x, int y) {
		int [] frame = platform.controls.get (peer).frame;
		assertNotNull (frame, "Paint must present a completed backing image");
		return frame [y * canvas.getClientArea ().width + x];
	}

	@Test
	public void test_presentedPixelsErasePreviousLayerExtent () {
		long peer = platform.lastCreated;
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		shell.open ();
		drain ();
		int backingImages = platform.images.size ();
		int layer = canvas.addLayer (solid (3, 2, RED));
		drain ();
		assertEquals (RED, presented (peer, 0, 0));
		assertTrue (platform.images.size () <= backingImages + 1);
		canvas.setLayerLocation (layer, 10, 11);
		drain ();
		assertEquals (WHITE, presented (peer, 0, 0));
		assertEquals (RED, presented (peer, 12, 12));
		assertTrue (platform.images.size () <= backingImages + 1,
				"Movement inside one canonical tile cannot retain a second tile image");
		canvas.setLayerVisible (layer, false);
		drain ();
		assertEquals (WHITE, presented (peer, 12, 12));
		canvas.setLayerImage (layer, solid (3, 2, BLUE));
		canvas.setLayerVisible (layer, true);
		drain ();
		assertEquals (BLUE, presented (peer, 12, 12));
		canvas.removeAllLayers ();
		drain ();
		assertEquals (WHITE, presented (peer, 12, 12));
		assertEquals (backingImages, platform.images.size (), "Clearing layers releases their cached Images");
		assertEquals (platform.openedGraphics, platform.closedGraphics);
	}

	@Test
	public void test_hiddenCanvasPresentsLatestLayerWhenShown () {
		long peer = platform.lastCreated;
		canvas.setVisible (false);
		int layer = canvas.addLayer (solid (2, 2, RED));
		shell.open ();
		drain ();
		assertNull (platform.controls.get (peer).frame);
		canvas.setLayerImage (layer, solid (2, 2, GREEN));
		canvas.setVisible (true);
		drain ();
		assertEquals (GREEN, presented (peer, 1, 1));
		assertEquals (platform.openedGraphics, platform.closedGraphics);
	}

	@Test
	public void test_repeatedTiledRepaintAndDisposeBalanceResources () {
		long peer = platform.lastCreated;
		canvas.setSize (259, 131);
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		shell.open ();
		drain ();
		int backingImages = platform.images.size ();
		int layer = canvas.addLayer (solid (259, 131, RED));
		drain ();
		int steadyImages = platform.images.size ();
		assertTrue (steadyImages > backingImages, "Visible tiles should be retained for reuse");
		assertTrue (steadyImages <= backingImages + 6, "The 3 by 2 tile viewport needs at most six cached Images");
		for (int iteration = 0; iteration < 16; iteration++) {
			canvas.setLayerOpacity (layer, 128 + iteration);
			canvas.setLayerLocation (layer, iteration & 1, 0);
			drain ();
			assertEquals (steadyImages, platform.images.size (), "No per-repaint Image retention");
			assertEquals (platform.openedGraphics, platform.closedGraphics, "No per-repaint GC retention");
		}
		canvas.dispose ();
		drain ();
		assertFalse (platform.controls.containsKey (peer));
		assertEquals (backingImages - 1, platform.images.size (),
				"Disposal releases every cached image and the Canvas backing image");
		assertEquals (platform.openedGraphics, platform.closedGraphics);
	}

	@Test
	public void test_warmExposureReusesImagesAndMutationIsAPositiveControl () {
		long peer = platform.lastCreated;
		canvas.setSize (256, 128);
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		shell.open ();
		drain ();
		int backingImages = platform.images.size ();
		int layer = canvas.addLayer (solid (256, 128, RED));
		drain ();
		int uploads = platform.createdImages;
		int draws = platform.imageDraws;
		int presentations = platform.presentations;
		for (int iteration = 0; iteration < 5; iteration++) {
			canvas.redraw ();
			drain ();
		}
		assertTrue (platform.presentations > presentations, "The warm exposure path must actually paint");
		assertTrue (platform.imageDraws > draws, "The warm exposure path must draw retained tile Images");
		assertEquals (uploads, platform.createdImages, "Unchanged exposure must perform zero new tile uploads");
		assertEquals (RED, presented (peer, 255, 127));
		canvas.setLayerOpacity (layer, 128);
		drain ();
		assertTrue (platform.createdImages > uploads, "A changed layer proves the upload counter detects real work");
		assertEquals (0xffff7f7f, presented (peer, 255, 127));
		canvas.removeAllLayers ();
		drain ();
		assertEquals (backingImages, platform.images.size ());
	}

	@Test
	public void test_coldFullEventWithOnePixelGcClipUploadsOnlyOneTile () {
		canvas.setSize (256, 128);
		canvas.addLayer (solid (256, 128, RED));
		Image target = new Image (display, 256, 128);
		try {
			GC gc = new GC (target);
			try {
				gc.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
				gc.fillRectangle (0, 0, 256, 128);
				gc.setClipping (130, 3, 1, 1);
				int uploads = platform.createdImages;
				int draws = platform.imageDraws;
				paint (gc, 0, 0, 256, 128);
				assertEquals (uploads + 1, platform.createdImages,
						"A full Paint event must not upload tiles outside the actual GC clip");
				assertEquals (draws + 1, platform.imageDraws);
				paint (gc, 0, 0, 256, 128);
				assertEquals (uploads + 1, platform.createdImages,
						"Repeating the clipped exposure must reuse its one warm tile");
				assertEquals (draws + 2, platform.imageDraws, "The warm path must actually draw");
			} finally {
				gc.dispose ();
			}
			var pixels = target.getImageData ();
			assertEquals (RED, rgb (pixels, 130, 3));
			assertEquals (WHITE, rgb (pixels, 0, 0));
			assertEquals (WHITE, rgb (pixels, 129, 3));
			assertEquals (WHITE, rgb (pixels, 131, 3));
			assertEquals (WHITE, rgb (pixels, 130, 2));
			assertEquals (WHITE, rgb (pixels, 130, 4));
			assertEquals (WHITE, rgb (pixels, 255, 127));
		} finally {
			target.dispose ();
		}
	}

	@Test
	public void test_localMutationRetainsUnrelatedNativeTile () {
		long peer = platform.lastCreated;
		canvas.setSize (256, 128);
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		int left = canvas.addLayer (solid (8, 8, RED));
		int right = canvas.addLayer (solid (8, 8, BLUE));
		canvas.setLayerLocation (right, 140, 0);
		shell.open ();
		drain ();
		long rightTile = -1;
		for (var entry : platform.images.entrySet ()) {
			var image = entry.getValue ();
			if (image.getWidth () == 128 && image.getHeight () == 128 && image.getRGB (12, 0) == BLUE) {
				assertEquals (-1, rightTile, "The separate blue tile must have one native image owner");
				rightTile = entry.getKey ();
			}
		}
		assertNotEquals (-1, rightTile, "The second canonical tile was uploaded");
		int uploads = platform.createdImages;
		canvas.setLayerImage (left, solid (8, 8, GREEN));
		drain ();
		assertTrue (platform.images.containsKey (rightTile), "Local invalidation must preserve an unrelated native tile handle");
		assertEquals (uploads + 1, platform.createdImages, "Only the changed tile is uploaded again");
		assertEquals (GREEN, presented (peer, 0, 0));
		assertEquals (BLUE, presented (peer, 140, 0));
	}

	@Test
	public void test_movingAcrossAnUnchangedTilePreservesItsImage () {
		long peer = platform.lastCreated;
		canvas.setSize (384, 128);
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		int moving = canvas.addLayer (solid (8, 8, RED));
		int middle = canvas.addLayer (solid (8, 8, BLUE));
		canvas.setLayerLocation (middle, 140, 0);
		shell.open ();
		drain ();
		long middleTile = -1;
		for (var entry : platform.images.entrySet ()) {
			var image = entry.getValue ();
			if (image.getWidth () == 128 && image.getHeight () == 128 && image.getRGB (12, 0) == BLUE) {
				assertEquals (-1, middleTile);
				middleTile = entry.getKey ();
			}
		}
		assertNotEquals (-1, middleTile);
		canvas.setLayerLocation (moving, 270, 0);
		drain ();
		assertTrue (platform.images.containsKey (middleTile),
				"Moving between two disjoint extents must preserve the unrelated tile between them");
		assertEquals (WHITE, presented (peer, 0, 0));
		assertEquals (BLUE, presented (peer, 140, 0));
		assertEquals (RED, presented (peer, 270, 0));
	}

	@Test
	public void test_offscreenAndNoOpMutationsDoNotSchedulePaint () {
		long peer = platform.lastCreated;
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		int layer = canvas.addLayer (solid (2, 2, RED));
		canvas.setLayerLocation (layer, 1000, 1000);
		shell.open ();
		drain ();
		int presentations = platform.presentations;
		int uploads = platform.createdImages;
		canvas.setLayerLocation (layer, 1001, 1000);
		canvas.setLayerOpacity (layer, 128);
		canvas.setLayerImage (layer, solid (2, 2, BLUE));
		drain ();
		assertEquals (presentations, platform.presentations, "Offscreen mutation cannot damage this Canvas viewport");
		assertEquals (uploads, platform.createdImages);
		canvas.setLayerLocation (layer, 0, 0);
		drain ();
		assertTrue (platform.presentations > presentations, "Visible movement is the repaint positive control");
		assertEquals (0xff7f7fff, presented (peer, 0, 0));
		presentations = platform.presentations;
		uploads = platform.createdImages;
		canvas.setLayerZOrder (layer, canvas.getLayerZOrder (layer));
		canvas.setLayerOpacity (layer, canvas.getLayerOpacity (layer));
		canvas.setLayerVisible (layer, canvas.getLayerVisible (layer));
		canvas.setLayerInputEnabled (layer, canvas.getLayerInputEnabled (layer));
		canvas.setLayerLocation (layer, 0, 0);
		canvas.setLayerTransform (layer, null);
		canvas.setLayerClip (layer, null);
		canvas.setLayerMask (layer, null);
		drain ();
		assertEquals (presentations, platform.presentations, "Unchanged properties must avoid repaint");
		assertEquals (uploads, platform.createdImages);
	}

	@Test
	public void test_cacheBoundAndEvictionKeepPixelsCorrect () {
		long peer = platform.lastCreated;
		canvas.setSize (640, 512); // Twenty visible 128 by 128 tiles exceed the sixteen-slot cache.
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		shell.open ();
		drain ();
		int backingImages = platform.images.size ();
		platform.peakImages = backingImages;
		canvas.addLayer (solid (640, 512, BLUE));
		drain ();
		assertTrue (platform.images.size () <= backingImages + 16, "Resident cache is bounded to sixteen Images");
		assertTrue (platform.peakImages <= backingImages + 17, "At most one replacement Image may transiently exceed the resident cache");
		assertTrue (platform.createdImages >= backingImages + 20, "The test must exercise more visible tiles than fit in the cache");
		assertEquals (BLUE, presented (peer, 0, 0));
		assertEquals (BLUE, presented (peer, 639, 511));
		int uploads = platform.createdImages;
		canvas.redraw (0, 0, 128, 128, false);
		drain ();
		assertTrue (platform.createdImages > uploads, "Repainting an evicted early tile must rebuild it");
		assertEquals (BLUE, presented (peer, 0, 0));
		assertEquals (BLUE, presented (peer, 127, 127));
		canvas.redraw (512, 384, 128, 128, false);
		drain ();
		assertEquals (BLUE, presented (peer, 639, 511));
		assertTrue (platform.images.size () <= backingImages + 16);
		assertTrue (platform.peakImages <= backingImages + 17);
		uploads = platform.createdImages;
		int presentations = platform.presentations;
		canvas.redraw ();
		drain ();
		assertTrue (platform.presentations > presentations, "The oversized warm exposure must actually paint");
		int newUploads = platform.createdImages - uploads;
		assertTrue (newUploads > 0, "Twenty nonempty visible tiles cannot all fit in sixteen cache slots");
		assertTrue (newUploads <= 5,
				"A full oversized exposure must protect resident tiles and avoid a twenty-tile eviction cascade");
		for (int pixel : platform.controls.get (peer).frame) {
			assertEquals (BLUE, pixel, "The entire oversized exposure must remain correct after eviction");
		}
		assertTrue (platform.images.size () <= backingImages + 16);
		assertTrue (platform.peakImages <= backingImages + 17);
		canvas.removeAllLayers ();
		drain ();
		assertEquals (backingImages, platform.images.size (), "Explicit clear releases the whole cache");
	}

	@Test
	public void test_suppressedDisposeEventCannotRetainTileImages () {
		long peer = platform.lastCreated;
		shell.open ();
		drain ();
		int backingImages = platform.images.size ();
		AtomicInteger callbacks = new AtomicInteger ();
		canvas.addDisposeListener (event -> callbacks.incrementAndGet ());
		canvas.addLayer (solid (16, 16, RED));
		drain ();
		assertTrue (platform.images.size () > backingImages);
		Listener suppress = event -> {
			if (event.widget == canvas) event.type = SWT.None;
		};
		display.addFilter (SWT.Dispose, suppress);
		try {
			canvas.dispose ();
		} finally {
			display.removeFilter (SWT.Dispose, suppress);
		}
		assertEquals (0, callbacks.get (), "The negative control must actually suppress Dispose listeners");
		assertTrue (canvas.isDisposed ());
		assertFalse (platform.controls.containsKey (peer));
		assertEquals (backingImages - 1, platform.images.size (),
				"Widget release must reclaim cached images even when the public event is suppressed");
		assertEquals (platform.openedGraphics, platform.closedGraphics);
	}

	@Test
	public void test_failingEarlierDisposeListenerCannotRetainTileImages () {
		long peer = platform.lastCreated;
		shell.open ();
		drain ();
		int backingImages = platform.images.size ();
		canvas.addDisposeListener (event -> { throw new IllegalStateException ("Injected dispose listener failure"); });
		canvas.addLayer (solid (16, 16, RED));
		drain ();
		assertTrue (platform.images.size () > backingImages);
		IllegalStateException failure = assertThrows (IllegalStateException.class, canvas::dispose);
		assertEquals ("Injected dispose listener failure", failure.getMessage ());
		assertTrue (canvas.isDisposed ());
		assertFalse (platform.controls.containsKey (peer));
		assertEquals (backingImages - 1, platform.images.size (),
				"A failed earlier listener cannot skip retained cache or backing-image cleanup");
		assertEquals (platform.openedGraphics, platform.closedGraphics);
	}

	@Test
	public void test_drawFailureRestoresGcAndKeepsImageOwnershipRecoverable () {
		int backingImages = platform.images.size ();
		canvas.addLayer (solid (4, 4, RED));
		Image target = new Image (display, 8, 8);
		try {
			GC gc = new GC (target);
			try {
				gc.setAlpha (83);
				platform.failNextImageDraw = true;
				IllegalStateException failure = assertThrows (IllegalStateException.class, () -> paint (gc, 0, 0, 8, 8));
				assertEquals ("Injected image draw failure", failure.getMessage ());
				assertEquals (83, gc.getAlpha (), "Exceptional drawing must restore caller graphics state");
				assertFalse (gc.isDisposed (), "The caller owns its GC");
				canvas.removeAllLayers ();
				assertEquals (backingImages + 1, platform.images.size (), "Clear releases cached images even after a failed draw");
				canvas.addLayer (solid (4, 4, BLUE));
				paint (gc, 0, 0, 8, 8);
				canvas.removeAllLayers ();
				assertEquals (backingImages + 1, platform.images.size ());
			} finally {
				gc.dispose ();
			}
			assertEquals (BLUE, rgb (target.getImageData (), 1, 1));
		} finally {
			target.dispose ();
		}
		assertEquals (backingImages, platform.images.size ());
		assertEquals (platform.openedGraphics, platform.closedGraphics);
	}
}
