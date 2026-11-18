/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include <jni.h>
// Test-only old library: no packet-version or packed-entry exports.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *env, jclass, jlong composition, jlongArray handles, jfloatArray props, jint count) {
    if (composition != 333 || count != 4096 || env->GetArrayLength(handles) != count
            || env->GetArrayLength(props) != count * 4) return -99;
    jlong first = 0, last = 0;
    env->GetLongArrayRegion(handles, 0, 1, &first);
    env->GetLongArrayRegion(handles, count - 1, 1, &last);
    return first == 1 && last == 4096 ? 909 : -99;
}
