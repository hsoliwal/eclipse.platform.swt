/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_NATIVE_HANDLE_REGISTRY_H
#define SWT_MAYA_NATIVE_HANDLE_REGISTRY_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <type_traits>
#include <unordered_map>

/*
 * Typed, monotonically issued JNI jlong tokens for one native process.
 *
 * Before this registry, Maya returned raw Composition* and Layer* addresses,
 * checked against live unordered_sets. An allocator may recycle the address
 * of a freed layer/composition, allowing an obsolete Java long to resolve to
 * a different live object (the ABA identity problem).
 *
 * Tokens are positive signed 64-bit values: high byte KIND, low 56 bits
 * a nonzero monotonically increasing issuance counter. Each native UI thread
 * owns its registry, while issuance is process-wide across those registries.
 * No token is recycled, even when allocation fails or native addresses repeat.
 * Fail closed at exhaustion rather than wrap. Different object kinds cannot
 * resolve each other's handles. The JVM observes opaque longs, not pointers.
 *
 * Each registry instance is confined to its native compositor owner thread.
 * Only issuance is shared and atomic; lookups/retirement remain lock-free on
 * their owner thread. A token from another Display cannot resolve here.
 * Do not access one registry instance concurrently from multiple threads.
 */
namespace swt_maya {

template<class T, std::uint8_t Kind>
class TokenRegistry {
    static_assert(Kind != 0 && Kind < 0x80,
            "positive signed JNI handles require a nonzero seven-bit kind");
    static constexpr std::uint64_t COUNTER_MASK = (std::uint64_t{1} << 56) - 1;
    static constexpr std::uint64_t KIND_BITS = std::uint64_t{Kind} << 56;

    std::unordered_map<std::uint64_t, T *> live_;
    inline static std::atomic<std::uint64_t> next_{1};

public:
    TokenRegistry() = default;
    TokenRegistry(const TokenRegistry &) = delete;
    TokenRegistry &operator=(const TokenRegistry &) = delete;

    /** Returns zero only when exhausted or given a null pointer. May throw bad_alloc. */
    std::uint64_t admit(T *object) {
        if (!object) return 0;
        // Issue one unique process-wide token across all display threads.
        // CAS prevents counter wrap and preserves retired-token non-reuse.
        std::uint64_t next = next_.load(std::memory_order_relaxed);
        while (next <= COUNTER_MASK && !next_.compare_exchange_weak(
                next, next + 1, std::memory_order_relaxed,
                std::memory_order_relaxed)) {
            // A competing admission wrote the new counter into next.
        }
        if (next > COUNTER_MASK) return 0;
        // Consume identity before allocating the table entry: even an
        // insertion exception never allows its token to be reissued.
        const std::uint64_t token = KIND_BITS | next;
        live_.emplace(token, object);
        return token;
    }

    /** Never interprets an untrusted Java integer as a process pointer. */
    T *get(std::uint64_t token) const noexcept {
        if ((token >> 56) != Kind || (token & COUNTER_MASK) == 0) return nullptr;
        const auto entry = live_.find(token);
        return entry == live_.end() ? nullptr : entry->second;
    }

    /** Retires only the expected owner; a stale or foreign token cannot erase another. */
    bool retire(std::uint64_t token, const T *expected) noexcept {
        if (!expected) return false;
        const auto entry = live_.find(token);
        if (entry == live_.end() || entry->second != expected) return false;
        live_.erase(entry);
        return true;
    }

    std::size_t liveCount() const noexcept { return live_.size(); }
};

} // namespace swt_maya
#endif
