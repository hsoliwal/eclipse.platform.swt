/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_COMPOSITION_TELEMETRY_H
#define SWT_MAYA_COMPOSITION_TELEMETRY_H

#include <array>
#include <cstddef>
#include <cstdint>
#include <limits>

/*
 * Opt-in, allocation-free snapshot counters owned by one native Maya
 * Composition. The SWT Display event thread already owns all mutation.
 * These are submission/copy counters, NOT actual GPU presentation, GPU
 * memory measurements, frame-rate or Windows diagnostics.
 *
 * All values are nonnegative signed-64-safe and saturate at INT64_MAX.
 * The stable 16-slot layout is shared with MayaComposition.java. A caller
 * explicitly requests a snapshot through one optional JNI method; there
 * is no per-frame JNI telemetry traffic or atomics.
 */
namespace swt_maya {

class CompositionTelemetry {
public:
    enum Field : std::size_t {
        ABI_VERSION = 0,
        FRAME_ATTEMPTS = 1,
        FRAME_COMMITS = 2,
        FRAME_NOOPS = 3,
        Z_REBUILDS = 4,
        FULL_UPLOADS = 5,
        DIRTY_PATCHES = 6,
        UPLOAD_BYTES = 7,
        HEALTH_FAILURES = 8,
        LAYERS_CREATED = 9,
        LAYERS_RETIRED = 10,
        FRAME_REJECTIONS = 11,
        COMMIT_FAILURES = 12,
        LIVE_LAYERS = 13,
        PENDING_SURFACE = 14,
        HEALTH_PROBES = 15,
        FIELD_COUNT = 16
    };
    static constexpr std::uint64_t VERSION = 1;
    static constexpr std::uint64_t LIMIT =
            static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max());
    using Snapshot = std::array<std::uint64_t, FIELD_COUNT>;

private:
    Snapshot counters_{};

    void add(Field field, std::uint64_t n = 1) noexcept {
        const std::size_t i = static_cast<std::size_t>(field);
        const std::uint64_t before = counters_[i];
        counters_[i] = n > LIMIT - before ? LIMIT : before + n;
    }

public:
    void attemptFrame() noexcept { add(FRAME_ATTEMPTS); }
    void rejectFrame() noexcept { add(FRAME_REJECTIONS); }

    /** Count only a SUCCESSFUL IDCompositionDevice::Commit in a frame. */
    void committedFrame(bool reordered) noexcept {
        add(FRAME_COMMITS);
        if (reordered) add(Z_REBUILDS);
    }
    void failedCommit() noexcept {
        add(COMMIT_FAILURES);
        add(FRAME_REJECTIONS);
    }
    void noopFrame() noexcept { add(FRAME_NOOPS); }

    /** An upload is counted after a successful EndDraw, not after Commit. */
    void fullUpload(std::uint64_t pixels) noexcept {
        add(FULL_UPLOADS);
        add(UPLOAD_BYTES, pixels > LIMIT / 4 ? LIMIT : pixels * 4);
    }
    /** Each successful dirty patch has its own successful EndDraw. */
    void dirtyPatch(std::uint64_t pixels) noexcept {
        add(DIRTY_PATCHES);
        add(UPLOAD_BYTES, pixels > LIMIT / 4 ? LIMIT : pixels * 4);
    }

    /** Counts synchronous DComp validity probes, including repeat failures. */
    void deviceProbe(bool failed) noexcept {
        add(HEALTH_PROBES);
        if (failed) add(HEALTH_FAILURES);
    }
    void createdLayer() noexcept { add(LAYERS_CREATED); }
    void retiredLayer() noexcept { add(LAYERS_RETIRED); }

    /** No allocations; generated fields reflect the current composition. */
    Snapshot snapshot(std::uint64_t liveLayers, bool pendingSurface) const noexcept {
        Snapshot result = counters_;
        result[ABI_VERSION] = VERSION;
        result[LIVE_LAYERS] = liveLayers > LIMIT ? LIMIT : liveLayers;
        result[PENDING_SURFACE] = pendingSurface ? 1 : 0;
        return result;
    }
};

} // namespace swt_maya
#endif
