/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render;

/**
 * CPU-only native image damage admission. Rectangles are x, y, width, height
 * quadruples in image-local pixels; overlapping rectangles are allowed and
 * consume pixels independently. The same 4096-rectangle and signed-int payload
 * limits are enforced by MayaCompositionDirty.h before any GPU mutation.
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class NativeDirtyRegions {
    public static final int MAX_RECTS = 4096;

    private NativeDirtyRegions() { }

    /**
     * Validates the complete packed prefix before allocating an ARGB payload.
     * Ignores spare rectangle capacity; returns the required int pixel count.
     */
    public static int packedPixelCount(int imageWidth, int imageHeight, int[] rectangles, int count) {
        if (imageWidth <= 0 || imageHeight <= 0 || (long) imageWidth * imageHeight > Integer.MAX_VALUE
                || rectangles == null || count < 0 || count > MAX_RECTS || count > rectangles.length / 4) {
            throw new IllegalArgumentException("invalid native image dimensions or region prefix");
        }
        long pixels = 0;
        for (int i = 0; i < count; i++) {
            int offset = i * 4;
            int x = rectangles[offset], y = rectangles[offset + 1];
            int width = rectangles[offset + 2], height = rectangles[offset + 3];
            // Subtractions happen only after positive sizes and bounded origins.
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                    || x >= imageWidth || y >= imageHeight
                    || width > imageWidth - x || height > imageHeight - y) {
                throw new IllegalArgumentException("dirty rectangle exceeds image bounds");
            }
            pixels += (long) width * height;
            if (pixels > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("packed native image payload overflows int[]");
            }
        }
        return (int) pixels;
    }
}
