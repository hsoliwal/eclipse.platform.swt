/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "MayaFrameDirect.h"
#include <jni.h>
#include <cstddef>
#include <cstdint>
#include <new>

// Both the original seven MayaComposition JNI exports and the packet ABI v1
// remain unchanged. This entrypoint now sends the decoded contiguous frame
// directly to the SAME native frame-transaction owner as legacy _nApply.
// It never recreates intermediate jlongArray/jfloatArray JNI objects.
//
// All memory is owned by the JNI stack frame and released on return; neither
// the packet's direct ByteBuffer nor the decoded frame is stored by COM.
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nFramePacketVersion
  (JNIEnv *, jclass) {
    return swt_maya_frame::VERSION;
}

extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nApplyPacked
  (JNIEnv *env, jclass, jlong composition, jobject direct, jint byteLength) {
    if (!direct || byteLength < 0 ||
            static_cast<std::size_t>(byteLength) > swt_maya_frame::MAX_BYTES) return -2;
    const jlong capacity = env->GetDirectBufferCapacity(direct);
    const void *data = env->GetDirectBufferAddress(direct);
    if (capacity < 0 || !data || static_cast<jlong>(byteLength) > capacity) return -2;
    try {
        swt_maya_frame::Frame frame;
        if (swt_maya_frame::decode(static_cast<const std::uint8_t *>(data),
                static_cast<std::size_t>(byteLength), frame) != swt_maya_frame::Result::OK) return -2;
        return swt_maya_frame::applyDecodedFrame(composition, frame);
    } catch (const std::bad_alloc &) {
        // No native exception may unwind through JNI. The transaction owner
        // also catches its own failures and invalidates any native state cache.
        return -3;
    } catch (...) {
        return -3;
    }
}
