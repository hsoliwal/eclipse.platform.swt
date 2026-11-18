// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#include "MayaCompositionDirty.h"
#include <cassert>
#include <climits>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <limits>
#include <random>
#include <vector>

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
    // Scratch reuse is bounded and must fail closed while another JNI frame
    // on this SWT UI thread has the temporary region buffer borrowed.
    for (int count = -1; count <= 4097; ++count) {
        for (std::size_t pixels : {std::size_t(0), std::size_t(1),
                std::size_t(262143), std::size_t(262144), std::size_t(262145),
                std::size_t(1048576)}) {
            for (bool busy : {false, true}) {
                const bool expected = count > 1 && pixels > 0 &&
                        pixels <= 262144 && !busy;
                assert(swt_maya::mayReuseDirtyPixels(count, pixels, busy) == expected);
                ++assertions;
            }
        }
    }
    // Model the reentrant case and capacity ownership with two independent
    // buffers: the nested request cannot modify the first staging snapshot.
    std::vector<std::uint32_t> retained(256, 0xA5A5A5A5u);
    std::vector<std::uint32_t> nested(256, 0x5A5A5A5Au);
    bool borrowed = true;
    assert(!swt_maya::mayReuseDirtyPixels(4, retained.size(), borrowed));
    ++assertions;
    for (std::size_t i = 0; i < retained.size(); ++i) {
        assert(retained[i] == 0xA5A5A5A5u);
        assert(nested[i] == 0x5A5A5A5Au);
        assertions += 2;
    }
    borrowed = false;
    assert(swt_maya::mayReuseDirtyPixels(4, retained.size(), borrowed));
    ++assertions;
    // These are the SAME retained native array and reentrant lease helpers
    // used by MayaComposition. A nested upload cannot overwrite the outer
    // frame, and large batches must never enlarge retained storage.
    swt_maya::DirtyPixelScratch<std::uint32_t> pixels;
    assert(pixels.retainedCapacity() == 0 && !pixels.busy()); ++assertions;
    std::uint32_t *previous = nullptr;
    {
        swt_maya::DirtyPixelScratch<std::uint32_t>::Lease outer(pixels, 3, 1024);
        previous = outer.data();
        assert(pixels.busy() && pixels.retainedCapacity() == 1024);
        ++assertions;
        for (std::size_t i=0;i<1024;i++) outer.data()[i] = 0xA5A5A5A5u;
        {
            swt_maya::DirtyPixelScratch<std::uint32_t>::Lease nested(pixels, 3, 500);
            assert(nested.data() != outer.data()); ++assertions;
            for (std::size_t i=0;i<500;i++) nested.data()[i] = 0x5A5A5A5Au;
            assert(pixels.busy()); ++assertions;
        }
        for (std::size_t i=0;i<1024;i++) {
            assert(outer.data()[i] == 0xA5A5A5A5u); ++assertions;
        }
    }
    assert(!pixels.busy()); ++assertions;
    {
        swt_maya::DirtyPixelScratch<std::uint32_t>::Lease reuse(pixels, 3, 500);
        assert(reuse.data() == previous && pixels.retainedCapacity() == 1024);
        ++assertions;
    }
    {
        swt_maya::DirtyPixelScratch<std::uint32_t>::Lease large(pixels, 3, 262145);
        assert(large.data() != previous && pixels.retainedCapacity() == 1024);
        ++assertions;
    }
    assert(!pixels.busy() && pixels.retainedCapacity() <= 262144);
    ++assertions;
    std::cout << "PASS " << assertions << " Maya upload byte-pitch and prefix checks\n";
}
