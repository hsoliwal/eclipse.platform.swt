package org.eclipse.swt.tests.junit.m3gc;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.render.m3.gc.DpiScalerUtil;
import org.eclipse.swt.internal.render.m3.gc.IM3Surface;
import org.eclipse.swt.internal.render.m3.gc.IM3CanvasExtension;
import org.eclipse.swt.internal.render.m3.gc.IM3Resources;

public class CanvasExtensionDouble implements IM3CanvasExtension {

	public IM3Surface surface;
	public IM3Resources resources;
	public DpiScalerUtil scaler;
	public Drawable drawable;
	public Rectangle clientArea;
	public Device device;
	public Rectangle bounds;

	@Override
	public IM3Surface getSurface() {
		return surface;
	}

	@Override
	public IM3Resources getResources() {
		return resources;
	}

	@Override
	public IM3Surface createSupportSurface(int width, int height) {
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