/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaFrameDirect.h"
#include <jni.h>
#include <cstdint>

// Test-only native transaction owner. Deliberately returns different codes
// from the legacy JNI method for 4096 layers: a packet that reconstructs
// temporary JNI arrays rather than invoking applyDecodedFrame now FAILS.
namespace swt_maya_frame {
jint applyDecodedFrame(jlong composition, const Frame &frame) noexcept {
    const std::size_t count = frame.layers.size();
    if (count == 0 && composition == 999L) return 777;
    if (count == 2 && composition == 999L) {
        const auto &a = frame.layers[0], &b = frame.layers[1];
        if (a.handle == 0x8000000000000001ULL && b.handle == 19 &&
                a.x == 3.5f && a.y == -7.25f && a.opacity == 0.5f && a.visible == 1 &&
                b.x == -1.f && b.y == 2.f && b.opacity == 1.f && b.visible == 0) return 777;
    }
    if (count == 4096 && composition == 777L &&
            frame.layers[0].handle == 1 && frame.layers[count - 1].handle == 4096)
        return 778;
    return -999;
}
}

// This legacy native stub serves only small direct JNI frames; any large
// packet accidentally using this array path cannot pass the JNI smoke.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *env, jclass, jlong composition, jlongArray handles, jfloatArray properties, jint count) {
    if (count != 2 || composition != 999L) return -198;
    jlong h[2] = {0, 0};
    jfloat p[8] = {0};
    env->GetLongArrayRegion(handles, 0, 2, h);
    env->GetFloatArrayRegion(properties, 0, 8, p);
    return h[0] == static_cast<jlong>(0x8000000000000001ULL) && h[1] == 19
            && p[0] == 3.5f && p[1] == -7.25f && p[2] == .5f && p[3] == 1.f
            && p[4] == -1.f && p[5] == 2.f && p[6] == 1.f && p[7] == 0.f ? 777 : -999;
}
