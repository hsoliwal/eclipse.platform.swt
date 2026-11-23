/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "MayaDeviceHealth.h"
#include <cassert>
#include <cstdint>
#include <iostream>
#include <limits>

int main() {
    using swt_maya::compositionHealth;
    assert(compositionHealth(0, true, 0) == 0);
    assert(compositionHealth(1, true, 0) == 0);
    assert(compositionHealth(0, false, 0) == swt_maya::DEVICE_REMOVED);
    assert(compositionHealth(0, false, swt_maya::DEVICE_RESET) == swt_maya::DEVICE_RESET);
    assert(compositionHealth(0, false, swt_maya::DEVICE_REMOVED) == swt_maya::DEVICE_REMOVED);
    const auto fail = static_cast<std::int32_t>(0x80004005u);
    assert(compositionHealth(fail, true, 0) == fail);
    assert(compositionHealth(fail, false, swt_maya::DEVICE_RESET) == fail);
    // A successful DComp probe controls the return value even if stale
    // diagnostic state was previously negative: the current device is valid.
    assert(compositionHealth(0, true, swt_maya::DEVICE_REMOVED) == 0);
    for (int n = 0; n < 100000; ++n) {
        bool healthy = (n % 5) == 0;
        std::int32_t reason = n % 2 ? swt_maya::DEVICE_RESET : 0;
        auto result = compositionHealth(0, healthy, reason);
        assert(healthy ? result == 0 : result < 0);
    }
    std::cout << "MayaDeviceHealthTest PASS: HRESULT normalization and 100000 deterministic transitions\n";
}
