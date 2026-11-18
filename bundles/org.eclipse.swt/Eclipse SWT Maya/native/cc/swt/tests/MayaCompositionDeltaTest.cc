/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaCompositionDelta.h"
#include <array>
#include <cassert>
#include <cstdint>
#include <iostream>
#include <utility>
#include <vector>

namespace {
struct Layer { int id; };
}

int main() {
    using swt_maya::CompositionFrameDelta;
    using swt_maya::CommittedLayerProperties;

    Layer a{1}, b{2}, c{3}, d{4};
    CompositionFrameDelta<Layer> frame;
    frame.begin();
    assert(frame.requiresRebuild()); // the first frame must publish a root
    frame.accept();
    frame.begin();
    assert(!frame.requiresRebuild()); // no-op empty frame
    frame.includeVisible(&a);
    frame.includeVisible(&b);
    assert(frame.requiresRebuild());
    frame.accept();
    frame.begin();
    frame.includeVisible(&a);
    frame.includeVisible(&b);
    assert(!frame.requiresRebuild()); // properties alone do not reorder
    frame.begin();
    frame.includeVisible(&b);
    frame.includeVisible(&a);
    assert(frame.requiresRebuild()); // Z swap
    frame.accept();
    frame.begin();
    frame.includeVisible(&b);
    assert(frame.requiresRebuild()); // one layer hidden or removed
    frame.accept();
    frame.begin();
    frame.includeVisible(&b);
    assert(!frame.requiresRebuild());
    frame.invalidate();
    assert(frame.requiresRebuild()); // failed native transaction forces replay
    frame.accept();

    CommittedLayerProperties property;
    assert(!property.sameX(0.0f) && !property.sameY(0.0f));
    assert(!property.sameOpacity(1.0f));
    property.accept(1.0f, -2.0f, 0.5f);
    assert(property.sameX(1.0f) && property.sameY(-2.0f));
    assert(property.sameOpacity(0.5f));
    assert(!property.sameY(0.0f) && !property.sameOpacity(1.0f));
    property.invalidate();
    assert(!property.sameX(1.0f)); // no state survives a failed commit

    // Deterministic differential oracle against an independently maintained
    // committed visible list. The order/membership signature has four slots.
    std::array<Layer *, 4> identities{{&a, &b, &c, &d}};
    std::vector<Layer *> committed;
    bool valid = false;
    frame.invalidate();
    std::uint32_t seed = 0x9E3779B9u;
    auto next = [&seed]() noexcept {
        seed ^= seed << 13;
        seed ^= seed >> 17;
        seed ^= seed << 5;
        return seed;
    };
    int checks = 0;
    for (int step = 0; step < 200000; ++step) {
        std::array<Layer *, 4> order = identities;
        for (int i = 3; i > 0; --i) {
            const int j = static_cast<int>(next() % static_cast<std::uint32_t>(i + 1));
            std::swap(order[static_cast<std::size_t>(i)], order[static_cast<std::size_t>(j)]);
        }
        frame.begin();
        std::vector<Layer *> desired;
        for (Layer *candidate : order) {
            if ((next() & 3u) != 0u) {
                frame.includeVisible(candidate);
                desired.push_back(candidate);
            }
        }
        if (step % 131 == 0) {
            frame.invalidate();
            valid = false;
        }
        const bool expected = !valid || desired != committed;
        assert(frame.requiresRebuild() == expected);
        ++checks;
        if (expected) {
            // A successful COM transaction is the only way to publish an order.
            frame.accept();
            committed = desired;
            valid = true;
        }
        frame.begin();
        for (Layer *candidate : committed) frame.includeVisible(candidate);
        assert(!frame.requiresRebuild());
        ++checks;
    }

    // Million-scale logical scenes are not constructed as OS visuals here.
    // Exercise the production order planner with a bounded 100k-layer native
    // snapshot and an extreme Z move, without quadratic comparisons.
    std::vector<Layer> large(100000);
    CompositionFrameDelta<Layer> many;
    for (int replay = 0; replay < 12; ++replay) {
        many.begin();
        for (Layer &item : large) many.includeVisible(&item);
        assert(many.requiresRebuild() == (replay == 0));
        if (replay == 0) many.accept();
    }
    many.begin();
    many.includeVisible(&large.back());
    for (std::size_t i = 0; i + 1 < large.size(); ++i) {
        many.includeVisible(&large[i]);
    }
    assert(many.requiresRebuild());
    many.accept();
    many.begin();
    many.includeVisible(&large.back());
    for (std::size_t i = 0; i + 1 < large.size(); ++i) {
        many.includeVisible(&large[i]);
    }
    assert(!many.requiresRebuild());

    std::cout << "MayaCompositionDelta: " << checks
              << " deterministic differential assertions passed\n";
}
