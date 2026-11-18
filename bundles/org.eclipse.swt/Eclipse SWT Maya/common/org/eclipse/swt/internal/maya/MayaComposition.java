/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

import java.nio.ByteBuffer;

import org.eclipse.swt.internal.maya.impl.Library;

/**
 * Layers composited by the window system above a window's own pixels: on Windows DirectComposition visuals, one per
 * layer. Z order, offset, opacity and visibility are properties the system composites; changing them repaints
 * nothing. One {@link #_nApply} per frame carries every layer's properties and commits once.
 * <p>
 * Handles are native pointers; 0 means "not created" (or not supported on this platform: the same exports exist in
 * every maya build). Pixels are premultiplied ARGB ints, row by row. Not thread safe: the UI thread owns a
 * composition.
 * </p>
 */
public final class MayaComposition {
	static {
		Library.staticLoad();
	}

	/** Floats per layer in {@link #_nApply}'s properties: x, y, opacity (0 to 1), visible (0 or 1). */
	public static final int PROPS = 4;

	/** The opt-in native scene frame stores fourteen float properties per layer. */
	public static final int SCENE_FLOATS = 14;

	/** Opt-in scene bit indicating a local rectangular clip. */
	public static final int SCENE_CLIP = 1;

	private MayaComposition() {
	}

	/** A composition above the window {@code hwnd} (topmost over its content), or 0. */
	public static native long _nCreate(long hwnd);

	/** A layer of the given pixel size, not yet shown, or 0. */
	public static native long _nAddLayer(long composition, int width, int height);

	/** Replaces the layer's pixels (width * height premultiplied ARGB ints); 0 or a failure code. */
	public static native int _nUpload(long layer, int[] pixels);

	/**
	 * One frame: {@code count} layers in Z order, bottom to top, with {@link #PROPS} floats each in {@code props}
	 * (x, y, opacity, visible); layers not listed are not shown. One commit. Returns 0 or a failure code.
	 */
	public static native int _nApply(long composition, long[] layers, float[] props, int count);

	/**
	 * One opt-in compositor frame with {@link #SCENE_FLOATS} floats per layer:
	 * offsetX, offsetY, opacity, visible, affine m11/m12/m21/m22/dx/dy,
	 * and local clip left/top/right/bottom. Each flags element may set
	 * {@link #SCENE_CLIP}; unsupported flags or malformed frames are refused
	 * without changing the last committed native pixels.
	 * <p>
	 * Existing {@link #_nApply} remains unchanged and resets opt-in transforms
	 * and clips before returning to legacy presentation. All arrays are read
	 * synchronously by one JNI call on the SWT display thread.
	 * Unsupported native builds return an error; callers must use the normal
	 * SWT backing-store route when this optional capability is unavailable.
	 * </p>
	 */
	public static native int _nApplyScene(long composition, long[] layers, float[] packed,
			int[] flags, int count);

	/** Optional packet ingress ABI only (not an OS backend capability); absent in older Maya binaries. */
	public static native int _nFramePacketVersion();

	/**
	 * Optional 32-byte-header/32-byte-record little-endian direct frame envelope.
	 * The native implementation validates all bytes before calling original _nApply;
	 * malformed buffers return -2, JNI staging failure returns -3. This is
	 * experimental transport, not a promised performance improvement.
	 */
	public static native int _nApplyPacked(long composition, ByteBuffer packet, int byteLength);

	/** Removes and frees a layer. */
	public static native void _nRemoveLayer(long composition, long layer);

	/** Waits until the last commit is composited (for tests and screenshots); 0 or a failure code. */
	public static native int _nWaitForCommit(long composition);

	/** Removes every layer from the window and frees the composition. */
	public static native void _nDispose(long composition);
}
