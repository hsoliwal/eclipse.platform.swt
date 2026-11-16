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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.ExceptionStash;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/** One SWT control owns one native view, its bounds, and its drawing image. */
public abstract class Control extends Widget implements Drawable {
	Composite parent;
	long peer;
	long clearedFocusGeneration;
	private Image image;
	private Rectangle bounds = new Rectangle(0, 0, 0, 0);
	private Rectangle damage;
	private boolean visible = true, enabled = true, paintQueued;
	private int redrawDepth, paintDepth;
	private Color foreground, background;
	private Font font;
	private Object layoutData;
	private String toolTipText;

	public Control(Composite parent, int style) {
		super(parent, style); this.parent = parent;
		createPeer(); parent.addChild(this);
	}
	Control(Display display, int style) { super(display, style); createPeer(); }
	private void createPeer() {
		MobilePlatform p = display.internal_platform();
		// Button owns a paintable/touch-activated peer rather than a noninteractive
        // composite peer. Android's accessibility click route is CANVAS-specific.
        int kind = this instanceof Text ? MobilePlatform.TEXT : this instanceof Shell ? MobilePlatform.SHELL
                : this instanceof Canvas || this instanceof Button || this instanceof Link || this instanceof Scale || this instanceof List || this instanceof Table ? MobilePlatform.CANVAS
                : MobilePlatform.COMPOSITE;
		peer = p.createControl(kind, parent == null ? 0 : parent.peer, style);
		if (peer == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		visible = !(this instanceof Shell);
		try { p.setVisible(peer, visible); display.register(this); }
		catch (RuntimeException | Error failure) {
			try { p.disposeControl(peer); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
			peer = 0; throw failure;
		}
	}
	@Override void releaseParent() { if (parent != null) parent.removeChild(this); parent = null; }
	@Override void releaseWidget() {
		try (ExceptionStash failures = new ExceptionStash()) {
			try { display.clearFocus(this); } catch (RuntimeException | Error failure) { failures.stash(failure); }
			display.unregister(this);
			try { if (image != null) image.internal_disposeWhenUnused(); }
			catch (RuntimeException | Error failure) { failures.stash(failure); }
			finally { image = null; }
			try { display.internal_platform().disposeControl(peer); }
			catch (RuntimeException | Error failure) { failures.stash(failure); }
			finally { peer = 0; }
		}
	}
	public Composite getParent() { checkWidget(); return parent; }
	public Shell getShell() {
		checkWidget(); Control c = this; while (c != null && !(c instanceof Shell)) c = c.parent;
		return (Shell) c;
	}
	public Rectangle getBounds() { checkWidget(); return new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height); }
	public Point getLocation() { checkWidget(); return new Point(bounds.x, bounds.y); }
	public Point getSize() { checkWidget(); return new Point(bounds.width, bounds.height); }
	public void setBounds(Rectangle value) { if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); setBounds(value.x, value.y, value.width, value.height); }
	public void setBounds(int x, int y, int width, int height) { setBoundsInternal(x, y, width, height, true); }
	private void setBoundsInternal(int x, int y, int width, int height, boolean nativeUpdate) {
		checkWidget(); width = Math.max(0, width); height = Math.max(0, height);
		boolean moved = x != bounds.x || y != bounds.y, resized = width != bounds.width || height != bounds.height;
		if (!moved && !resized) return;
		bounds = new Rectangle(x, y, width, height);
		if (nativeUpdate) display.internal_platform().setBounds(peer, x, y, width, height);
		if (resized && clientBounds().isEmpty()) {
			damage = null;
			if (image != null) { image.internal_disposeWhenUnused(); image = null; }
		}
		if (moved) sendEvent(SWT.Move, new Event());
		if (disposed) return;
		if (resized) { sendEvent(SWT.Resize, new Event()); if (!disposed) { resized(); if (!disposed) redraw(); } }
	}
	void nativeResize(int width, int height) { setBoundsInternal(bounds.x, bounds.y, width, height, false); }
	void resized() { }
	public void setLocation(int x, int y) { setBounds(x, y, bounds.width, bounds.height); }
	public void setLocation(Point p) { if (p == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); setLocation(p.x, p.y); }
	public void setSize(int width, int height) { setBounds(bounds.x, bounds.y, width, height); }
	public void setSize(Point p) { if (p == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); setSize(p.x, p.y); }
	public int getBorderWidth() { checkWidget(); return (style & SWT.BORDER) == 0 ? 0 : 1; }
	private Rectangle clientBounds() {
		int trim = getBorderWidth() * 2;
		return new Rectangle(0, 0, Math.max(0, bounds.width - trim), Math.max(0, bounds.height - trim));
	}
	public Point computeSize(int wHint, int hHint) { return computeSize(wHint, hHint, true); }
	public Point computeSize(int wHint, int hHint, boolean changed) {
		checkWidget(); int border = getBorderWidth() * 2;
		return new Point((wHint == SWT.DEFAULT ? 64 : wHint) + border, (hHint == SWT.DEFAULT ? 64 : hHint) + border);
	}
	public void pack() { pack(true); }
	public void pack(boolean changed) { setSize(computeSize(SWT.DEFAULT, SWT.DEFAULT, changed)); }
	public boolean getVisible() { checkWidget(); return visible; }
	public boolean isVisible() { checkWidget(); return visible && (parent == null || parent.isVisible()); }
	public void setVisible(boolean value) {
		checkWidget(); if (visible == value) return;
		visible = value; display.internal_platform().setVisible(peer, value);
		if (!value) { display.clearFocus(this); if (disposed) return; }
		sendEvent(value ? SWT.Show : SWT.Hide, new Event());
		if (value && !disposed) redrawAll();
	}
	public boolean getEnabled() { checkWidget(); return enabled; }
	public boolean isEnabled() { checkWidget(); return enabled && (parent == null || parent.isEnabled()); }
	public void setEnabled(boolean value) {
		checkWidget(); if (enabled == value) return;
		enabled = value; display.internal_platform().setEnabled(peer, value);
		try { if (!value) display.clearFocus(this); }
		finally { redrawAll(); }
	}
	public Color getForeground() { checkWidget(); return foreground != null ? foreground : parent != null ? parent.getForeground() : display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND); }
	public Color getBackground() { checkWidget(); return background != null ? background : parent != null ? parent.getBackground() : display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND); }
	public void setForeground(Color value) { checkWidget(); validate(value); foreground = value; redrawAll(); }
	public void setBackground(Color value) { checkWidget(); validate(value); background = value; redrawAll(); }
	private void validate(Color value) { if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT); }
	public Font getFont() { checkWidget(); return font != null ? font : parent != null ? parent.getFont() : display.getSystemFont(); }
	public void setFont(Font value) { checkWidget(); if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT); font = value; redrawAll(); }
	public Object getLayoutData() { checkWidget(); return layoutData; }
	public void setLayoutData(Object value) { checkWidget(); layoutData = value; }
	public void requestLayout() { checkWidget(); if (parent != null) parent.layout(new Control[] {this}); }
	public void setToolTipText(String text) {
		checkWidget();
		if (text != null && !text.isEmpty()) throw new UnsupportedOperationException("Native mobile tooltips are not implemented");
		toolTipText = text;
	}
	public String getToolTipText() { checkWidget(); return toolTipText; }
	public boolean forceFocus() { return setFocus(); }
	public boolean setFocus() {
		checkWidget(); if (display.isDisposing() || !isVisible() || !isEnabled() || (style & SWT.NO_FOCUS) != 0) return false;
		for (Control owner = this; owner != null; owner = owner.parent) if (owner.disposing) return false;
		if (display.focusControl == this) return true;
		long generation = display.beginFocusRequest();
		if (!display.internal_platform().requestFocus(peer)) return false;
		// Later native changes within this request retain the same attempted generation.
		display.commitFocusRequest(generation);
		Control previous = display.focusControl; display.focusControl = this;
		if (previous != null && previous != this && !previous.isDisposed()) previous.sendEvent(SWT.FocusOut, new Event());
		if (!disposed && display.focusControl == this && previous != this) sendEvent(SWT.FocusIn, new Event());
		return !disposed && display.focusControl == this;
	}
	public boolean isFocusControl() { checkWidget(); return display.focusControl == this; }
	public Point toDisplay(int x, int y) {
		checkWidget();
		for (Control c = this; c != null; c = c.parent) {
			int border = c.getBorderWidth();
			x = Math.addExact(Math.addExact(x, c.bounds.x), border);
			y = Math.addExact(Math.addExact(y, c.bounds.y), border);
			// Owned shells retain a logical parent but every OS shell peer is rooted
			// at the native window host; its bounds are already display-relative.
			if (c instanceof Shell) break;
		}
		return new Point(x, y);
	}
	public Point toDisplay(Point point) { if (point == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); return toDisplay(point.x, point.y); }
	public Point toControl(int x, int y) { Point origin = toDisplay(0, 0); return new Point(x - origin.x, y - origin.y); }
	public Point toControl(Point point) { if (point == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); return toControl(point.x, point.y); }
	public Monitor getMonitor() { checkWidget(); return display.getPrimaryMonitor(); }
	public int getOrientation() { checkWidget(); return (style & SWT.RIGHT_TO_LEFT) != 0 ? SWT.RIGHT_TO_LEFT : SWT.LEFT_TO_RIGHT; }
	public void setOrientation(int orientation) {
		checkWidget(); if ((orientation & SWT.RIGHT_TO_LEFT) != 0) throw new UnsupportedOperationException("RTL native control layout is not implemented");
		if ((orientation & SWT.LEFT_TO_RIGHT) != 0) style = style & ~SWT.RIGHT_TO_LEFT | SWT.LEFT_TO_RIGHT;
	}
	public void redraw() { redraw(0, 0, bounds.width, bounds.height, false); }
	public void redraw(int x, int y, int width, int height, boolean all) {
		checkWidget(); if (width <= 0 || height <= 0) return;
		Rectangle clipped = clientBounds().intersection(new Rectangle(x, y, width, height));
		if (clipped.isEmpty()) return;
		damage = damage == null ? clipped : damage.union(clipped);
		if (all && this instanceof Composite composite) for (Control child : composite.getChildren()) child.redrawAll();
		queuePaint();
	}
	void redrawAll() { if (disposed) return; redraw(0, 0, bounds.width, bounds.height, true); }
	private void queuePaint() {
		if (paintQueued || redrawDepth != 0 || !isVisible() || !display.active) return;
		paintQueued = true;
		display.asyncExec(() -> { paintQueued = false; if (!disposed) update(); });
	}
	public void setRedraw(boolean redraw) {
		checkWidget(); if (!redraw) redrawDepth++;
		else if (redrawDepth > 0 && --redrawDepth == 0) { if (damage == null) redraw(); else queuePaint(); }
	}
	private Image ensureImage() {
		Rectangle client = clientBounds();
		if (client.isEmpty()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		if (image != null) {
			Rectangle b = image.getBounds();
			if (b.width == client.width && b.height == client.height) return image;
		}
		Image replacement = new Image(display, client.width, client.height);
		Image previous = image; image = replacement;
		if (previous != null) previous.internal_disposeWhenUnused();
		return image;
	}
	public void update() {
		checkWidget();
		if (paintDepth != 0 || damage == null || redrawDepth != 0 || !isVisible() || !display.active || clientBounds().isEmpty()) return;
		Rectangle clip = damage.intersection(clientBounds());
		GC gc = new GC(this); damage = null; paintDepth++;
		try {
			gc.setClipping(clip); gc.setBackground(getBackground()); gc.fillRectangle(clip);
			gc.setForeground(getForeground()); gc.setFont(getFont());
			Event event = new Event(); event.gc = gc; event.x = clip.x; event.y = clip.y; event.width = clip.width; event.height = clip.height;
			sendEvent(SWT.Paint, event);
		} finally {
			try { gc.dispose(); } finally { paintDepth--; }
			if (!disposed && image != null && !image.isDisposed()) display.internal_platform().present(peer, image.internal_handle());
		}
	}
	public boolean print(GC gc) {
		checkWidget(); if (gc == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (gc.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		return printTree(gc, 0, 0);
	}
	private boolean printTree(GC gc, int x, int y) {
		if (disposed || !isVisible() || bounds.width == 0 || bounds.height == 0) return false;
		if (!clientBounds().isEmpty()) { if (image == null) redraw(); update(); }
		if (disposed) return false;
		int border = getBorderWidth();
		Rectangle client = clientBounds();
		Rectangle savedClip = gc.getClipping();
		Color savedBackground = gc.getBackground();
		try {
			gc.setClipping(savedClip.intersection(new Rectangle(x, y, bounds.width, bounds.height)));
			if (border != 0) {
				gc.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
				gc.fillRectangle(x, y, bounds.width, 1);
				if (bounds.height > 1) gc.fillRectangle(x, y + bounds.height - 1, bounds.width, 1);
				if (bounds.height > 2) {
					gc.fillRectangle(x, y + 1, 1, bounds.height - 2);
					if (bounds.width > 1) gc.fillRectangle(x + bounds.width - 1, y + 1, 1, bounds.height - 2);
				}
			}
			int clientX = Math.addExact(x, border), clientY = Math.addExact(y, border);
			gc.setClipping(gc.getClipping().intersection(new Rectangle(clientX, clientY, client.width, client.height)));
			if (image != null) gc.drawImage(image, clientX, clientY);
			if (!client.isEmpty() && this instanceof Composite composite) for (Control child : composite.getChildren()) {
				child.printTree(gc, Math.addExact(clientX, child.bounds.x), Math.addExact(clientY, child.bounds.y));
			}
		} finally { gc.setBackground(savedBackground); gc.setClipping(savedClip); }
		return true;
	}
	@Override public long internal_new_GC(GCData data) {
		checkWidget(); data.device = display; data.control = this; data.image = ensureImage(); return data.image.internal_handle();
	}
	@Override public void internal_dispose_GC(long handle, GCData data) {
		if (!disposed && paintDepth == 0 && image != null && !image.isDisposed() && isVisible()) display.internal_platform().present(peer, image.internal_handle());
	}
	public void addPaintListener(PaintListener l) { addTypedListener(l, SWT.Paint); }
	public void removePaintListener(PaintListener l) { removeTypedListener(SWT.Paint, l); }
	public void addControlListener(ControlListener l) { addTypedListener(l, SWT.Move, SWT.Resize); }
	public void removeControlListener(ControlListener l) { removeTypedListener(SWT.Move, l); removeTypedListener(SWT.Resize, l); }
	public void addMouseListener(MouseListener l) { addTypedListener(l, SWT.MouseDown, SWT.MouseUp, SWT.MouseDoubleClick); }
	public void removeMouseListener(MouseListener l) { removeTypedListener(SWT.MouseDown, l); removeTypedListener(SWT.MouseUp, l); removeTypedListener(SWT.MouseDoubleClick, l); }
	public void addMouseMoveListener(MouseMoveListener l) { addTypedListener(l, SWT.MouseMove); }
	public void removeMouseMoveListener(MouseMoveListener l) { removeTypedListener(SWT.MouseMove, l); }
	public void addMouseTrackListener(MouseTrackListener l) { addTypedListener(l, SWT.MouseEnter, SWT.MouseExit, SWT.MouseHover); }
	public void removeMouseTrackListener(MouseTrackListener l) { removeTypedListener(SWT.MouseEnter, l); removeTypedListener(SWT.MouseExit, l); removeTypedListener(SWT.MouseHover, l); }
	public void addMouseWheelListener(MouseWheelListener l) { addTypedListener(l, SWT.MouseWheel); }
	public void removeMouseWheelListener(MouseWheelListener l) { removeTypedListener(SWT.MouseWheel, l); }
	public void addKeyListener(KeyListener l) { addTypedListener(l, SWT.KeyDown, SWT.KeyUp); }
	public void removeKeyListener(KeyListener l) { removeTypedListener(SWT.KeyDown, l); removeTypedListener(SWT.KeyUp, l); }
	public void addFocusListener(FocusListener l) { addTypedListener(l, SWT.FocusIn, SWT.FocusOut); }
	public void removeFocusListener(FocusListener l) { removeTypedListener(SWT.FocusIn, l); removeTypedListener(SWT.FocusOut, l); }
	public void addTraverseListener(TraverseListener l) { addTypedListener(l, SWT.Traverse); }
	public void removeTraverseListener(TraverseListener l) { removeTypedListener(SWT.Traverse, l); }
}
