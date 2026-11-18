/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "MayaFrameView.h"
#include <cassert>
#include <cmath>
#include <cstring>
#include <iostream>
#include <limits>
#include <random>
#include <vector>

using namespace swt_maya_frame;

static bool identical(Values a, Values b) {
    return a.handle == b.handle && a.x == b.x && a.y == b.y &&
            a.opacity == b.opacity && a.visible == b.visible;
}

int main() {
    std::mt19937_64 rng(0x98b2aa35d55aU);
    for (int trial = 0; trial < 10000; trial++) {
        const std::size_t count = static_cast<std::size_t>(rng() % 1024);
        std::vector<std::int64_t> handles(count);
        std::vector<float> props(count * 4);
        Frame packet;
        packet.layers.resize(count);
        for (std::size_t i = 0; i < count; i++) {
            std::uint64_t bits = rng() | 1ULL;
            std::memcpy(handles.data() + i, &bits, sizeof(bits));
            const float x = static_cast<float>(static_cast<std::int32_t>(rng())) / 32.0f;
            const float y = static_cast<float>(static_cast<std::int32_t>(rng())) / 32.0f;
            const float alpha = static_cast<float>(rng() % 256) / 255.0f;
            const std::uint32_t visible = static_cast<std::uint32_t>(rng() & 1ULL);
            props[i * 4 + 0] = x;
            props[i * 4 + 1] = y;
            props[i * 4 + 2] = alpha;
            props[i * 4 + 3] = static_cast<float>(visible);
            packet.layers[i] = {bits, x, y, alpha, visible};
        }
        LegacyView<std::int64_t, float> legacy{handles.data(), props.data(), count};
        PackedView packed{packet};
        assert(legacy.size() == packed.size());
        for (std::size_t i = 0; i < count; i++) assert(identical(legacy.at(i), packed.at(i)));
    }
    // Exact opaque sign-bit preservation, including all-ones handle.
    const std::uint64_t bitPattern = std::numeric_limits<std::uint64_t>::max();
    std::int64_t opaque;
    std::memcpy(&opaque, &bitPattern, sizeof(opaque));
    const float props[4] = {-0.0f, 1.0f, 0.25f, 1.0f};
    const LegacyView<std::int64_t, float> legacy{&opaque, props, 1};
    Frame decoded;
    decoded.layers.push_back({bitPattern, props[0], props[1], props[2], 1});
    const PackedView packed{decoded};
    assert(identical(legacy.at(0), packed.at(0)));
    std::cout << "MayaFrameViewTest PASS: 10000 seeded frames, exact legacy/packed parity\n";
}
