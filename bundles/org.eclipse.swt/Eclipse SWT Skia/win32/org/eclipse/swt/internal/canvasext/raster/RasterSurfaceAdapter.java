/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * Contributors:
 *     SAP SE and others - initial API and implementation
 *     Hitesh Soliwal (Synexia) - adoption into this fork, 2026
 *******************************************************************************/

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