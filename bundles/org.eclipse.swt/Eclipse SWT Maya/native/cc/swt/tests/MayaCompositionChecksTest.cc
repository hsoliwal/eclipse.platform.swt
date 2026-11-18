/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaCompositionChecks.h"
#include <cassert>
#include <climits>
#include <limits>
#include <iostream>

int main() {
    using namespace swt_maya;
    assert(validPixelSize(1, 1));
    assert(validPixelSize(100, 100));
    assert(!validPixelSize(0, 1));
    assert(!validPixelSize(1, -1));
    assert(!validPixelSize(INT_MAX, INT_MAX));
    assert(validPixelBuffer(2, 3, 6));
    assert(validPixelBuffer(2, 3, 7));
    assert(!validPixelBuffer(2, 3, 5));
    assert(!validPixelBuffer(INT_MAX, INT_MAX, INT_MAX));
    assert(validFrameArrays(0, 0, 0));
    assert(validFrameArrays(2, 2, 8));
    assert(!validFrameArrays(-1, 0, 0));
    assert(!validFrameArrays(2, 1, 8));
    assert(!validFrameArrays(2, 2, 7));
    assert(!validFrameArrays(INT_MAX, INT_MAX, INT_MAX));
    assert(validLayerProperties(0.0f, -10.0f, 1.0f, 0.0f));
    assert(validLayerProperties(100.0f, 1.0f, 0.5f, 1.0f));
    assert(!validLayerProperties(0.0f, 0.0f, -0.1f, 1.0f));
    assert(!validLayerProperties(0.0f, 0.0f, 1.1f, 1.0f));
    assert(!validLayerProperties(0.0f, 0.0f, 0.5f, 0.5f));
    assert(!validLayerProperties(std::numeric_limits<float>::infinity(), 0.0f, 1.0f, 1.0f));
    assert(!validLayerProperties(0.0f, 0.0f, std::numeric_limits<float>::quiet_NaN(), 1.0f));
    std::cout << "MayaCompositionChecks: 22/22 checks passed\\n";
}
