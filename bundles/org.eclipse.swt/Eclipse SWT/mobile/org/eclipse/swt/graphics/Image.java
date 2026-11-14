/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import java.io.InputStream;
import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/** Native offscreen image. Selecting it into a GC retains its handle until GC disposal. */
public final class Image extends Resource implements Drawable {
	private final MobilePlatform platform;
	private long handle;
	private int width, height, leases;
	private boolean disposalRequested;
	private ImageDataProvider provider;
	private Color background;

	public Image(Device device, int width, int height) {
		super(device); platform = this.device.internal_platform();
		if (width <= 0 || height <= 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		int[] pixels = new int[Math.multiplyExact(width, height)]; Arrays.fill(pixels, 0xffffffff);
		create(width, height, pixels);
	}
	public Image(Device device, Rectangle bounds) { this(device, requireBounds(bounds).width, bounds.height); }
	private static Rectangle requireBounds(Rectangle b) { if (b == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); return b; }
	public Image(Device device, ImageData data) {
		super(device); platform = this.device.internal_platform();
		if (data == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		create(data.width, data.height, pixels(data));
	}
	public Image(Device device, ImageData source, ImageData mask) { this(device, masked(source, mask)); }
	private static ImageData masked(ImageData source, ImageData mask) {
		if (source == null || mask == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (source.width != mask.width || source.height != mask.height) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		ImageData result = (ImageData) source.clone(); result.maskData = null; result.transparentPixel = -1; result.alpha = -1;
		result.alphaData = new byte[Math.multiplyExact(source.width, source.height)];
		for (int y = 0; y < source.height; y++) for (int x = 0; x < source.width; x++) result.setAlpha(x, y, mask.getPixel(x, y) == 0 ? 0 : 255);
		return result;
	}
	public Image(Device device, InputStream stream) { this(device, new ImageData(stream)); }
	public Image(Device device, String filename) { this(device, new ImageData(filename)); }
	public Image(Device device, ImageDataProvider provider) { this(device, initial(provider)); this.provider = provider; }
	private static ImageData initial(ImageDataProvider provider) {
		if (provider == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		ImageData data = provider.getImageData(100); if (data == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT); return data;
	}
	public Image(Device device, ImageFileNameProvider provider) { this(device, initial(provider)); }
	private static ImageData initial(ImageFileNameProvider provider) {
		if (provider == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		String name = provider.getImagePath(100); if (name == null) SWT.error(SWT.ERROR_INVALID_ARGUMENT); return new ImageData(name);
	}
	public Image(Device device, ImageGcDrawer drawer, int width, int height) {
		this(device, width, height);
		if (drawer == null) { dispose(); SWT.error(SWT.ERROR_NULL_ARGUMENT); }
		try {
			if ((drawer.getGcStyle() & SWT.TRANSPARENT) != 0) replacePixels(new int[Math.multiplyExact(width, height)]);
			GC gc = new GC(this, drawer.getGcStyle());
			try { drawer.drawOn(gc, width, height); } finally { gc.dispose(); }
			ImageData result = getImageData(); drawer.postProcess(result); replacePixels(pixels(result));
		} catch (RuntimeException | Error failure) { dispose(); throw failure; }
	}
	public Image(Device device, Image source, int flag) { this(device, transformed(source, flag)); }
	private static ImageData transformed(Image source, int flag) {
		if (source == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (source.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		ImageData result = source.getImageData();
		if (flag == SWT.IMAGE_COPY) return result;
		if (flag != SWT.IMAGE_GRAY && flag != SWT.IMAGE_DISABLE) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		for (int y = 0; y < result.height; y++) for (int x = 0; x < result.width; x++) {
			RGB rgb = result.palette.getRGB(result.getPixel(x, y)); int gray = (rgb.red * 2 + rgb.green * 5 + rgb.blue) / 8;
			if (flag == SWT.IMAGE_DISABLE) gray = (gray + 255) / 2;
			result.setPixel(x, y, gray << 16 | gray << 8 | gray);
		}
		return result;
	}
	private void create(int width, int height, int[] pixels) {
		this.width = width; this.height = height;
		handle = platform.createImage(width, height, pixels);
		if (handle == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		init();
	}
	private static int[] pixels(ImageData source) {
		int[] pixels = new int[Math.multiplyExact(source.width, source.height)];
		ImageData mask = source.maskData != null ? source.getTransparencyMask() : null;
		for (int y = 0; y < source.height; y++) for (int x = 0; x < source.width; x++) {
			int pixel = source.getPixel(x, y); RGB rgb = source.palette.getRGB(pixel);
			int alpha = source.alpha >= 0 ? source.alpha : source.getAlpha(x, y);
			if (pixel == source.transparentPixel || mask != null && mask.getPixel(x, y) == 0) alpha = 0;
			pixels[y * source.width + x] = alpha << 24 | rgb.red << 16 | rgb.green << 8 | rgb.blue;
		}
		return pixels;
	}
	private void checkImage() { if (isDisposed()) SWT.error(SWT.ERROR_GRAPHIC_DISPOSED); device.checkDevice(); }
	public Rectangle getBounds() { checkImage(); return new Rectangle(0, 0, width, height); }
	public Rectangle getBoundsInPixels() { return getBounds(); }
	public ImageData getImageData() {
		checkImage(); int[] pixels = platform.imagePixels(handle);
		if (pixels.length != Math.multiplyExact(width, height)) throw new IllegalStateException("Native image pixel count changed");
		ImageData data = new ImageData(width, height, 32, new PaletteData(0xff0000, 0x00ff00, 0x0000ff));
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) { int p = pixels[y * width + x]; data.setPixel(x, y, p & 0xffffff); data.setAlpha(x, y, p >>> 24); }
		return data;
	}
	public ImageData getImageData(int zoom) {
		checkImage(); if (zoom <= 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		if (zoom == 100) return getImageData();
		if (provider != null) { ImageData requested = provider.getImageData(zoom); if (requested != null) return (ImageData) requested.clone(); }
		return getImageData().scaledTo(Math.max(1, Math.round(width * (zoom / 100f))), Math.max(1, Math.round(height * (zoom / 100f))));
	}
	public ImageData getImageDataAtCurrentZoom() { return getImageData(); }
	public Color getBackground() { checkImage(); return background; }
	public void setBackground(Color color) {
		checkImage(); if (color == null) SWT.error(SWT.ERROR_NULL_ARGUMENT); if (color.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		int[] p = platform.imagePixels(handle); int rgb = color.internal_argb() & 0xffffff;
		for (int i = 0; i < p.length; i++) if ((p[i] >>> 24) == 0) p[i] = rgb;
		replacePixels(p); background = color;
	}
	/** Internal API shared by platform SWT classes; no application ownership is transferred. */
	public long internal_handle() { checkImage(); return handle; }
	long acquire() {
		checkImage(); if (leases != 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		leases++; return handle;
	}
	void release() {
		if (leases <= 0) throw new IllegalStateException("Unbalanced native image lease");
		if (--leases == 0 && disposalRequested) destroyHandle();
	}
	void replacePixels(int[] pixels) {
		checkImage(); if (pixels == null || pixels.length != Math.multiplyExact(width, height)) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		if (leases != 0) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		long replacement = platform.createImage(width, height, pixels); if (replacement == 0) SWT.error(SWT.ERROR_NO_HANDLES);
		long old = handle; handle = replacement; platform.disposeImage(old);
	}
	Image atSize(int width, int height) {
		checkImage(); if (!(provider instanceof ImageDataAtSizeProvider atSize)) return null;
		ImageData data = atSize.getImageData(width, height);
		if (data == null || data.width != width || data.height != height) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		return new Image(device, data);
	}
	public static void drawAtSize(GC gc, ImageData data, int width, int height) {
		if (gc == null || data == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		Image image = new Image(gc.getDevice(), data);
		try { gc.drawImage(image, 0, 0, data.width, data.height, 0, 0, width, height); } finally { image.dispose(); }
	}
	@Override void destroy() { disposalRequested = true; if (leases == 0) destroyHandle(); }
	private void destroyHandle() { long old = handle; handle = 0; if (old != 0) platform.disposeImage(old); }
	public void internal_disposeWhenUnused() { dispose(); }
	@Override public boolean isDisposed() { return disposalRequested || handle == 0 || device == null || device.isDisposed(); }
	@Override public long internal_new_GC(GCData data) { checkImage(); data.device = device; data.image = this; return handle; }
	@Override public void internal_dispose_GC(long handle, GCData data) { }
	@Override public String toString() { return isDisposed() ? "Image {*DISPOSED*}" : "Image {" + width + "x" + height + "}"; }
}
