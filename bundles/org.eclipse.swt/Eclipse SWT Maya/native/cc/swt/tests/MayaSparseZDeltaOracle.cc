/* Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 */
#include "../MayaCompositionDelta.h"
#include <algorithm>
#include <array>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <vector>
namespace {
struct Layer { int id; };
using Frame = swt_maya::CompositionFrameDelta<Layer>;
// Model the exact documented IDCompositionVisual::AddVisual arguments:
//   TRUE + nullptr -> bottom; FALSE + nullptr -> top;
//   TRUE + sibling -> above it; FALSE + sibling -> below it.
// This helper intentionally accepts BOOL semantics, not a precomputed index.
void addVisual(std::vector<Layer *> &screen, Layer *visual,
        bool insertAbove, Layer *reference) {
    auto at = reference == nullptr ? screen.end()
            : std::find(screen.begin(), screen.end(), reference);
    if (reference == nullptr) {
        at = insertAbove ? screen.begin() : screen.end();
    } else {
        assert(at != screen.end());
        if (insertAbove) ++at;
    }
    screen.insert(at, visual);
}
// Simulate the COM parent; invoke the identical C++ helper as production Maya.
struct MockVisualParent {
    std::vector<Layer *> &screen;
    int AddVisual(Layer *visual, bool insertAbove, Layer *reference) {
        addVisual(screen, visual, insertAbove, reference);
        return 0;
    }
};
void apply(std::vector<Layer *> &screen, const std::vector<Layer *> &desired,
        const Frame &frame, Frame::ZPlan plan) {
    if (plan == Frame::ZPlan::Unchanged) {
        assert(screen == desired);
        return;
    }
    if (plan == Frame::ZPlan::Full) {
        screen.clear();
        for (Layer *visual : desired) addVisual(screen, visual, false, nullptr);
        assert(screen == desired);
        return;
    }
    for (Layer *removed : frame.zRemovals()) {
        auto at = std::find(screen.begin(), screen.end(), removed);
        assert(at != screen.end());
        screen.erase(at);
    }
    for (const auto &insert : frame.zInsertions()) {
        MockVisualParent parent{screen};
        const int result = swt_maya::addVisualAboveLowerSibling(
                &parent, insert.layer, insert.lowerSibling);
        assert(result == 0);
    }
    assert(screen == desired);
}
} // namespace
int main() {
    std::array<Layer, 66> store{};
    for (int i = 0; i < 66; ++i) store[static_cast<std::size_t>(i)].id = i;
    Frame frame;
    std::vector<Layer *> screen;
    frame.begin();
    for (int i = 0; i < 6; ++i) frame.includeVisible(&store[static_cast<std::size_t>(i)]);
    assert(frame.planZOrder() == Frame::ZPlan::Full);
    frame.accept();
    for (int i = 0; i < 6; ++i) screen.push_back(&store[static_cast<std::size_t>(i)]);
    frame.begin();
    for (int i = 1; i < 6; ++i) frame.includeVisible(&store[static_cast<std::size_t>(i)]);
    frame.includeVisible(&store[0]);
    auto plan = frame.planZOrder();
    assert(plan == Frame::ZPlan::Sparse);
    assert(frame.zRemovals().size() == 1 && frame.zInsertions().size() == 1);
    assert(frame.zInsertions()[0].layer == &store[0]);
    assert(frame.zInsertions()[0].lowerSibling == &store[5]);
    std::vector<Layer *> desired;
    for (int i = 1; i < 6; ++i) desired.push_back(&store[static_cast<std::size_t>(i)]);
    desired.push_back(&store[0]);
    // Negative regression: the old Windows FALSE + non-null sibling
    // compiles and reports success but produces the wrong native order.
    std::vector<Layer *> incorrect = screen;
    auto oldTop = std::find(incorrect.begin(), incorrect.end(), &store[0]);
    assert(oldTop != incorrect.end());
    incorrect.erase(oldTop);
    addVisual(incorrect, &store[0], false, &store[5]);
    assert(incorrect != desired);
    apply(screen, desired, frame, plan);
    frame.accept();
    std::uint32_t seed = 0x9E3779B9u;
    auto next = [&seed]() noexcept {
        seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5;
        return seed;
    };
    std::uint64_t frames = 0, sparse = 0, full = 0, noop = 0;
    for (int n = 0; n <= 64; n += n < 9 ? 1 : n < 32 ? 7 : 16) {
        Frame owned;
        std::vector<Layer *> present;
        for (int replay = 0; replay < 5000; ++replay) {
            std::vector<Layer *> wanted;
            for (int i = 0; i < n; ++i) wanted.push_back(&store[static_cast<std::size_t>(i)]);
            for (int i = n - 1; i > 0; --i) {
                std::swap(wanted[static_cast<std::size_t>(i)],
                        wanted[static_cast<std::size_t>(next() % static_cast<std::uint32_t>(i + 1))]);
            }
            wanted.erase(std::remove_if(wanted.begin(), wanted.end(),
                    [&](Layer *) { return (next() & 7u) == 0u; }), wanted.end());
            if (replay % 31 == 0) wanted = present;
            owned.begin();
            for (Layer *layer : wanted) owned.includeVisible(layer);
            if (replay % 277 == 0) owned.invalidate();
            auto decision = owned.planZOrder();
            if (decision == Frame::ZPlan::Sparse) ++sparse;
            else if (decision == Frame::ZPlan::Full) ++full;
            else ++noop;
            apply(present, wanted, owned, decision);
            if (decision != Frame::ZPlan::Unchanged) owned.accept();
            ++frames;
        }
    }
    assert(frames == 75000 && sparse > 100 && full > 0 && noop > 0);
    Frame large;
    large.begin();
    for (int i = 0; i < 66; ++i) large.includeVisible(&store[static_cast<std::size_t>(i)]);
    assert(large.planZOrder() == Frame::ZPlan::Full);
    large.accept();
    large.begin();
    large.includeVisible(&store[65]);
    for (int i = 0; i < 65; ++i) large.includeVisible(&store[static_cast<std::size_t>(i)]);
    assert(large.planZOrder() == Frame::ZPlan::Full);
    std::cout << "MayaSparseZDeltaOracle PASS " << frames << " frames; sparse "
              << sparse << " full " << full << " noop " << noop << "\n";
}
