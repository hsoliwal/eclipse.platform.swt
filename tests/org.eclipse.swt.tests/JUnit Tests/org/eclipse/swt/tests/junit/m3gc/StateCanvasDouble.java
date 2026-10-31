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
 *     Hitesh Soliwal (Synexia) - initial API and implementation (GC seam, leaf 6)
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3gc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.Region;
import io.github.humbleui.types.Rect;

/**
 * Leaf 6: a call-logging canvas double that also keeps Skia's save stack: per level the matrix and the clips applied in it
 * (a clipRect as its local rectangle, a clipRegion as its device bounds read during the call, a clipPath as its bounds).
 * save pushes a copy, restore pops one, restoreToCount pops down to the count, getLocalToDevice answers the top matrix.
 */
class StateCanvasDouble extends M3CanvasDouble {
	record Level(Matrix33 matrix, List<Rect> clips) {
	}

	final Deque<Level> stack = new ArrayDeque<>();

	StateCanvasDouble(String name, List<MethodCall> calls, Matrix33 frameMatrix) {
		super(0, 0, name, calls);
		stack.push(new Level(frameMatrix, new ArrayList<>()));
		saveCount = 1;
	}

	/* the clips in force: every level's, bottom first */
	List<Rect> clips() {
		List<Rect> all = new ArrayList<>();
		stack.descendingIterator().forEachRemaining(level -> all.addAll(level.clips()));
		return all;
	}

	Matrix33 matrix() {
		return stack.peek().matrix();
	}

	@Override
	public Matrix33 getLocalToDevice() {
		return matrix();
	}

	@Override
	public void save() {
		super.save();
		stack.push(new Level(matrix(), new ArrayList<>()));
	}

	@Override
	public void restore() {
		super.restore();
		stack.pop();
		saveCount--;
	}

	@Override
	public void restoreToCount(int count) {
		IntStream.range(0, Math.max(0, stack.size() - Math.max(1, count))).forEach(i -> stack.pop());
		super.restoreToCount(count);
	}

	@Override
	public void setMatrix(Matrix33 matrix) {
		super.setMatrix(matrix);
		Level top = stack.pop();
		stack.push(new Level(matrix, top.clips()));
	}

	@Override
	public void clipRect(Rect rect) {
		super.clipRect(rect);
		stack.peek().clips().add(rect);
	}

	@Override
	public void clipRegion(Region region) {
		super.clipRegion(region);
		stack.peek().clips().add(region.getBounds().toRect());
	}

	@Override
	public void clipPath(Path path, ClipMode mode, boolean antiAlias) {
		super.clipPath(path, mode, antiAlias);
		stack.peek().clips().add(path.computeTightBounds());
	}
}
