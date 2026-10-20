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

import java.util.*;

/**
 * The frame log of a drawn plane (docs/m3/RENDER_MODES.md, interaction model, leaf IM1): the last {@link #CAPACITY}
 * frames in preallocated lanes (rule 18), each with its start and end (nanoseconds), the snapshot generation it
 * painted and its input-to-pixel latency (from the oldest input not yet shown to the end of the frame that shows it,
 * -1 when the frame followed no input). Percentiles sort a preallocated scratch lane: recording and reading allocate
 * nothing. One thread (the plane's UI thread) writes and reads it.
 */
public final class FrameLog {
	/** Frames kept: the window of the percentiles. */
	public static final int CAPACITY = 256;
	private final long [] start = new long [CAPACITY], end = new long [CAPACITY], generation = new long [CAPACITY],
		latency = new long [CAPACITY], scratch = new long [CAPACITY];
	private long frames, pendingInput, fullRepaints, paintedPixels, rowsDrawn, rowsKept;

	/**
	 * Records the damage a frame paints (brief rule 24, "all delta based view"): a frame that drew every row of the rows
	 * area is a full repaint. A model delta repaints only the rows it changes (the plane keeps the others from its
	 * retained frame, whatever the native control invalidated), so a delta adds no full repaint; {@link #fullRepaints}
	 * before and after a delta is the gate.
	 *
	 * @param full the frame drew every row of the rows area
	 * @param pixels the damage's area
	 */
	public void damage (boolean full, long pixels) {
		if (full) fullRepaints++;
		paintedPixels += pixels;
	}

	/** Frames that repainted the whole rows area since the log was made. */
	public long fullRepaints () {
		return fullRepaints;
	}

	/** Records the rows a frame drew and the rows it kept from the retained frame (unchanged since they were drawn). */
	public void rows (int drawn, int kept) {
		rowsDrawn += drawn;
		rowsKept += kept;
	}

	/** Rows drawn since the log was made. */
	public long rowsDrawn () {
		return rowsDrawn;
	}

	/** Rows kept from the retained frame since the log was made. */
	public long rowsKept () {
		return rowsKept;
	}

	/** Pixels of damage painted since the log was made. */
	public long paintedPixels () {
		return paintedPixels;
	}

	/** Notes an input event (key, wheel, click, scroll) at <code>now</code>; the next frame shows it. */
	public void input (long now) {
		if (pendingInput == 0) pendingInput = now;
	}

	/** Records a frame that painted a snapshot generation. */
	public void frame (long begin, long finish, long snapshotGeneration) {
		int i = (int) (frames++ % CAPACITY);
		start [i] = begin;
		end [i] = finish;
		generation [i] = snapshotGeneration;
		latency [i] = pendingInput == 0 ? -1 : finish - pendingInput;
		pendingInput = 0;
	}

	/** Frames recorded since the log was made. */
	public long frames () {
		return frames;
	}

	/** The given percentile (1 to 100) of the frame times in the window, in nanoseconds; 0 without frames. */
	public long frameTime (int percent) {
		int n = window ();
		for (int i = 0; i < n; i++) scratch [i] = end [i] - start [i];
		return percentile (n, percent);
	}

	/** The given percentile of the input-to-pixel latencies in the window, in nanoseconds; 0 without input. */
	public long inputLatency (int percent) {
		int n = 0;
		for (int i = 0, w = window (); i < w; i++) {
			if (latency [i] >= 0) scratch [n++] = latency [i];
		}
		return percentile (n, percent);
	}

	/** True when the snapshot generations of the frames in the window never go back, oldest to newest (invariant I2). */
	public boolean monotonic () {
		int n = window ();
		long first = frames - n, previous = Long.MIN_VALUE;
		for (long f = first; f < frames; f++) {
			long g = generation [(int) (f % CAPACITY)];
			if (g < previous) return false;
			previous = g;
		}
		return true;
	}

	private int window () {
		return (int) Math.min (frames, CAPACITY);
	}

	private long percentile (int n, int percent) {
		if (n == 0) return 0;
		Arrays.sort (scratch, 0, n);
		int rank = (int) Math.ceil (Math.max (1, Math.min (100, percent)) * n / 100.0) - 1;
		return scratch [Math.max (0, rank)];
	}
}
