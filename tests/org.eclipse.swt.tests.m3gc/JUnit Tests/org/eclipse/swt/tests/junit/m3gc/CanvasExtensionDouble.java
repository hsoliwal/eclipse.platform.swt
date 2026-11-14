package org.eclipse.swt.tests.junit.m3gc;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.canvasext.raster.DpiScalerUtil;
import org.eclipse.swt.internal.canvasext.raster.IRasterCanvasExtension;
import org.eclipse.swt.internal.canvasext.raster.IRasterResources;
import org.eclipse.swt.internal.canvasext.raster.IRasterSurface;

public class CanvasExtensionDouble implements IRasterCanvasExtension {

	public IRasterSurface surface;
	public IRasterResources resources;
	public DpiScalerUtil scaler;
	public Drawable drawable;
	public Rectangle clientArea;
	public Device device;
	public Rectangle bounds;

	@Override
	public IRasterSurface getSurface() {
		return surface;
	}

	@Override
	public IRasterResources getResources() {
		return resources;
	}

	@Override
	public IRasterSurface createSupportSurface(int width, int height) {
		return surface.makeSurface(width, height);
	}

	@Override
	public DpiScalerUtil getScaler() {
		return scaler;
	}

	@Override
	public Drawable getDrawable() {
		return drawable;
	}

	@Override
	public Rectangle getClientArea() {
		return clientArea;
	}

	@Override
	public Device getDevice() {
		return device;
	}

	@Override
	public Rectangle getBounds() {
		return bounds;
	}

	@Override
	public void redraw(int srcX, int srcY, int width, int height, boolean b) {
		// TODO Auto-generated method stub

	}

}