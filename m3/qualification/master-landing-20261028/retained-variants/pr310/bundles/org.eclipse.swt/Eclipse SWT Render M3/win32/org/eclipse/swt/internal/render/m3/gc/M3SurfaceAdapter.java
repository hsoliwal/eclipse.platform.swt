package org.eclipse.swt.internal.render.m3.gc;

import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.IRect;

/**
 * Adapter to wrap Skija's Surface as IM3Surface.
 */
public class M3SurfaceAdapter implements IM3Surface {
	private final Surface delegate;

	public M3SurfaceAdapter(Surface surface) {
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
	public IM3Canvas getCanvas() {
		// For now, return the raw Skija Canvas. Later, wrap in IM3Canvas if needed.
		return new M3CanvasAdapter(delegate.getCanvas());
	}

	@Override
	public IM3Image makeImageSnapshot() {
		return new M3ImageAdapter(delegate.makeImageSnapshot());
	}

	@Override
	public boolean readRasterPixels(io.github.humbleui.skija.Bitmap bitmap, int x, int y) {
		// L13 TEXT_FALLBACK_SAFE v2: raster surfaces only (no GPU readback per text layout)
		return delegate._context == null && delegate.readPixels(bitmap, x, y);
	}

	@Override
	public IM3Image makeImageSnapshot(IRect rect) {
		// M3 (GC seam, leaf 6; PIXEL_COPY, census GF20): Skija answers null for a rectangle outside the surface; so does the adapter
		// (the donor wrapped the null, so copyArea's null check was dead and drawImage threw)
		final Image image = delegate.makeImageSnapshot(rect);
		return image == null ? null : new M3ImageAdapter(image);
	}

	@Override
	public IM3Surface makeSurface(int width, int height) {
		final Surface s = delegate.makeSurface(width, height);
		return s != null ? new M3SurfaceAdapter(s) : null;
	}

	@Override
	public void close() {
		delegate.close();
	}

}