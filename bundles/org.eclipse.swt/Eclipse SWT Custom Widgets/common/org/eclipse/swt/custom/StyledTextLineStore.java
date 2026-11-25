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
package org.eclipse.swt.custom;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * Per-line state of a StyledText document as bits and ints, owned by the
 * renderer (one store per renderer, no object per line).
 * <ul>
 * <li>Attributes: one <code>int</code> mask per line (allocated on the first
 * dense write) and one parallel <code>int[]</code> of values per attribute kind
 * (allocated on the first dense write of that kind). Object values (colours,
 * segments, tab stops) are identity-interned int ids, id 0 is null.</li>
 * <li>Runs and patterns: packed <code>(start, end, stride, mask, value)</code>
 * entries for uniform ranges; a run has stride 1; the newest entry wins and a
 * dense write after it wins over it.</li>
 * <li>Sizes: width and height ints over a window of lines (the whole document in
 * exact mode, the visible lines plus overscan in viewport mode) with the
 * transient bits of the line (dirty layout, wrapped, has style, dirty paint).</li>
 * <li>Extent (exact mode): per block of {@link #BLOCK} lines, the sum of the
 * measured heights, the counts of lines without a height or a width, a width
 * bound and the count of negative heights, with Fenwick trees over the sums
 * and counts. Prefix heights, the next or previous unmeasured line and the line
 * at a pixel distance cost O(log(lines / BLOCK) + BLOCK) instead of a loop over
 * every line. The blocks follow every size write; an edit that changes the line
 * count marks the blocks from the edit on for a rebuild on the next query (the
 * same lines the size arrays move).</li>
 * </ul>
 * Plain Java (arrays and ints only), GWT-safe.
 */
final class StyledTextLineStore {
	//FLLDCIM: MASKS - per-line state in bits, ints and packed patterns; no per-line objects
	/* Attribute bits: the values of the former LineInfo.flags. */
	static final int BACKGROUND = 1, ALIGNMENT = 1 << 1, INDENT = 1 << 2, JUSTIFY = 1 << 3;
	static final int SEGMENTS = 1 << 5, TABSTOPS = 1 << 6, WRAP_INDENT = 1 << 7, SEGMENT_CHARS = 1 << 8;
	static final int VERTICAL_INDENT = 1 << 9;
	/** Value of the justify attribute, beside its JUSTIFY presence bit. */
	static final int JUSTIFY_ON = 1 << 10;
	/** Attributes cleared by StyledText's clear of the line style (alignment, indents, justify, tabs). */
	static final int LINE_STYLE = ALIGNMENT | INDENT | VERTICAL_INDENT | WRAP_INDENT | JUSTIFY | TABSTOPS;
	/* Transient bits of a line in the size window. */
	static final int DIRTY_LAYOUT = 1 << 16, WRAPPED = 1 << 17, HAS_STYLE = 1 << 18, DIRTY_PAINT = 1 << 19;

	/** Kinds with a value array, in bit order; JUSTIFY keeps its value in JUSTIFY_ON. */
	static final int KIND_COUNT = 9;
	static final int PATTERN_INTS = 5, MAX_PATTERNS = 64, RUN_MIN = 32;
	static final int UNMEASURED = -1;
	static final int WINDOW_MIN = 4096;

	int lineCount;
	/* Attributes */
	int[] mask;
	final int[][] values = new int[KIND_COUNT][];
	int[] patterns = new int[0];
	int patternCount;
	/** Last single-line dense write per kind: {line, value}; line -1 = none. */
	final int[] lastWrite = new int[2 * KIND_COUNT];
	/** Lookup result of {@link #find(int, int)}. */
	int found;
	/* Identity-interned objects: id -> object, id -> packed RGBA (colours); open-addressed id slots. */
	Object[] objects = new Object[8];
	int[] objectRgba = new int[8];
	int objectCount = 1;
	int[] objectSlots = new int[16];
	/* Size window */
	boolean windowed;
	int sizeBase;
	int[] width = new int[0], height = new int[0], lineBits = new int[0];
	/* Extent of the size window (exact mode only: not windowed, base 0) */
	//FLLDCIM: VIEWPORT - prefix extent over the height lane; blocks of BLOCK lines, Fenwick trees over the blocks
	static final int BLOCK_SHIFT = 6, BLOCK = 1 << BLOCK_SHIFT;
	/** Ints per block in {@link #blockInts}: lines without a height, without a width, width bound, negative heights. */
	static final int BLOCK_INTS = 4, NO_HEIGHT = 0, NO_WIDTH = 1, MAX_WIDTH = 2, NEGATIVE = 3;
	/** Width bound of a block that must be recomputed before use. */
	static final int STALE = Integer.MIN_VALUE;
	/** Whether the blocks describe the current arrays (else they are rebuilt on the next query). */
	boolean extentValid;
	int extentBlocks;
	/** Blocks [dirtyFrom, dirtyTo) are recomputed from the arrays on the next query. */
	int dirtyFrom = Integer.MAX_VALUE, dirtyTo;
	/** Measured heights below zero in the window: searches by pixel distance need monotone sums. */
	int negativeHeights;
	long[] blockHeight = new long[0], heightTree = new long[1];
	int[] blockInts = new int[0], noHeightTree = new int[1], noWidthTree = new int[1];
	/** Height prefix of the block found by the last {@link #findHeightBlock(long)}. */
	private long searchSum;

	StyledTextLineStore() {
		Arrays.fill(lastWrite, -1);
	}

static int kind(int bit) {
	switch (bit) {
		case BACKGROUND: return 0;
		case ALIGNMENT: return 1;
		case INDENT: return 2;
		case JUSTIFY: return 3;
		case SEGMENTS: return 4;
		case TABSTOPS: return 5;
		case WRAP_INDENT: return 6;
		case SEGMENT_CHARS: return 7;
		case VERTICAL_INDENT: return 8;
		default:
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			return -1;
	}
}
/** Clears attributes and sizes for a document of {@code lineCount} lines. */
void reset(int lineCount) {
	clearAttributes();
	this.lineCount = lineCount;
	sizeBase = 0;
	int capacity = windowed ? Math.max(width.length, WINDOW_MIN) : lineCount;
	if (width.length != capacity) {
		width = new int[capacity];
		height = new int[capacity];
		lineBits = new int[capacity];
	}
	resetAllSizes();
}
/** Drops every attribute, pattern and interned object (all text replaced). */
void clearAttributes() {
	mask = null;
	Arrays.fill(values, null);
	patternCount = 0;
	Arrays.fill(lastWrite, -1);
	if (objectCount > 1) {
		objects = new Object[8];
		objectRgba = new int[8];
		objectSlots = new int[16];
		objectCount = 1;
	}
}
/* ---------------------------------------------------------------- objects */
int intern(Object object) {
	if (object == null) {
		return 0;
	}
	int slotMask = objectSlots.length - 1;
	int slot = mix(System.identityHashCode(object)) & slotMask;
	while (objectSlots[slot] != 0) {
		if (objects[objectSlots[slot]] == object) {
			return objectSlots[slot];
		}
		slot = (slot + 1) & slotMask;
	}
	int id = objectCount++;
	if (id == objects.length) {
		objects = Arrays.copyOf(objects, StyledTextRenderer.grownCapacity(id, id + 1, 4));
		objectRgba = Arrays.copyOf(objectRgba, objects.length);
	}
	objects[id] = object;
	objectRgba[id] = object instanceof Color && !((Color) object).isDisposed() ? rgba((Color) object) : 0;
	objectSlots[slot] = id;
	if (2 * objectCount > objectSlots.length) {
		rehashObjects(2 * objectSlots.length);
	}
	return id;
}
static int rgba(Color color) {
	return color.getRed() << 24 | color.getGreen() << 16 | color.getBlue() << 8 | color.getAlpha();
}
private void rehashObjects(int size) {
	objectSlots = new int[size];
	int slotMask = size - 1;
	for (int id = 1; id < objectCount; id++) {
		int slot = mix(System.identityHashCode(objects[id])) & slotMask;
		while (objectSlots[slot] != 0) {
			slot = (slot + 1) & slotMask;
		}
		objectSlots[slot] = id;
	}
}
static int mix(int h) {
	h *= 0x9E3779B9;
	return h ^ (h >>> 16);
}
Object object(int id) {
	return objects[id];
}
/* ------------------------------------------------------------- attributes */
/**
 * Finds the value of attribute {@code bit} on {@code line}: a dense write, else
 * the newest pattern holding the line. Sets {@link #found} and answers whether
 * the line has the attribute. Allocation free.
 */
boolean find(int line, int bit) {
	if (mask != null && line < mask.length && (mask[line] & bit) != 0) {
		found = bit == JUSTIFY ? (mask[line] & JUSTIFY_ON) != 0 ? 1 : 0 : values[kind(bit)][line];
		return true;
	}
	for (int i = patternCount - 1; i >= 0; i--) {
		int p = i * PATTERN_INTS;
		if (patterns[p + 3] == bit && holds(p, line)) {
			found = patterns[p + 4];
			return true;
		}
	}
	return false;
}
private boolean holds(int p, int line) {
	int start = patterns[p];
	return line >= start && line < patterns[p + 1] && (line - start) % patterns[p + 2] == 0;
}
/** True when any line has an attribute (dense or pattern). */
boolean hasAttributes() {
	return mask != null || patternCount > 0;
}
/** True when any line holds attribute {@code bit} with a non-zero value. */
boolean anyNonZero(int bit) {
	for (int i = 0; i < patternCount; i++) {
		int p = i * PATTERN_INTS;
		if (patterns[p + 3] == bit && patterns[p + 4] != 0) {
			return true;
		}
	}
	if (mask == null) {
		return false;
	}
	int[] kindValues = bit == JUSTIFY ? null : values[kind(bit)];
	for (int i = 0, end = Math.min(lineCount, mask.length); i < end; i++) {
		if ((mask[i] & bit) != 0 && (kindValues == null ? (mask[i] & JUSTIFY_ON) != 0 : kindValues[i] != 0)) {
			return true;
		}
	}
	return false;
}
/** Hash of every attribute of a line (values and object ids), for the layout key. */
int attributeHash(int line) {
	int hash = 0;
	for (int k = 0; k < KIND_COUNT; k++) {
		hash = 31 * hash + (find(line, 1 << bitOfKind(k)) ? 31 * found + 1 : 0);
	}
	return hash;
}
int get(int line, int bit, int defaultValue) {
	return find(line, bit) ? found : defaultValue;
}
Object getObject(int line, int bit, Object defaultValue) {
	return find(line, bit) ? objects[found] : defaultValue;
}
void setObject(int start, int count, int bit, Object value) {
	set(start, count, bit, intern(value));
}
/**
 * Sets attribute {@code bit} to {@code value} on {@code count} lines from
 * {@code start}: extends the newest pattern of the attribute when the lines
 * continue it, records a run for {@link #RUN_MIN} lines or more, turns two
 * single-line writes of one value into a stride pattern, else writes densely.
 */
void set(int start, int count, int bit, int value) {
	if (count <= 0) {
		return;
	}
	int end = start + count, kind = kind(bit);
	int newest = newestPattern(bit);
	if (newest >= 0 && patterns[newest + 4] == value && extend(newest, start, end, bit)) {
		lastWrite[2 * kind] = -1;
		return;
	}
	int last = lastWrite[2 * kind];
	if (count == 1 && last >= 0 && start > last && lastWrite[2 * kind + 1] == value && denseHolds(last, bit, value)) {
		clearDense(last, last + 1, bit);
		clearDense(start, start + 1, bit);
		addPattern(last, start + 1, start - last, bit, value);
		lastWrite[2 * kind] = -1;
		return;
	}
	if (count >= RUN_MIN || mask == null && patternCount < MAX_PATTERNS / 2) {
		// a run; while no line is dense, short writes are runs too (default lines cost no ints)
		clearDense(start, end, bit);
		dropCovered(start, end, bit);
		addPattern(start, end, 1, bit, value);
		lastWrite[2 * kind] = -1;
		return;
	}
	writeDense(start, end, bit, value);
	lastWrite[2 * kind] = count == 1 ? start : -1;
	lastWrite[2 * kind + 1] = value;
}
/** Extends the pattern at {@code p} by lines [start, end) when they continue it. */
private boolean extend(int p, int start, int end, int bit) {
	int pStart = patterns[p], pEnd = patterns[p + 1], stride = patterns[p + 2];
	if (stride == 1 && start >= pStart && start <= pEnd) {
		patterns[p + 1] = Math.max(pEnd, end);
		clearDense(start, end, bit);
		return true;
	}
	if (stride > 1 && end - start == 1 && start == pEnd - 1 + stride) {
		patterns[p + 1] = end;
		clearDense(start, end, bit);
		return true;
	}
	if (pEnd - pStart == 1 && end - start == 1 && start > pEnd) {
		// a single-line entry and a later single line of the same value: a stride pattern
		patterns[p + 1] = end;
		patterns[p + 2] = start - pStart;
		clearDense(start, end, bit);
		return true;
	}
	return false;
}
private int newestPattern(int bit) {
	for (int i = patternCount - 1; i >= 0; i--) {
		if (patterns[i * PATTERN_INTS + 3] == bit) {
			return i * PATTERN_INTS;
		}
	}
	return -1;
}
private boolean denseHolds(int line, int bit, int value) {
	if (mask == null || line >= mask.length || (mask[line] & bit) == 0) {
		return false;
	}
	return bit == JUSTIFY ? ((mask[line] & JUSTIFY_ON) != 0) == (value != 0) : values[kind(bit)][line] == value;
}
private void writeDense(int start, int end, int bit, int value) {
	ensureDense(lineCount, bit);
	int[] kindValues = values[kind(bit)];
	for (int i = start; i < end; i++) {
		mask[i] |= bit;
		if (bit == JUSTIFY) {
			mask[i] = value != 0 ? mask[i] | JUSTIFY_ON : mask[i] & ~JUSTIFY_ON;
		} else {
			kindValues[i] = value;
		}
	}
}
private void clearDense(int start, int end, int bits) {
	if (mask == null) {
		return;
	}
	int clear = (bits & JUSTIFY) != 0 ? bits | JUSTIFY_ON : bits;
	for (int i = start, last = Math.min(end, mask.length); i < last; i++) {
		mask[i] &= ~clear;
	}
}
/** Grows the dense mask (and the value array of {@code bit}, 0 = none) to hold {@code lines}. */
private void ensureDense(int lines, int bit) {
	int capacity = mask == null ? 0 : mask.length;
	if (capacity < lines) {
		int grown = mask == null ? lines : StyledTextRenderer.grownCapacity(capacity, lines, 4 * (1 + kindsInUse()));
		mask = mask == null ? new int[grown] : Arrays.copyOf(mask, grown);
		for (int k = 0; k < KIND_COUNT; k++) {
			if (values[k] != null) {
				values[k] = Arrays.copyOf(values[k], grown);
			}
		}
	}
	if (bit != 0 && bit != JUSTIFY && values[kind(bit)] == null) {
		values[kind(bit)] = new int[mask.length];
	}
}
private int kindsInUse() {
	int kinds = 0;
	for (int[] kindValues : values) {
		kinds += kindValues != null ? 1 : 0;
	}
	return kinds;
}
private void addPattern(int start, int end, int stride, int bit, int value) {
	if (patternCount == MAX_PATTERNS) {
		materializeSmallest();
	}
	insertPattern(patternCount, start, end, stride, bit, value);
}
private void insertPattern(int index, int start, int end, int stride, int bit, int value) {
	int need = (patternCount + 1) * PATTERN_INTS;
	if (need > patterns.length) {
		patterns = Arrays.copyOf(patterns, Math.max(need, 2 * patterns.length));
	}
	int p = index * PATTERN_INTS;
	System.arraycopy(patterns, p, patterns, p + PATTERN_INTS, (patternCount - index) * PATTERN_INTS);
	patterns[p] = start;
	patterns[p + 1] = end;
	patterns[p + 2] = stride;
	patterns[p + 3] = bit;
	patterns[p + 4] = value;
	patternCount++;
}
private void removePattern(int index) {
	int p = index * PATTERN_INTS;
	System.arraycopy(patterns, p + PATTERN_INTS, patterns, p, (patternCount - index - 1) * PATTERN_INTS);
	patternCount--;
}
/** Removes older patterns of {@code bit} whose lines all lie inside [start, end). */
private void dropCovered(int start, int end, int bit) {
	for (int i = patternCount - 1; i >= 0; i--) {
		int p = i * PATTERN_INTS;
		if (patterns[p + 3] == bit && patterns[p] >= start && patterns[p + 1] <= end) {
			removePattern(i);
		}
	}
}
/**
 * Moves the pattern with the fewest lines into the dense arrays. A line held by
 * a newer pattern of the same attribute, or written densely after the pattern
 * (its dense bit is set), keeps that newer value.
 */
private void materializeSmallest() {
	int smallest = 0;
	long fewest = Long.MAX_VALUE;
	for (int i = 0; i < patternCount; i++) {
		int p = i * PATTERN_INTS;
		long lines = (patterns[p + 1] - patterns[p] + patterns[p + 2] - 1L) / patterns[p + 2];
		if (lines < fewest) {
			fewest = lines;
			smallest = i;
		}
	}
	int p = smallest * PATTERN_INTS;
	int start = patterns[p], end = patterns[p + 1], stride = patterns[p + 2], bit = patterns[p + 3], value = patterns[p + 4];
	ensureDense(Math.max(lineCount, end), bit);
	int[] kindValues = bit == JUSTIFY ? null : values[kind(bit)];
	for (int line = start; line < end; line += stride) {
		if ((mask[line] & bit) != 0 || heldByNewer(smallest, line, bit)) {
			continue;
		}
		mask[line] |= bit;
		if (kindValues != null) {
			kindValues[line] = value;
		} else if (value != 0) {
			mask[line] |= JUSTIFY_ON;
		}
	}
	removePattern(smallest);
}
private boolean heldByNewer(int index, int line, int bit) {
	for (int i = index + 1; i < patternCount; i++) {
		int p = i * PATTERN_INTS;
		if (patterns[p + 3] == bit && holds(p, line)) {
			return true;
		}
	}
	return false;
}
/** Removes attributes {@code bits} from {@code count} lines from {@code start}. */
void clear(int start, int count, int bits) {
	if (count <= 0) {
		return;
	}
	int end = start + count;
	clearDense(start, end, bits);
	for (int i = patternCount - 1; i >= 0; i--) {
		int p = i * PATTERN_INTS;
		if ((patterns[p + 3] & bits) != 0 && patterns[p] < end && patterns[p + 1] > start) {
			splitPattern(i, start, end, 0);
		}
	}
	boundPatterns();
	for (int k = 0; k < KIND_COUNT; k++) {
		if ((bits & (1 << bitOfKind(k))) != 0) {
			lastWrite[2 * k] = -1;
		}
	}
}
static int bitOfKind(int kind) {
	return kind < 4 ? kind : kind + 1;
}
/**
 * Replaces pattern {@code index} by its lines below {@code start} and its lines
 * at or after {@code end} moved by {@code delta}, keeping the stride phase.
 */
private void splitPattern(int index, int start, int end, int delta) {
	int p = index * PATTERN_INTS;
	int pStart = patterns[p], pEnd = patterns[p + 1], stride = patterns[p + 2], bit = patterns[p + 3], value = patterns[p + 4];
	int leftEnd = -1;
	if (pStart < start) {
		int lastLeft = pStart + (Math.min(pEnd, start) - 1 - pStart) / stride * stride;
		leftEnd = lastLeft + 1;
	}
	int rightStart = -1;
	int from = Math.max(end, pStart);
	int first = pStart + (int) (((long) from - pStart + stride - 1) / stride * stride);
	if (first < pEnd) {
		rightStart = first;
	}
	removePattern(index);
	int at = index;
	if (leftEnd != -1) {
		insertPattern(at++, pStart, leftEnd, stride, bit, value);
	}
	if (rightStart != -1) {
		insertPattern(at, rightStart + delta, pEnd + delta, stride, bit, value);
	}
}
private void boundPatterns() {
	while (patternCount > MAX_PATTERNS) {
		materializeSmallest();
	}
}
/**
 * Moves line attributes for an edit at line {@code start} that removes
 * {@code replaceLineCount} lines and inserts {@code newLineCount}: lines below
 * {@code start} keep theirs, removed lines drop theirs, later lines move by the
 * delta and inserted lines have none (the former LineInfo[] arraycopy). Call
 * before {@link #lineCount} is updated.
 */
void shiftAttributes(int start, int replaceLineCount, int newLineCount) {
	int delta = newLineCount - replaceLineCount;
	if (mask != null) {
		ensureDense(lineCount + delta, 0);
		int startIndex = start + replaceLineCount, endIndex = start + newLineCount;
		int length = lineCount - startIndex;
		if (length > 0) {
			System.arraycopy(mask, startIndex, mask, endIndex, length);
			for (int[] kindValues : values) {
				if (kindValues != null) {
					System.arraycopy(kindValues, startIndex, kindValues, endIndex, length);
				}
			}
		}
		Arrays.fill(mask, start, Math.min(endIndex, mask.length), 0);
		if (delta < 0) {
			Arrays.fill(mask, Math.max(0, lineCount + delta), lineCount, 0);
		}
	}
	for (int i = patternCount - 1; i >= 0; i--) {
		int p = i * PATTERN_INTS;
		if (patterns[p + 1] > start) {
			splitPattern(i, start, start + replaceLineCount, delta);
		}
	}
	boundPatterns();
	Arrays.fill(lastWrite, -1);
}
/* ------------------------------------------------------------------ sizes */
int width(int line) {
	int i = line - sizeBase;
	return i >= 0 && i < width.length ? width[i] : UNMEASURED;
}
int height(int line) {
	int i = line - sizeBase;
	return i >= 0 && i < height.length ? height[i] : UNMEASURED;
}
boolean inWindow(int line) {
	int i = line - sizeBase;
	return i >= 0 && i < width.length;
}
int lineBits(int line) {
	int i = line - sizeBase;
	return i >= 0 && i < lineBits.length ? lineBits[i] : DIRTY_LAYOUT;
}
void setSize(int line, int lineWidth, int lineHeight, int bits) {
	int i = line - sizeBase;
	if (i >= 0 && i < width.length) {
		if (extentValid) {
			extentChange(i, lineWidth, lineHeight);
		}
		width[i] = lineWidth;
		height[i] = lineHeight;
		lineBits[i] = (lineBits[i] & DIRTY_PAINT) | bits;
	}
}
void setHeight(int line, int lineHeight) {
	int i = line - sizeBase;
	if (i >= 0 && i < height.length) {
		if (extentValid) {
			extentChange(i, width[i], lineHeight);
		}
		height[i] = lineHeight;
	}
}
void markLines(int start, int end, int bits) {
	int from = Math.max(0, start - sizeBase), to = Math.min(lineBits.length, end - sizeBase);
	for (int i = from; i < to; i++) {
		lineBits[i] |= bits;
	}
}
void unmarkLines(int start, int end, int bits) {
	int from = Math.max(0, start - sizeBase), to = Math.min(lineBits.length, end - sizeBase);
	for (int i = from; i < to; i++) {
		lineBits[i] &= ~bits;
	}
}
void resetAllSizes() {
	extentValid = false;
	Arrays.fill(width, UNMEASURED);
	Arrays.fill(height, UNMEASURED);
	Arrays.fill(lineBits, DIRTY_LAYOUT);
}
/** Marks lines [start, end) unmeasured (the part inside the window). */
void resetSizes(int start, int end) {
	int from = Math.max(0, start - sizeBase), to = Math.min(width.length, end - sizeBase);
	if (from < to) {
		if (extentValid) {
			if (to - from > 4 * BLOCK) {
				markDirty(from >> BLOCK_SHIFT, ((to - 1) >> BLOCK_SHIFT) + 1);
			} else {
				for (int i = from; i < to; i++) {
					extentChange(i, UNMEASURED, UNMEASURED);
				}
			}
		}
		Arrays.fill(width, from, to, UNMEASURED);
		Arrays.fill(height, from, to, UNMEASURED);
		for (int i = from; i < to; i++) {
			lineBits[i] = (lineBits[i] & DIRTY_PAINT) | DIRTY_LAYOUT;
		}
	}
}
void resetSize(int line) {
	resetSizes(line, line + 1);
}
/**
 * Exact mode: the window is the whole document, grown to {@code lines}.
 * Viewport mode: a window of at least {@link #WINDOW_MIN} lines, and at least
 * four times {@code span}, around {@code top}.
 */
void sizeWindow(boolean windowed, int top, int span, int lines) {
	if (!windowed) {
		if (this.windowed || sizeBase != 0) {
			rebase(0, Math.max(lines, width.length));
		} else if (width.length < lines) {
			rebase(0, StyledTextRenderer.grownCapacity(width.length, lines, 12));
		}
		this.windowed = false;
		return;
	}
	int capacity = Math.max(WINDOW_MIN, 4 * span);
	if (!this.windowed || capacity > width.length) {
		this.windowed = true;
		rebase(Math.max(0, top - capacity / 2), capacity);
		return;
	}
	follow(top);
}
/** Viewport mode: re-centres the window when {@code top} leaves its middle half. */
void follow(int top) {
	if (!windowed) {
		return;
	}
	int capacity = width.length, offset = top - sizeBase;
	if (offset >= capacity / 4 && offset < 3 * capacity / 4) {
		return;
	}
	int base = Math.max(0, top - capacity / 2);
	if (base != sizeBase) {
		rebase(base, capacity);
	}
}
private void rebase(int base, int capacity) {
	extentValid = false;
	if (capacity == width.length) {
		// same capacity: move the overlap in place (no allocation while scrolling)
		int shift = base - sizeBase;
		sizeBase = base;
		if (shift == 0) {
			return;
		}
		if (Math.abs((long) shift) >= capacity) {
			resetAllSizes();
			return;
		}
		if (shift > 0) {
			System.arraycopy(width, shift, width, 0, capacity - shift);
			System.arraycopy(height, shift, height, 0, capacity - shift);
			System.arraycopy(lineBits, shift, lineBits, 0, capacity - shift);
			invalidateSlots(capacity - shift, capacity);
		} else {
			System.arraycopy(width, 0, width, -shift, capacity + shift);
			System.arraycopy(height, 0, height, -shift, capacity + shift);
			System.arraycopy(lineBits, 0, lineBits, -shift, capacity + shift);
			invalidateSlots(0, -shift);
		}
		return;
	}
	int[] newWidth = new int[capacity], newHeight = new int[capacity], newBits = new int[capacity];
	Arrays.fill(newWidth, UNMEASURED);
	Arrays.fill(newHeight, UNMEASURED);
	Arrays.fill(newBits, DIRTY_LAYOUT);
	int from = Math.max(base, sizeBase), to = Math.min(base + capacity, sizeBase + width.length);
	if (from < to) {
		System.arraycopy(width, from - sizeBase, newWidth, from - base, to - from);
		System.arraycopy(height, from - sizeBase, newHeight, from - base, to - from);
		System.arraycopy(lineBits, from - sizeBase, newBits, from - base, to - from);
	}
	width = newWidth;
	height = newHeight;
	lineBits = newBits;
	sizeBase = base;
}
/**
 * Moves sizes for an edit at line {@code startLine} that replaces
 * {@code replaceLineCount} lines by {@code newLineCount}: the start line and the
 * inserted lines become unmeasured, later lines move by the delta (the former
 * LineSizeInfo[] arraycopy, inside the window). Call before {@link #lineCount}
 * is updated.
 */
void shiftSizes(int startLine, int replaceLineCount, int newLineCount) {
	int delta = newLineCount - replaceLineCount;
	if (!windowed && width.length < lineCount + delta) {
		rebase(0, StyledTextRenderer.grownCapacity(width.length, lineCount + delta, 12));
	}
	int startIndex = startLine + replaceLineCount + 1, endIndex = startLine + newLineCount + 1;
	if (delta == 0) {
		resetSizes(startLine, endIndex); // the line count is kept: nothing moves
		return;
	}
	int capacity = width.length, base = sizeBase;
	if (startIndex <= base) {
		sizeBase = base + delta; // edit above the window: its lines all move
		extentValid = false;
		return;
	}
	int low = Math.max(0, startLine - base);
	markDirty(low >> BLOCK_SHIFT, extentBlocks); // the lines from the edit on move
	int src = startIndex - base, dst = src + delta, length = capacity - src;
	if (dst < 0) {
		src -= dst;
		length += dst;
		dst = 0;
	}
	if (dst + length > capacity) {
		length = capacity - dst;
	}
	if (length > 0 && src < capacity) {
		System.arraycopy(width, src, width, dst, length);
		System.arraycopy(height, src, height, dst, length);
		System.arraycopy(lineBits, src, lineBits, dst, length);
		invalidateSlots(low, dst);
		invalidateSlots(dst + length, capacity);
	} else {
		invalidateSlots(low, capacity);
	}
}
private void invalidateSlots(int from, int to) {
	from = Math.max(0, from);
	to = Math.min(width.length, to);
	if (from < to) {
		Arrays.fill(width, from, to, UNMEASURED);
		Arrays.fill(height, from, to, UNMEASURED);
		Arrays.fill(lineBits, from, to, DIRTY_LAYOUT);
	}
}
/* ----------------------------------------------------------------- extent */
/*
 * The extent answers the sums the widget would otherwise compute with a loop over
 * the height lane. A line without a height (UNMEASURED) adds 0 to the height sums
 * and 1 to the count of lines without a height; callers handle those lines one by
 * one (they measure or estimate them) and take the measured runs between them from
 * the blocks. Valid only in exact mode, where the window is the whole document.
 */
/** Brings the blocks up to date; false when the window is not the whole document (viewport mode). */
boolean ensureExtent() {
	if (windowed || sizeBase != 0) {
		return false;
	}
	int blocks = (height.length + BLOCK - 1) >> BLOCK_SHIFT;
	if (!extentValid || blocks != extentBlocks) {
		if (blockHeight.length != blocks) {
			blockHeight = new long[blocks];
			blockInts = new int[blocks * BLOCK_INTS];
			heightTree = new long[blocks + 1];
			noHeightTree = new int[blocks + 1];
			noWidthTree = new int[blocks + 1];
		} else {
			Arrays.fill(blockInts, 0);
		}
		extentBlocks = blocks;
		negativeHeights = 0;
		dirtyFrom = 0;
		dirtyTo = blocks;
		extentValid = true;
	}
	if (dirtyFrom < dirtyTo) {
		for (int b = dirtyFrom; b < dirtyTo; b++) {
			recomputeBlock(b);
		}
		buildTrees();
		dirtyFrom = Integer.MAX_VALUE;
		dirtyTo = 0;
	}
	return true;
}
/** Whether the extent covers lines [0, to) (exact mode). */
boolean extentCovers(int to) {
	return to <= height.length && ensureExtent();
}
/** Whether lines [0, to) can be searched by pixel distance: covered, and no measured height is negative. */
boolean extentSearchable(int to) {
	return extentCovers(to) && negativeHeights == 0;
}
private void markDirty(int fromBlock, int toBlock) {
	if (extentValid) {
		dirtyFrom = Math.min(dirtyFrom, fromBlock);
		dirtyTo = Math.max(dirtyTo, Math.min(toBlock, extentBlocks));
	}
}
private void recomputeBlock(int b) {
	int start = b << BLOCK_SHIFT, end = Math.min(height.length, start + BLOCK);
	long sum = 0;
	int noHeight = 0, noWidth = 0, negative = 0, bound = UNMEASURED;
	for (int i = start; i < end; i++) {
		int h = height[i], w = width[i];
		if (h == UNMEASURED) {
			noHeight++;
		} else {
			sum += h;
			if (h < UNMEASURED) {
				negative++;
			}
		}
		if (w == UNMEASURED) {
			noWidth++;
		}
		bound = Math.max(bound, w);
	}
	int p = b * BLOCK_INTS;
	negativeHeights += negative - blockInts[p + NEGATIVE];
	blockHeight[b] = sum;
	blockInts[p + NO_HEIGHT] = noHeight;
	blockInts[p + NO_WIDTH] = noWidth;
	blockInts[p + MAX_WIDTH] = bound;
	blockInts[p + NEGATIVE] = negative;
}
/** Builds the three Fenwick trees from the block values, in linear time. */
private void buildTrees() {
	int n = extentBlocks;
	for (int i = 1; i <= n; i++) {
		heightTree[i] = blockHeight[i - 1];
		noHeightTree[i] = blockInts[(i - 1) * BLOCK_INTS + NO_HEIGHT];
		noWidthTree[i] = blockInts[(i - 1) * BLOCK_INTS + NO_WIDTH];
	}
	for (int i = 1; i <= n; i++) {
		int j = i + (i & -i);
		if (j <= n) {
			heightTree[j] += heightTree[i];
			noHeightTree[j] += noHeightTree[i];
			noWidthTree[j] += noWidthTree[i];
		}
	}
}
/** Applies one line's size change (window index {@code i}, before the arrays are written) to its block. */
private void extentChange(int i, int newWidth, int newHeight) {
	int b = i >> BLOCK_SHIFT;
	if (b >= dirtyFrom && b < dirtyTo || b >= extentBlocks) {
		return; // recomputed on the next query
	}
	int oldWidth = width[i], oldHeight = height[i];
	int p = b * BLOCK_INTS;
	long dh = known(newHeight) - known(oldHeight);
	if (dh != 0) {
		blockHeight[b] += dh;
		for (int k = b + 1; k <= extentBlocks; k += k & -k) {
			heightTree[k] += dh;
		}
	}
	int dn = (newHeight == UNMEASURED ? 1 : 0) - (oldHeight == UNMEASURED ? 1 : 0);
	if (dn != 0) {
		blockInts[p + NO_HEIGHT] += dn;
		add(noHeightTree, b, dn);
	}
	dn = (newWidth == UNMEASURED ? 1 : 0) - (oldWidth == UNMEASURED ? 1 : 0);
	if (dn != 0) {
		blockInts[p + NO_WIDTH] += dn;
		add(noWidthTree, b, dn);
	}
	dn = (newHeight < UNMEASURED ? 1 : 0) - (oldHeight < UNMEASURED ? 1 : 0);
	if (dn != 0) {
		blockInts[p + NEGATIVE] += dn;
		negativeHeights += dn;
	}
	int bound = blockInts[p + MAX_WIDTH];
	if (bound != STALE) {
		if (newWidth > bound) {
			blockInts[p + MAX_WIDTH] = newWidth;
		} else if (oldWidth == bound && newWidth < oldWidth) {
			blockInts[p + MAX_WIDTH] = STALE; // the widest line may have shrunk: recomputed when asked
		}
	}
}
private void add(int[] tree, int b, int delta) {
	for (int k = b + 1; k <= extentBlocks; k += k & -k) {
		tree[k] += delta;
	}
}
static long known(int height) {
	return height == UNMEASURED ? 0 : height;
}
private long heightBlocks(int blocks) {
	long sum = 0;
	for (int k = blocks; k > 0; k -= k & -k) {
		sum += heightTree[k];
	}
	return sum;
}
private static int countBlocks(int[] tree, int blocks) {
	int sum = 0;
	for (int k = blocks; k > 0; k -= k & -k) {
		sum += tree[k];
	}
	return sum;
}
/** Largest block count k whose count prefix over blocks [0, k) is at most {@code target}. */
private int findBlock(int[] tree, int target) {
	int k = 0;
	for (int step = Integer.highestOneBit(Math.max(1, extentBlocks)); step > 0; step >>= 1) {
		int next = k + step;
		if (next <= extentBlocks && tree[next] <= target) {
			k = next;
			target -= tree[next];
		}
	}
	return k;
}
/** Largest block count k whose height prefix over blocks [0, k) is at most {@code target}; the prefix is left in {@link #searchSum}. */
private int findHeightBlock(long target) {
	int k = 0;
	long sum = 0;
	for (int step = Integer.highestOneBit(Math.max(1, extentBlocks)); step > 0; step >>= 1) {
		int next = k + step;
		if (next <= extentBlocks && sum + heightTree[next] <= target) {
			k = next;
			sum += heightTree[next];
		}
	}
	searchSum = sum;
	return k;
}
/**
 * Sum of the measured heights of lines [0, line) (window indices); lines without
 * a height add 0. Requires {@link #extentCovers(int) extentCovers(line)}.
 */
long heightPrefix(int line) {
	int b = line >> BLOCK_SHIFT, start = b << BLOCK_SHIFT;
	long sum;
	if (line - start <= BLOCK / 2 || b >= extentBlocks) {
		sum = heightBlocks(b);
		for (int i = start; i < line; i++) {
			sum += known(height[i]);
		}
	} else {
		// nearer the end of the block: subtract the rest of the block
		sum = heightBlocks(b + 1);
		for (int i = line, end = Math.min(height.length, start + BLOCK); i < end; i++) {
			sum -= known(height[i]);
		}
	}
	return sum;
}
/** Sum of the measured heights of lines [from, to). */
long heightSum(int from, int to) {
	if (to - from <= BLOCK) {
		long sum = 0;
		for (int i = from; i < to; i++) {
			sum += known(height[i]);
		}
		return sum;
	}
	return heightPrefix(to) - heightPrefix(from);
}
/** Number of lines in [from, to) without a height. */
int noHeightCount(int from, int to) {
	return from < to ? noHeightPrefix(to) - noHeightPrefix(from) : 0;
}
private int noHeightPrefix(int line) {
	int b = line >> BLOCK_SHIFT, start = b << BLOCK_SHIFT;
	int count = countBlocks(noHeightTree, b);
	for (int i = start; i < line; i++) {
		if (height[i] == UNMEASURED) {
			count++;
		}
	}
	return count;
}
/**
 * First line in [from, to) without a height ({@code widthLane} false) or without
 * a width (true), else {@code to}. Scans at most two blocks.
 */
int nextUnmeasured(int from, int to, boolean widthLane) {
	if (from >= to) {
		return to;
	}
	int[] lane = widthLane ? width : height;
	int b = from >> BLOCK_SHIFT;
	int end = Math.min(to, (b + 1) << BLOCK_SHIFT);
	for (int i = from; i < end; i++) {
		if (lane[i] == UNMEASURED) {
			return i;
		}
	}
	if (end >= to) {
		return to;
	}
	int[] tree = widthLane ? noWidthTree : noHeightTree;
	int k = findBlock(tree, countBlocks(tree, b + 1)); // the first later block holding such a line
	int start = k << BLOCK_SHIFT;
	if (k >= extentBlocks || start >= to) {
		return to;
	}
	for (int i = start, last = Math.min(to, start + BLOCK); i < last; i++) {
		if (lane[i] == UNMEASURED) {
			return i;
		}
	}
	return to;
}
/** Last line in [from, to) without a height, else {@code from - 1}. Scans at most two blocks. */
int previousUnmeasured(int from, int to) {
	if (from >= to) {
		return from - 1;
	}
	int b = (to - 1) >> BLOCK_SHIFT, start = Math.max(from, b << BLOCK_SHIFT);
	for (int i = to - 1; i >= start; i--) {
		if (height[i] == UNMEASURED) {
			return i;
		}
	}
	if (start <= from) {
		return from - 1;
	}
	int count = countBlocks(noHeightTree, b);
	if (count == 0) {
		return from - 1;
	}
	int k = findBlock(noHeightTree, count - 1); // the block holding the last such line before block b
	int blockStart = k << BLOCK_SHIFT;
	for (int i = Math.min(height.length, blockStart + BLOCK) - 1; i >= Math.max(from, blockStart); i--) {
		if (height[i] == UNMEASURED) {
			return i;
		}
	}
	return from - 1;
}
/**
 * Smallest line L in [from, to) whose height sum over [from, L] exceeds
 * {@code rest}, else -1. Lines in [from, to) must all have a height; requires
 * {@link #extentSearchable(int) extentSearchable(to)}.
 */
int firstExceeding(int from, int to, long rest) {
	if (from >= to) {
		return -1;
	}
	if (rest < 0) {
		return from;
	}
	if (to - from <= BLOCK) {
		long sum = 0;
		for (int i = from; i < to; i++) {
			sum += known(height[i]);
			if (sum > rest) {
				return i;
			}
		}
		return -1;
	}
	long target = heightPrefix(from) + rest;
	// the line is in the block after the largest block prefix that is at most target
	int k = findHeightBlock(target);
	long sum = searchSum;
	for (int i = k << BLOCK_SHIFT, end = Math.min(to, (k + 1) << BLOCK_SHIFT); i < end; i++) {
		sum += known(height[i]);
		if (sum > target) {
			return i;
		}
	}
	return -1;
}
/**
 * Largest line L in [from, to) whose height sum over [L, to) exceeds
 * {@code rest}, else -1. Lines in [from, to) must all have a height; requires
 * {@link #extentSearchable(int) extentSearchable(to)}.
 */
int lastExceeding(int from, int to, long rest) {
	if (from >= to) {
		return -1;
	}
	if (rest < 0) {
		return to - 1;
	}
	if (to - from <= BLOCK) {
		long sum = 0;
		for (int i = to - 1; i >= from; i--) {
			sum += known(height[i]);
			if (sum > rest) {
				return i;
			}
		}
		return -1;
	}
	// the largest L with heightPrefix(L) <= heightPrefix(to) - rest - 1
	long target = heightPrefix(to) - rest - 1;
	if (target < 0) {
		return -1;
	}
	int k = findHeightBlock(target);
	long sum = searchSum;
	int line = Math.min(to - 1, (k + 1) << BLOCK_SHIFT);
	for (int i = k << BLOCK_SHIFT, end = Math.min(to, (k + 1) << BLOCK_SHIFT); i < end; i++) {
		sum += known(height[i]);
		if (sum > target) {
			line = i;
			break;
		}
	}
	return line >= from ? line : -1;
}
/**
 * Whether every line of block {@code b} has a width and a height and none is
 * wider than {@code maxWidth}: a measuring pass over the block changes nothing.
 */
boolean blockSettled(int b, int maxWidth) {
	int p = b * BLOCK_INTS;
	if (blockInts[p + NO_WIDTH] != 0 || blockInts[p + NO_HEIGHT] != 0) {
		return false;
	}
	int bound = blockInts[p + MAX_WIDTH];
	if (bound == STALE) {
		bound = UNMEASURED;
		for (int i = b << BLOCK_SHIFT, end = Math.min(width.length, (b + 1) << BLOCK_SHIFT); i < end; i++) {
			bound = Math.max(bound, width[i]);
		}
		blockInts[p + MAX_WIDTH] = bound;
	}
	return bound <= maxWidth;
}
/* ------------------------------------------------------------------- copy */
/** A store with the same attributes (printing); sizes start unmeasured. */
StyledTextLineStore copyAttributes() {
	StyledTextLineStore copy = new StyledTextLineStore();
	copy.lineCount = lineCount;
	copy.mask = mask == null ? null : Arrays.copyOf(mask, mask.length);
	for (int k = 0; k < KIND_COUNT; k++) {
		copy.values[k] = values[k] == null ? null : Arrays.copyOf(values[k], values[k].length);
	}
	copy.patterns = Arrays.copyOf(patterns, patterns.length);
	copy.patternCount = patternCount;
	copy.objects = Arrays.copyOf(objects, objects.length);
	copy.objectRgba = Arrays.copyOf(objectRgba, objectRgba.length);
	copy.objectCount = objectCount;
	copy.objectSlots = Arrays.copyOf(objectSlots, objectSlots.length);
	copy.rebase(0, lineCount);
	return copy;
}
/* ------------------------------------------------------------- footprint */
/** Bytes held by this store's arrays (64-bit JVM, compressed references), without the shared objects. */
long footprintBytes() {
	long bytes = 16 + 12 * 8; // object header and fields
	bytes += arrayBytes(mask == null ? -1 : mask.length, 4);
	bytes += arrayBytes(values.length, 4);
	for (int[] kindValues : values) {
		bytes += arrayBytes(kindValues == null ? -1 : kindValues.length, 4);
	}
	bytes += arrayBytes(patterns.length, 4) + arrayBytes(lastWrite.length, 4);
	bytes += arrayBytes(objects.length, 4) + arrayBytes(objectRgba.length, 4) + arrayBytes(objectSlots.length, 4);
	bytes += arrayBytes(width.length, 4) + arrayBytes(height.length, 4) + arrayBytes(lineBits.length, 4);
	bytes += arrayBytes(blockHeight.length, 8) + arrayBytes(heightTree.length, 8) + arrayBytes(blockInts.length, 4);
	bytes += arrayBytes(noHeightTree.length, 4) + arrayBytes(noWidthTree.length, 4);
	return bytes;
}
static long arrayBytes(int length, int elementBytes) {
	return length < 0 ? 0 : (16 + (long) length * elementBytes + 7) & ~7L;
}
}
