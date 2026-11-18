/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include <jni.h>
// Test-only stand-in for the existing seven-export Maya native compositor:
// checks exact order, opaque handle bits, fields, zero frame and 4096 layers.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *env, jclass, jlong composition, jlongArray handles, jfloatArray properties, jint count) {
    if (count == 0 && composition == 999L) return 777;
    if (count == 2 && composition == 999L) {
        jlong h[2] = {0, 0};
        jfloat p[8] = {0};
        env->GetLongArrayRegion(handles, 0, 2, h);
        env->GetFloatArrayRegion(properties, 0, 8, p);
        if (h[0] == static_cast<jlong>(0x8000000000000001ULL) && h[1] == 19
                && p[0] == 3.5f && p[1] == -7.25f && p[2] == .5f && p[3] == 1.f
                && p[4] == -1.f && p[5] == 2.f && p[6] == 1.f && p[7] == 0.f) return 777;
    }
    if (count == 4096 && composition == 777L) {
        jlong first = 0, last = 0;
        env->GetLongArrayRegion(handles, 0, 1, &first);
        env->GetLongArrayRegion(handles, count - 1, 1, &last);
        if (first == 1 && last == 4096) return 778;
    }
    return -999;
}
