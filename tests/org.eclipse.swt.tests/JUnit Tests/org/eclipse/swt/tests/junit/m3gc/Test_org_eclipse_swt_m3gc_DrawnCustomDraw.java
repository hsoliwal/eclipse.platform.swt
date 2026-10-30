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
package org.eclipse.swt.tests.junit.m3gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCExtension;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.render.DrawnCustomDraw;
import org.eclipse.swt.internal.render.m3.gc.M3ExternalGC;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.Test;

import io.github.humbleui.types.Rect;

/**
 * Headless (no Display): the owner-draw protocol of the drawn planes (GC seam, leaf 3) over a <code>GCExtension</code> on
 * the M3 doubles. Per cell MeasureItem, then EraseItem, then PaintItem; the detail bits a listener clears are the outcome;
 * the GC is clipped to the cell before EraseItem and PaintItem and released (disposed) after each event; nothing is sent
 * when nothing is hooked or no GC is available.
 */
public class Test_org_eclipse_swt_m3gc_DrawnCustomDraw {
	static final int MEASURE = 1, ERASE = 2, PAINT = 4, ALL = MEASURE | ERASE | PAINT;

	/** One event as a listener saw it: the Event is reused, so its fields are copied when it is sent. */
	record Seen(int type, int index, int detail, boolean doit, int x, int y, int width, int height, GC gc, int clipRectsSoFar) {
	}

	/** The protocol over a GCExtension on the M3 doubles, with a recording sender and a listener hook. */
	static final class Harness {
		final M3SurfaceDouble surface = new M3SurfaceDouble(null);
		final CanvasExtensionDouble ext = new CanvasExtensionDouble();
		final GCExtension gc;
		final List<Seen> seen = new ArrayList<>();
		final List<Integer> releasedAfter = new ArrayList<>();
		final DrawnCustomDraw protocol;
		int hooked, gcAsked;
		boolean noGC;
		Consumer<Event> listener = e -> {
		};

		Harness(int hooked, int zoom) {
			this.hooked = hooked;
			M3ResourcesDouble resources = new M3ResourcesDouble(zoom);
			ext.surface = surface;
			ext.resources = resources;
			ext.clientArea = new Rectangle(0, 0, 400, 300);
			ext.bounds = ext.clientArea;
			gc = new GCExtension(new M3ExternalGC(ext, SWT.NONE));
			protocol = new DrawnCustomDraw(type -> (this.hooked & bit(type)) != 0, this::send, this::supply, this::release);
			protocol.frame(zoom);
		}

		static int bit(int type) {
			return type == SWT.MeasureItem ? MEASURE : type == SWT.EraseItem ? ERASE : type == SWT.PaintItem ? PAINT : 0;
		}

		GC supply() {
			gcAsked++;
			return noGC ? null : gc;
		}

		void send(int type, Event e) {
			listener.accept(e);
			seen.add(new Seen(type, e.index, e.detail, e.doit, e.x, e.y, e.width, e.height, e.gc, clipRects()));
		}

		void release(GC released) {
			assertSame(gc, released);
			releasedAfter.add(seen.size());
			released.dispose();
		}

		int clipRects() {
			return (int) surface.calls.stream().filter(c -> "clipRect".equals(c.methodName)).count();
		}

		int restores() {
			return (int) surface.calls.stream().filter(c -> "restoreToCount".equals(c.methodName)).count();
		}
	}

	@Test
	public void testNothingHookedSendsNothing() {
		Harness h = new Harness(0, 100);
		assertFalse(h.protocol.hooked());
		assertFalse(h.protocol.measure(null, 0, 0, 20, 10, 60, 18));
		int detail = SWT.FOREGROUND | SWT.SELECTED | SWT.BACKGROUND;
		assertEquals(detail, h.protocol.erase(null, 0, detail, 0, 10, 100, 18), "nothing suppressed when EraseItem is not hooked");
		assertFalse(h.protocol.paint(null, 0, detail, 20, 10, 60, 18, 0, 10, 100, 18));
		assertEquals(0, h.seen.size());
		assertEquals(0, h.gcAsked, "no GC is asked for when nothing is hooked");
		assertEquals(0, h.protocol.sent);
	}

	@Test
	public void testNoGCSendsNothing() {
		Harness h = new Harness(ALL, 100);
		h.noGC = true;
		assertTrue(h.protocol.hooked());
		assertFalse(h.protocol.measure(null, 0, 0, 20, 10, 60, 18));
		assertEquals(SWT.FOREGROUND, h.protocol.erase(null, 0, SWT.FOREGROUND, 0, 10, 100, 18));
		assertFalse(h.protocol.paint(null, 0, SWT.FOREGROUND, 20, 10, 60, 18, 0, 10, 100, 18));
		assertEquals(0, h.seen.size());
		assertEquals(3, h.gcAsked);
	}

	@Test
	public void testMeasureThenEraseThenPaintPerCell() {
		Harness h = new Harness(ALL, 100);
		int detail = SWT.FOREGROUND | SWT.SELECTED | SWT.FOCUSED;
		assertTrue(h.protocol.measure(null, 2, SWT.SELECTED, 20, 10, 60, 18));
		assertEquals(detail, h.protocol.erase(null, 2, detail, 0, 10, 100, 18), "a listener that changes nothing keeps every bit");
		assertTrue(h.protocol.paint(null, 2, detail, 20, 10, 60, 18, 0, 10, 100, 18));
		assertEquals(3, h.seen.size());
		assertEquals(3, h.protocol.sent);
		Seen measure = h.seen.get(0), erase = h.seen.get(1), paint = h.seen.get(2);
		assertEquals(SWT.MeasureItem, measure.type());
		assertEquals(SWT.EraseItem, erase.type());
		assertEquals(SWT.PaintItem, paint.type());
		/* MeasureItem: the content bounds, SELECTED only, no clip (as natively) */
		assertEquals(List.of(2, SWT.SELECTED, 20, 10, 60, 18, 0), List.of(measure.index(), measure.detail(), measure.x(), measure.y(), measure.width(), measure.height(), measure.clipRectsSoFar()));
		/* EraseItem: the cell bounds, the full detail, the GC clipped to the cell before the send */
		assertEquals(List.of(2, detail, 0, 10, 100, 18, 1), List.of(erase.index(), erase.detail(), erase.x(), erase.y(), erase.width(), erase.height(), erase.clipRectsSoFar()));
		/* PaintItem: the content bounds again, clipped to the cell */
		assertEquals(List.of(2, detail, 20, 10, 60, 18, 2), List.of(paint.index(), paint.detail(), paint.x(), paint.y(), paint.width(), paint.height(), paint.clipRectsSoFar()));
		for (Seen s : h.seen) {
			assertSame(h.gc, s.gc(), "event.gc is the frame's GCExtension");
			assertTrue(s.doit());
		}
		assertEquals(List.of(1, 2, 3), h.releasedAfter, "the GC is released after each event");
		assertEquals(3, h.restores(), "each release restores the renderer's canvas to the frame's save count");
	}

	@Test
	public void testEraseDetailAndDoitAreTheOutcome() {
		Harness h = new Harness(ERASE | PAINT, 100);
		int detail = SWT.FOREGROUND | SWT.BACKGROUND | SWT.SELECTED;
		h.listener = e -> e.detail &= ~SWT.BACKGROUND;
		assertEquals(SWT.FOREGROUND | SWT.SELECTED, h.protocol.erase(null, 0, detail, 0, 0, 100, 18), "a cleared bit suppresses that default drawing");
		h.listener = e -> e.doit = false;
		assertEquals(0, h.protocol.erase(null, 0, detail, 0, 18, 100, 18), "doit false suppresses all default drawing");
		h.listener = e -> {
		};
		assertTrue(h.protocol.paint(null, 0, detail, 4, 18, 90, 18, 0, 18, 100, 18));
		Seen paint = h.seen.get(2);
		assertTrue(paint.doit(), "the reused event is reset: doit is true again for the next event");
		assertEquals(detail, paint.detail(), "the reused event is reset: the detail is the cell's again");
		assertEquals(0, h.seen.get(0).detail() & SWT.BACKGROUND, "the sender saw the listener's cleared bit");
		assertFalse(h.seen.get(1).doit(), "the sender saw the listener's doit");
	}

	@Test
	public void testClipIsTheCellInPointsScaledBackToPixels() {
		Harness h = new Harness(ERASE, 200);
		/* cell (10, 20, 30, 40) pixels at 200 % = (5, 10, 15, 20) points; the M3 GC scales the clip back up to the surface */
		h.protocol.erase(null, 1, SWT.FOREGROUND, 10, 20, 30, 40);
		Seen erase = h.seen.get(0);
		assertEquals(List.of(5, 10, 15, 20), List.of(erase.x(), erase.y(), erase.width(), erase.height()), "event bounds are points");
		assertTrue(h.surface.calls.contains(MethodCall.get("surface-canvas", "clipRect", new Rect(10f, 20f, 40f, 60f))), "the clip is the cell: " + h.surface.calls);
		assertEquals(1, erase.clipRectsSoFar(), "clipped before the listener ran");
		assertEquals(1, h.restores(), "released after the listener ran");
		int clipAt = -1, restoreAt = -1;
		for (int i = 0; i < h.surface.calls.size(); i++) {
			String name = h.surface.calls.get(i).methodName;
			if ("clipRect".equals(name)) clipAt = i;
			if ("restoreToCount".equals(name)) restoreAt = i;
		}
		assertTrue(clipAt >= 0 && restoreAt > clipAt, "clip, listener, release in this order: " + h.surface.calls);
	}

	@Test
	public void testMeasureReadsTheListenerBoundsBackInPixels() {
		Harness h = new Harness(MEASURE, 200);
		h.listener = e -> {
			e.width = 50;
			e.height = 40;
		};
		assertTrue(h.protocol.measure(null, 0, 0, 20, 10, 60, 18));
		Seen measure = h.seen.get(0);
		assertEquals(List.of(10, 5, 50, 40), List.of(measure.x(), measure.y(), measure.width(), measure.height()));
		assertEquals(20, h.protocol.measuredX);
		assertEquals(100, h.protocol.measuredWidth, "(10 + 50) points - 10 points, in pixels at 200 %");
		assertEquals(80, h.protocol.measuredHeight);
		assertNotNull(measure.gc());
	}

	@Test
	public void testGCExtensionIsReusableAfterDispose() {
		Harness h = new Harness(ERASE, 100);
		h.protocol.erase(null, 0, SWT.FOREGROUND, 0, 0, 100, 18);
		h.protocol.erase(null, 1, SWT.FOREGROUND, 100, 0, 100, 18);
		assertEquals(2, h.clipRects());
		assertEquals(2, h.restores());
		assertFalse(h.gc.isDisposed(), "the surface is open: the extension serves the next event");
		/* the second clip is a fresh save + clip, not a restore of the first (the dispose reset isClipSet) */
		int saves = (int) h.surface.calls.stream().filter(c -> "save".equals(c.methodName)).count();
		int restores = (int) h.surface.calls.stream().filter(c -> "restore".equals(c.methodName)).count();
		assertEquals(2, saves);
		assertEquals(0, restores, "no stray restore() between events: " + h.surface.calls);
	}
}
