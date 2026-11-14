/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.ExceptionStash;

import java.util.Arrays;

/** Mobile composite with parent-owned primitive bounds/state and stable child identities. */
public class Composite extends Scrollable {
	private Control[] children = new Control[4];
	int[] xs = new int[4], ys = new int[4], widths = new int[4], heights = new int[4];
	short[] flags = new short[4];
	private int childCount;
	private Layout layout;
	private boolean layoutDeferred;

	public Composite(Composite parent, int style) {
		super(parent, style);
	}

	Composite(Display display, int style) {
		super(display, style);
	}

	int attach(Control c) {
		checkWidget();
		if (childCount == children.length) {
			int n = Math.addExact(childCount, Math.max(4, childCount >>> 1));
			children = Arrays.copyOf(children, n);
			xs = Arrays.copyOf(xs, n);
			ys = Arrays.copyOf(ys, n);
			widths = Arrays.copyOf(widths, n);
			heights = Arrays.copyOf(heights, n);
			flags = Arrays.copyOf(flags, n);
		}
		int slot = childCount++;
		children[slot] = c;
		flags[slot] = VISIBLE | ENABLED;
		return slot;
	}

	void detach(Control c) {
		int i = c.slot;
		if (i < 0 || i >= childCount || children[i] != c) return;
		int n = --childCount - i;
		System.arraycopy(children, i + 1, children, i, n);
		System.arraycopy(xs, i + 1, xs, i, n);
		System.arraycopy(ys, i + 1, ys, i, n);
		System.arraycopy(widths, i + 1, widths, i, n);
		System.arraycopy(heights, i + 1, heights, i, n);
		System.arraycopy(flags, i + 1, flags, i, n);
		children[childCount] = null;
		xs[childCount] = ys[childCount] = widths[childCount] = heights[childCount] = 0;
		flags[childCount] = 0;
		for (int j = i; j < childCount; j++) children[j].slot = j;
		c.slot = -1;
	}

	Control[] childrenSnapshot() {
		return Arrays.copyOf(children, childCount);
	}

	public Control[] getChildren() {
		checkWidget();
		return childrenSnapshot();
	}

	public Layout getLayout() {
		checkWidget();
		return layout;
	}

	public void setLayout(Layout value) {
		checkWidget();
		layout = value;
	}

	public void layout() {
		layout(true);
	}

	public void layout(boolean changed) {
		layout(changed, false);
	}

	public void layout(boolean changed, boolean all) {
		checkWidget();
		if (layoutDeferred) return;
		if (layout != null) layout.layout(this, changed);
		if (isDisposed()) return;
		if (all)
			for (Control c : childrenSnapshot())
				if (c instanceof Composite composite && !c.isDisposed())
					composite.layout(changed, true);
		if (!isDisposed()) redraw();
	}

	public boolean getLayoutDeferred() {
		checkWidget();
		return layoutDeferred;
	}

	public boolean isLayoutDeferred() {
		checkWidget();
		return layoutDeferred || parent != null && parent.isLayoutDeferred();
	}

	public void setLayoutDeferred(boolean value) {
		checkWidget();
		boolean old = layoutDeferred;
		layoutDeferred = value;
		if (old && !value) layout();
	}

	@Override
	public Point computeSize(int w, int h, boolean changed) {
		checkWidget();
		if (layout != null) return layout.computeSize(this, w, h, changed);
		int right = 0, bottom = 0;
		for (int i = 0; i < childCount; i++) {
			right = Math.max(right, xs[i] + widths[i]);
			bottom = Math.max(bottom, ys[i] + heights[i]);
		}
		return new Point(w == SWT.DEFAULT ? right : w, h == SWT.DEFAULT ? bottom : h);
	}

	@Override
	Control hit(int x, int y) {
		if (super.hit(x, y) == null) return null;
		int border = getBorderWidth();
		x -= border;
		y -= border;
		if (x < 0 || y < 0 || x >= width() - 2 * border || y >= height() - 2 * border) return this;
		for (int i = childCount - 1; i >= 0; i--) {
			Control hit = children[i].hit(x - xs[i], y - ys[i]);
			if (hit != null) return hit;
		}
		return this;
	}

	@Override
	void releaseChildren() {
		try (ExceptionStash errors = new ExceptionStash()) {
			for (Control c : childrenSnapshot())
				try {
					c.dispose();
				} catch (RuntimeException | Error e) {
					errors.stash(e);
				}
		}
	}
}
