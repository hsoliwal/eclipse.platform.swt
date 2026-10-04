/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

import java.util.*;

/**
 * Bounded primitive damage-region accumulator for viewport paint layers.
 *
 * <p>Regions on the same layer are coalesced when they overlap or touch. The
 * accumulator never grows beyond a fixed budget. If distinct layer damage
 * exceeds that budget, it degrades conservatively to one {@link #ALL_LAYERS}
 * union rectangle. This can increase repaint work but cannot hide damaged
 * pixels.</p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ViewportDamageRegions {
	public static final int ALL_LAYERS = Integer.MIN_VALUE;
	public static final int DEFAULT_MAX_REGIONS = 64;

	private final int maxRegions;
	private int [] layers;
	private int [] x;
	private int [] y;
	private int [] width;
	private int [] height;
	private int count;

	public ViewportDamageRegions () {
		this (DEFAULT_MAX_REGIONS);
	}

	public ViewportDamageRegions (int maxRegions) {
		if (maxRegions <= 0) throw new IllegalArgumentException ("maxRegions must be positive");
		this.maxRegions = maxRegions;
		int capacity = Math.min (maxRegions, 8);
		layers = new int [capacity];
		x = new int [capacity];
		y = new int [capacity];
		width = new int [capacity];
		height = new int [capacity];
	}

	public int count () {
		return count;
	}

	public boolean isEmpty () {
		return count == 0;
	}

	public int maxRegions () {
		return maxRegions;
	}

	public void clear () {
		count = 0;
	}

	/**
	 * Adds one damaged rectangle.
	 *
	 * <p>Non-positive extents are ignored. Coordinates are saturated rather
	 * than allowed to overflow while unions are computed.</p>
	 */
	public void invalidate (int layer, int rx, int ry, int rWidth, int rHeight) {
		if (rWidth <= 0 || rHeight <= 0) return;
		int left = rx;
		int top = ry;
		int right = saturatedAdd (rx, rWidth);
		int bottom = saturatedAdd (ry, rHeight);
		if (right <= left || bottom <= top) return;

		if (count == 1 && layers [0] == ALL_LAYERS) {
			unionInto (0, left, top, right, bottom);
			return;
		}

		int merged = -1;
		for (int index = 0; index < count; index++) {
			if (layers [index] != layer) continue;
			if (!touches (index, left, top, right, bottom)) continue;
			if (merged < 0) {
				unionInto (index, left, top, right, bottom);
				merged = index;
			} else {
				unionInto (
						merged,
						x [index], y [index],
						saturatedAdd (x [index], width [index]),
						saturatedAdd (y [index], height [index]));
				removeAt (index--);
			}
		}
		if (merged >= 0) {
			coalesceLayer (merged);
			return;
		}

		if (count == maxRegions) {
			collapseToAllLayers (left, top, right, bottom);
			return;
		}
		ensureCapacity (count + 1);
		layers [count] = layer;
		x [count] = left;
		y [count] = top;
		width [count] = right - left;
		height [count] = bottom - top;
		count++;
	}

	/**
	 * Adds damage clipped to one viewport rectangle.
	 */
	public void invalidateClipped (
			int layer, int rx, int ry, int rWidth, int rHeight,
			int clipX, int clipY, int clipWidth, int clipHeight) {
		if (rWidth <= 0 || rHeight <= 0 || clipWidth <= 0 || clipHeight <= 0) return;
		long left = Math.max ((long)rx, clipX);
		long top = Math.max ((long)ry, clipY);
		long right = Math.min ((long)rx + rWidth, (long)clipX + clipWidth);
		long bottom = Math.min ((long)ry + rHeight, (long)clipY + clipHeight);
		if (right <= left || bottom <= top) return;
		invalidate (
				layer,
				saturatingInt (left),
				saturatingInt (top),
				saturatingInt (right - left),
				saturatingInt (bottom - top));
	}

	public int layerAt (int index) {
		checkIndex (index);
		return layers [index];
	}

	public int xAt (int index) {
		checkIndex (index);
		return x [index];
	}

	public int yAt (int index) {
		checkIndex (index);
		return y [index];
	}

	public int widthAt (int index) {
		checkIndex (index);
		return width [index];
	}

	public int heightAt (int index) {
		checkIndex (index);
		return height [index];
	}

	private void coalesceLayer (int merged) {
		boolean changed;
		do {
			changed = false;
			int left = x [merged];
			int top = y [merged];
			int right = saturatedAdd (left, width [merged]);
			int bottom = saturatedAdd (top, height [merged]);
			for (int index = 0; index < count; index++) {
				if (index == merged || layers [index] != layers [merged]) continue;
				if (!touches (index, left, top, right, bottom)) continue;
				unionInto (
						merged,
						x [index], y [index],
						saturatedAdd (x [index], width [index]),
						saturatedAdd (y [index], height [index]));
				removeAt (index);
				if (index < merged) merged--;
				changed = true;
				break;
			}
		} while (changed);
	}

	private void collapseToAllLayers (int left, int top, int right, int bottom) {
		for (int index = 0; index < count; index++) {
			left = Math.min (left, x [index]);
			top = Math.min (top, y [index]);
			right = Math.max (right, saturatedAdd (x [index], width [index]));
			bottom = Math.max (bottom, saturatedAdd (y [index], height [index]));
		}
		count = 1;
		layers [0] = ALL_LAYERS;
		x [0] = left;
		y [0] = top;
		width [0] = Math.max (0, right - left);
		height [0] = Math.max (0, bottom - top);
	}

	private boolean touches (int index, int left, int top, int right, int bottom) {
		int existingLeft = x [index];
		int existingTop = y [index];
		int existingRight = saturatedAdd (existingLeft, width [index]);
		int existingBottom = saturatedAdd (existingTop, height [index]);
		return existingRight >= left
				&& right >= existingLeft
				&& existingBottom >= top
				&& bottom >= existingTop;
	}

	private void unionInto (int index, int left, int top, int right, int bottom) {
		int existingRight = saturatedAdd (x [index], width [index]);
		int existingBottom = saturatedAdd (y [index], height [index]);
		int nextLeft = Math.min (x [index], left);
		int nextTop = Math.min (y [index], top);
		int nextRight = Math.max (existingRight, right);
		int nextBottom = Math.max (existingBottom, bottom);
		x [index] = nextLeft;
		y [index] = nextTop;
		width [index] = Math.max (0, nextRight - nextLeft);
		height [index] = Math.max (0, nextBottom - nextTop);
	}

	private void removeAt (int index) {
		int tail = --count;
		if (index == tail) return;
		layers [index] = layers [tail];
		x [index] = x [tail];
		y [index] = y [tail];
		width [index] = width [tail];
		height [index] = height [tail];
	}

	private void ensureCapacity (int required) {
		if (required <= layers.length) return;
		int next = Math.min (maxRegions, Math.max (required, layers.length * 2));
		layers = Arrays.copyOf (layers, next);
		x = Arrays.copyOf (x, next);
		y = Arrays.copyOf (y, next);
		width = Arrays.copyOf (width, next);
		height = Arrays.copyOf (height, next);
	}

	private void checkIndex (int index) {
		if (index < 0 || index >= count) throw new IndexOutOfBoundsException (index);
	}

	private static int saturatedAdd (int left, int right) {
		long value = (long)left + right;
		return saturatingInt (value);
	}

	private static int saturatingInt (long value) {
		if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
		if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
		return (int)value;
	}
}
