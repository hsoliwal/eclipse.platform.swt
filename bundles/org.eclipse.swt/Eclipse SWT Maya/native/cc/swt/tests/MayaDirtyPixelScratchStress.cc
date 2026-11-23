// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#include "MayaCompositionDirty.h"
#include <atomic>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <iostream>
#include <limits>
#include <new>
#include <thread>

namespace {
struct FailureInjectedPixel {
    static int attempts;
    static int throwAt;
    int bits = 0;
    FailureInjectedPixel() {
        ++attempts;
        if (attempts == throwAt) throw std::bad_alloc();
    }
};
int FailureInjectedPixel::attempts = 0;
int FailureInjectedPixel::throwAt = 0;

void injectFailureOn(int attempt) {
    FailureInjectedPixel::attempts = 0;
    FailureInjectedPixel::throwAt = attempt;
}

using Scratch = swt_maya::DirtyPixelScratch<FailureInjectedPixel>;
} // namespace

int main() {
    std::uint64_t checks = 0;
    Scratch scratch;
    injectFailureOn(3);
    try {
        Scratch::Lease impossible(scratch, 3, 20);
        (void) impossible;
        assert(false && "new[] must propagate injected allocation failure");
    } catch (const std::bad_alloc &) {
        assert(!scratch.busy() && scratch.retainedCapacity() == 0);
        ++checks;
    }
    injectFailureOn(0);
    FailureInjectedPixel *original = nullptr;
    {
        Scratch::Lease first(scratch, 3, 20);
        original = first.data();
        assert(scratch.busy() && scratch.retainedCapacity() == 20);
        ++checks;
        for (int i = 0; i < 20; ++i) first.data()[i].bits = i * 37;
        // An injected nested allocation error must not unlock or modify
        // the outer retained JNI pixel array, even while stack unwinding.
        injectFailureOn(4);
        try {
            Scratch::Lease failedNested(scratch, 3, 40);
            (void) failedNested;
            assert(false);
        } catch (const std::bad_alloc &) {
            assert(scratch.busy() && first.data() == original);
            ++checks;
        }
        for (int i = 0; i < 20; ++i) {
            assert(first.data()[i].bits == i * 37);
            ++checks;
        }
    }
    assert(!scratch.busy()); ++checks;
    // A failed growth from a previously retained allocation must not erase
    // its old buffer, change its capacity or leave the busy bit set.
    injectFailureOn(5);
    try {
        Scratch::Lease failedGrowth(scratch, 3, 64);
        (void) failedGrowth;
        assert(false);
    } catch (const std::bad_alloc &) {
        assert(!scratch.busy() && scratch.retainedCapacity() == 20);
        ++checks;
    }
    injectFailureOn(0);
    {
        Scratch::Lease reused(scratch, 3, 15);
        assert(reused.data() == original && scratch.retainedCapacity() == 20);
        ++checks;
    }
    // Large batches never enlarge retained storage; single-region batches
    // also get temporary storage, even when they are under the 1 MiB cap.
    {
        Scratch::Lease large(scratch, 3, 262145);
        assert(large.data() != original && scratch.retainedCapacity() == 20);
        ++checks;
    }
    {
        Scratch::Lease single(scratch, 1, 10);
        assert(single.data() != original && scratch.retainedCapacity() == 20);
        ++checks;
    }
    // The actual JNI receiver declares its Scratch as thread_local. Exercise
    // that same model with two simultaneous event threads and an explicit
    // synchronization barrier, avoiding false successes from malloc reuse.
    std::atomic<int> ready{0};
    std::atomic<std::uintptr_t> addressA{0}, addressB{0};
    auto worker = [&](std::atomic<std::uintptr_t> &destination, int marker) {
        static thread_local swt_maya::DirtyPixelScratch<std::uint32_t> tls;
        swt_maya::DirtyPixelScratch<std::uint32_t>::Lease lease(tls, 3, 1024);
        for (int i = 0; i < 1024; ++i) lease.data()[i] = static_cast<std::uint32_t>(marker);
        destination.store(reinterpret_cast<std::uintptr_t>(lease.data()), std::memory_order_release);
        ready.fetch_add(1, std::memory_order_acq_rel);
        while (ready.load(std::memory_order_acquire) != 2) std::this_thread::yield();
        assert(tls.busy() && tls.retainedCapacity() == 1024);
        for (int i = 0; i < 1024; ++i) assert(lease.data()[i] == static_cast<std::uint32_t>(marker));
    };
    std::thread a(worker, std::ref(addressA), 0xA5);
    std::thread b(worker, std::ref(addressB), 0x5A);
    a.join(); b.join();
    assert(addressA.load() != 0 && addressB.load() != 0);
    assert(addressA.load() != addressB.load());
    checks += 2 * 1024 + 3;
    std::cout << "PASS " << checks << " Maya JNI native staging failure/thread-isolation checks\n";
}
