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
 * preallocated slabs per widget, a lock-free triple buffer. The model side owns the <em>back</em> slab and fills it;
 * the renderer owns the <em>front</em> slab and paints it; the third is the <em>middle</em>, the latest complete
 * snapshot. A slab holds what one frame needs in lanes (per row: y, height, state bits, text id, foreground,
 * background, font id, image id) plus the window (first row, row count, scroll offset) and the theme generation; it is
 * immutable once published.
 * <p>
 * One atomic word holds the middle slab and a <em>fresh</em> bit. {@link #publish} exchanges the back slab with the
 * middle and sets the bit; {@link #acquire} exchanges the front slab with the middle only when the bit is set. Each
 * side only ever touches the slab it owns, so a frame never sees a slab being written (invariant I1); the renderer only
 * takes a slab newer than its own (I2); both sides finish in one atomic exchange, never waiting for the other (I3);
 * nothing is allocated after construction (I4).
 * </p>
 */
public final class RowSnapshots {
	/** One slab: the lanes of a row window. */
	public static final class Slab {
		/** The snapshot generation this slab holds (0: never published). */
		public long generation;
		/** The model generation it was built from. */
		public long modelGeneration;
		/** The theme generation and the window. */
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

	private static final int FRESH = 4;
	private final Slab [] slabs = new Slab [3];
	/* bits 0-1: the middle slab; FRESH: it is newer than the front */
	private final AtomicInteger middle = new AtomicInteger (1);
	/* owned by the model side */
	private int back = 0;
	private long generation;
	/* owned by the renderer */
	private int front = 2;
	private volatile long latest;

	/** @param rows the largest row window a slab holds */
	public RowSnapshots (int rows) {
		for (int i = 0; i < slabs.length; i++) slabs [i] = new Slab (Math.max (1, rows));
	}

	/** The slab the model side fills now: neither the latest snapshot nor the renderer's. Model side only. */
	public Slab writable () {
		return slabs [back];
	}

	/**
	 * Publishes the filled {@link #writable} slab as the next generation: its checksum is written, then it is exchanged
	 * with the middle slab, which becomes the next writable one. Model side only.
	 *
	 * @return the generation published
	 */
	public long publish (Slab slab) {
		if (slab != slabs [back]) throw new IllegalArgumentException ("not the writable slab of these snapshots");
		slab.generation = ++generation;
		slab.checksum = slab.sum ();
		back = middle.getAndSet (back | FRESH) & 3;
		latest = generation;
		return generation;
	}

	/**
	 * The latest complete snapshot for a frame: the middle slab when it is newer than the renderer's, else the
	 * renderer's slab again; null before the first publication. The slab stays the renderer's until the next call.
	 * Renderer only.
	 */
	public Slab acquire () {
		if ((middle.get () & FRESH) != 0) front = middle.getAndSet (front) & 3;
		Slab slab = slabs [front];
		return slab.generation == 0 ? null : slab;
	}

	/** The generation of the latest published snapshot (0: none). */
	public long generation () {
		return latest;
	}
}
