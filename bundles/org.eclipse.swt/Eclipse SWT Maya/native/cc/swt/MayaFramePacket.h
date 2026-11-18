/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_FRAME_PACKET_H
#define SWT_MAYA_FRAME_PACKET_H

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <vector>

// Portable, independent of JNI/COM/Skia. All numeric fields are little-endian.
// It does not dereference opaque native handles or mutate the compositor.
namespace swt_maya_frame {
constexpr std::uint32_t MAGIC = 0x464c334dU; // bytes "M3LF"
constexpr std::uint16_t VERSION = 1;
constexpr std::uint16_t HEADER_BYTES = 32;
constexpr std::uint16_t RECORD_BYTES = 32;
constexpr std::uint32_t MAX_LAYERS = 65536;
constexpr std::size_t MAX_BYTES = HEADER_BYTES + static_cast<std::size_t>(MAX_LAYERS) * RECORD_BYTES;

enum class Result : int {
    OK = 0, NULL_DATA, SHORT_HEADER, BAD_MAGIC, BAD_VERSION,
    BAD_LAYOUT, BAD_FLAGS, BAD_SIZE, TOO_MANY_LAYERS,
    BAD_HANDLE, BAD_NUMBER, BAD_VISIBILITY, BAD_RESERVED, DUPLICATE_HANDLE
};

struct Layer {
    std::uint64_t handle = 0;
    float x = 0, y = 0, opacity = 0;
    std::uint32_t visible = 0;
};
struct Frame {
    std::uint64_t sequence = 0;
    std::vector<Layer> layers;
};

inline std::uint16_t u16(const std::uint8_t *p) {
    return static_cast<std::uint16_t>(p[0] | static_cast<std::uint16_t>(p[1]) << 8);
}
inline std::uint32_t u32(const std::uint8_t *p) {
    return static_cast<std::uint32_t>(p[0]) | static_cast<std::uint32_t>(p[1]) << 8 |
           static_cast<std::uint32_t>(p[2]) << 16 | static_cast<std::uint32_t>(p[3]) << 24;
}
inline std::uint64_t u64(const std::uint8_t *p) {
    return static_cast<std::uint64_t>(u32(p)) | static_cast<std::uint64_t>(u32(p + 4)) << 32;
}
inline float f32(const std::uint8_t *p) {
    const std::uint32_t bits = u32(p);
    float value;
    static_assert(sizeof(value) == sizeof(bits) && std::numeric_limits<float>::is_iec559,
                  "requires IEEE-754 binary32");
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}

// Index the already validated candidate layers, rather than sorting a second
// array of copied native handles on every compositor frame. Hash entries are
// 1-based layer indices (zero means empty), so the table has bounded 4-byte
// slots and the original frame order remains authoritative.
inline std::uint32_t handleBucket(std::uint64_t value) noexcept {
    value ^= value >> 33;
    value *= UINT64_C(0xff51afd7ed558ccd);
    value ^= value >> 33;
    value *= UINT64_C(0xc4ceb9fe1a85ec53);
    return static_cast<std::uint32_t>(value ^ (value >> 33));
}

inline bool hasDuplicateHandles(const std::vector<Layer> &layers) {
    const std::size_t count = layers.size();
    if (count < 2) return false;
    // MAX_LAYERS bounds the size, multiplication, power of two and indices.
    std::size_t capacity = 2;
    while (capacity < count * 2) capacity <<= 1;
    std::vector<std::uint32_t> slots(capacity, 0);
    const std::size_t mask = capacity - 1;
    for (std::size_t i = 0; i < count; ++i) {
        std::size_t slot = handleBucket(layers[i].handle) & mask;
        unsigned probes = 0;
        while (slots[slot] != 0) {
            if (layers[slots[slot] - 1].handle == layers[i].handle) return true;
            // A malicious packet may choose colliding native-handle bits.
            // Preserve deterministic bounded worst-case work with a sorted
            // fallback rather than accepting an unbounded probe chain.
            if (++probes == 32) {
                std::vector<std::uint64_t> ordered;
                ordered.reserve(count);
                for (const Layer &layer : layers) ordered.push_back(layer.handle);
                std::sort(ordered.begin(), ordered.end());
                return std::adjacent_find(ordered.begin(), ordered.end()) != ordered.end();
            }
            slot = (slot + 1) & mask;
        }
        slots[slot] = static_cast<std::uint32_t>(i + 1);
    }
    return false;
}

// Transactional parser: on failure the caller's previous Frame is unchanged.
// Entire buffer size, offsets, visibility, opacity, opaque handles and duplicate
// handles are checked *before* any platform compositor operation is attempted.
inline Result decode(const std::uint8_t *bytes, std::size_t length, Frame &output) {
    if (!bytes) return Result::NULL_DATA;
    if (length < HEADER_BYTES) return Result::SHORT_HEADER;
    if (u32(bytes) != MAGIC) return Result::BAD_MAGIC;
    if (u16(bytes + 4) != VERSION) return Result::BAD_VERSION;
    if (u16(bytes + 6) != HEADER_BYTES || u16(bytes + 8) != RECORD_BYTES) return Result::BAD_LAYOUT;
    if (u16(bytes + 10) != 0) return Result::BAD_FLAGS;
    const std::uint32_t count = u32(bytes + 12);
    if (count > MAX_LAYERS) return Result::TOO_MANY_LAYERS;
    const std::size_t exact = HEADER_BYTES + static_cast<std::size_t>(count) * RECORD_BYTES;
    if (length != exact) return Result::BAD_SIZE;
    if (u64(bytes + 24) != 0) return Result::BAD_RESERVED;

    Frame candidate;
    candidate.sequence = u64(bytes + 16);
    candidate.layers.reserve(count);
    for (std::uint32_t i = 0; i < count; i++) {
        const std::uint8_t *record = bytes + HEADER_BYTES + static_cast<std::size_t>(i) * RECORD_BYTES;
        Layer layer;
        layer.handle = u64(record);
        if (!layer.handle) return Result::BAD_HANDLE;
        layer.x = f32(record + 8);
        layer.y = f32(record + 12);
        layer.opacity = f32(record + 16);
        layer.visible = u32(record + 20);
        if (!std::isfinite(layer.x) || !std::isfinite(layer.y) || !std::isfinite(layer.opacity)
                || layer.opacity < 0.0f || layer.opacity > 1.0f) return Result::BAD_NUMBER;
        if (layer.visible > 1) return Result::BAD_VISIBILITY;
        if (u64(record + 24) != 0) return Result::BAD_RESERVED;
        candidate.layers.push_back(layer);
    }
    if (hasDuplicateHandles(candidate.layers)) return Result::DUPLICATE_HANDLE;
    output = std::move(candidate);
    return Result::OK;
}
}
#endif // SWT_MAYA_FRAME_PACKET_H
