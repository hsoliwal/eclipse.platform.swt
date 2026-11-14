package org.eclipse.swt.internal.canvasext.raster;

import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.IRect;

/**
 * Adapter to wrap Skija's Surface as IRasterSurface.
 */
class RasterSurfaceAdapter implements IRasterSurface {
	private final Surface delegate;

	public RasterSurfaceAdapter(Surface surface) {
		this.delegate = surface;
	}

	@Override
	public int getWidth() {
		return delegate.getWidth();
	}

	@Override
	public int getHeight() {
		return delegate.getHeight();
	}

	@Override
	public boolean isClosed() {
		return delegate.isClosed();
	}

	@Override
	public IRasterCanvas getCanvas() {
		// For now, return the raw Skija Canvas. Later, wrap in IRasterCanvas if needed.
		return new RasterCanvasAdapter(delegate.getCanvas());
	}

	@Override
	public IRasterImage makeImageSnapshot() {
		return new RasterImageAdapter(delegate.makeImageSnapshot());
	}

	@Override
	public IRasterImage makeImageSnapshot(IRect rect) {
		return new RasterImageAdapter(delegate.makeImageSnapshot(rect));
	}

	@Override
	public IRasterSurface makeSurface(int width, int height) {
		final Surface s = delegate.makeSurface(width, height);
		return s != null ? new RasterSurfaceAdapter(s) : null;
	}

	@Override
	public void close() {
		delegate.close();
	}

}