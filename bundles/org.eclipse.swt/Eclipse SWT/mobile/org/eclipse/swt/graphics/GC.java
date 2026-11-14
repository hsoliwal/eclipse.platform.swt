/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.mobile.MobileFrame;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/** Canvas drawing recorded by SWT and presented by the actual Android/iOS backend. */
public final class GC extends Resource {
	private Drawable drawable;
	private GCData data;
	public long handle;
	private int alpha = 255,
			lineWidth = 1,
			antialias = SWT.DEFAULT,
			textAntialias = SWT.DEFAULT,
			style;
	private boolean advanced;
	private Rectangle explicitClip;

	public GC(Drawable drawable) {
		this(drawable, SWT.NONE);
	}

	public GC(Drawable drawable, int style) {
		if (drawable == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		this.drawable = drawable;
		this.style = style;
		data = new GCData();
		handle = drawable.internal_new_GC(data);
		device = data.device;
		if (handle == 0 || device == null) SWT.error(SWT.ERROR_NO_HANDLES);
		init();
	}

	private void check() {
		if (handle == 0 || device == null || device.isDisposed())
			SWT.error(SWT.ERROR_GRAPHIC_DISPOSED);
		data.check.run();
	}

	private int rgba(Color color) {
		return color.internalArgb() & 0xffffff | ((color.getAlpha() * alpha + 127) / 255) << 24;
	}

	private void shape(int op, Color color, int x, int y, int w, int h, int a, int b) {
		check();
		MobileFrame.Builder commands = begin(antialias);
		commands.shape(op, rgba(color), lineWidth, x, y, w, h, a, b);
		end(commands);
	}

	private MobileFrame.Builder begin(int mode) {
		MobileFrame.Builder commands = data.commands.get();
		commands.setAntialias(mode);
		commands.save();
		if (explicitClip != null)
			commands.clip(explicitClip.x, explicitClip.y, explicitClip.width, explicitClip.height);
		return commands;
	}

	private void end(MobileFrame.Builder commands) {
		commands.restore();
		data.changed.run();
	}

	public void drawLine(int x1, int y1, int x2, int y2) {
		shape(MobileFrame.DRAW_LINE, data.foreground, x1, y1, x2, y2, 0, 0);
	}

	public void drawRectangle(int x, int y, int w, int h) {
		shape(MobileFrame.DRAW_RECT, data.foreground, x, y, w, h, 0, 0);
	}

	public void drawRectangle(Rectangle r) {
		if (r == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		drawRectangle(r.x, r.y, r.width, r.height);
	}

	public void fillRectangle(int x, int y, int w, int h) {
		shape(MobileFrame.FILL_RECT, data.background, x, y, w, h, 0, 0);
	}

	public void fillRectangle(Rectangle r) {
		if (r == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		fillRectangle(r.x, r.y, r.width, r.height);
	}

	public void drawOval(int x, int y, int w, int h) {
		shape(MobileFrame.DRAW_OVAL, data.foreground, x, y, w, h, 0, 0);
	}

	public void fillOval(int x, int y, int w, int h) {
		shape(MobileFrame.FILL_OVAL, data.background, x, y, w, h, 0, 0);
	}

	public void drawRoundRectangle(int x, int y, int w, int h, int aw, int ah) {
		shape(MobileFrame.DRAW_ROUND_RECT, data.foreground, x, y, w, h, aw, ah);
	}

	public void fillRoundRectangle(int x, int y, int w, int h, int aw, int ah) {
		shape(MobileFrame.FILL_ROUND_RECT, data.background, x, y, w, h, aw, ah);
	}

	public void drawArc(int x, int y, int w, int h, int start, int sweep) {
		shape(MobileFrame.DRAW_ARC, data.foreground, x, y, w, h, start, sweep);
	}

	public void fillArc(int x, int y, int w, int h, int start, int sweep) {
		shape(MobileFrame.FILL_ARC, data.background, x, y, w, h, start, sweep);
	}

	public void drawPoint(int x, int y) {
		shape(MobileFrame.FILL_RECT, data.foreground, x, y, 1, 1, 0, 0);
	}

	public Color getForeground() {
		check();
		return data.foreground;
	}

	public Color getBackground() {
		check();
		return data.background;
	}

	public void setForeground(Color color) {
		checkColor(color);
		data.foreground = color;
	}

	public void setBackground(Color color) {
		checkColor(color);
		data.background = color;
	}

	private void checkColor(Color color) {
		check();
		if (color == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (color.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}

	public int getAlpha() {
		check();
		return alpha;
	}

	public void setAlpha(int value) {
		check();
		alpha = value & 255;
	}

	public int getLineWidth() {
		check();
		return lineWidth;
	}

	public void setLineWidth(int value) {
		check();
		if (value < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		lineWidth = value;
	}

	public int getAntialias() {
		check();
		return antialias;
	}

	public void setAntialias(int value) {
		check();
		validateMode(value);
		antialias = value;
	}

	public int getTextAntialias() {
		check();
		return textAntialias;
	}

	public void setTextAntialias(int value) {
		check();
		validateMode(value);
		textAntialias = value;
	}

	private static void validateMode(int value) {
		if (value != SWT.DEFAULT && value != SWT.ON && value != SWT.OFF)
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	}

	public boolean getAdvanced() {
		check();
		return advanced;
	}

	public void setAdvanced(boolean value) {
		check();
		advanced = value;
	}

	public int getStyle() {
		check();
		return style;
	}

	public Font getFont() {
		check();
		return data.font;
	}

	public void setFont(Font font) {
		check();
		if (font != null && font.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		data.font = font == null ? device.getSystemFont() : font;
	}

	public Rectangle getClipping() {
		check();
		Rectangle r =
				explicitClip == null ? data.clipping : explicitClip.intersection(data.clipping);
		return new Rectangle(r.x, r.y, r.width, r.height);
	}

	public boolean isClipped() {
		check();
		return explicitClip != null;
	}

	public void setClipping(Rectangle clip) {
		check();
		explicitClip =
				clip == null
						? null
						: new Rectangle(
								clip.x, clip.y, Math.max(0, clip.width), Math.max(0, clip.height));
	}

	public void setClipping(int x, int y, int w, int h) {
		setClipping(new Rectangle(x, y, w, h));
	}

	public Point stringExtent(String value) {
		return textExtent(value, SWT.NONE);
	}

	public Point textExtent(String value) {
		return textExtent(value, SWT.DRAW_DELIMITER | SWT.DRAW_TAB);
	}

	public Point textExtent(String value, int flags) {
		check();
		if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		String text = prepare(value, flags);
		FontData f = data.font.getFontData()[0];
		int[] result =
				MobilePlatform.current()
						.measureText(
								text, f.getName(), data.font.internalPixelSize(), f.getStyle());
		return new Point(result[0], result[1]);
	}

	public void drawString(String text, int x, int y) {
		drawString(text, x, y, false);
	}

	public void drawString(String text, int x, int y, boolean transparent) {
		drawText(text, x, y, transparent ? SWT.DRAW_TRANSPARENT : SWT.NONE);
	}

	public void drawText(String text, int x, int y) {
		drawText(text, x, y, SWT.DRAW_DELIMITER | SWT.DRAW_TAB);
	}

	public void drawText(String text, int x, int y, boolean transparent) {
		drawText(
				text,
				x,
				y,
				SWT.DRAW_DELIMITER | SWT.DRAW_TAB | (transparent ? SWT.DRAW_TRANSPARENT : 0));
	}

	public void drawText(String text, int x, int y, int flags) {
		check();
		if (text == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		String prepared = prepare(text, flags);
		if ((flags & SWT.DRAW_TRANSPARENT) == 0) {
			Point p = textExtent(text, flags);
			fillRectangle(x, y, p.x, p.y);
		}
		FontData f = data.font.getFontData()[0];
		MobileFrame.Builder commands = begin(textAntialias);
		commands.text(
				prepared,
				x,
				y,
				rgba(data.foreground),
				f.getName(),
				data.font.internalPixelSize(),
				f.getStyle());
		end(commands);
	}

	private static String prepare(String value, int flags) {
		String text =
				(flags & SWT.DRAW_DELIMITER) != 0
						? value.replace("\r\n", "\n").replace('\r', '\n')
						: value.replace('\r', ' ').replace('\n', ' ');
		return (flags & SWT.DRAW_TAB) != 0 ? text.replace("\t", "        ") : text;
	}

	@Override
	void destroy() {
		if (handle != 0) {
			long h = handle;
			handle = 0;
			drawable.internal_dispose_GC(h, data);
		}
	}

	@Override
	public boolean isDisposed() {
		return handle == 0 || device == null || device.isDisposed();
	}

	@Override
	public String toString() {
		return isDisposed() ? "GC {*DISPOSED*}" : "GC {" + handle + "}";
	}
}
