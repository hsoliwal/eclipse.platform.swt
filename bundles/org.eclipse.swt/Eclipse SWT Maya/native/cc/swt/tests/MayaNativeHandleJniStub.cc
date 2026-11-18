/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 ******************************************************************************/
#include "../MayaNativeHandleRegistry.h"
#include <jni.h>
#include <cstdint>
#include <new>

// Test-only bridge: no COM, no exported production Maya symbols.
// A fixed aligned slot forces exact native pointer reuse across lifetimes.
namespace {
struct NativeRecord { std::uint64_t generation; };
template<std::uint8_t Kind> class Slot {
    swt_maya::TokenRegistry<NativeRecord, Kind> registry;
    alignas(NativeRecord) unsigned char storage[sizeof(NativeRecord)];
    NativeRecord *current = nullptr;
    std::uint64_t generation = 1;
public:
    jlong issue() {
        if (current) return 0;
        current = new (storage) NativeRecord{generation++};
        try {
            const std::uint64_t token = registry.admit(current);
            if (token) return static_cast<jlong>(token);
        } catch (...) {
            // Native allocation failures must not cross a JNI boundary.
        }
        current->~NativeRecord();
        current = nullptr;
        return 0;
    }
    jboolean live(jlong token) const noexcept {
        return registry.get(static_cast<std::uint64_t>(token)) == current &&
                current != nullptr ? JNI_TRUE : JNI_FALSE;
    }
    jboolean retire(jlong token) noexcept {
        NativeRecord *found = registry.get(static_cast<std::uint64_t>(token));
        if (!found || found != current ||
                !registry.retire(static_cast<std::uint64_t>(token), found)) return JNI_FALSE;
        current->~NativeRecord();
        current = nullptr;
        return JNI_TRUE;
    }
};
Slot<0x31> compositions;
Slot<0x32> layers;
}
extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaNativeHandleJniSmoke_issueComposition
  (JNIEnv *, jclass) { return compositions.issue(); }
extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaNativeHandleJniSmoke_issueLayer
  (JNIEnv *, jclass) { return layers.issue(); }
extern "C" JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_maya_MayaNativeHandleJniSmoke_liveComposition
  (JNIEnv *, jclass, jlong token) { return compositions.live(token); }
extern "C" JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_maya_MayaNativeHandleJniSmoke_liveLayer
  (JNIEnv *, jclass, jlong token) { return layers.live(token); }
extern "C" JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_maya_MayaNativeHandleJniSmoke_retireComposition
  (JNIEnv *, jclass, jlong token) { return compositions.retire(token); }
extern "C" JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_maya_MayaNativeHandleJniSmoke_retireLayer
  (JNIEnv *, jclass, jlong token) { return layers.retire(token); }
