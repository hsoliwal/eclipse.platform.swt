package org.eclipse.swt.internal.render.m3.gc;

import jdk.m3.graphics.M3IRect;

/**
 * Interface to abstract Skija's Surface for SWT integration.
 * This allows decoupling from the direct Skija dependency in client code.
 */
public interface IM3Surface extends AutoCloseable {
	/**
	 * Returns the width of the surface in pixels.
	 */
	int getWidth();

	/**
	 * Returns the height of the surface in pixels.
	 */
	int getHeight();

	/**
	 * Returns true if the surface is closed/disposed.
	 */
	boolean isClosed();

	/**
	 * Returns the IM3Canvas for drawing operations.
	 */
	IM3Canvas getCanvas();

	/**
	 * Creates an image snapshot of the current surface contents.
	 * The return type should be an abstraction (e.g., IM3Image) but for now Object.
	 */
	IM3Image makeImageSnapshot();

	IM3Image makeImageSnapshot(M3IRect rect);

	/**
	 * Creates a new surface with the given width and height.
	 * The return type should be IM3Surface.
	 */
	IM3Surface makeSurface(int width, int height);

	/** L13 TEXT_FALLBACK_SAFE v2: copies device pixels at (x, y) into the bitmap when the surface is CPU memory (raster); false otherwise */
	default boolean readRasterPixels(jdk.m3.graphics.M3Bitmap bitmap, int x, int y) {
		return false;
	}

	/**
	 * Closes/releases the surface and its resources.
	 */
	@Override
	void close();
}