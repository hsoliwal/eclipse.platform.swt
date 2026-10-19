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

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The interactive differential of the drawn modes (docs/m3/RENDER_MODES.md, interaction model, IM6; this is its stage 1
 * form over the IM1 frame log): a virtual Table of 10^6 rows on the invisible desktop, a scripted fast scroll (wheel
 * bursts, page keys) and typing while a model worker posts row removals and count changes in bursts. Every frame of
 * the drawn plane is in its frame log. Asserted: the snapshot generations never go back (I2); with
 * <code>-Dswt.drawn.budgets=true</code> also the frame time p99 under 16.6 ms and the input-to-pixel latency p99 under
 * two frames (I6). The numbers go to the log and, with <code>-Dswt.drawn.interaction=&lt;tsv&gt;</code>, to a TSV row;
 * the final pixels (a fixed view after the churn) are saved as <code>interaction-&lt;mode&gt;.png</code> in
 * <code>-Dswt.viewport.evidence</code> for the comparison across modes (gpu equals m3 pixel for pixel).
 */
public class Test_org_eclipse_swt_widgets_Table_DrawnInteraction {
	private static final Logger LOG = Logger.getLogger (Test_org_eclipse_swt_widgets_Table_DrawnInteraction.class.getName ());
	static final int ROWS = 1_000_000, STEPS = 240, CHURN = 400;
	static final int WM_KEYDOWN = 0x100, WM_CHAR = 0x102, WM_MOUSEWHEEL = 0x20A, VK_NEXT = 0x22, VK_PRIOR = 0x21;

	@Test
	public void fastScrollAndTypingUnderModelChurn () throws Exception {
		Assumptions.assumeTrue (ViewportDifferential.drawn ("Table"), "a drawn mode (custom, m3, gpu) only");
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (display.getBounds ().x, display.getBounds ().y, 420, 520);
		Table table = new Table (shell, SWT.VIRTUAL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		table.setBounds (5, 5, 400, 480);
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		for (int c = 0; c < 3; c++) new TableColumn (table, SWT.NONE).setWidth (c == 2 ? 180 : 100);
		table.addListener (SWT.SetData, event -> ((TableItem) event.item).setText (new String [] {"row " + event.index, "v" + event.index % 97, "a longer cell " + event.index}));
		table.setItemCount (ROWS);
		shell.open ();
		ViewportDifferential.settle (display, 200);
		FrameLog frames = frameLog (table);
		AtomicInteger applied = new AtomicInteger ();
		Thread worker = churn (display, table, applied);
		try {
			script (display, table);
		} finally {
			worker.join (60_000);
		}
		while (applied.get () < CHURN) ViewportDifferential.settle (display, 20);
		report (frames, finalPixels (display, table));
		shell.dispose ();
	}

	/* the model worker: bursts of removals and count changes, posted to the UI thread (seeded, CHURN operations) */
	static Thread churn (Display display, Table table, AtomicInteger applied) {
		Thread worker = new Thread (() -> {
			Random random = new Random (0xC0FFEEL);
			for (int i = 0; i < CHURN; i++) {
				int kind = random.nextInt (3), at = random.nextInt (ROWS / 2), delta = 1 + random.nextInt (50);
				display.asyncExec (() -> {
					if (!table.isDisposed ()) {
						if (kind == 0) table.remove (at); else if (kind == 1) table.setItemCount (table.getItemCount () + delta); else table.clear (at);
					}
					applied.incrementAndGet ();
				});
				if (i % 20 == 19) sleep (8);
			}
		}, "drawn-interaction-model");
		worker.start ();
		return worker;
	}

	/* fast scroll and typing: wheel bursts, page keys, type-ahead characters, each followed by one frame's time */
	static void script (Display display, Table table) {
		Random random = new Random (0x5C2011L);
		for (int step = 0; step < STEPS; step++) {
			int kind = random.nextInt (4);
			if (kind == 0) {
				for (int k = 0; k < 5; k++) ViewportDifferential.send (table, WM_MOUSEWHEEL, ((random.nextBoolean () ? -120 : 120) & 0xFFFFL) << 16, 0);
			} else if (kind == 1) {
				ViewportDifferential.send (table, WM_KEYDOWN, random.nextBoolean () ? VK_NEXT : VK_PRIOR, 0);
			} else if (kind == 2) {
				ViewportDifferential.send (table, WM_CHAR, 'r', 0);
			} else {
				table.setTopIndex (random.nextInt (ROWS / 2));
			}
			table.update ();
			ViewportDifferential.settle (display, 16);
		}
	}

	/* a fixed view once the churn is done: the pixels depend only on the final model */
	static ImageData finalPixels (Display display, Table table) {
		table.setTopIndex (123_456);
		table.setSelection (123_460);
		ViewportDifferential.settle (display, 100);
		ImageData pixels = ViewportDifferential.render (table);
		ViewportDifferential.save ("interaction-" + ViewportDifferential.reference ("Table"), pixels);
		return pixels;
	}

	static void report (FrameLog frames, ImageData pixels) throws IOException {
		String mode = ViewportDifferential.reference ("Table");
		double frameP99 = frames.frameTime (99) / 1e6, inputP99 = frames.inputLatency (99) / 1e6, frameP50 = frames.frameTime (50) / 1e6;
		String row = String.format (Locale.ROOT, "%s\t%d frames\tframe p50 %.3f ms\tframe p99 %.3f ms\tinput p99 %.3f ms\tmonotonic %s\tpixels %s", mode,
			frames.frames (), frameP50, frameP99, inputP99, frames.monotonic (), ViewportDifferential.hash (pixels));
		LOG.info ("drawn interaction: " + row);
		String output = System.getProperty ("swt.drawn.interaction");
		if (output != null) Files.write (Paths.get (output), List.of (row), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		assertTrue (frames.frames () > STEPS / 2, "the script drew frames: " + frames.frames ());
		assertTrue (frames.monotonic (), "I2: a frame painted an older snapshot");
		if (Boolean.getBoolean ("swt.drawn.budgets")) {
			if ("gpu".equals (mode)) assertTrue (frameP99 < 16.6, "I6: gpu frame time p99 " + frameP99 + " ms");
			assertTrue (inputP99 < 33.3, "I6: input-to-pixel latency p99 " + inputP99 + " ms");
		}
	}

	/* the drawn plane's frame log (Table.drawnPlane, DrawnPlane.frames: package-private) */
	static FrameLog frameLog (Table table) throws ReflectiveOperationException {
		Field plane = Table.class.getDeclaredField ("drawnPlane");
		plane.setAccessible (true);
		Object drawn = plane.get (table);
		assertNotNull (drawn, "the Table has no drawn plane in this mode");
		Field log = Class.forName ("org.eclipse.swt.widgets.DrawnPlane").getDeclaredField ("frames");
		log.setAccessible (true);
		return (FrameLog) log.get (drawn);
	}

	static void sleep (long millis) {
		try {
			Thread.sleep (millis);
		} catch (InterruptedException e) {
			Thread.currentThread ().interrupt ();
		}
	}
}
