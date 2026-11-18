/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_FRAME_VIEW_H
#define SWT_MAYA_FRAME_VIEW_H

#include "MayaFramePacket.h"
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <type_traits>

// One indexed view of frame data for both JNI heap arrays and versioned
// direct-buffer frames. No copying, boxing, platform pointers or JNI resource
// ownership is introduced by these accessors.
namespace swt_maya_frame {

struct Values {
    std::uint64_t handle;
    float x;
    float y;
    float opacity;
    float visible;
};

template<typename NativeLong, typename NativeFloat>
struct LegacyView {
    const NativeLong *handles;
    const NativeFloat *properties;
    std::size_t count;
    static_assert(sizeof(NativeLong) == sizeof(std::uint64_t) &&
                  std::is_integral<NativeLong>::value,
                  "legacy native handles must be 64-bit integral values");
    static_assert(sizeof(NativeFloat) == sizeof(float) &&
                  std::is_floating_point<NativeFloat>::value,
                  "legacy native properties must be 32-bit floats");

    std::size_t size() const noexcept { return count; }
    Values at(std::size_t i) const noexcept {
        const std::size_t p = i * 4;
        std::uint64_t opaque = 0;
        std::memcpy(&opaque, handles + i, sizeof(opaque));
        return {opaque, static_cast<float>(properties[p]),
                static_cast<float>(properties[p + 1]),
                static_cast<float>(properties[p + 2]),
                static_cast<float>(properties[p + 3])};
    }
};

struct PackedView {
    const Frame &frame;
    std::size_t size() const noexcept { return frame.layers.size(); }
    Values at(std::size_t i) const noexcept {
        const auto &layer = frame.layers[i];
        return {layer.handle, layer.x, layer.y, layer.opacity,
                static_cast<float>(layer.visible)};
    }
};

} // namespace swt_maya_frame
#endif
