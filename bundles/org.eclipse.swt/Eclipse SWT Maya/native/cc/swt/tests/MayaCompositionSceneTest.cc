/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaCompositionScene.h"
#include <cassert>
#include <cstdint>
#include <iostream>
#include <limits>

int main() {
    using namespace swt_maya;
    assert(validSceneArrays(0, 0, 0, 0));
    assert(validSceneArrays(2, 4, 28, 2));
    assert(validSceneArrays(1, 2, 28, 2));
    assert(validSceneArrays(MAX_SCENE_LAYERS, MAX_SCENE_LAYERS,
                            MAX_SCENE_LAYERS * SCENE_FLOATS, MAX_SCENE_LAYERS));
    assert(!validSceneArrays(MAX_SCENE_LAYERS + 1, MAX_SCENE_LAYERS + 1,
                             (MAX_SCENE_LAYERS + 1) * SCENE_FLOATS,
                             MAX_SCENE_LAYERS + 1));
    assert(!validSceneArrays(-1, 2, 28, 2));
    assert(!validSceneArrays(2, 1, 28, 2));
    assert(!validSceneArrays(2, 2, 27, 2));
    assert(!validSceneArrays(2, 2, 28, 1));
    assert(!validSceneArrays(std::numeric_limits<int>::max(),
                             std::numeric_limits<int>::max(),
                             std::numeric_limits<int>::max(),
                             std::numeric_limits<int>::max()));

    float v[SCENE_FLOATS] = {20, 10, 0.5f, 1, 1, 0, 0, 1, 0, 0, 0, 0, 100, 80};
    assert(validSceneLayer(v, 0));
    assert(validSceneLayer(v, SCENE_CLIP));
    assert(!validSceneLayer(v, 2));
    assert(!validSceneLayer(nullptr, 0));
    SceneLayerState state;
    assert(!state.sameMatrix(v));
    assert(!state.sameClip(v, SCENE_CLIP));
    state.accept(v, SCENE_CLIP);
    assert(state.sameMatrix(v));
    assert(state.sameClip(v, SCENE_CLIP));
    assert(!state.sameClip(v, 0));
    v[10] = 99;
    assert(!state.sameClip(v, SCENE_CLIP));
    v[10] = 0;
    v[4] = 2;
    assert(!state.sameMatrix(v));
    v[4] = 1;
    v[10] = 101;
    assert(!validSceneLayer(v, SCENE_CLIP)); // clip is reversed
    assert(validSceneLayer(v, 0)); // disabled clip has no bounds semantics
    v[10] = 0;
    v[6] = std::numeric_limits<float>::infinity();
    assert(!validSceneLayer(v, 0));
    v[6] = 0;
    v[12] = std::numeric_limits<float>::quiet_NaN();
    assert(!validSceneLayer(v, 0));
    v[12] = 100;
    v[5] = SCENE_LIMIT + 1;
    assert(!validSceneLayer(v, 0));
    v[5] = 0;
    state.invalidate();
    assert(!state.sameMatrix(v) && !state.sameClip(v, SCENE_CLIP));

    std::uint32_t seed = 0x9E3779B9u;
    auto next = [&seed]() noexcept {
        seed ^= seed << 13;
        seed ^= seed >> 17;
        seed ^= seed << 5;
        return seed;
    };
    std::uint64_t checked = 0;
    for (int trial = 0; trial < 200000; ++trial) {
        v[0] = static_cast<float>(next() % 2000);
        v[1] = static_cast<float>(next() % 2000);
        v[2] = static_cast<float>(next() % 256) / 255.0f;
        v[3] = static_cast<float>(next() % 2);
        for (int i = 4; i < 10; ++i) v[i] = static_cast<float>(next() % 20000) / 100.0f - 100.0f;
        v[10] = static_cast<float>(next() % 100);
        v[11] = static_cast<float>(next() % 100);
        v[12] = v[10] + static_cast<float>(next() % 100);
        v[13] = v[11] + static_cast<float>(next() % 100);
        int flags = static_cast<int>(next() & 1u);
        assert(validSceneLayer(v, flags));
        ++checked;
        state.accept(v, flags);
        assert(state.sameMatrix(v));
        ++checked;
        assert(state.sameClip(v, flags));
        ++checked;
        if (flags == SCENE_CLIP) {
            const float old = v[10];
            v[10] += 1;
            assert(!state.sameClip(v, flags));
            v[10] = old;
        } else {
            v[10] += 1;
            assert(state.sameClip(v, flags)); // ignored clip lanes
            v[10] -= 1;
        }
        ++checked;
    }
    std::cout << "MayaCompositionScene: " << checked
              << " seeded validation and state assertions passed\n";
}
