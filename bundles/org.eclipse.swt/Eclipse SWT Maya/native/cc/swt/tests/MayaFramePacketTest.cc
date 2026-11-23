/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaFramePacket.h"
#include <cassert>
#include <cstdint>
#include <iostream>
#include <limits>
#include <random>
#include <vector>
using namespace swt_maya_frame;

static void put16(std::vector<std::uint8_t> &b, std::size_t p, std::uint16_t x) {
    b.at(p) = static_cast<std::uint8_t>(x);
    b.at(p + 1) = static_cast<std::uint8_t>(x >> 8);
}
static void put32(std::vector<std::uint8_t> &b, std::size_t p, std::uint32_t x) {
    for (int i = 0; i < 4; i++) b.at(p + static_cast<std::size_t>(i)) = static_cast<std::uint8_t>(x >> (i * 8));
}
static void put64(std::vector<std::uint8_t> &b, std::size_t p, std::uint64_t x) {
    put32(b, p, static_cast<std::uint32_t>(x));
    put32(b, p + 4, static_cast<std::uint32_t>(x >> 32));
}
static std::vector<std::uint8_t> packet(std::uint32_t n) {
    std::vector<std::uint8_t> b(HEADER_BYTES + static_cast<std::size_t>(n) * RECORD_BYTES, 0);
    put32(b, 0, MAGIC); put16(b, 4, VERSION); put16(b, 6, HEADER_BYTES);
    put16(b, 8, RECORD_BYTES); put32(b, 12, n); put64(b, 16, 0x123456789abcdef0ULL);
    for (std::uint32_t i = 0; i < n; i++) {
        auto p = HEADER_BYTES + static_cast<std::size_t>(i) * RECORD_BYTES;
        put64(b, p, static_cast<std::uint64_t>(i) + 1);
        put32(b, p + 8, 0x40a00000U); // x=5
        put32(b, p + 12, 0xc1200000U); // y=-10
        put32(b, p + 16, 0x3f000000U); // opacity=0.5
        put32(b, p + 20, i % 2);
    }
    return b;
}
static void wrong(std::vector<std::uint8_t> b, Result error) {
    Frame unchanged;
    unchanged.sequence = 0xBAD;
    unchanged.layers.push_back({333, 1.0f, 2.0f, 1.0f, 1});
    assert(decode(b.data(), b.size(), unchanged) == error);
    assert(unchanged.sequence == 0xBAD && unchanged.layers.size() == 1 && unchanged.layers[0].handle == 333);
}
int main() {
    Frame frame;
    auto b = packet(0);
    assert(decode(b.data(), b.size(), frame) == Result::OK && frame.layers.empty());
    assert(frame.sequence == 0x123456789abcdef0ULL);
    b = packet(3);
    assert(decode(b.data(), b.size(), frame) == Result::OK && frame.layers.size() == 3);
    assert(frame.layers[0].x == 5.0f && frame.layers[0].y == -10.0f);
    assert(frame.layers[1].visible == 1 && frame.layers[0].opacity == 0.5f);

    b = packet(3); b[0] ^= 1; wrong(b, Result::BAD_MAGIC);
    b = packet(3); put16(b, 4, 2); wrong(b, Result::BAD_VERSION);
    b = packet(3); put16(b, 8, 1); wrong(b, Result::BAD_LAYOUT);
    b = packet(3); put16(b, 10, 1); wrong(b, Result::BAD_FLAGS);
    b = packet(3); put32(b, 12, MAX_LAYERS + 1); wrong(b, Result::TOO_MANY_LAYERS);
    b = packet(3); b.pop_back(); wrong(b, Result::BAD_SIZE);
    b = packet(3); b.push_back(0); wrong(b, Result::BAD_SIZE);
    b = packet(3); put64(b, 24, 1); wrong(b, Result::BAD_RESERVED);
    b = packet(3); put64(b, 32, 0); wrong(b, Result::BAD_HANDLE);
    b = packet(3); put32(b, 32 + 8, 0x7fc00001U); wrong(b, Result::BAD_NUMBER);
    b = packet(3); put32(b, 32 + 16, 0x3f800001U); wrong(b, Result::BAD_NUMBER);
    b = packet(3); put32(b, 32 + 20, 2); wrong(b, Result::BAD_VISIBILITY);
    b = packet(3); put64(b, 32 + 24, 1); wrong(b, Result::BAD_RESERVED);
    b = packet(3); put64(b, 32 + RECORD_BYTES, 1); wrong(b, Result::DUPLICATE_HANDLE);
    // Adversarial double-hash collision: all 40 opaque handles share both
    // the initial bucket and odd stride at capacity 128. The 33rd probe
    // exercises the original sorted fallback, not an unbounded search.
    std::vector<std::uint64_t> colliding;
    for (std::uint64_t handle = 1; colliding.size() < 40; ++handle) {
        if ((handleBucket(handle) & 127U) == 0 &&
                (handleStride(handle) & 127U) == 1) colliding.push_back(handle);
    }
    b = packet(40);
    for (std::size_t i = 0; i < colliding.size(); ++i) {
        put64(b, HEADER_BYTES + i * RECORD_BYTES, colliding[i]);
    }
    assert(decode(b.data(), b.size(), frame) == Result::OK);
    put64(b, HEADER_BYTES + 39 * RECORD_BYTES, colliding.front());
    wrong(b, Result::DUPLICATE_HANDLE);
    // A malformed record still takes precedence over duplicate detection.
    put64(b, HEADER_BYTES + RECORD_BYTES + 24, 7);
    wrong(b, Result::BAD_RESERVED);
    b = packet(3); assert(decode(b.data(), 8, frame) == Result::SHORT_HEADER);
    assert(decode(nullptr, 32, frame) == Result::NULL_DATA);

    // Signed-long handles remain opaque; all bits except zero are accepted.
    b = packet(1); put64(b, 32, std::numeric_limits<std::uint64_t>::max());
    assert(decode(b.data(), b.size(), frame) == Result::OK);
    assert(frame.layers[0].handle == std::numeric_limits<std::uint64_t>::max());
    // Boundary at the exact agreed maximum.
    b = packet(MAX_LAYERS);
    assert(decode(b.data(), b.size(), frame) == Result::OK && frame.layers.size() == MAX_LAYERS);
    put64(b, HEADER_BYTES + (static_cast<std::size_t>(MAX_LAYERS) - 1) * RECORD_BYTES, 1);
    wrong(b, Result::DUPLICATE_HANDLE);
    // Max-size high-bit, widely spaced handle pattern triggered the former
    // linear-probing fallback on otherwise distinct handles. It must remain
    // a valid frame with no additional native handle assumptions.
    b = packet(MAX_LAYERS);
    for (std::uint32_t i = 0; i < MAX_LAYERS; ++i) {
        put64(b, HEADER_BYTES + static_cast<std::size_t>(i) * RECORD_BYTES,
                0x8000000000000001ULL + static_cast<std::uint64_t>(i)
                * UINT64_C(0x9e3779b97f4a7c15));
    }
    assert(decode(b.data(), b.size(), frame) == Result::OK);

    std::mt19937_64 random(0x3e9b127aU);
    for (int trial = 0; trial < 10000; trial++) {
        const auto n = static_cast<std::uint32_t>(random() % 100);
        b = packet(n);
        Frame accepted;
        assert(decode(b.data(), b.size(), accepted) == Result::OK);
        assert(accepted.layers.size() == n);
        if (n) {
            const auto i = static_cast<std::uint32_t>(random() % n);
            put32(b, HEADER_BYTES + static_cast<std::size_t>(i) * RECORD_BYTES + 16,
                    (trial & 1) == 0 ? 0x3f800000U : 0);
            assert(decode(b.data(), b.size(), accepted) == Result::OK);
            put64(b, HEADER_BYTES + static_cast<std::size_t>(i) * RECORD_BYTES + 24, 4);
            assert(decode(b.data(), b.size(), accepted) == Result::BAD_RESERVED);
        }
    }
    std::cout << "MayaFramePacketTest PASS: portable parser, max bounds, 10000 random trials\n";
}
