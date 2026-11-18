/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_COMPOSITION_SCENE_H
#define SWT_MAYA_COMPOSITION_SCENE_H

#include "MayaCompositionChecks.h"
#include <array>
#include <cmath>
#include <cstddef>

// The counted, immutable-at-call-boundary transport shared by Java and the
// DirectComposition JNI owner. All geometry is in the visual's local space;
// presentation and native pointers remain owned by the window's composition.
namespace swt_maya {

constexpr int SCENE_FLOATS = 14;
constexpr int SCENE_CLIP = 1;
constexpr float SCENE_LIMIT = 1048576.0f;

inline bool validSceneArrays(int count, int handles, int floats, int flags) noexcept {
    return count >= 0 && handles >= 0 && floats >= 0 && flags >= 0
        && count <= handles && count <= flags && count <= floats / SCENE_FLOATS;
}

inline bool validSceneLayer(const float *v, int flags) noexcept {
    if (!v || (flags & ~SCENE_CLIP) != 0 || flags < 0
            || !validLayerProperties(v[0], v[1], v[2], v[3])) return false;
    for (int i = 4; i < SCENE_FLOATS; ++i) {
        if (!std::isfinite(v[i]) || std::fabs(v[i]) > SCENE_LIMIT) return false;
    }
    return (flags & SCENE_CLIP) == 0 || (v[10] <= v[12] && v[11] <= v[13]);
}

// A valid cache represents only a successfully committed native visual state.
// Different disabled-clip coordinates have identical presentation semantics.
struct SceneLayerState {
    std::array<float, 6> matrix{{1, 0, 0, 1, 0, 0}};
    std::array<float, 4> clip{{0, 0, 0, 0}};
    bool clipped = false;
    bool valid = false;

    bool sameMatrix(const float *v) const noexcept {
        if (!valid) return false;
        for (int i = 0; i < 6; ++i) if (matrix[static_cast<std::size_t>(i)] != v[4 + i]) return false;
        return true;
    }

    bool sameClip(const float *v, int flags) const noexcept {
        const bool enabled = (flags & SCENE_CLIP) != 0;
        if (!valid || clipped != enabled) return false;
        if (!enabled) return true;
        for (int i = 0; i < 4; ++i) if (clip[static_cast<std::size_t>(i)] != v[10 + i]) return false;
        return true;
    }

    void accept(const float *v, int flags) noexcept {
        for (int i = 0; i < 6; ++i) matrix[static_cast<std::size_t>(i)] = v[4 + i];
        for (int i = 0; i < 4; ++i) clip[static_cast<std::size_t>(i)] = v[10 + i];
        clipped = (flags & SCENE_CLIP) != 0;
        valid = true;
    }

    void invalidate() noexcept { valid = false; }
};

} // namespace swt_maya
#endif
