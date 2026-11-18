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

	/** Floats per visual in the optional internal affine scene transaction. */
	public static final int SCENE_FLOATS = 14;

	/** Bit indicating that a scene visual has a local rectangular clip. */
	public static final int SCENE_CLIP = 1;

	private MayaComposition() {
	}

	/** A composition above the window {@code hwnd} (topmost over its content), or 0. */
	public static native long _nCreate(long hwnd);

	/** A layer of the given pixel size, not yet shown, or 0. */
	public static native long _nAddLayer(long composition, int width, int height);

	/** Replaces the layer's pixels (width * height premultiplied ARGB ints); 0 or a failure code. */
	public static native int _nUpload(long layer, int[] pixels);

	/** Batched dirty image regions in layer-local coordinates, optional Maya JNI ABI. */
	public static native int _nUploadDirty(long layer, int[] packedArgb, int[] regions, int count);

	/** Old Maya binaries use the existing full-image upload, not partial pixels. */
	public static int uploadDirty(long layer, int[] packedArgb, int[] regions, int count) {
		try {
			return _nUploadDirty(layer, packedArgb, regions, count);
		} catch (UnsatisfiedLinkError olderMayaBinary) {
			return -1;
		}
	}

	/**
	 * One frame: {@code count} layers in Z order, bottom to top, with {@link #PROPS} floats each in {@code props}
	 * (x, y, opacity, visible); layers not listed are not shown. One commit. Returns 0 or a failure code.
	 */
	public static native int _nApply(long composition, long[] layers, float[] props, int count);

	/**
	 * Optional scene frame. Each of the `count` layers has a packed
	 * {@link #SCENE_FLOATS}-float record: offset X/Y, opacity, visibility,
	 * a 3x2 affine matrix and a local clip rectangle. Flags are zero or
	 * {@link #SCENE_CLIP}. The native library copies only active array
	 * prefixes and validates the entire frame before changing visuals.
	 * Existing {@link #_nApply} resets native transform/clip state.
	 *
	 * @return zero on success; an error code otherwise; absent from older Maya binaries
	 */
	public static native int _nApplyScene(long composition, long[] layers, float[] packed,
			int[] flags, int count);

	/**
	 * Compatibility-safe entry for the optional scene capability. This method
	 * never falls back to `_nApply` because ordinary frames cannot represent
	 * transforms or clips; callers must choose their painted fallback.
	 */
	public static int applyScene(long composition, long[] layers, float[] packed,
			int[] flags, int count) {
		try {
			return _nApplyScene(composition, layers, packed, flags, count);
		} catch (UnsatisfiedLinkError olderMayaBinary) {
			return -1;
		}
	}

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
