/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_SURFACE_BUDGET_H
#define SWT_MAYA_SURFACE_BUDGET_H

#include <array>
#include <cstddef>
#include <cstdint>
#include <limits>

// Logical uncompressed BGRA surface reservations, NOT physical GPU memory.
// Per-composition, UI-owner-thread confined, allocation-free and opt-in.
// The default limit of zero disables quota enforcement while keeping totals.
namespace swt_maya {
class SurfaceBudget {
public:
    enum Field : std::size_t {
        VERSION = 0, LIMIT_BYTES = 1, RESERVED_BYTES = 2,
        HIGH_WATER_BYTES = 3, DENIALS = 4, ACCEPTED = 5,
        FIELD_COUNT = 6
    };
    static constexpr std::uint64_t ABI_VERSION = 1;
    static constexpr std::uint64_t MAX_VALUE =
            static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max());
    using Snapshot = std::array<std::uint64_t, FIELD_COUNT>;

private:
    std::uint64_t limit_ = 0;
    std::uint64_t reserved_ = 0;
    std::uint64_t peak_ = 0;
    std::uint64_t denials_ = 0;
    std::uint64_t accepted_ = 0;

    static void increase(std::uint64_t &counter) noexcept {
        if (counter < MAX_VALUE) ++counter;
    }

public:
    // A zero limit means unlimited. A nonzero limit cannot be reduced below
    // the currently reserved image surfaces; refusal leaves the old limit.
    bool setLimit(std::uint64_t maximum) noexcept {
        if (maximum > MAX_VALUE || (maximum && maximum < reserved_)) return false;
        limit_ = maximum;
        return true;
    }

    static bool logicalBytes(int width, int height, std::uint64_t &bytes) noexcept {
        bytes = 0;
        if (width <= 0 || height <= 0) return false;
        const auto pixels = static_cast<std::uint64_t>(width) *
                static_cast<std::uint64_t>(height);
        if (pixels > MAX_VALUE / 4) return false;
        bytes = pixels * 4; // DXGI_FORMAT_B8G8R8A8_UNORM
        return true;
    }

    // Reserve before COM CreateSurface. Caller must release the exact granted
    // byte count on every failure/retirement path. Denied reservations leave
    // both the live count and the caller's output at zero/unchanged.
    bool reserve(int width, int height, std::uint64_t &grantedBytes) noexcept {
        grantedBytes = 0;
        std::uint64_t bytes = 0;
        if (!logicalBytes(width, height, bytes) ||
                bytes > MAX_VALUE - reserved_ ||
                (limit_ && (reserved_ > limit_ || bytes > limit_ - reserved_))) {
            increase(denials_);
            return false;
        }
        reserved_ += bytes;
        if (reserved_ > peak_) peak_ = reserved_;
        increase(accepted_);
        grantedBytes = bytes;
        return true;
    }

    // If given a stale/duplicate amount, refuse underflow and do not mutate.
    bool release(std::uint64_t bytes) noexcept {
        if (!bytes || bytes > reserved_) return false;
        reserved_ -= bytes;
        return true;
    }

    Snapshot snapshot() const noexcept {
        return {ABI_VERSION, limit_, reserved_, peak_, denials_, accepted_};
    }
};
} // namespace swt_maya
#endif
