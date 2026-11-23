/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "MayaSurfaceBudget.h"
#include <cassert>
#include <cstdint>
#include <iostream>
#include <limits>
#include <random>
#include <vector>

using swt_maya::SurfaceBudget;
int main() {
    static_assert(SurfaceBudget::FIELD_COUNT == 6, "stable JNI snapshot shape");
    SurfaceBudget budget;
    auto s = budget.snapshot();
    assert(s[0] == 1 && s[1] == 0 && s[2] == 0 && s[3] == 0);
    std::uint64_t bytes = 17;
    assert(!budget.reserve(0, 1, bytes) && bytes == 0);
    assert(!budget.reserve(-1, 2, bytes) && bytes == 0);
    assert(!SurfaceBudget::logicalBytes(INT32_MAX, INT32_MAX, bytes));
    assert(!budget.setLimit(std::numeric_limits<std::uint64_t>::max()));
    assert(budget.setLimit(48));
    assert(budget.reserve(2, 3, bytes) && bytes == 24);
    assert(budget.reserve(2, 3, bytes) && bytes == 24);
    assert(!budget.reserve(2, 3, bytes) && bytes == 0);
    assert(!budget.setLimit(47));
    s = budget.snapshot();
    assert(s[SurfaceBudget::LIMIT_BYTES] == 48);
    assert(s[SurfaceBudget::RESERVED_BYTES] == 48);
    assert(s[SurfaceBudget::HIGH_WATER_BYTES] == 48);
    assert(s[SurfaceBudget::DENIALS] == 3); // two bad dimensions + cap
    assert(s[SurfaceBudget::ACCEPTED] == 2);
    assert(!budget.release(100) && budget.snapshot()[2] == 48);
    assert(budget.release(24) && budget.snapshot()[2] == 24);
    assert(budget.setLimit(24));
    assert(!budget.reserve(1, 1, bytes));
    assert(budget.release(24));
    assert(!budget.release(24));
    assert(budget.setLimit(0));
    assert(budget.reserve(2, 3, bytes) && bytes == 24);
    assert(budget.release(bytes));

    // Independent int64 oracle of bounded reservations and release ordering.
    SurfaceBudget bulk;
    std::mt19937_64 random(0x20261118ULL);
    std::vector<std::uint64_t> outstanding;
    std::uint64_t used = 0, high = 0, denied = 0, admitted = 0;
    std::uint64_t limit = 1024;
    assert(bulk.setLimit(limit));
    for (int i = 0; i < 100000; ++i) {
        const auto op = static_cast<int>(random() % 7);
        if (op == 0) {
            const std::uint64_t next = (random() % 8) * 128;
            const bool possible = next == 0 || next >= used;
            assert(bulk.setLimit(next) == possible);
            if (possible) limit = next;
        } else if (op == 1 && !outstanding.empty()) {
            const auto index = static_cast<std::size_t>(random() % outstanding.size());
            const auto retiring = outstanding[index];
            assert(bulk.release(retiring));
            used -= retiring;
            outstanding[index] = outstanding.back();
            outstanding.pop_back();
        } else {
            const int width = static_cast<int>(random() % 20) - 1;
            const int height = static_cast<int>(random() % 20) - 1;
            const bool valid = width > 0 && height > 0;
            const std::uint64_t expectedBytes = valid ?
                    static_cast<std::uint64_t>(width) * height * 4 : 0;
            const bool expected = valid && (!limit || expectedBytes <= limit - used);
            const bool result = bulk.reserve(width, height, bytes);
            assert(result == expected);
            if (result) {
                assert(bytes == expectedBytes);
                outstanding.push_back(bytes);
                used += bytes;
                if (used > high) high = used;
                admitted++;
            } else {
                assert(bytes == 0);
                denied++;
            }
        }
        s = bulk.snapshot();
        assert(s[SurfaceBudget::LIMIT_BYTES] == limit);
        assert(s[SurfaceBudget::RESERVED_BYTES] == used);
        assert(s[SurfaceBudget::HIGH_WATER_BYTES] == high);
        assert(s[SurfaceBudget::DENIALS] == denied);
        assert(s[SurfaceBudget::ACCEPTED] == admitted);
    }
    for (auto bytesToRetire : outstanding) {
        assert(bulk.release(bytesToRetire));
    }
    assert(bulk.snapshot()[SurfaceBudget::RESERVED_BYTES] == 0);
    std::cout << "MayaSurfaceBudgetTest PASS: 100000 deterministic reservations/releases\n";
}
