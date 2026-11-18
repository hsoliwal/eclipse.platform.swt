/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal;

/**
 * Layers composited by the window system above a control's own pixels (DirectComposition visuals on Windows): Z
 * order, offset, opacity and visibility are properties the system composites, so changing them repaints nothing.
 * Implemented by an optional backend (maya's {@code org.eclipse.swt.internal.render.MayaNativeLayers}), found by
 * class name; {@link #platform()} is null where no backend is installed.
 * <p>
 * Handles are native pointers, 0 meaning "none". Pixels are premultiplied ARGB ints, row by row.
 * </p>
 *
 * @noreference This interface is not intended to be referenced by clients.
 */
public interface NativeLayers {

	/** The class of the backend, loaded by name so that SWT has no compile-time dependency on it. */
	String BACKEND = "org.eclipse.swt.internal.render.MayaNativeLayers";

	/** A composition above the control's own pixels, or 0. */
	long create(org.eclipse.swt.widgets.Control control);

	/** A layer of the given pixel size, not shown until {@link #apply}, or 0. */
	long addLayer(long composition, int width, int height);

	/** Replaces a layer's pixels; 0, or a failure code. */
	int upload(long layer, int[] premultipliedArgb);

	/**
	 * Optional packed dirty rectangles; -1 if this backend lacks subregion
	 * submission. Legacy implementations retain their original binary contract.
	 */
	default int uploadDirty(long layer, int[] packedArgb, int[] regions, int count) {
		return -1;
	}

	/**
	 * One frame: {@code count} layers in Z order, bottom to top, with x, y, opacity (0 to 1) and visible (0 or 1)
	 * each in {@code props}; one commit. 0, or a failure code.
	 */
	int apply(long composition, long[] layers, float[] props, int count);

	/** Removes and frees a layer. */
	void removeLayer(long composition, long layer);

	/** Waits until the last commit is shown; 0, or a failure code. */
	int waitForCommit(long composition);

	/** Removes every layer and frees the composition. */
	void dispose(long composition);

	/** The installed backend, or null; looked up once. */
	static NativeLayers platform() {
		return Holder.INSTANCE;
	}

	/** Lazy holder of the backend lookup. */
	final class Holder {
		static final NativeLayers INSTANCE = load();

		private Holder() {
		}

		private static NativeLayers load() {
			try {
				return (NativeLayers) Class.forName(BACKEND).getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException | LinkageError | ClassCastException absent) {
				return null;
			}
		}
	}
}
