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
package org.eclipse.swt.widgets;

import java.util.function.*;

/**
 * Primitive retained render-command stream for viewport-controlled SWT paint.
 *
 * <p>This is not a replacement for {@code org.eclipse.swt.graphics.GC}. It owns
 * no native resources and never escapes through the public API. Widgets may
 * record reusable SWT-controlled paint atoms here, then replay them through a
 * short-lived platform GC adapter for only the dirty paint-graph nodes.</p>
 *
 * <p>Commands reference external paths/text/images by stable long resource IDs.
 * Geometry and affine transforms remain primitive/columnar so repeated rows can
 * share a command shape without allocating one object per draw operation.</p>
 */
final class ViewportRenderCommandBuffer {

	static final int SAVE = 1;
	static final int RESTORE = 2;
	static final int CONCAT_TRANSFORM = 3;
	static final int CLIP_RECT = 4;
	static final int STROKE_PATH = 5;
	static final int FILL_PATH = 6;
	static final int DRAW_TEXT = 7;
	static final int DRAW_IMAGE = 8;

	@FunctionalInterface
	interface CommandVisitor {
		void accept (
				int opcode, int paintNode, long resourceId,
				double a, double b, double c, double d, double e, double f);
	}

	interface Replayer {
		void save ();
		void restore ();
		void concatTransform (double m00, double m10, double m01, double m11, double tx, double ty);
		void clipRect (double x, double y, double width, double height);
		void strokePath (long resourceId);
		void fillPath (long resourceId);
		void drawText (long resourceId, double x, double y);
		void drawImage (long resourceId, double x, double y, double width, double height);
	}

	private static final int INITIAL_CAPACITY = 16;

	private int [] opcode = new int [INITIAL_CAPACITY];
	private int [] paintNode = new int [INITIAL_CAPACITY];
	private long [] resourceId = new long [INITIAL_CAPACITY];
	private double [] a = new double [INITIAL_CAPACITY];
	private double [] b = new double [INITIAL_CAPACITY];
	private double [] c = new double [INITIAL_CAPACITY];
	private double [] d = new double [INITIAL_CAPACITY];
	private double [] e = new double [INITIAL_CAPACITY];
	private double [] f = new double [INITIAL_CAPACITY];

	private int size;
	private int saveDepth;

	int size () {
		return size;
	}

	void clear () {
		size = 0;
		saveDepth = 0;
	}

	void save (int node) {
		append (SAVE, node, 0, 0, 0, 0, 0, 0, 0);
		saveDepth++;
	}

	void restore (int node) {
		if (saveDepth == 0) throw new IllegalStateException ("unbalanced render-state restore");
		append (RESTORE, node, 0, 0, 0, 0, 0, 0, 0);
		saveDepth--;
	}

	void concatTransform (int node, ViewportAffineTransform transform) {
		if (transform == null) throw new IllegalArgumentException ("null transform");
		append (
				CONCAT_TRANSFORM, node, 0,
				transform.m00, transform.m10, transform.m01,
				transform.m11, transform.tx, transform.ty);
	}

	void clipRect (int node, double x, double y, double width, double height) {
		if (width < 0 || height < 0) throw new IllegalArgumentException ("negative clip");
		append (CLIP_RECT, node, 0, x, y, width, height, 0, 0);
	}

	void strokePath (int node, long pathId) {
		append (STROKE_PATH, node, pathId, 0, 0, 0, 0, 0, 0);
	}

	void fillPath (int node, long pathId) {
		append (FILL_PATH, node, pathId, 0, 0, 0, 0, 0, 0);
	}

	void drawText (int node, long textId, double x, double y) {
		append (DRAW_TEXT, node, textId, x, y, 0, 0, 0, 0);
	}

	void drawImage (int node, long imageId, double x, double y, double width, double height) {
		if (width < 0 || height < 0) throw new IllegalArgumentException ("negative image bounds");
		append (DRAW_IMAGE, node, imageId, x, y, width, height, 0, 0);
	}

	void validateBalancedState () {
		if (saveDepth != 0) throw new IllegalStateException ("unbalanced render-state save");
	}

	void forEach (CommandVisitor visitor) {
		if (visitor == null) throw new IllegalArgumentException ("null visitor");
		for (int index = 0; index < size; index++) {
			visitor.accept (
					opcode [index], paintNode [index], resourceId [index],
					a [index], b [index], c [index], d [index], e [index], f [index]);
		}
	}

	void forEachDirty (ViewportPaintGraph graph, CommandVisitor visitor) {
		if (graph == null) throw new IllegalArgumentException ("null graph");
		if (visitor == null) throw new IllegalArgumentException ("null visitor");
		for (int index = 0; index < size; index++) {
			int node = paintNode [index];
			if (node >= 0 && graph.isDirty (node)) {
				visitor.accept (
						opcode [index], node, resourceId [index],
						a [index], b [index], c [index], d [index], e [index], f [index]);
			}
		}
	}

	void replayDirty (ViewportPaintGraph graph, Replayer replayer) {
		if (replayer == null) throw new IllegalArgumentException ("null replayer");
		forEachDirty (graph, (op, node, resource, p0, p1, p2, p3, p4, p5) -> {
			switch (op) {
				case SAVE -> replayer.save ();
				case RESTORE -> replayer.restore ();
				case CONCAT_TRANSFORM ->
					replayer.concatTransform (p0, p1, p2, p3, p4, p5);
				case CLIP_RECT -> replayer.clipRect (p0, p1, p2, p3);
				case STROKE_PATH -> replayer.strokePath (resource);
				case FILL_PATH -> replayer.fillPath (resource);
				case DRAW_TEXT -> replayer.drawText (resource, p0, p1);
				case DRAW_IMAGE -> replayer.drawImage (resource, p0, p1, p2, p3);
				default -> throw new IllegalStateException ("unknown render opcode");
			}
		});
	}

	private void append (
			int op, int node, long resource,
			double p0, double p1, double p2, double p3, double p4, double p5) {
		if (node < 0) throw new IllegalArgumentException ("negative paint node");
		ensureCapacity (size + 1);
		opcode [size] = op;
		paintNode [size] = node;
		resourceId [size] = resource;
		a [size] = p0;
		b [size] = p1;
		c [size] = p2;
		d [size] = p3;
		e [size] = p4;
		f [size] = p5;
		size++;
	}

	private void ensureCapacity (int required) {
		if (required <= opcode.length) return;
		int next = Math.max (required, opcode.length * 2);
		opcode = java.util.Arrays.copyOf (opcode, next);
		paintNode = java.util.Arrays.copyOf (paintNode, next);
		resourceId = java.util.Arrays.copyOf (resourceId, next);
		a = java.util.Arrays.copyOf (a, next);
		b = java.util.Arrays.copyOf (b, next);
		c = java.util.Arrays.copyOf (c, next);
		d = java.util.Arrays.copyOf (d, next);
		e = java.util.Arrays.copyOf (e, next);
		f = java.util.Arrays.copyOf (f, next);
	}
}
