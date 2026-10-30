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
package org.eclipse.swt.internal.render;

import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;

/**
 * The owner-draw protocol of the native row widgets, for a drawn plane (GC seam, leaf 3). The win32 Table and Tree
 * send it from <code>NM_CUSTOMDRAW</code>: per cell <code>SWT.MeasureItem</code> (bounds: the cell's image and text),
 * then <code>SWT.EraseItem</code> (bounds and clip: the cell) before the default background, then
 * <code>SWT.PaintItem</code> (bounds: image and text, clip: the cell) after the default content; <code>event.gc</code> is
 * a GC on the item's device context that the listener draws with, disposed after the listener (Initiative 31 trick T9,
 * <code>TreeItemRenderer</code>, draws the same sequence on its canvas). The detail bits a listener clears in
 * <code>EraseItem</code> (<code>SWT.FOREGROUND</code>, <code>BACKGROUND</code>, <code>SELECTED</code>,
 * <code>FOCUSED</code>, <code>HOT</code>) suppress the matching default drawing; <code>event.doit == false</code>
 * suppresses all of it.
 * <p>
 * This class is the protocol without the widget: it knows the hooks, a sender, a GC supplier and what releases a GC
 * after an event, so it runs headless. One {@link Event} is reused (every field a listener sees is reset per event); the
 * GC is the frame's (one per frame: the plane's GC in the <code>custom</code> mode, a <code>GCExtension</code> over the M3
 * renderer in <code>m3</code> and <code>gpu</code>), clipped to the cell for the listener and handed to
 * <code>released</code> after each event, which restores its clip or disposes it (a <code>GCExtension</code> is reusable
 * after <code>dispose</code>). Coordinates arrive in device pixels and reach the listener in points, as natively
 * (<code>Win32DPIUtils.pixelToPoint</code> with the widget's autoscaling zoom), edge by edge.
 * </p>
 */
public final class DrawnCustomDraw {
	/** Sends an event to the widget ({@code Widget.sendEvent (int, Event)}). */
	public interface Sender {
		void send (int eventType, Event event);
	}

	private final IntPredicate hooks;
	private final Sender sender;
	private final Supplier<GC> gcs;
	private final Consumer<GC> released;
	/* one event per plane: a listener never keeps it (as natively, where each event is new) */
	private final Event event = new Event ();
	private int zoom = 100;
	/** The listener's <code>MeasureItem</code> bounds in pixels, valid after {@link #measure} answered true. */
	public int measuredX, measuredWidth, measuredHeight;
	/** Events sent since {@link #frame}. */
	public int sent;

	/**
	 * @param hooks whether the widget hooks an event type ({@code Widget.hooks})
	 * @param sender sends an event to the widget
	 * @param gcs the frame's GC, made on first use (null: none, nothing is sent)
	 * @param released takes the GC after each event: restores its clip, or disposes it
	 */
	public DrawnCustomDraw (IntPredicate hooks, Sender sender, Supplier<GC> gcs, Consumer<GC> released) {
		this.hooks = hooks;
		this.sender = sender;
		this.gcs = gcs;
		this.released = released;
	}

	/** True when the widget hooks any of the three events: the plane then takes the owner-draw path (UI thread, a GC). */
	public boolean hooked () {
		return hooks.test (SWT.MeasureItem) || hooks.test (SWT.EraseItem) || hooks.test (SWT.PaintItem);
	}

	/** Starts a frame: the zoom its pixel coordinates are converted to points with (the widget's autoscaling zoom). */
	public void frame (int zoom) {
		this.zoom = zoom;
		sent = 0;
	}

	/**
	 * <code>MeasureItem</code> for a cell, with the bounds of its image and text (pixels); the listener's bounds are read
	 * back into {@link #measuredX}, {@link #measuredWidth} and {@link #measuredHeight}.
	 *
	 * @param detail {@code SWT.SELECTED} when the cell is selected, else 0 (as natively)
	 * @return false when the event is not hooked or no GC is available
	 */
	public boolean measure (Widget item, int index, int detail, int x, int y, int width, int height) {
		if (!hooks.test (SWT.MeasureItem)) return false;
		GC gc = gcs.get ();
		if (gc == null) return false;
		fill (item, index, detail, gc, x, y, width, height);
		send (SWT.MeasureItem, gc);
		measuredX = DPIUtil.pointToPixel (event.x, zoom);
		measuredWidth = DPIUtil.pointToPixel (event.x + event.width, zoom) - measuredX;
		measuredHeight = DPIUtil.pointToPixel (event.height, zoom);
		return true;
	}

	/**
	 * <code>EraseItem</code> for a cell (bounds and clip: the cell rectangle, pixels).
	 *
	 * @param detail the bits the default drawing would draw ({@code SWT.FOREGROUND} always, {@code BACKGROUND} for a cell
	 *               with its own colour, {@code SELECTED}, {@code FOCUSED}, {@code HOT} when the cell shows them)
	 * @return the bits the default drawing keeps: <code>detail</code> when the event is not hooked, the listener's
	 *         <code>event.detail</code> when it answered <code>doit</code>, 0 when it did not
	 */
	public int erase (Widget item, int index, int detail, int cellX, int cellY, int cellWidth, int cellHeight) {
		if (!hooks.test (SWT.EraseItem)) return detail;
		GC gc = gcs.get ();
		if (gc == null) return detail;
		clip (gc, cellX, cellY, cellWidth, cellHeight);
		fill (item, index, detail, gc, cellX, cellY, cellWidth, cellHeight);
		send (SWT.EraseItem, gc);
		return event.doit ? event.detail : 0;
	}

	/**
	 * <code>PaintItem</code> for a cell: bounds of its image and text, the GC clipped to the cell (pixels).
	 *
	 * @return false when the event is not hooked or no GC is available
	 */
	public boolean paint (Widget item, int index, int detail, int x, int y, int width, int height, int cellX, int cellY, int cellWidth, int cellHeight) {
		if (!hooks.test (SWT.PaintItem)) return false;
		GC gc = gcs.get ();
		if (gc == null) return false;
		clip (gc, cellX, cellY, cellWidth, cellHeight);
		fill (item, index, detail, gc, x, y, width, height);
		send (SWT.PaintItem, gc);
		return true;
	}

	/* the clip, in points, edge by edge (as Win32DPIUtils.pixelToPoint scales a rectangle) */
	private void clip (GC gc, int x, int y, int width, int height) {
		int left = DPIUtil.pixelToPoint (x, zoom), top = DPIUtil.pixelToPoint (y, zoom);
		gc.setClipping (left, top, DPIUtil.pixelToPoint (x + width, zoom) - left, DPIUtil.pixelToPoint (y + height, zoom) - top);
	}

	/* every field a listener reads or writes, reset: the event is reused */
	private void fill (Widget item, int index, int detail, GC gc, int x, int y, int width, int height) {
		event.item = item;
		event.index = index;
		event.detail = detail;
		event.gc = gc;
		event.doit = true;
		event.time = 0;
		event.data = null;
		event.text = null;
		event.count = 0;
		int left = DPIUtil.pixelToPoint (x, zoom), top = DPIUtil.pixelToPoint (y, zoom);
		event.x = left;
		event.y = top;
		event.width = DPIUtil.pixelToPoint (x + width, zoom) - left;
		event.height = DPIUtil.pixelToPoint (y + height, zoom) - top;
	}

	private void send (int eventType, GC gc) {
		sent++;
		sender.send (eventType, event);
		event.gc = null;
		released.accept (gc);
	}
}
