/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_COMPOSITION_CHECKS_H
#define SWT_MAYA_COMPOSITION_CHECKS_H

#include <cmath>
#include <cstdint>
#include <limits>

// A platform-independent validation atom: checked before touching a JNI region,
// allocating a host pixel buffer, or mutating a native composition frame.
namespace swt_maya {

inline bool validPixelSize(int width, int height) noexcept {
    return width > 0 && height > 0
        && static_cast<std::uint64_t>(width) * static_cast<std::uint64_t>(height)
            <= static_cast<std::uint64_t>(std::numeric_limits<std::int32_t>::max());
}

inline bool validPixelBuffer(int width, int height, int length) noexcept {
    return validPixelSize(width, height) && length >= 0
        && static_cast<std::uint64_t>(length)
            >= static_cast<std::uint64_t>(width) * static_cast<std::uint64_t>(height);
}

inline bool validFrameArrays(int count, int layerLength, int propertyLength) noexcept {
    // Division avoids both count*4 signed overflow and excessive allocations.
    return count >= 0 && layerLength >= 0 && propertyLength >= 0
        && count <= layerLength && count <= propertyLength / 4;
}

inline bool validLayerProperties(float x, float y, float opacity, float visible) noexcept {
    return std::isfinite(x) && std::isfinite(y) && std::isfinite(opacity)
        && opacity >= 0.0f && opacity <= 1.0f
        && (visible == 0.0f || visible == 1.0f);
}

}  // namespace swt_maya
#endif
