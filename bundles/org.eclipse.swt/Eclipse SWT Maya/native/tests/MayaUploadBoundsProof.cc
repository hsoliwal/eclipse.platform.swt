// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#include "MayaCompositionDirty.h"
#include <cassert>
#include <climits>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <limits>
#include <random>

int main() {
    std::uint64_t assertions = 0;
    const auto check = [&](int x, int y, int w, int h) {
        const bool reference = x >= 0 && y >= 0 && w > 0 && h > 0 &&
            static_cast<std::uint64_t>(w) * sizeof(std::uint32_t) <=
                std::numeric_limits<std::uint32_t>::max() &&
            static_cast<std::uint64_t>(x) + static_cast<std::uint64_t>(w) <=
                std::numeric_limits<std::uint32_t>::max() &&
            static_cast<std::uint64_t>(y) + static_cast<std::uint64_t>(h) <=
                std::numeric_limits<std::uint32_t>::max();
        assert(swt_maya::validD3dUploadBox(x, y, w, h) == reference);
        ++assertions;
    };
    for (int x : {-1, 0, 1, 2, INT_MAX - 1, INT_MAX})
        for (int y : {-1, 0, 1, 2, INT_MAX - 1, INT_MAX})
            for (int w : {-1, 0, 1, 2, INT_MAX / 4, INT_MAX / 2, INT_MAX})
                for (int h : {-1, 0, 1, 2, INT_MAX / 4, INT_MAX / 2, INT_MAX})
                    check(x, y, w, h);
    std::mt19937_64 rng(20261118);
    for (int i = 0; i < 250000; ++i) {
        auto next = [&]() -> int { return static_cast<int>(rng() & 0x7FFFFFFF); };
        const int x = i % 13 == 0 ? -1 : next();
        const int y = i % 17 == 0 ? -1 : next();
        const int w = i % 19 == 0 ? 0 : next();
        const int h = i % 23 == 0 ? 0 : next();
        check(x,y,w,h);
    }
    check(0, 0, 1'073'741'823, 1);
    check(0, 0, 1'073'741'824, 1);
    check(INT_MAX, INT_MAX, 1, 1);
    check(-1, 0, 1, 1);
    check(0, -1, 1, 1);
    check(0, 0, 0, 1);
    const int a[] = {0,0,2,3,4,5,4,5,0,0,1,1};
    assert(swt_maya::packedDirtyPixelCount(a,0) == 0); ++assertions;
    assert(swt_maya::packedDirtyPixelCount(a,1) == 6); ++assertions;
    assert(swt_maya::packedDirtyPixelCount(a,2) == 26); ++assertions;
    assert(swt_maya::packedDirtyPixelCount(a,3) == 27); ++assertions;
    const int one[] = {0,0,INT_MAX,1};
    assert(swt_maya::packedDirtyPixelCount(one,1) ==
        static_cast<std::size_t>(INT_MAX)); ++assertions;
    std::cout << "PASS " << assertions << " Maya upload byte-pitch and prefix checks\n";
}
