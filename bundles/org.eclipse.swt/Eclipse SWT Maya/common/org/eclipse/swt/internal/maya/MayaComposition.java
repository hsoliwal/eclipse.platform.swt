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
 * Handles are opaque, typed non-reused tokens, never raw C++ addresses on Windows;
 * 0 means "not created" (or not supported on this platform: the same exports exist in
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
	/** Additive scene-v1 record: x/y, opacity, visible, affine 3x2, local clip L/T/R/B. */
	public static final int SCENE_FLOATS = 14;
	/** Scene flags bit enabling the source-local rectangular clip. */
	public static final int SCENE_CLIP = 1;
	/** Native scene-v1 affine transform support. */
	public static final long CAP_AFFINE_2D = 1L;
	/** Native scene-v1 local rectangle clip support. */
	public static final long CAP_LOCAL_RECT_CLIP = 2L;
	/** Optional dirty image upload support. */
	public static final long CAP_DIRTY_UPLOAD = 4L;
	private static volatile boolean capabilitiesUnavailable;
	private static volatile boolean sceneUnavailable;

    /** Version-1 native metrics snapshot shape, all counters are nonnegative longs. */
    public static final int METRIC_COUNT = 16;
    public static final int METRIC_VERSION = 0;
    public static final int METRIC_FRAME_ATTEMPTS = 1;
    public static final int METRIC_FRAME_COMMITS = 2;
    public static final int METRIC_FRAME_NOOPS = 3;
    public static final int METRIC_Z_REBUILDS = 4;
    public static final int METRIC_FULL_UPLOADS = 5;
    public static final int METRIC_DIRTY_PATCHES = 6;
    public static final int METRIC_UPLOAD_BYTES = 7;
    public static final int METRIC_HEALTH_FAILURES = 8;
    public static final int METRIC_LAYERS_CREATED = 9;
    public static final int METRIC_LAYERS_RETIRED = 10;
    public static final int METRIC_FRAME_REJECTIONS = 11;
    public static final int METRIC_COMMIT_FAILURES = 12;
    public static final int METRIC_LIVE_LAYERS = 13;
    public static final int METRIC_PENDING_SURFACE = 14;
    public static final int METRIC_HEALTH_PROBES = 15;


	private MayaComposition() {
	}

	/** A composition above the window {@code hwnd} (topmost over its content), or 0. */
	public static native long _nCreate(long hwnd);

	/** A layer of the given pixel size, not yet shown, or 0. */
	public static native long _nAddLayer(long composition, int width, int height);

	/** Extension capabilities for this live composition, or zero; absent in older binaries. */
	public static native long _nCapabilities(long composition);

	/**
	 * Reports supported rendering extensions, independently of the packed transport
	 * version. Older Maya binaries retain their original legacy operations.
	 */
	public static long capabilities(long composition) {
		if (composition == 0 || capabilitiesUnavailable) return 0;
		try {
			return _nCapabilities(composition);
		} catch (UnsatisfiedLinkError olderMayaBinary) {
			capabilitiesUnavailable = true;
			return 0;
		}
	}

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
	 * Optional scene-v1 transaction. Each layer has {@link #SCENE_FLOATS} floats:
	 * physical client offsets X/Y, opacity, visibility, affine m11/m12/m21/m22/dx/dy,
	 * and source-image-local clip left/top/right/bottom. The affine transform maps
	 * source image pixels and transforms the clip with the visual. All values must
	 * be finite and within +/- 2^20; opacity is 0..1 and visibility is 0 or 1.
	 * Flags are zero or {@link #SCENE_CLIP}; an enabled clip must not be reversed.
	 * All records are validated before native visual mutation. Legacy array and
	 * packed-v1 frames restore identity transform and no clip for their layers.
	 */
	public static native int _nApplyScene(long composition, long[] layers, float[] packed,
			int[] flags, int count);

	/**
	 * Uses the optional scene ABI, or returns unsupported (-1). A caller must use
	 * its painted fallback when unsupported; a legacy frame cannot carry a scene.
	 */
	public static int applyScene(long composition, long[] layers, float[] packed,
			int[] flags, int count) {
		if (sceneUnavailable) return -1;
		try {
			return _nApplyScene(composition, layers, packed, flags, count);
		} catch (UnsatisfiedLinkError olderMayaBinary) {
			sceneUnavailable = true;
			return -1;
		}
	}

	/** Optional packet ingress ABI only (not an OS backend capability); absent in older Maya binaries. */
	public static native int _nFramePacketVersion();

	/**
	 * Optional 32-byte-header/32-byte-record little-endian direct frame envelope.
	 * The native implementation validates the whole buffer and feeds the same
	 * ownership-checked DirectComposition transaction as the legacy _nApply,
	 * without reconstructing JNI heap arrays. Malformed buffers return -2;
	 * native decoding failures return -3. This is experimental transport,
	 * not a measured performance guarantee.
	 */
	public static native int _nApplyPacked(long composition, ByteBuffer packet, int byteLength);

	/** Removes and frees a layer. */
	public static native void _nRemoveLayer(long composition, long layer);

	/** Waits until the last commit is composited (for tests and screenshots); 0 or a failure code. */
	public static native int _nWaitForCommit(long composition);

    /**
     * Copies a fixed 16-long, versioned native metrics snapshot into the
     * supplied array. This optional entry point has no Java allocations.
     * Counters describe successful native command submissions, not GPU
     * presentation or measured graphics memory. Returns 0 on success.
     * Old Maya libraries do not export this method.
     */
    public static native int _nReadMetrics(long composition, long[] out);

    /**
     * Optional explicit snapshot for diagnostics only. Returns null for
     * older Maya DLLs and missing/disposed native compositions; never
     * polls counters in the normal SWT rendering path.
     */
    public static long[] readMetrics(long composition) {
        long[] snapshot = new long[METRIC_COUNT];
        try {
            return _nReadMetrics(composition, snapshot) == 0 ? snapshot : null;
        } catch (UnsatisfiedLinkError olderMayaBinary) {
            return null;
        }
    }

    /** Version 1: the logical BGRA surface budget diagnostic array has six slots. */
    public static final int SURFACE_BUDGET_FIELDS = 6;
    public static final int BUDGET_VERSION = 0;
    public static final int BUDGET_LIMIT_BYTES = 1;
    public static final int BUDGET_RESERVED_BYTES = 2;
    public static final int BUDGET_PEAK_BYTES = 3;
    public static final int BUDGET_DENIALS = 4;
    public static final int BUDGET_ACCEPTED = 5;

    /**
     * Optional per-composition logical BGRA surface reservation cap. Zero
     * means unlimited. The native method refuses a cap below the already
     * reserved logical surface bytes. It does NOT limit actual GPU residency.
     * An unrecognized native method is not silently treated as success.
     */
    public static native int _nSetSurfaceBudget(long composition, long maxLogicalBytes);

    /** Explicit opt-in budget, returning -1 on an older unsupported Maya DLL. */
    public static int setSurfaceBudget(long composition, long maxLogicalBytes) {
        if (maxLogicalBytes < 0) throw new IllegalArgumentException("negative native surface quota");
        try {
            return _nSetSurfaceBudget(composition, maxLogicalBytes);
        } catch (UnsatisfiedLinkError olderMayaBinary) {
            return -1;
        }
    }

    /** Copies six primitive counters into the caller-owned prefix only. */
    public static native int _nReadSurfaceBudget(long composition, long[] out);

    /** Optional read-only logical reservation snapshot, or null if unsupported. */
    public static long[] readSurfaceBudget(long composition) {
        long[] snapshot = new long[SURFACE_BUDGET_FIELDS];
        try {
            return _nReadSurfaceBudget(composition, snapshot) == 0 ? snapshot : null;
        } catch (UnsatisfiedLinkError olderMayaBinary) {
            return null;
        }
    }

	/** Removes every layer from the window and frees the composition. */
	public static native void _nDispose(long composition);
}
