/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaCompositionTelemetry.h"

#include <cassert>
#include <cstdint>
#include <iostream>
#include <limits>

using swt_maya::CompositionTelemetry;

int main() {
    static_assert(CompositionTelemetry::FIELD_COUNT == 16, "version-1 JNI array shape");
    CompositionTelemetry t;
    auto empty = t.snapshot(0, false);
    assert(empty[CompositionTelemetry::ABI_VERSION] == 1);
    for (std::size_t i = 1; i < empty.size(); i++) assert(empty[i] == 0);

    t.attemptFrame();
    t.committedFrame(true);
    t.fullUpload(9);
    t.dirtyPatch(4);
    t.createdLayer();
    t.deviceProbe(false);
    auto first = t.snapshot(1, true);
    assert(first[CompositionTelemetry::FRAME_ATTEMPTS] == 1);
    assert(first[CompositionTelemetry::FRAME_COMMITS] == 1);
    assert(first[CompositionTelemetry::Z_REBUILDS] == 1);
    assert(first[CompositionTelemetry::FULL_UPLOADS] == 1);
    assert(first[CompositionTelemetry::DIRTY_PATCHES] == 1);
    assert(first[CompositionTelemetry::UPLOAD_BYTES] == (9 + 4) * 4);
    assert(first[CompositionTelemetry::LIVE_LAYERS] == 1);
    assert(first[CompositionTelemetry::PENDING_SURFACE] == 1);
    assert(first[CompositionTelemetry::HEALTH_PROBES] == 1);
    assert(first[CompositionTelemetry::HEALTH_FAILURES] == 0);
    assert(t.snapshot(0, false)[CompositionTelemetry::PENDING_SURFACE] == 0);

    std::uint64_t rejected = 0, failedCommits = 0, noops = 0;
    std::uint64_t committed = 1, reordered = 1, bytes = 52;
    for (int i = 0; i < 100000; i++) {
        t.attemptFrame();
        if (i % 5 == 0) {
            t.noopFrame();
            noops++;
        } else if (i % 5 == 1) {
            t.committedFrame(false);
            committed++;
        } else if (i % 5 == 2) {
            t.committedFrame(true);
            committed++;
            reordered++;
        } else if (i % 5 == 3) {
            t.rejectFrame();
            rejected++;
        } else {
            t.failedCommit();
            rejected++;
            failedCommits++;
        }
        t.deviceProbe(i % 11 == 0);
        if (i % 4 == 0) {
            t.dirtyPatch(2);
            bytes += 8;
        }
    }
    t.retiredLayer();
    auto later = t.snapshot(0, false);
    assert(later[CompositionTelemetry::FRAME_ATTEMPTS] == 100001);
    assert(later[CompositionTelemetry::FRAME_COMMITS] == committed);
    assert(later[CompositionTelemetry::FRAME_NOOPS] == noops);
    assert(later[CompositionTelemetry::Z_REBUILDS] == reordered);
    assert(later[CompositionTelemetry::FRAME_REJECTIONS] == rejected);
    assert(later[CompositionTelemetry::COMMIT_FAILURES] == failedCommits);
    assert(later[CompositionTelemetry::UPLOAD_BYTES] == bytes);
    assert(later[CompositionTelemetry::DIRTY_PATCHES] == 1 + 25000);
    assert(later[CompositionTelemetry::HEALTH_PROBES] == 100001);
    assert(later[CompositionTelemetry::HEALTH_FAILURES] == (100000 + 10) / 11);
    assert(later[CompositionTelemetry::LAYERS_CREATED] == 1);
    assert(later[CompositionTelemetry::LAYERS_RETIRED] == 1);
    assert(later[CompositionTelemetry::LIVE_LAYERS] == 0);
    assert(later[CompositionTelemetry::PENDING_SURFACE] == 0);

    CompositionTelemetry overflow;
    overflow.fullUpload(std::numeric_limits<std::uint64_t>::max());
    overflow.fullUpload(42);
    auto saturated = overflow.snapshot(std::numeric_limits<std::uint64_t>::max(), true);
    assert(saturated[CompositionTelemetry::UPLOAD_BYTES] == CompositionTelemetry::LIMIT);
    assert(saturated[CompositionTelemetry::LIVE_LAYERS] == CompositionTelemetry::LIMIT);
    assert(saturated[CompositionTelemetry::PENDING_SURFACE] == 1);
    assert(saturated[CompositionTelemetry::FULL_UPLOADS] == 2);

    std::cout << "MayaCompositionTelemetry PASS: 100000 differential counter events and saturation\n";
    return 0;
}
