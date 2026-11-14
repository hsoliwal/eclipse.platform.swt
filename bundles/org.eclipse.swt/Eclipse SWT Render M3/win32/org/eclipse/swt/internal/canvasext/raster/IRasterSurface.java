package org.eclipse.swt.internal.canvasext.raster;

import io.github.humbleui.types.IRect;

/**
 * Interface to abstract Skija's Surface for SWT integration.
 * This allows decoupling from the direct Skija dependency in client code.
 */
public interface IRasterSurface extends AutoCloseable {
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
	 * Returns the IRasterCanvas for drawing operations.
	 */
	IRasterCanvas getCanvas();

	/**
	 * Creates an image snapshot of the current surface contents.
	 * The return type should be an abstraction (e.g., IRasterImage) but for now Object.
	 */
	IRasterImage makeImageSnapshot();

	IRasterImage makeImageSnapshot(IRect rect);

	/**
	 * Creates a new surface with the given width and height.
	 * The return type should be IRasterSurface.
	 */
	IRasterSurface makeSurface(int width, int height);

	/**
	 * Closes/releases the surface and its resources.
	 */
	@Override
	void close();
}