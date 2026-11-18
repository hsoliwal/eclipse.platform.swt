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
package org.eclipse.swt.widgets;

import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.ExceptionStash;

/** Original parent owner for child peer order and SWT Layout execution. */
public class Composite extends Scrollable {
	private Control[] children = new Control[8];
	private int childCount;
	private Layout layout;
	private int deferDepth;
	private boolean layoutNeeded;
	private boolean flushNeeded;
	public Composite(Composite parent, int style) { super(parent, style); }
	/** Retain original scroll flags for mobile controls with software viewports. */
	Composite(Composite parent, int style, boolean softwareScroll) {
		super(parent, style, softwareScroll);
	}
	Composite(Display display, int style) { super(display, style); }

	/**
	 * Compatibility alias over the original Control's native border relief.
	 * No second mobile peer, border painter or lifecycle state is introduced.
	 */
	public int getBevelBorder() {
		checkWidget();
		int relief = getBorderRelief();
		return relief == SWT.DEFAULT || relief == SWT.SHADOW_NONE ? SWT.NONE : relief;
	}

	public void setBevelBorder(int border) {
		checkWidget();
		if (border == SWT.NONE || border == SWT.SHADOW_NONE) {
			setBorderRelief(SWT.DEFAULT);
			return;
		}
		if (border != SWT.SHADOW_IN && border != SWT.SHADOW_OUT
				&& border != SWT.SHADOW_ETCHED_IN && border != SWT.SHADOW_ETCHED_OUT)
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		setBorderRelief(border);
	}
	@Override protected void checkSubclass() { }
	void addChild(Control child) { if (childCount == children.length) children = Arrays.copyOf(children, childCount + 8); children[childCount++] = child; }
	void removeChild(Control child) { for (int i = 0; i < childCount; i++) if (children[i] == child) { System.arraycopy(children, i + 1, children, i, --childCount - i); children[childCount] = null; break; } }
	public Control[] getChildren() { checkWidget(); return Arrays.copyOf(children, childCount); }
	public Layout getLayout() { checkWidget(); return layout; }
	public void setLayout(Layout value) { checkWidget(); layout = value; }
	public void layout() { layout(true); }
	public void layout(boolean changed) { layout(changed, false); }
	public void layout(boolean changed, boolean all) {
		checkWidget(); layoutNeeded = true; flushNeeded |= changed;
		if (isLayoutDeferred()) return;
		if (layout != null) { boolean flush = flushNeeded; layoutNeeded = false; flushNeeded = false; layout.layout(this, flush); }
		if (disposed) return;
		if (all) for (Control c : getChildren()) {
			if (disposed) return;
			if (!c.isDisposed() && c instanceof Composite nested) nested.layout(changed, true);
		}
	}
	public void layout(Control[] changed) { layout(changed, 0); }
	public void layout(Control[] changed, int flags) {
		checkWidget(); if (changed == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		for (Control c : changed) {
			if (c == null || c.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			Composite p = c.parent; boolean descendant = false;
			while (p != null) { if (p.layout != null) p.flushNeeded |= !p.layout.flushCache(c); if (p == this) { descendant = true; break; } c = p; p = p.parent; }
			if (!descendant) SWT.error(SWT.ERROR_INVALID_PARENT);
		}
		layout(true, (flags & SWT.ALL) != 0);
	}
	public void changed(Control[] changed) { layout(changed); }
	public void setLayoutDeferred(boolean defer) {
		checkWidget(); if (defer) deferDepth++; else if (deferDepth > 0 && --deferDepth == 0 && layoutNeeded) layout(flushNeeded, true);
	}
	public boolean getLayoutDeferred() { checkWidget(); return deferDepth != 0; }
	public boolean isLayoutDeferred() { checkWidget(); return deferDepth != 0 || parent != null && parent.isLayoutDeferred(); }
	@Override void resized() { if (layout != null) layout(false); }
	@Override public Point computeSize(int wHint, int hHint, boolean changed) {
		checkWidget(); Point size;
		if (layout != null) size = layout.computeSize(this, wHint, hHint, changed);
		else { int w = 0, h = 0; for (Control c : getChildren()) { Rectangle b = c.getBounds(); w = Math.max(w, b.x + b.width); h = Math.max(h, b.y + b.height); } size = new Point(w, h); }
		if (wHint != SWT.DEFAULT) size.x = wHint; if (hHint != SWT.DEFAULT) size.y = hHint;
		Rectangle trim = computeTrim(0, 0, size.x, size.y); return new Point(trim.width, trim.height);
	}
	@Override void releaseChildren() { try (ExceptionStash failures = new ExceptionStash()) { for (Control c : getChildren()) try { c.dispose(); } catch (RuntimeException | Error e) { failures.stash(e); } } }
}
