/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
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
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.tests.junit.Test_org_eclipse_swt_internal_render_RowRenderer.Recorder;
import org.junit.jupiter.api.*;

/**
 * The frame slab of brief rule 28 (leaf R28-2), headless: a row read back from a slab paints exactly as the row the
 * plane filled; the slab carries the keep instructions of rule 24 and the font descriptors; slabs cross threads through
 * a triple buffer without a torn frame; filling and reading a frame allocates nothing once the lanes fit.
 */
public class Test_org_eclipse_swt_internal_render_FrameSlab {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_internal_render_FrameSlab.class.getName ());
	static final ImageData PIXELS = pixels ();

	static ImageData pixels () {
		ImageData data = new ImageData (4, 4, 32, new PaletteData (0xFF0000, 0xFF00, 0xFF));
		data.alphaData = new byte [16];
		Arrays.fill (data.alphaData, (byte) 0xFF);
		return data;
	}

	/* a row as a plane fills it: three cells, colours, a cell font, an image as pixels in the second cell */
	static RowPaint row (int i) {
		RowPaint row = Test_org_eclipse_swt_internal_render_RowRenderer.row (RowPaint.CHILDREN | (i % 2 == 0 ? RowPaint.SELECTED : 0) | RowPaint.CHECKBOX);
		row.y = i * 18;
		row.index = i;
		row.cells (3);
		for (int c = 0; c < 3; c++) {
			row.cellX [c] = 40 + c * 100;
			row.cellWidth [c] = 100;
			row.textX [c] = 42 + c * 100;
			row.textWidth [c] = 96;
			row.text [c] = "r" + i + "c" + c;
		}
		row.cellFont [2] = 0x7FFF_1234_5678L + i;
		row.cellForeground [1] = 0x123456;
		row.imageData [1] = PIXELS;
		row.imageX [1] = 141;
		row.imageWidth [1] = 16;
		row.font = 0x0000_7FFF_0000_0042L;
		return row;
	}

	@Test
	public void aRowReadBackPaintsLikeTheRow () {
		RowRenderer renderer = new RowRenderer (Theme.SWING);
		FrameSlab slab = new FrameSlab ();
		Recorder direct = new Recorder (), readBack = new Recorder ();
		for (int i = 0; i < 40; i++) {
			RowPaint row = row (i);
			renderer.paint (direct, row);
			slab.add (row, i % 3 == 0 ? FrameSlab.KEEP : i % 3 == 1 ? FrameSlab.DRAW : 200);
		}
		assertEquals (40, slab.rows);
		RowPaint into = new RowPaint ();
		for (int r = 0; r < slab.rows; r++) {
			slab.read (r, into);
			renderer.paint (readBack, into);
			assertNull (into.image [1], "no SWT image on the render side");
			assertSame (PIXELS, into.imageData [1]);
			assertEquals (0x7FFF_1234_5678L + r, into.cellFont [2], "a 64-bit font handle survives");
			assertEquals (-1L, into.cellFont [0], "the default font stays -1");
		}
		assertEquals (direct.calls, readBack.calls, "the same canvas calls");
		assertTrue (direct.count ("imageData") >= 40, "the cell images are drawn from their pixels");
		assertEquals (FrameSlab.KEEP, slab.keep (0));
		assertEquals (FrameSlab.DRAW, slab.keep (1));
		assertEquals (200, slab.keep (2));
	}

	@Test
	public void fontsAreDescribedOncePerHandle () {
		FrameSlab slab = new FrameSlab ();
		FontDescriptor segoe = new FontDescriptor ("Segoe UI", -12, 400, false);
		for (int i = 0; i < 20; i++) slab.font (0x42 + i % 3, i % 3 == 0 ? segoe : new FontDescriptor ("Consolas", -13, 700, i % 2 == 0));
		assertEquals (3, slab.fonts ());
		assertSame (segoe, slab.font (0x42));
		assertNull (slab.font (7));
		slab.clear ();
		assertEquals (0, slab.fonts ());
		assertNull (slab.font (0x42));
	}

	@Test
	public void slabsCrossThreadsWhole () throws InterruptedException {
		TripleBuffer<FrameSlab> frames = new TripleBuffer<> (FrameSlab::new);
		AtomicBoolean stop = new AtomicBoolean ();
		AtomicLong torn = new AtomicLong (), backwards = new AtomicLong (), seen = new AtomicLong ();
		Thread render = new Thread (() -> {
			RowPaint into = new RowPaint ();
			long previous = 0;
			while (!stop.get ()) {
				FrameSlab slab = frames.acquire ();
				if (slab == null) continue;
				long generation = frames.acquired ();
				if (generation < previous) backwards.incrementAndGet ();
				previous = generation;
				/* every row of a frame names the frame's generation in its first text */
				String tag = "g" + slab.zoom;
				for (int r = 0; r < slab.rows; r++) {
					slab.read (r, into);
					if (!into.text [0].startsWith (tag + "r")) torn.incrementAndGet ();
				}
				seen.incrementAndGet ();
			}
		}, "render");
		render.start ();
		String [] tags = new String [64];
		for (int f = 1; f <= 50_000; f++) {
			FrameSlab slab = frames.writable ();
			slab.clear ();
			slab.zoom = f;
			RowPaint row = new RowPaint ();
			row.cells (1);
			for (int r = 0; r < 24; r++) {
				tags [r] = "g" + f + "r" + r;
				row.text [0] = tags [r];
				row.y = r;
				slab.add (row, FrameSlab.DRAW);
			}
			frames.publish ();
		}
		stop.set (true);
		render.join (10_000);
		LOG.info ("frame slabs: 50000 published, " + seen.get () + " frames read concurrently");
		assertEquals (0, torn.get (), "a frame mixed rows of two generations");
		assertEquals (0, backwards.get (), "the newest generation wins: never an older one");
		assertEquals (50_000, frames.generation ());
	}

	@Test
	public void framesAllocateNothingOnceTheLanesFit () {
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean ();
		TripleBuffer<FrameSlab> frames = new TripleBuffer<> (FrameSlab::new);
		RowPaint [] rows = new RowPaint [40];
		Arrays.setAll (rows, Test_org_eclipse_swt_internal_render_FrameSlab::row);
		RowPaint into = new RowPaint ();
		FontDescriptor font = new FontDescriptor ("Segoe UI", -12, 400, false);
		Runnable frame = () -> {
			FrameSlab slab = frames.writable ();
			slab.clear ();
			for (RowPaint row : rows) slab.add (row, FrameSlab.DRAW);
			slab.font (0x42, font);
			frames.publish ();
			FrameSlab read = frames.acquire ();
			for (int r = 0; r < read.rows; r++) read.read (r, into);
		};
		for (int i = 0; i < 2_000; i++) frame.run ();
		long id = Thread.currentThread ().threadId (), before = threads.getThreadAllocatedBytes (id);
		for (int i = 0; i < 10_000; i++) frame.run ();
		long bytes = threads.getThreadAllocatedBytes (id) - before;
		LOG.info ("frame slabs: 10000 frames of 40 rows written and read, " + bytes + " bytes allocated");
		assertTrue (bytes < 4096, "a frame allocated: " + bytes + " bytes over 10000 frames");
	}
}
