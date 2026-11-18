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
#include <cstdint>

namespace swt_maya {

// Scene v1 is an additive counted-array ABI. The packed v1 envelope is unchanged.
constexpr int SCENE_FLOATS = 14;
constexpr int MAX_SCENE_LAYERS = 65536;
constexpr int SCENE_CLIP = 1;
constexpr float SCENE_LIMIT = 1048576.0f;
constexpr std::uint64_t CAP_AFFINE_2D = 1;
constexpr std::uint64_t CAP_LOCAL_RECT_CLIP = 2;
constexpr std::uint64_t CAP_DIRTY_UPLOAD = 4;

inline bool validSceneArrays(int count, int handles, int floats, int flags) noexcept {
    return count >= 0 && count <= MAX_SCENE_LAYERS
        && handles >= 0 && floats >= 0 && flags >= 0
        && count <= handles && count <= flags && count <= floats / SCENE_FLOATS;
}

// Offsets are physical client pixels, the affine matrix maps source pixels,
// and clip coordinates are source-local. DirectComposition transforms the clip
// with the visual. Empty clips and negative coordinates are valid.
inline bool validSceneLayer(const float *v, int flags) noexcept {
    if (!v || (flags & ~SCENE_CLIP) != 0
            || !validLayerProperties(v[0], v[1], v[2], v[3])) return false;
    for (int i = 0; i < SCENE_FLOATS; ++i) {
        if (!std::isfinite(v[i]) || std::fabs(v[i]) > SCENE_LIMIT) return false;
    }
    return (flags & SCENE_CLIP) == 0 || (v[10] <= v[12] && v[11] <= v[13]);
}

// DirectComposition applies visual OffsetX/Y BEFORE its transform. The scene
// ABI instead gives a physical client origin AFTER the source-pixel affine
// map. Use zero native offsets and fold that origin into matrix translation;
// this also works for rotations, skew and singular matrices without inversion.
inline std::array<float, 6> sceneMatrix(const float *v) noexcept {
    return {{v[4], v[5], v[6], v[7], v[8] + v[0], v[9] + v[1]}};
}

// This snapshot describes only successfully committed visual properties.
// An attempted native mutation separately keeps sceneMayBeActive true until
// a legacy frame successfully resets both the transform and the clip.
struct SceneLayerState {
    std::array<float, 6> matrix{{1, 0, 0, 1, 0, 0}};
    std::array<float, 4> clip{{0, 0, 0, 0}};
    bool clipped = false;
    bool valid = false;

    bool sameMatrix(const float *v) const noexcept {
        return valid && matrix == sceneMatrix(v);
    }

    bool sameClip(const float *v, int flags) const noexcept {
        const bool enabled = (flags & SCENE_CLIP) != 0;
        if (!valid || clipped != enabled) return false;
        if (!enabled) return true;
        for (int i = 0; i < 4; ++i) if (clip[static_cast<std::size_t>(i)] != v[10 + i]) return false;
        return true;
    }

    void accept(const float *v, int flags) noexcept {
        matrix = sceneMatrix(v);
        for (int i = 0; i < 4; ++i) clip[static_cast<std::size_t>(i)] = v[10 + i];
        clipped = (flags & SCENE_CLIP) != 0;
        valid = true;
    }

    void invalidate() noexcept { valid = false; }
};

} // namespace swt_maya
#endif
