/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_COMPOSITION_DIRTY_H
#define SWT_MAYA_COMPOSITION_DIRTY_H

#include <cstddef>
#include <cstdint>
#include <limits>
#include <memory>

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

/*
 * Retain at most 1 MiB of native ARGB staging per SWT UI thread. A reentrant
 * dirty JNI call uses an independent temporary buffer, never the in-use cache.
 * Large packets still copy their WHOLE validated prefix before BeginDraw;
 * they are transient rather than retained, preserving the no-JNI-after-COM
 * behavior of the original Maya upload implementation.
 */
constexpr std::size_t MAX_RETAINED_DIRTY_PIXELS = 1u << 18;
inline bool mayReuseDirtyPixels(int count, std::size_t pixels, bool inUse) noexcept {
    return count > 1 && pixels > 0 && pixels <= MAX_RETAINED_DIRTY_PIXELS && !inUse;
}

/**
 * Per-thread native pixel-copy staging. The retained allocation is at most
 * MAX_RETAINED_DIRTY_PIXELS elements. Large packets and nested JNI calls
 * receive independent short-lived allocations, keeping the full copied-in
 * frame immutable until every GPU UpdateSubresource call completes.
 *
 * No Java reference, operating-system handle or JNI region is stored here.
 */
template<class Pixel> class DirtyPixelScratch final {
    std::unique_ptr<Pixel[]> retained_;
    std::size_t capacity_ = 0;
    bool busy_ = false;

public:
    bool busy() const noexcept { return busy_; }
    std::size_t retainedCapacity() const noexcept { return capacity_; }

    class Lease final {
        DirtyPixelScratch *owner_;
        std::unique_ptr<Pixel[]> temporary_;
        Pixel *data_ = nullptr;
        bool retained_ = false;

    public:
        Lease(DirtyPixelScratch &owner, int rectCount, std::size_t pixelCount)
                : owner_(&owner) {
            retained_ = mayReuseDirtyPixels(rectCount, pixelCount, owner.busy_);
            if (retained_) {
                // new[] completes before resetting the old retained buffer:
                // allocation failure cannot change owner state or ownership.
                if (owner.capacity_ < pixelCount) {
                    owner.retained_.reset(new Pixel[pixelCount]);
                    owner.capacity_ = pixelCount;
                }
                owner.busy_ = true;
                data_ = owner.retained_.get();
            } else {
                temporary_.reset(new Pixel[pixelCount]);
                data_ = temporary_.get();
            }
        }
        Lease(const Lease &) = delete;
        Lease &operator=(const Lease &) = delete;
        Lease(Lease &&) = delete;
        Lease &operator=(Lease &&) = delete;
        ~Lease() noexcept { if (retained_) owner_->busy_ = false; }
        Pixel *data() const noexcept { return data_; }
    };
};

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
