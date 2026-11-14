/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/** SWT graphics context backed by one OS drawing context over the owner's image. */
public final class GC extends Resource {
	private Drawable drawable;
	private GCData data;
	private long handle;
	private MobilePlatform.Graphics graphics;
	private Color foreground, background;
	private Font font;
	private int alpha = 255, antialias = SWT.DEFAULT, lineWidth = 1;
	private Rectangle clipping;
	private float[] transform = {1, 0, 0, 1, 0, 0};
	private boolean disposed, advanced = true;

	public GC(Drawable drawable) { this(drawable, SWT.NONE); }
	public GC(Drawable drawable, int style) {
		super(); if (drawable == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		this.drawable = drawable; data = new GCData(); data.style = style;
		handle = drawable.internal_new_GC(data); device = data.device;
		if (device == null || handle == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		boolean acquired = false;
		try {
			device.checkDevice();
			foreground = data.control == null ? device.getSystemColor(SWT.COLOR_BLACK) : data.control.getForeground();
			background = data.control == null ? device.getSystemColor(SWT.COLOR_WHITE) : data.control.getBackground();
			font = data.control == null ? device.getSystemFont() : data.control.getFont();
			if (!data.screen) {
				if (data.image == null) throw new IllegalStateException("Drawable did not provide its native image");
				handle = data.image.acquire(); acquired = true;
				graphics = device.internal_platform().graphics(handle);
				graphics.setForeground(foreground.internal_argb()); graphics.setBackground(background.internal_argb());
				graphics.setAlpha(alpha); graphics.setLineWidth(lineWidth); graphics.setAntialias(true);
				applyFont();
			}
			init();
		} catch (RuntimeException | Error failure) {
			if (graphics != null) try { graphics.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
			if (acquired) try { data.image.release(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
			handle = 0; this.drawable = null; this.device = null; throw failure;
		}
	}
	private void check() { if (isDisposed()) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED); device.checkDevice(); }
	private MobilePlatform.Graphics drawing() { check(); if (graphics == null) throw new UnsupportedOperationException("Screen GCs support capture; mobile OS windows cannot be drawn on directly"); return graphics; }
	@Override void destroy() {
		if (disposed) return;
		disposed = true; Throwable failure = null;
		try { if (graphics != null) graphics.close(); } catch (RuntimeException | Error e) { failure = e; }
		try { if (graphics != null) data.image.release(); } catch (RuntimeException | Error e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
		try { drawable.internal_dispose_GC(handle, data); } catch (RuntimeException | Error e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
		finally { graphics = null; handle = 0; drawable = null; }
		if (failure instanceof RuntimeException e) throw e; if (failure instanceof Error e) throw e;
	}
	@Override public boolean isDisposed() { return disposed || handle == 0 || device == null || device.isDisposed(); }
	public GCData getGCData() { check(); return data; }
	public int getStyle() { check(); return data.style; }
	public Color getForeground() { check(); return foreground; }
	public void setForeground(Color color) { check(); requireColor(color); drawing().setForeground(color.internal_argb()); foreground = color; }
	public Color getBackground() { check(); return background; }
	public void setBackground(Color color) { check(); requireColor(color); drawing().setBackground(color.internal_argb()); background = color; }
	private void requireColor(Color color) { if (color == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (color.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT); }
	public Font getFont() { check(); return font; }
	public void setFont(Font value) {
		check(); if (value == null) value = device.getSystemFont();
		if (value.isDisposed() || value.getDevice() != device) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		font = value; applyFont();
	}
	private void applyFont() { FontData descriptor = font.getFontData()[0]; drawing().setFont(descriptor.getName(), Math.max(1f, descriptor.getHeight() * device.getDPI().y / 72f), descriptor.getStyle()); }
	public FontMetrics getFontMetrics() { return new FontMetrics(drawing().fontMetrics()); }
	public int getAdvanceWidth(char ch) { return stringExtent(String.valueOf(ch)).x; }
	public int getCharWidth(char ch) { return getAdvanceWidth(ch); }
	public int getAlpha() { check(); return alpha; }
	public void setAlpha(int value) { check(); if (value < 0 || value > 255) SWT.error(SWT.ERROR_INVALID_ARGUMENT); drawing().setAlpha(value); alpha = value; }
	public int getAntialias() { check(); return antialias; }
	public void setAntialias(int value) { check(); if (value != SWT.DEFAULT && value != SWT.ON && value != SWT.OFF) SWT.error(SWT.ERROR_INVALID_ARGUMENT); drawing().setAntialias(value != SWT.OFF); antialias = value; }
	public int getLineWidth() { check(); return lineWidth; }
	public void setLineWidth(int width) { check(); if (width < 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT); drawing().setLineWidth(width); lineWidth = width; }
	public boolean getAdvanced() { check(); return advanced; }
	public void setAdvanced(boolean value) { check(); if (!value) { setAlpha(255); setAntialias(SWT.DEFAULT); setTransform(null); setClipping((Rectangle) null); } advanced = value; }
	public void getTransform(Transform target) { check(); if (target == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (target.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT); target.setElements(transform[0],transform[1],transform[2],transform[3],transform[4],transform[5]); }
	public void setTransform(Transform value) { check(); float[] next = {1,0,0,1,0,0}; if (value != null) { if (value.isDisposed() || value.getDevice() != device) SWT.error(SWT.ERROR_INVALID_ARGUMENT); value.getElements(next); } drawing().setTransform(next); transform = next; }
	public Rectangle getClipping() { check(); Rectangle b = clipping == null ? data.screen ? device.getBounds() : data.image.getBounds() : clipping; return new Rectangle(b.x,b.y,b.width,b.height); }
	public boolean isClipped() { check(); return clipping != null; }
	public void setClipping(int x, int y, int width, int height) { setClipping(new Rectangle(x,y,width,height)); }
	public void setClipping(Rectangle rectangle) {
		check(); if (rectangle == null) { drawing().resetClipping(); clipping = null; return; }
		Rectangle r = normalized(rectangle.x,rectangle.y,rectangle.width,rectangle.height);
		drawing().setClipping(r.x,r.y,r.width,r.height); clipping = r;
	}
	private static Rectangle normalized(int x,int y,int width,int height) {
		if (width < 0) { x=Math.addExact(x,width); width=Math.negateExact(width); }
		if (height < 0) { y=Math.addExact(y,height); height=Math.negateExact(height); }
		return new Rectangle(x,y,width,height);
	}
	public void drawPoint(int x,int y) { drawing().drawLine(x,y,x,y); }
	public void drawLine(int x1,int y1,int x2,int y2) { drawing().drawLine(x1,y1,x2,y2); }
	public void drawRectangle(Rectangle r) { if(r==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); drawRectangle(r.x,r.y,r.width,r.height); }
	public void drawRectangle(int x,int y,int width,int height) { Rectangle r=normalized(x,y,width,height); drawing().drawRectangle(r.x,r.y,r.width,r.height); }
	public void fillRectangle(Rectangle r) { if(r==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); fillRectangle(r.x,r.y,r.width,r.height); }
	public void fillRectangle(int x,int y,int width,int height) { Rectangle r=normalized(x,y,width,height); drawing().fillRectangle(r.x,r.y,r.width,r.height); }
	public void drawOval(int x,int y,int width,int height) { Rectangle r=normalized(x,y,width,height); drawing().drawOval(r.x,r.y,r.width,r.height); }
	public void fillOval(int x,int y,int width,int height) { Rectangle r=normalized(x,y,width,height); drawing().fillOval(r.x,r.y,r.width,r.height); }
	public void drawPolygon(int[] points) { polygon(points,false); }
	public void fillPolygon(int[] points) { polygon(points,true); }
	private void polygon(int[] points,boolean fill) { check(); if(points==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if(points.length<4)return; drawing().drawPolygon(Arrays.copyOf(points,points.length&~1),fill); }
	public void drawPolyline(int[] points) { check(); if(points==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); for(int i=2;i+1<points.length;i+=2) drawing().drawLine(points[i-2],points[i-1],points[i],points[i+1]); }
	public void drawString(String text,int x,int y) { drawString(text,x,y,false); }
	public void drawString(String text,int x,int y,boolean transparent) { drawText(text,x,y,transparent?SWT.DRAW_TRANSPARENT:0); }
	public void drawText(String text,int x,int y) { drawText(text,x,y,SWT.DRAW_DELIMITER|SWT.DRAW_TAB); }
	public void drawText(String text,int x,int y,boolean transparent) { drawText(text,x,y,SWT.DRAW_DELIMITER|SWT.DRAW_TAB|(transparent?SWT.DRAW_TRANSPARENT:0)); }
	public void drawText(String text,int x,int y,int flags) { check(); if(text==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); drawing().drawText(text,x,y,flags); }
	public Point stringExtent(String text) { return textExtent(text,0); }
	public Point textExtent(String text) { return textExtent(text,SWT.DRAW_DELIMITER|SWT.DRAW_TAB); }
	public Point textExtent(String text,int flags) { check(); if(text==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); int[] e=drawing().textExtent(text,flags); return new Point(e[0],e[1]); }
	public void drawImage(Image image,int x,int y) { checkImage(image); Rectangle b=image.getBounds(); drawImage(image,0,0,b.width,b.height,x,y,b.width,b.height); }
	public void drawImage(Image image,int x,int y,int width,int height) {
		checkImage(image); if(width<0||height<0) SWT.error(SWT.ERROR_INVALID_ARGUMENT); if(width==0||height==0)return;
		Image sized=image.atSize(width,height);
		try { Image source=sized==null?image:sized; Rectangle b=source.getBounds(); drawImage(source,0,0,b.width,b.height,x,y,width,height); } finally { if(sized!=null)sized.dispose(); }
	}
	public void drawImage(Image image,int srcX,int srcY,int srcWidth,int srcHeight,int destX,int destY,int destWidth,int destHeight) {
		checkImage(image); Rectangle b=image.getBounds();
		if(srcX<0||srcY<0||srcWidth<0||srcHeight<0||destWidth<0||destHeight<0||(long)srcX+srcWidth>b.width||(long)srcY+srcHeight>b.height) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		if(srcWidth==0||srcHeight==0||destWidth==0||destHeight==0)return;
		drawing().drawImage(image.internal_handle(),srcX,srcY,srcWidth,srcHeight,destX,destY,destWidth,destHeight);
	}
	private void checkImage(Image image) { check(); if(image==null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if(image.isDisposed()||image.getDevice()!=device) SWT.error(SWT.ERROR_INVALID_ARGUMENT); }
	/** Capture uses the native compositor only for a Display GC; an Image GC copies its image. */
	public void copyArea(Image image,int x,int y) {
		checkImage(image); Rectangle target=image.getBounds(); int[] pixels;
		if(data.screen) pixels=device.internal_platform().captureScreen(x,y,target.width,target.height);
		else {
			Rectangle source=data.image.getBounds();
			if(x<0||y<0||(long)x+target.width>source.width||(long)y+target.height>source.height) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			int[] original=device.internal_platform().imagePixels(handle); pixels=new int[Math.multiplyExact(target.width,target.height)];
			for(int row=0;row<target.height;row++)System.arraycopy(original,(y+row)*source.width+x,pixels,row*target.width,target.width);
		}
		image.replacePixels(pixels);
	}
	@Override public String toString() { return isDisposed()?"GC {*DISPOSED*}":"GC {"+handle+"}"; }
}
