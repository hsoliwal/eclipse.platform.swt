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

import jdk.m3.graphics.M3ClipMode;
import jdk.m3.graphics.M3Matrix33;
import jdk.m3.graphics.M3Path;
import jdk.m3.graphics.M3Region;
import jdk.m3.graphics.M3Rect;

/**
 * Leaf 6: a call-logging canvas double that also keeps Skia's save stack: per level the matrix and the clips applied in it
 * (a clipRect as its local rectangle, a clipRegion as its device bounds read during the call, a clipPath as its bounds).
 * save pushes a copy, restore pops one, restoreToCount pops down to the count, getLocalToDevice answers the top matrix.
 */
class StateCanvasDouble extends M3CanvasDouble {
	record Level(M3Matrix33 matrix, List<M3Rect> clips) {
	}

	final Deque<Level> stack = new ArrayDeque<>();

	StateCanvasDouble(String name, List<MethodCall> calls, M3Matrix33 frameMatrix) {
		super(0, 0, name, calls);
		stack.push(new Level(frameMatrix, new ArrayList<>()));
		saveCount = 1;
	}

	/* the clips in force: every level's, bottom first */
	List<M3Rect> clips() {
		List<M3Rect> all = new ArrayList<>();
		stack.descendingIterator().forEachRemaining(level -> all.addAll(level.clips()));
		return all;
	}

	M3Matrix33 matrix() {
		return stack.peek().matrix();
	}

	@Override
	public M3Matrix33 getLocalToDevice() {
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
	public void setMatrix(M3Matrix33 matrix) {
		super.setMatrix(matrix);
		Level top = stack.pop();
		stack.push(new Level(matrix, top.clips()));
	}

	@Override
	public void clipRect(M3Rect rect) {
		super.clipRect(rect);
		stack.peek().clips().add(rect);
	}

	@Override
	public void clipRegion(M3Region region) {
		super.clipRegion(region);
		stack.peek().clips().add(region.getBounds().toRect());
	}

	@Override
	public void clipPath(M3Path path, M3ClipMode mode, boolean antiAlias) {
		super.clipPath(path, mode, antiAlias);
		stack.peek().clips().add(path.computeTightBounds());
	}
}
