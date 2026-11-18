/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_COMPOSITION_DIRTY_H
#define SWT_MAYA_COMPOSITION_DIRTY_H

#include <cstddef>
#include <cstdint>
#include <limits>

/*
 * Portable validation/copy atom for the optional DirectComposition dirty-region
 * JNI entrypoint. No platform header, JNI object, callback, or GC pin escapes
 * this interface. The caller checks all rectangles before any GPU mutation.
 */
namespace swt_maya {

constexpr int MAX_DIRTY_RECTS = 4096;

inline bool validDirtyFrame(int width, int height, int pixels,
        const int *rects, int count, int rectInts) noexcept {
    if (width <= 0 || height <= 0 || pixels < 0 || count < 0 ||
            count > MAX_DIRTY_RECTS || rectInts < 0 ||
            (count != 0 && rects == nullptr) || count > rectInts / 4) return false;
    const std::uint64_t total = static_cast<std::uint64_t>(width) * height;
    if (total > static_cast<std::uint64_t>(std::numeric_limits<std::int32_t>::max()) ||
            total > static_cast<std::uint64_t>(pixels)) return false;
    for (int i = 0; i < count; ++i) {
        const int *r = rects + static_cast<std::size_t>(i) * 4;
        // Subtract only after checking non-negative origins: no int overflow.
        if (r[0] < 0 || r[1] < 0 || r[2] <= 0 || r[3] <= 0 ||
                r[0] >= width || r[1] >= height ||
                r[2] > width - r[0] || r[3] > height - r[1]) return false;
    }
    return true;
}

/**
 * A single packed int[] contains all changed pixels in rectangle order,
 * tightly row-major within each rectangle. Its trailing spare capacity is
 * ignored. Every rectangle and the aggregate prefix are checked before any
 * IDCompositionSurface::BeginDraw call.
 */
inline bool validPackedDirtyFrame(int width, int height, int pixels,
        const int *rects, int count, int rectInts) noexcept {
    if (width <= 0 || height <= 0 || pixels < 0 || count < 0 ||
            count > MAX_DIRTY_RECTS || rectInts < 0 ||
            (count != 0 && rects == nullptr) || count > rectInts / 4) return false;
    const std::uint64_t whole = static_cast<std::uint64_t>(width) * height;
    if (whole > static_cast<std::uint64_t>(std::numeric_limits<std::int32_t>::max())) return false;
    std::uint64_t consumed = 0;
    for (int i = 0; i < count; ++i) {
        const int *r = rects + static_cast<std::size_t>(i) * 4;
        if (r[0] < 0 || r[1] < 0 || r[2] <= 0 || r[3] <= 0 ||
                r[0] >= width || r[1] >= height ||
                r[2] > width - r[0] || r[3] > height - r[1] ||
                static_cast<std::uint64_t>(r[2]) * sizeof(std::uint32_t) >
                    std::numeric_limits<std::uint32_t>::max()) return false;
        consumed += static_cast<std::uint64_t>(r[2]) * r[3];
        if (consumed > static_cast<std::uint64_t>(pixels)) return false;
    }
    return true;
}

/**
 * Portable D3D11 upload geometry: a four-byte ARGB row pitch must fit UINT,
 * and a returned DirectComposition update offset must never be added using
 * signed arithmetic. Only call after the source dimensions have been checked.
 */
inline bool validD3dUploadBox(int offsetX, int offsetY,
        int width, int height) noexcept {
    if (offsetX < 0 || offsetY < 0 || width <= 0 || height <= 0) return false;
    constexpr std::uint64_t MAX_UINT = std::numeric_limits<std::uint32_t>::max();
    return static_cast<std::uint64_t>(width) * sizeof(std::uint32_t) <= MAX_UINT
        && static_cast<std::uint64_t>(offsetX) + width <= MAX_UINT
        && static_cast<std::uint64_t>(offsetY) + height <= MAX_UINT;
}

/**
 * Count the exact prefix of already-validated packed dirty rectangles. This
 * runs before JNI pixel copy and before any BeginDraw; the earlier validation
 * guarantees the count fits Java array length and a signed jsize.
 */
inline std::size_t packedDirtyPixelCount(const int *rects, int count) noexcept {
    std::size_t total = 0;
    for (int i = 0; i < count; ++i) {
        const int *r = rects + static_cast<std::size_t>(i) * 4;
        total += static_cast<std::size_t>(r[2]) * static_cast<std::size_t>(r[3]);
    }
    return total;
}

inline std::size_t dirtyArea(const int *rect) noexcept {
    return static_cast<std::size_t>(rect[2]) * static_cast<std::size_t>(rect[3]);
}

/* A tightly packed, row-major copy of a single region for D3D11 UpdateSubresource. */
inline void copyDirty(const std::uint32_t *pixels, int stride,
        const int *rect, std::uint32_t *out) noexcept {
    const auto x = static_cast<std::size_t>(rect[0]);
    const auto y = static_cast<std::size_t>(rect[1]);
    const auto w = static_cast<std::size_t>(rect[2]);
    const auto h = static_cast<std::size_t>(rect[3]);
    const auto pitch = static_cast<std::size_t>(stride);
    for (std::size_t row = 0; row < h; ++row) {
        const std::uint32_t *src = pixels + (y + row) * pitch + x;
        std::uint32_t *dst = out + row * w;
        for (std::size_t col = 0; col < w; ++col) dst[col] = src[col];
    }
}

} // namespace swt_maya
#endif
