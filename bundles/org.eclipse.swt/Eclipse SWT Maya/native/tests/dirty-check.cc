// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#include "MayaCompositionDirty.h"
#include <cassert>
#include <cstdint>
#include <iostream>
#include <vector>

int main() {
    std::uint32_t state = 0x5ac2026u;
    auto next = [&]() { state ^= state << 13; state ^= state >> 17; state ^= state << 5; return state; };
    std::uint64_t checks = 0;
    for (int trial = 0; trial < 20000; ++trial) {
        int width = 1 + static_cast<int>(next() % 64), height = 1 + static_cast<int>(next() % 32);
        int n = 1 + static_cast<int>(next() % 8);
        std::vector<int> rects(static_cast<std::size_t>(n) * 4);
        std::vector<std::uint32_t> image(static_cast<std::size_t>(width) * height);
        for (auto &p : image) p = next();
        std::vector<std::uint32_t> staged;
        for (int i = 0; i < n; ++i) {
            int x = static_cast<int>(next() % width), y = static_cast<int>(next() % height);
            int w = 1 + static_cast<int>(next() % (width - x));
            int h = 1 + static_cast<int>(next() % (height - y));
            int *r = rects.data() + static_cast<std::size_t>(i) * 4;
            r[0] = x; r[1] = y; r[2] = w; r[3] = h;
            std::vector<std::uint32_t> packed(swt_maya::dirtyArea(r));
            swt_maya::copyDirty(image.data(), width, r, packed.data());
            for (int row = 0; row < h; ++row)
                for (int col = 0; col < w; ++col) {
                    assert(packed[static_cast<std::size_t>(row) * w + col] ==
                           image[static_cast<std::size_t>(y + row) * width + x + col]);
                    ++checks;
                }
            staged.insert(staged.end(), packed.begin(), packed.end());
        }
        assert(swt_maya::validPackedDirtyFrame(width, height, static_cast<int>(staged.size()),
                rects.data(), n, static_cast<int>(rects.size())));
        assert(!swt_maya::validPackedDirtyFrame(width, height, static_cast<int>(staged.size()) - 1,
                rects.data(), n, static_cast<int>(rects.size())));
        checks += 2;
    }
    const int good[4] = {0, 0, 1, 1}, bad[4] = {0, 0, 2, 1};
    assert(swt_maya::validPackedDirtyFrame(1, 1, 1, good, 1, 4));
    assert(!swt_maya::validPackedDirtyFrame(1, 1, 1, bad, 1, 4));
    assert(!swt_maya::validPackedDirtyFrame(1, 1, 1, good, 4097, 16388));
    // The UINT pitch is in BYTES, not pixels. Keep JNI and Java preflight
    // aligned at the exact 4-byte/pixel 0xffffffff boundary.
    const int largestGoodPitch[4] = {0, 0, 1'073'741'823, 1};
    const int firstBadPitch[4] = {0, 0, 1'073'741'824, 1};
    assert(swt_maya::validPackedDirtyFrame(1'073'741'823, 1, INT32_MAX,
            largestGoodPitch, 1, 4));
    assert(!swt_maya::validPackedDirtyFrame(1'073'741'824, 1, INT32_MAX,
            firstBadPitch, 1, 4));
    checks += 5;
    std::cout << "PASS " << checks << " packed native dirty-pixel checks\n";
}
