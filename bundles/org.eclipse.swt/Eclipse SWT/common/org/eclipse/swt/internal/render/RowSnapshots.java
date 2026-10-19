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

import java.util.concurrent.atomic.*;

/**
 * The snapshot protocol of the interaction model (docs/m3/RENDER_MODES.md, interaction model, leaf IM2): three
 * preallocated slabs per widget, one being written by the model side, the latest published, one being read by the
 * renderer. A slab holds what one frame needs in lanes (per row: y, height, state bits, text id, foreground,
 * background, font id, image id) plus the window (first row, row count, scroll offset) and the theme generation; it is
 * immutable once published.
 * <p>
 * One atomic state word holds the published slab and the slab being read (two 2-bit fields, 3 for none). The model
 * side publishes a filled slab with a compare-and-set that puts it into the published field; the renderer acquires a
 * frame with a compare-and-set that moves the published slab into the read field, so the writer can never pick a
 * slab the renderer may be reading: {@link #writable} answers a slab that is in neither field. Generations are
 * strictly increasing (invariant I2); a frame sees a complete slab or keeps the previous one (I1); neither side blocks
 * or spins on the other for more than a retried compare-and-set (I3); nothing is allocated after construction (I4).
 * </p>
 */
public final class RowSnapshots {
	/** One slab: the lanes of a row window. */
	public static final class Slab {
		/** The snapshot generation this slab holds (0: never published). */
		public long generation;
		/** The model generation it was built from, the theme generation, the window. */
		public long modelGeneration;
		public int themeGeneration, firstRow, rows, scrollX, scrollY;
		/** Per row, indexed from 0 to <code>rows - 1</code>. */
		public final int [] y, height, state, text, foreground, background, font, image;
		/** A checksum of the lanes, written last by the model side: a torn read would not match it. */
		public long checksum;

		Slab (int capacity) {
			y = new int [capacity];
			height = new int [capacity];
			state = new int [capacity];
			text = new int [capacity];
			foreground = new int [capacity];
			background = new int [capacity];
			font = new int [capacity];
			image = new int [capacity];
		}

		/** The checksum of the window and every lane in use (FNV-1a over the ints). */
		public long sum () {
			long h = 0xcbf29ce484222325L;
			h = mix (h, firstRow);
			h = mix (h, rows);
			h = mix (h, scrollX);
			h = mix (h, scrollY);
			h = mix (h, themeGeneration);
			for (int i = 0; i < rows; i++) {
				h = mix (h, y [i]);
				h = mix (h, height [i]);
				h = mix (h, state [i]);
				h = mix (h, text [i]);
				h = mix (h, foreground [i]);
				h = mix (h, background [i]);
				h = mix (h, font [i]);
				h = mix (h, image [i]);
			}
			return h;
		}

		/** True when the checksum matches the lanes: the slab was read whole. */
		public boolean consistent () {
			return checksum == sum ();
		}

		private static long mix (long h, int v) {
			return (h ^ v) * 0x100000001b3L;
		}
	}

	private static final int NONE = 3;
	private final Slab [] slabs = new Slab [3];
	/* bits 0-1: the published slab, bits 2-3: the slab being read; NONE when empty */
	private final AtomicInteger state = new AtomicInteger (NONE | NONE << 2);
	private long generation;
	private volatile long latest;

	/** @param rows the largest row window a slab holds */
	public RowSnapshots (int rows) {
		for (int i = 0; i < slabs.length; i++) slabs [i] = new Slab (Math.max (1, rows));
	}

	/**
	 * The slab the model side may fill now: neither the published one nor the one being read. With three slabs there is
	 * always one. Model side only.
	 */
	public Slab writable () {
		int s = state.get (), published = s & 3, reading = s >>> 2 & 3;
		for (int i = 0; i < slabs.length; i++) {
			if (i != published && i != reading) return slabs [i];
		}
		throw new IllegalStateException ("no writable slab"); /* unreachable with three slabs */
	}

	/**
	 * Publishes a filled slab as the next generation: its checksum is written, then the slab becomes the published one.
	 * Model side only.
	 *
	 * @return the generation published
	 */
	public long publish (Slab slab) {
		int index = index (slab);
		slab.generation = ++generation;
		slab.checksum = slab.sum ();
		int s;
		do {
			s = state.get ();
		} while (!state.compareAndSet (s, s & ~3 | index));
		latest = generation;
		return generation;
	}

	/**
	 * The latest complete snapshot for a frame: the published slab becomes the one being read (and stays so until the
	 * next call or {@link #release}); when nothing was published since the last frame, the slab of the last frame
	 * again. Null before the first publication. Renderer only.
	 */
	public Slab acquire () {
		int s, next;
		do {
			s = state.get ();
			int published = s & 3;
			if (published == NONE) {
				int reading = s >>> 2 & 3;
				return reading == NONE ? null : slabs [reading];
			}
			next = NONE | published << 2;
		} while (!state.compareAndSet (s, next));
		return slabs [next >>> 2 & 3];
	}

	/** The renderer is done with the acquired slab; the writer may reuse it. */
	public void release () {
		int s;
		do {
			s = state.get ();
		} while (!state.compareAndSet (s, s & 3 | NONE << 2));
	}

	/** The generation of the latest published snapshot (0: none). */
	public long generation () {
		return latest;
	}

	private int index (Slab slab) {
		for (int i = 0; i < slabs.length; i++) {
			if (slabs [i] == slab) return i;
		}
		throw new IllegalArgumentException ("not a slab of these snapshots");
	}
}
