/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaNativeHandleRegistry.h"

#include <cassert>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <new>
#include <vector>

namespace {
struct NativeObject {
    std::uint64_t id;
};
}

int main() {
    swt_maya::TokenRegistry<NativeObject, 0x31> compositions;
    swt_maya::TokenRegistry<NativeObject, 0x32> layers;
    assert(compositions.liveCount() == 0 && layers.liveCount() == 0);
    assert(compositions.get(0) == nullptr);
    assert(compositions.admit(nullptr) == 0);

    // Same in-process native address is reused for distinct object lifetimes:
    // the stale token must NEVER become a handle to its new resident.
    alignas(NativeObject) unsigned char slot[sizeof(NativeObject)];
    NativeObject *first = new (slot) NativeObject{1};
    const auto firstToken = layers.admit(first);
    assert(firstToken > 0 && (firstToken >> 56) == 0x32);
    assert(layers.get(firstToken) == first);
    assert(!layers.retire(firstToken, nullptr));
    assert(!layers.retire(firstToken, reinterpret_cast<NativeObject *>(0x1)));
    assert(layers.retire(firstToken, first));
    first->~NativeObject();
    assert(layers.get(firstToken) == nullptr);

    NativeObject *second = new (slot) NativeObject{2};
    assert(second == first); // exact forced allocator-address ABA condition
    const auto secondToken = layers.admit(second);
    assert(secondToken != firstToken);
    assert(layers.get(firstToken) == nullptr);
    assert(layers.get(secondToken) == second);
    assert(second->id == 2);
    assert(!layers.retire(firstToken, second));
    assert(layers.retire(secondToken, second));
    second->~NativeObject();

    // Disjoint kinds never alias despite the same numeric issuance sequence.
    NativeObject object{3};
    const auto compositionToken = compositions.admit(&object);
    const auto layerToken = layers.admit(&object);
    assert((compositionToken >> 56) == 0x31);
    assert((layerToken >> 56) == 0x32);
    assert(compositions.get(layerToken) == nullptr);
    assert(layers.get(compositionToken) == nullptr);
    assert(compositions.get(compositionToken) == &object);
    assert(layers.get(layerToken) == &object);
    assert(compositions.retire(compositionToken, &object));
    assert(layers.retire(layerToken, &object));

    // A long churn run simulates a native allocator recycling one address.
    // No returned token can equal an earlier retired token.
    std::vector<std::uint64_t> retired;
    retired.reserve(100000);
    std::uint64_t previous = 0;
    for (std::uint64_t i = 0; i < 100000; i++) {
        NativeObject current{i};
        const auto token = layers.admit(&current);
        assert(token != 0 && token > previous);
        assert(layers.get(token) == &current);
        assert(layers.retire(token, &current));
        assert(layers.get(token) == nullptr);
        previous = token;
        retired.push_back(token);
    }
    assert(layers.liveCount() == 0);
    for (std::size_t i = 0; i < retired.size(); i += 127) {
        assert(layers.get(retired[i]) == nullptr);
    }
    // Wrong-kind, null, arbitrary high-bit and nonissued tokens all fail.
    assert(layers.get(0x3200000000000000ULL) == nullptr);
    assert(layers.get(0x7fffffffffffffffULL) == nullptr);
    assert(layers.get(0xffffffffffffffffULL) == nullptr);
    assert(layers.get(0x3200ffffffffffffULL) == nullptr);

    std::cout << "MayaNativeHandleRegistryTest PASS: typed handles, forced ABA, 100000 retire/reissue rounds\n";
    return 0;
}
