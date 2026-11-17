/*******************************************************************************
 * Copyright (c) 2000, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Hitesh Soliwal (Synexia) - Android and iOS port on the control's native peer
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * SWT Sash on Android/iOS, drawn on the control's own native peer with the event contract of the desktop widget: the
 * sash never moves itself. A press and every move of a drag send <code>SWT.Selection</code> with the bounds the sash
 * would take, <code>detail</code> <code>SWT.DRAG</code> unless the style is <code>SWT.SMOOTH</code>, and a listener that
 * sets <code>doit</code> to false refuses the position; the release sends the final <code>SWT.Selection</code>
 * (<code>detail</code> 0) at the last accepted position, and the listener moves the sash. The arrow keys across the sash
 * move it by 9 pixels, by 1 with Ctrl; the native accessibility increment and decrement do the same.
 */
public class Sash extends Control {
	static final int INCREMENT = 1, PAGE_INCREMENT = 9;
	private boolean dragging;
	private int startX, startY, lastX, lastY;

	/** Create a Sash with SWT.HORIZONTAL or SWT.VERTICAL orientation, optionally SWT.SMOOTH. */
	public Sash(Composite parent, int style) {
		super(parent, checkStyle(style));
		addListener(SWT.Paint, event -> paint(event.gc));
		addListener(SWT.MouseDown, this::mouseDown);
		addListener(SWT.MouseMove, this::mouseMove);
		addListener(SWT.MouseUp, this::mouseUp);
		addListener(SWT.KeyDown, this::keyDown);
		publishAccessibility();
	}

	static int checkStyle(int style) {
		return WidgetStylePolicy.ORIENTATION.applyAsInt(style) | (style & SWT.SMOOTH);
	}

	/** Add SWT.Selection notification for drags and keyboard moves. */
	public void addSelectionListener(SelectionListener listener) {
		addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
	}

	/** Remove a previously attached selection listener. */
	public void removeSelectionListener(SelectionListener listener) {
		removeTypedListener(SWT.Selection, listener);
		removeTypedListener(SWT.DefaultSelection, listener);
	}

	private boolean horizontal() {
		return (style & SWT.HORIZONTAL) != 0;
	}

	private boolean smooth() {
		return (style & SWT.SMOOTH) != 0;
	}

	private boolean live() {
		return !isDisposed() && isEnabled() && isVisible();
	}

	/* the position the sash would take along its axis, kept inside the parent's client area */
	private int clamp(int value, int extent, int size) {
		return Math.max(0, Math.min(value, extent - size));
	}

	private Event select(int x, int y, Rectangle bounds, int detail) {
		Event event = new Event();
		event.x = x;
		event.y = y;
		event.width = bounds.width;
		event.height = bounds.height;
		event.detail = detail;
		event.doit = true;
		sendEvent(SWT.Selection, event);
		return event;
	}

	private void mouseDown(Event event) {
		if (event.button != 1 || !live()) return;
		Rectangle bounds = getBounds();
		startX = event.x;
		startY = event.y;
		Event selection = select(bounds.x, bounds.y, bounds, smooth() ? 0 : SWT.DRAG);
		if (isDisposed() || !selection.doit) return;
		dragging = true;
		lastX = selection.x;
		lastY = selection.y;
		redraw();
	}

	private void mouseMove(Event event) {
		if (!dragging || !live()) return;
		Rectangle bounds = getBounds(), client = parent.getClientArea();
		int x = lastX, y = lastY;
		if (horizontal()) y = clamp(bounds.y + event.y - startY, client.height, bounds.height);
		else x = clamp(bounds.x + event.x - startX, client.width, bounds.width);
		if (x == lastX && y == lastY) return;
		Event selection = select(x, y, bounds, smooth() ? 0 : SWT.DRAG);
		if (isDisposed() || !selection.doit) return;
		lastX = selection.x;
		lastY = selection.y;
	}

	private void mouseUp(Event event) {
		if (event.button != 1 || !dragging) return;
		dragging = false;
		if (isDisposed()) return;
		select(lastX, lastY, getBounds(), 0);
		if (!isDisposed()) redraw();
	}

	private void keyDown(Event event) {
		if (dragging || !live()) return;
		int key = event.keyCode;
		boolean across = horizontal() ? key == SWT.ARROW_UP || key == SWT.ARROW_DOWN : key == SWT.ARROW_LEFT || key == SWT.ARROW_RIGHT;
		if (!across) return;
		int step = (event.stateMask & SWT.CTRL) != 0 ? INCREMENT : PAGE_INCREMENT;
		if (key == SWT.ARROW_UP || key == SWT.ARROW_LEFT) step = -step;
		if (!horizontal() && (parent.getStyle() & SWT.MIRRORED) != 0) step = -step;
		Rectangle bounds = getBounds(), client = parent.getClientArea();
		int x = bounds.x, y = bounds.y;
		if (horizontal()) y = clamp(y + step, client.height, bounds.height);
		else x = clamp(x + step, client.width, bounds.width);
		if (x != bounds.x || y != bounds.y) select(x, y, bounds, 0);
	}

	@Override public void setEnabled(boolean value) {
		if (!value) dragging = false;
		super.setEnabled(value);
		if (!isDisposed()) publishAccessibility();
	}

	@Override public void setVisible(boolean value) {
		if (!value) dragging = false;
		super.setVisible(value);
	}

	private void publishAccessibility() {
		int flags = isEnabled() ? ACC.STATE_NORMAL : ACC.STATE_DISABLED;
		display.internal_platform().setAccessibility(peer, horizontal() ? "Horizontal sash" : "Vertical sash", ACC.ROLE_SLIDER, flags);
	}

	private void paint(GC gc) {
		if (isDisposed()) return;
		Point size = getSize();
		if (size.x <= 0 || size.y <= 0) return;
		gc.setBackground(dragging ? display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW) : getBackground());
		gc.fillRectangle(0, 0, size.x, size.y);
	}

	/** Return the desktop defaults (3 pixels across, 64 along) or caller-provided hints. */
	@Override public Point computeSize(int wHint, int hHint, boolean changed) {
		checkWidget();
		int border = getBorderWidth() * 2;
		return new Point((wHint == SWT.DEFAULT ? (horizontal() ? 64 : 3) : Math.max(0, wHint)) + border,
				(hHint == SWT.DEFAULT ? (horizontal() ? 3 : 64) : Math.max(0, hHint)) + border);
	}
}
