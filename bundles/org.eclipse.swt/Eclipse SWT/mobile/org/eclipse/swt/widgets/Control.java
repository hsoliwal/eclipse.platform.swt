/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.mobile.MobileFrame;

import java.util.concurrent.atomic.AtomicLong;

/** Mobile Control; parent primitive lanes own bounds and visibility/enabled bits. */
public abstract class Control extends Widget implements Drawable {
	static final short VISIBLE = 1, ENABLED = 2;
	private static final AtomicLong GC_IDS = new AtomicLong();
	Composite parent;
	int slot = -1;
	int rootX, rootY, rootWidth, rootHeight;
	short rootFlags = VISIBLE | ENABLED;
	private Color foreground, background;
	private Font font;
	private Object layoutData;
	private String toolTipText;
	private int redrawDepth;
	private boolean dirty = true;
	private MobileFrame content;
	private MobileFrame.Builder painting, direct = new MobileFrame.Builder();

	Control(Composite parent, int style) {
		super(parent, style);
		this.parent = parent;
		slot = parent.attach(this);
	}

	Control(Display display, int style) {
		super(display, style);
	}

	int x() {
		return parent == null ? rootX : parent.xs[slot];
	}

	int y() {
		return parent == null ? rootY : parent.ys[slot];
	}

	int width() {
		return parent == null ? rootWidth : parent.widths[slot];
	}

	int height() {
		return parent == null ? rootHeight : parent.heights[slot];
	}

	short flags() {
		return parent == null ? rootFlags : parent.flags[slot];
	}

	void flags(short f) {
		if (parent == null) rootFlags = f;
		else parent.flags[slot] = f;
	}

	public Composite getParent() {
		checkWidget();
		return parent;
	}

	public Shell getShell() {
		checkWidget();
		Control c = this;
		while (c.parent != null) c = c.parent;
		return (Shell) c;
	}

	public Rectangle getBounds() {
		checkWidget();
		return new Rectangle(x(), y(), width(), height());
	}

	public Point getSize() {
		checkWidget();
		return new Point(width(), height());
	}

	public Point getLocation() {
		checkWidget();
		return new Point(x(), y());
	}

	public void setBounds(Rectangle r) {
		if (r == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		setBounds(r.x, r.y, r.width, r.height);
	}

	public void setBounds(int x, int y, int w, int h) {
		checkWidget();
		w = Math.max(0, w);
		h = Math.max(0, h);
		boolean moved = x() != x || y() != y, resized = width() != w || height() != h;
		if (!moved && !resized) return;
		if (parent == null) {
			rootX = x;
			rootY = y;
			rootWidth = w;
			rootHeight = h;
		} else {
			parent.xs[slot] = x;
			parent.ys[slot] = y;
			parent.widths[slot] = w;
			parent.heights[slot] = h;
		}
		redraw();
		if (moved) notifyListeners(SWT.Move, new Event());
		if (!isDisposed() && resized) notifyListeners(SWT.Resize, new Event());
	}

	public void setSize(int w, int h) {
		setBounds(x(), y(), w, h);
	}

	public void setSize(Point p) {
		if (p == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		setSize(p.x, p.y);
	}

	public void setLocation(int x, int y) {
		setBounds(x, y, width(), height());
	}

	public void setLocation(Point p) {
		if (p == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		setLocation(p.x, p.y);
	}

	public int getBorderWidth() {
		checkWidget();
		return (style & SWT.BORDER) != 0 ? 1 : 0;
	}

	public boolean getVisible() {
		checkWidget();
		return (flags() & VISIBLE) != 0;
	}

	public boolean isVisible() {
		checkWidget();
		return getVisible() && (parent == null || parent.isVisible());
	}

	public void setVisible(boolean visible) {
		checkWidget();
		if (getVisible() == visible) return;
		flags((short) (visible ? flags() | VISIBLE : flags() & ~VISIBLE));
		notifyListeners(visible ? SWT.Show : SWT.Hide, new Event());
		if (!isDisposed()) {
			redraw();
			if (!visible) display.clearFocus(this);
		}
	}

	public boolean getEnabled() {
		checkWidget();
		return (flags() & ENABLED) != 0;
	}

	public boolean isEnabled() {
		checkWidget();
		return getEnabled() && (parent == null || parent.isEnabled());
	}

	public void setEnabled(boolean enabled) {
		checkWidget();
		flags((short) (enabled ? flags() | ENABLED : flags() & ~ENABLED));
		if (!enabled) display.clearFocus(this);
		if (!isDisposed()) redraw();
	}

	public Color getForeground() {
		checkWidget();
		return foreground != null
				? foreground
				: parent != null
						? parent.getForeground()
						: display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
	}

	public Color getBackground() {
		checkWidget();
		return background != null
				? background
				: parent != null
						? parent.getBackground()
						: display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
	}

	public void setForeground(Color value) {
		checkWidget();
		if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		foreground = value;
		redraw();
	}

	public void setBackground(Color value) {
		checkWidget();
		if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		background = value;
		redraw();
	}

	public Font getFont() {
		checkWidget();
		return font != null ? font : parent != null ? parent.getFont() : display.getSystemFont();
	}

	public void setFont(Font value) {
		checkWidget();
		if (value != null && value.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		font = value;
		redraw();
	}

	public Object getLayoutData() {
		checkWidget();
		return layoutData;
	}

	public void setLayoutData(Object value) {
		checkWidget();
		layoutData = value;
	}

	public String getToolTipText() {
		checkWidget();
		return toolTipText;
	}

	public void setToolTipText(String value) {
		checkWidget();
		toolTipText = value;
	}

	public Point computeSize(int wHint, int hHint) {
		return computeSize(wHint, hHint, true);
	}

	public Point computeSize(int wHint, int hHint, boolean changed) {
		checkWidget();
		int b = getBorderWidth() * 2;
		return new Point(
				wHint == SWT.DEFAULT ? 64 + b : wHint + b,
				hHint == SWT.DEFAULT ? 64 + b : hHint + b);
	}

	public void pack() {
		pack(true);
	}

	public void pack(boolean changed) {
		Point p = computeSize(SWT.DEFAULT, SWT.DEFAULT, changed);
		setSize(p);
	}

	public boolean setFocus() {
		checkWidget();
		return display.setFocus(this);
	}

	public boolean forceFocus() {
		return setFocus();
	}

	public boolean isFocusControl() {
		checkWidget();
		return display.getFocusControl() == this;
	}

	public Point toDisplay(int x, int y) {
		checkWidget();
		Control c = this;
		while (c != null) {
			x += c.x() + c.getBorderWidth();
			y += c.y() + c.getBorderWidth();
			c = c.parent;
		}
		return new Point(x, y);
	}

	public Point toDisplay(Point p) {
		if (p == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return toDisplay(p.x, p.y);
	}

	public Point toControl(int x, int y) {
		Point p = toDisplay(0, 0);
		return new Point(x - p.x, y - p.y);
	}

	public Point toControl(Point p) {
		if (p == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return toControl(p.x, p.y);
	}

	public void redraw() {
		checkWidget();
		dirty = true;
		direct = new MobileFrame.Builder();
		display.requestFrame();
	}

	public void redraw(int x, int y, int width, int height, boolean all) {
		checkWidget();
		if (width <= 0 || height <= 0) return;
		redraw();
		if (all && this instanceof Composite c)
			for (Control child : c.childrenSnapshot()) if (!child.isDisposed()) child.redraw();
	}

	public void update() {
		checkWidget();
		display.update();
	}

	public void setRedraw(boolean redraw) {
		checkWidget();
		if (!redraw) {
			redrawDepth++;
		} else if (redrawDepth > 0 && --redrawDepth == 0) {
			redraw();
		}
	}

	boolean descendantOf(Control c) {
		for (Control p = this; p != null; p = p.parent) if (p == c) return true;
		return false;
	}

	Control hit(int px, int py) {
		if (isDisposed()
				|| !getVisible()
				|| !getEnabled()
				|| px < 0
				|| py < 0
				|| px >= width()
				|| py >= height()) return null;
		return this;
	}

	void paint(MobileFrame.Builder frame) {
		if (isDisposed() || !getVisible() || width() <= 0 || height() <= 0) return;
		int border = getBorderWidth(),
				clientWidth = Math.max(0, width() - 2 * border),
				clientHeight = Math.max(0, height() - 2 * border);
		if (dirty && redrawDepth == 0) {
			dirty = false;
			MobileFrame.Builder local = new MobileFrame.Builder();
			painting = local;
			try {
				if ((style & SWT.NO_BACKGROUND) == 0)
					local.shape(
							MobileFrame.FILL_RECT,
							getBackground().internalArgb(),
							0,
							0,
							0,
							clientWidth,
							clientHeight,
							0,
							0);
				Event event = new Event();
				event.x = 0;
				event.y = 0;
				event.width = clientWidth;
				event.height = clientHeight;
				try (GCLease lease = new GCLease(new GC(this))) {
					event.gc = lease.gc;
					notifyListeners(SWT.Paint, event);
				}
				if (!isDisposed()) content = local.build(clientWidth, clientHeight);
			} finally {
				painting = null;
			}
		}
		if (isDisposed()) return;
		frame.save();
		frame.translate(x(), y());
		frame.clip(0, 0, width(), height());
		if (border != 0) {
			frame.shape(
					MobileFrame.DRAW_RECT,
					getForeground().internalArgb(),
					1,
					0.5f,
					0.5f,
					width() - 1,
					height() - 1,
					0,
					0);
			frame.translate(border, border);
		}
		frame.clip(0, 0, clientWidth, clientHeight);
		if (content != null) frame.append(content);
		frame.append(direct.build(clientWidth, clientHeight));
		if (this instanceof Composite c)
			for (Control child : c.childrenSnapshot()) if (!child.isDisposed()) child.paint(frame);
		frame.restore();
	}

	private static final class GCLease implements AutoCloseable {
		final GC gc;

		GCLease(GC gc) {
			this.gc = gc;
		}

		public void close() {
			gc.dispose();
		}
	}

	@Override
	public long internal_new_GC(GCData data) {
		checkWidget();
		data.device = display;
		data.foreground = getForeground();
		data.background = getBackground();
		data.font = getFont();
		int border = getBorderWidth();
		data.clipping =
				new Rectangle(
						0,
						0,
						Math.max(0, width() - 2 * border),
						Math.max(0, height() - 2 * border));
		data.check = this::checkWidget;
		data.commands = () -> painting != null ? painting : direct;
		data.changed =
				() -> {
					if (painting == null) display.requestFrame();
				};
		return GC_IDS.incrementAndGet();
	}

	@Override
	public void internal_dispose_GC(long handle, GCData data) {}

	@Override
	void releaseWidget() {
		try {
			display.clearFocus(this);
		} finally {
			if (parent != null) parent.detach(this);
			content = null;
			direct = new MobileFrame.Builder();
			display.requestFrame();
		}
	}

	public void addPaintListener(PaintListener l) {
		addTypedListener(l, SWT.Paint);
	}

	public void removePaintListener(PaintListener l) {
		removeTypedListener(SWT.Paint, l);
	}

	public void addMouseListener(MouseListener l) {
		addTypedListener(l, SWT.MouseDown, SWT.MouseUp, SWT.MouseDoubleClick);
	}

	public void removeMouseListener(MouseListener l) {
		removeTypedListener(SWT.MouseDown, l);
		removeTypedListener(SWT.MouseUp, l);
		removeTypedListener(SWT.MouseDoubleClick, l);
	}

	public void addMouseMoveListener(MouseMoveListener l) {
		addTypedListener(l, SWT.MouseMove);
	}

	public void removeMouseMoveListener(MouseMoveListener l) {
		removeTypedListener(SWT.MouseMove, l);
	}

	public void addMouseTrackListener(MouseTrackListener l) {
		addTypedListener(l, SWT.MouseEnter, SWT.MouseExit, SWT.MouseHover);
	}

	public void removeMouseTrackListener(MouseTrackListener l) {
		removeTypedListener(SWT.MouseEnter, l);
		removeTypedListener(SWT.MouseExit, l);
		removeTypedListener(SWT.MouseHover, l);
	}

	public void addMouseWheelListener(MouseWheelListener l) {
		addTypedListener(l, SWT.MouseWheel);
	}

	public void removeMouseWheelListener(MouseWheelListener l) {
		removeTypedListener(SWT.MouseWheel, l);
	}

	public void addKeyListener(KeyListener l) {
		addTypedListener(l, SWT.KeyDown, SWT.KeyUp);
	}

	public void removeKeyListener(KeyListener l) {
		removeTypedListener(SWT.KeyDown, l);
		removeTypedListener(SWT.KeyUp, l);
	}

	public void addFocusListener(FocusListener l) {
		addTypedListener(l, SWT.FocusIn, SWT.FocusOut);
	}

	public void removeFocusListener(FocusListener l) {
		removeTypedListener(SWT.FocusIn, l);
		removeTypedListener(SWT.FocusOut, l);
	}

	public void addControlListener(ControlListener l) {
		addTypedListener(l, SWT.Move, SWT.Resize);
	}

	public void removeControlListener(ControlListener l) {
		removeTypedListener(SWT.Move, l);
		removeTypedListener(SWT.Resize, l);
	}
}
