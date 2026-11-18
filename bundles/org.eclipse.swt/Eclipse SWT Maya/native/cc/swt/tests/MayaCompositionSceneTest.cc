/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaCompositionScene.h"
#include "../MayaFrameView.h"
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <limits>

int main() {
    using namespace swt_maya;
    using namespace swt_maya_frame;
    static_assert(CAP_AFFINE_2D == 1 && CAP_LOCAL_RECT_CLIP == 2 && CAP_DIRTY_UPLOAD == 4);
    static_assert(SCENE_FLOATS == 14 && SCENE_CLIP == 1);
    assert(validSceneArrays(0, 0, 0, 0));
    assert(validSceneArrays(2, 4, 28, 2));
    assert(validSceneArrays(1, 2, 28, 2)); // only the counted prefixes are read
    assert(validSceneArrays(MAX_SCENE_LAYERS, MAX_SCENE_LAYERS,
                           MAX_SCENE_LAYERS * SCENE_FLOATS, MAX_SCENE_LAYERS));
    assert(!validSceneArrays(-1, 2, 28, 2));
    assert(!validSceneArrays(2, 1, 28, 2));
    assert(!validSceneArrays(2, 2, 27, 2));
    assert(!validSceneArrays(2, 2, 28, 1));
    assert(!validSceneArrays(0, -1, 0, 0));
    assert(!validSceneArrays(0, 0, -1, 0));
    assert(!validSceneArrays(0, 0, 0, -1));
    assert(!validSceneArrays(MAX_SCENE_LAYERS + 1, MAX_SCENE_LAYERS + 1,
                            (MAX_SCENE_LAYERS + 1) * SCENE_FLOATS, MAX_SCENE_LAYERS + 1));
    assert(!validSceneArrays(std::numeric_limits<int>::max(), std::numeric_limits<int>::max(),
                            std::numeric_limits<int>::max(), std::numeric_limits<int>::max()));

    float v[SCENE_FLOATS] = {-20, 10, 0.5f, 1, 2, 0, 0, 1, -5, 3, -10, -8, 100, 80};
    assert(validSceneLayer(v, 0));
    assert(validSceneLayer(v, SCENE_CLIP));
    assert(!validSceneLayer(nullptr, 0));
    assert(!validSceneLayer(v, 2));
    assert(!validSceneLayer(v, -1));
    assert(!validSceneLayer(v, std::numeric_limits<int>::max()));
    const float exceptional[] = {std::numeric_limits<float>::quiet_NaN(),
        std::numeric_limits<float>::infinity(), -std::numeric_limits<float>::infinity()};
    for (int lane = 0; lane < SCENE_FLOATS; ++lane) {
        const float before = v[lane];
        for (float value : exceptional) {
            v[lane] = value;
            assert(!validSceneLayer(v, SCENE_CLIP));
            assert(!validSceneLayer(v, 0)); // unused clip lanes still have defined finite data
        }
        v[lane] = before;
    }
    const float originalAlpha = v[2];
    for (float alpha : {0.0f, 1.0f, 128.0f / 255.0f}) {
        v[2] = alpha;
        assert(validSceneLayer(v, SCENE_CLIP));
    }
    for (float alpha : {-0.001f, 1.001f}) {
        v[2] = alpha;
        assert(!validSceneLayer(v, 0));
    }
    v[2] = originalAlpha;
    v[3] = 0;
    assert(validSceneLayer(v, 0));
    v[3] = 0.5f;
    assert(!validSceneLayer(v, 0));
    v[3] = 1;
    v[10] = v[12];
    v[11] = v[13];
    assert(validSceneLayer(v, SCENE_CLIP)); // an empty local clip is legal
    v[10] += 1;
    assert(!validSceneLayer(v, SCENE_CLIP));
    assert(validSceneLayer(v, 0)); // no rectangle semantics for a disabled clip
    v[10] = -10;
    v[11] = -8;
    for (int lane : {0, 1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}) {
        const float before = v[lane];
        v[lane] = SCENE_LIMIT;
        assert(validSceneLayer(v, 0));
        v[lane] = -SCENE_LIMIT;
        assert(validSceneLayer(v, 0));
        v[lane] = SCENE_LIMIT + 1;
        assert(!validSceneLayer(v, 0));
        v[lane] = -SCENE_LIMIT - 1;
        assert(!validSceneLayer(v, 0));
        v[lane] = before;
    }

    SceneLayerState state;
    assert(!state.sameMatrix(v) && !state.sameClip(v, SCENE_CLIP));
    state.accept(v, SCENE_CLIP);
    for (int lane : {0, 1, 4, 5, 6, 7, 8, 9}) {
        v[lane] += 1;
        assert(!state.sameMatrix(v));
        v[lane] -= 1;
        assert(state.sameMatrix(v));
    }
    for (int lane = 10; lane < SCENE_FLOATS; ++lane) {
        v[lane] += 1;
        assert(!state.sameClip(v, SCENE_CLIP));
        v[lane] -= 1;
        assert(state.sameClip(v, SCENE_CLIP));
    }
    assert(!state.sameClip(v, 0));
    state.accept(v, 0);
    v[10] += 1;
    assert(state.sameClip(v, 0));
    assert(!state.sameClip(v, SCENE_CLIP));
    v[10] -= 1;
    state.invalidate();
    assert(!state.sameMatrix(v) && !state.sameClip(v, 0));

    // A source point is transformed first, then translated by the physical
    // client origin. DirectComposition's visual offsets precede its matrix,
    // so native scene offsets are zero and the final origin belongs in it.
    const auto mappedPoint = [](const std::array<float, 6> &m, float x, float y) {
        return std::array<float, 2>{{x * m[0] + y * m[2] + m[4],
                                    x * m[1] + y * m[3] + m[5]}};
    };
    float geometry[SCENE_FLOATS] = {40, 60, 1, 1, 2, 0, 0, 3, 0, 0, 1, 1, 4, 5};
    assert((sceneMatrix(geometry) == std::array<float, 6>{{2, 0, 0, 3, 40, 60}}));
    assert((mappedPoint(sceneMatrix(geometry), 5, 7) == std::array<float, 2>{{50, 81}}));
    // The old direct SetOffset(40,60) + scale(2,3) would produce (90,201).
    state.accept(geometry, SCENE_CLIP);
    geometry[0] += 20;
    assert(!state.sameMatrix(geometry)); // pan must update the folded transform
    assert(state.sameClip(geometry, SCENE_CLIP)); // source-local clipping stays fixed
    assert((mappedPoint(sceneMatrix(geometry), 5, 7) == std::array<float, 2>{{70, 81}}));
    geometry[4] = 0; geometry[5] = 1; geometry[6] = -1; geometry[7] = 0;
    geometry[8] = 7; geometry[9] = -11;
    assert((mappedPoint(sceneMatrix(geometry), 5, 7) == std::array<float, 2>{{60, 54}}));
    geometry[0] = -20; geometry[1] = -40;
    geometry[4] = -2; geometry[5] = 0.5f; geometry[6] = 1.5f; geometry[7] = -3;
    geometry[8] = 8; geometry[9] = -4;
    assert((mappedPoint(sceneMatrix(geometry), 2, 6) == std::array<float, 2>{{-7, -61}}));
    geometry[4] = geometry[5] = geometry[6] = geometry[7] = 0;
    assert((mappedPoint(sceneMatrix(geometry), 2, 6) == std::array<float, 2>{{-12, -44}}));
    geometry[0] = geometry[1] = geometry[8] = geometry[9] = SCENE_LIMIT;
    assert(validSceneLayer(geometry, SCENE_CLIP));
    assert(std::isfinite(sceneMatrix(geometry)[4]) && std::isfinite(sceneMatrix(geometry)[5]));

    const std::uint64_t opaqueBits = std::numeric_limits<std::uint64_t>::max();
    std::int64_t handle;
    std::memcpy(&handle, &opaqueBits, sizeof(handle));
    const std::int32_t flag = SCENE_CLIP;
    const SceneView<std::int64_t, float, std::int32_t> scene{&handle, v, &flag, 1};
    const LegacyView<std::int64_t, float> legacy{&handle, v, 1};
    Frame frame;
    frame.layers.push_back({opaqueBits, v[0], v[1], v[2], 1});
    const PackedView packed{frame};
    const auto value = scene.at(0);
    assert(scene.size() == 1 && value.handle == opaqueBits);
    assert(value.scene == v && value.sceneFlags == SCENE_CLIP);
    for (const auto plain : {legacy.at(0), packed.at(0)}) {
        assert(plain.handle == value.handle && plain.x == value.x && plain.y == value.y);
        assert(plain.opacity == value.opacity && plain.visible == value.visible);
        assert(plain.scene == nullptr && plain.sceneFlags == 0); // mandatory legacy reset signal
    }

    // Several records ensure the new view does not accidentally use the old
    // four-float stride or alias adjacent flags/opaque handles.
    const std::int64_t handles[] = {11, 23, 47};
    std::int32_t flags[] = {0, SCENE_CLIP, 0};
    float records[3 * SCENE_FLOATS];
    for (int record = 0; record < 3; ++record) {
        for (int lane = 0; lane < SCENE_FLOATS; ++lane) records[record * SCENE_FLOATS + lane] = v[lane];
        records[record * SCENE_FLOATS] += record;
    }
    const SceneView<std::int64_t, float, std::int32_t> three{handles, records, flags, 3};
    for (std::size_t i = 0; i < three.size(); ++i) {
        const auto item = three.at(i);
        assert(item.handle == static_cast<std::uint64_t>(handles[i]));
        assert(item.scene == records + i * SCENE_FLOATS && item.sceneFlags == flags[i]);
        assert(item.x == v[0] + static_cast<float>(i));
        assert(validSceneLayer(item.scene, item.sceneFlags));
    }
    // Retain the seeded admission/cache coverage accepted with the scene ABI.
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
    std::cout << "MayaCompositionSceneTest PASS: counted ABI, finite bounds, affine order, clip, cache and three-view parity\n";
    std::cout << "MayaCompositionScene: " << checked
              << " seeded validation and state assertions passed\n";
}
