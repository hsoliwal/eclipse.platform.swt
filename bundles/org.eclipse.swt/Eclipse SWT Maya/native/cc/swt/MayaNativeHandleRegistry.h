/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#ifndef SWT_MAYA_NATIVE_HANDLE_REGISTRY_H
#define SWT_MAYA_NATIVE_HANDLE_REGISTRY_H

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
 * a nonzero monotonically increasing issuance counter. Each live native
 * owner has exactly one process-lifetime registry per kind. No token is
 * recycled, even when an allocation fails or native memory addresses repeat.
 * Fail closed at exhaustion rather than wrap. Different object kinds cannot
 * resolve each other's handles. The JVM observes opaque longs, not pointers.
 *
 * Registry use is confined to the original SWT UI / native compositor owner
 * thread, exactly like the pre-existing live native pointer sets. This class
 * does not make unsynchronized lifetime changes across threads safe.
 */
namespace swt_maya {

template<class T, std::uint8_t Kind>
class TokenRegistry {
    static_assert(Kind != 0 && Kind < 0x80,
            "positive signed JNI handles require a nonzero seven-bit kind");
    static constexpr std::uint64_t COUNTER_MASK = (std::uint64_t{1} << 56) - 1;
    static constexpr std::uint64_t KIND_BITS = std::uint64_t{Kind} << 56;

    std::unordered_map<std::uint64_t, T *> live_;
    std::uint64_t next_ = 1;

public:
    TokenRegistry() = default;
    TokenRegistry(const TokenRegistry &) = delete;
    TokenRegistry &operator=(const TokenRegistry &) = delete;

    /** Returns zero only when exhausted or given a null pointer. May throw bad_alloc. */
    std::uint64_t admit(T *object) {
        if (!object || next_ > COUNTER_MASK) return 0;
        // Consume identity before allocating the table entry: even an
        // insertion exception never allows its token to be reissued.
        const std::uint64_t token = KIND_BITS | next_++;
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
