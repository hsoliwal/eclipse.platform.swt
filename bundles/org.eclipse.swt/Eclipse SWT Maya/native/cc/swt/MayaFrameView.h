/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_FRAME_VIEW_H
#define SWT_MAYA_FRAME_VIEW_H

#include "MayaFramePacket.h"
#include "MayaCompositionScene.h"
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
    const float *scene = nullptr;
    int sceneFlags = 0;
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

// Scene records have the same four leading properties as the legacy arrays.
// All references remain borrowed until the synchronous native call finishes.
template<typename NativeLong, typename NativeFloat, typename NativeInt>
struct SceneView {
    const NativeLong *handles;
    const NativeFloat *properties;
    const NativeInt *flags;
    std::size_t count;
    static_assert(sizeof(NativeLong) == sizeof(std::uint64_t) &&
                  std::is_integral<NativeLong>::value,
                  "scene native handles must be 64-bit integral values");
    static_assert(std::is_same<NativeFloat, float>::value,
                  "scene native properties must be 32-bit float values");
    static_assert(sizeof(NativeInt) == sizeof(std::int32_t) &&
                  std::is_integral<NativeInt>::value,
                  "scene flags must be 32-bit integral values");

    std::size_t size() const noexcept { return count; }
    Values at(std::size_t i) const noexcept {
        const float *p = properties + i * swt_maya::SCENE_FLOATS;
        std::uint64_t opaque = 0;
        std::memcpy(&opaque, handles + i, sizeof(opaque));
        return {opaque, p[0], p[1], p[2], p[3], p, static_cast<int>(flags[i])};
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
