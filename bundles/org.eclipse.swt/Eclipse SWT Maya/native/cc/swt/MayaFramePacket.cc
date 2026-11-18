/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "MayaFramePacket.h"
#include <jni.h>
#include <cstdint>
#include <cstring>
#include <vector>

// The original seven MayaComposition JNI exports remain unchanged. This
// compatibility ingress decodes a bulk direct frame and calls the existing
// validated _nApply implementation. It does NOT claim fewer copies or faster
// execution: direct native scene ingestion is a separately qualified step.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply
  (JNIEnv *, jclass, jlong, jlongArray, jfloatArray, jint);

// Capability means that the packet parser ABI is available, NOT that an OS
// native compositor has been installed or supports arbitrary transformations.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nFramePacketVersion
  (JNIEnv *, jclass) {
    return swt_maya_frame::VERSION;
}

extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApplyPacked
  (JNIEnv *env, jclass clazz, jlong composition, jobject direct, jint byteLength) {
    if (!direct || byteLength < 0 || static_cast<std::size_t>(byteLength) > swt_maya_frame::MAX_BYTES) return -2;
    const jlong capacity = env->GetDirectBufferCapacity(direct);
    const void *data = env->GetDirectBufferAddress(direct);
    if (capacity < 0 || !data || static_cast<jlong>(byteLength) > capacity) return -2;
    swt_maya_frame::Frame decoded;
    auto result = swt_maya_frame::decode(static_cast<const std::uint8_t *>(data),
            static_cast<std::size_t>(byteLength), decoded);
    if (result != swt_maya_frame::Result::OK) return -2;
    const jint count = static_cast<jint>(decoded.layers.size());
    jlongArray handles = env->NewLongArray(count);
    if (!handles || env->ExceptionCheck()) return -3;
    jfloatArray props = env->NewFloatArray(count * 4);
    if (!props || env->ExceptionCheck()) {
        env->DeleteLocalRef(handles);
        return -3;
    }
    std::vector<jlong> handlesCopy(static_cast<std::size_t>(count));
    std::vector<jfloat> properties(static_cast<std::size_t>(count) * 4);
    static_assert(sizeof(jlong) == sizeof(std::uint64_t), "opaque pointer width mismatch");
    for (jint i = 0; i < count; i++) {
        const auto &layer = decoded.layers[static_cast<std::size_t>(i)];
        // Preserve every bit of an opaque handle, including bit 63.
        std::memcpy(&handlesCopy[static_cast<std::size_t>(i)], &layer.handle, sizeof(jlong));
        const std::size_t j = static_cast<std::size_t>(i) * 4;
        properties[j] = layer.x;
        properties[j + 1] = layer.y;
        properties[j + 2] = layer.opacity;
        properties[j + 3] = static_cast<jfloat>(layer.visible);
    }
    if (count) {
        env->SetLongArrayRegion(handles, 0, count, handlesCopy.data());
        if (!env->ExceptionCheck()) env->SetFloatArrayRegion(props, 0, count * 4, properties.data());
    }
    jint status = -3;
    if (!env->ExceptionCheck()) {
        status = Java_org_eclipse_swt_internal_maya_MayaComposition__1nApply(
                env, clazz, composition, handles, props, count);
    }
    env->DeleteLocalRef(props);
    env->DeleteLocalRef(handles);
    return status;
}
