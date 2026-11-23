/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "MayaSurfaceBudget.h"
#include <jni.h>
#include <array>
#include <cstdint>

// Test-only legacy Create/Dispose entry points ensure opt-in SWT backend
// quota admission fails closed when an old DLL lacks the two budget symbols.
namespace {
int disposals = 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nCreate
  (JNIEnv *, jclass, jlong hwnd) { return hwnd == 777 ? 731 : 0; }
extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nDispose
  (JNIEnv *, jclass, jlong composition) { if (composition == 731) ++disposals; }
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaSurfaceBudgetJniSmoke_disposals
  (JNIEnv *, jclass) { return disposals; }

#ifdef SWT_MAYA_SURFACE_BUDGET_LEGACY
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    return JNI_VERSION_1_6;
}
#else
namespace {
thread_local swt_maya::SurfaceBudget budget;
constexpr jlong COMPOSITION = 731;
}
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nSetSurfaceBudget
  (JNIEnv *, jclass, jlong handle, jlong maximumBytes) {
    if (handle != COMPOSITION) return -1;
    if (maximumBytes < 0 || !budget.setLimit(static_cast<std::uint64_t>(maximumBytes)))
        return -2;
    return 0;
}
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nReadSurfaceBudget
  (JNIEnv *env, jclass, jlong handle, jlongArray output) {
    if (handle != COMPOSITION || !output) return -1;
    if (env->GetArrayLength(output) < static_cast<jsize>(swt_maya::SurfaceBudget::FIELD_COUNT))
        return -2;
    const auto state = budget.snapshot();
    std::array<jlong, swt_maya::SurfaceBudget::FIELD_COUNT> values{};
    for (std::size_t i = 0; i < state.size(); ++i) values[i] = static_cast<jlong>(state[i]);
    env->SetLongArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
    return env->ExceptionCheck() ? -3 : 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_maya_MayaSurfaceBudgetJniSmoke_reserve
  (JNIEnv *, jclass, jint width, jint height) {
    std::uint64_t granted = 0;
    return budget.reserve(width, height, granted) ? static_cast<jlong>(granted) : 0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_maya_MayaSurfaceBudgetJniSmoke_release
  (JNIEnv *, jclass, jlong count) {
    return count > 0 && budget.release(static_cast<std::uint64_t>(count)) ? JNI_TRUE : JNI_FALSE;
}
#endif
