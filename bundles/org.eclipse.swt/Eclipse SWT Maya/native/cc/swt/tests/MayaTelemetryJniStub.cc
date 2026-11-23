/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
#include "../MayaCompositionTelemetry.h"
#include <jni.h>
#include <array>
#include <cstdint>

#ifndef SWT_MAYA_TELEMETRY_LEGACY
namespace {
swt_maya::CompositionTelemetry telemetry;
bool pending = false;
std::uint64_t live = 0;
}

extern "C" JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_maya_MayaTelemetryJniSmoke_exercise
  (JNIEnv *, jclass) {
    telemetry.createdLayer();
    live = 1;
    telemetry.fullUpload(16);
    pending = true;
    telemetry.deviceProbe(false);
    telemetry.attemptFrame();
    telemetry.committedFrame(true);
    pending = false;
    telemetry.attemptFrame();
    telemetry.noopFrame();
    telemetry.deviceProbe(true);
    telemetry.attemptFrame();
    telemetry.rejectFrame();
    telemetry.dirtyPatch(2);
}
extern "C" JNIEXPORT jint JNICALL Java_org_eclipse_swt_internal_maya_MayaComposition__1nReadMetrics
  (JNIEnv *env, jclass, jlong composition, jlongArray output) {
    if (composition != 123 || !output) return -1;
    if (env->GetArrayLength(output) < static_cast<jsize>(swt_maya::CompositionTelemetry::FIELD_COUNT))
        return -2;
    const auto snapshot = telemetry.snapshot(live, pending);
    std::array<jlong, swt_maya::CompositionTelemetry::FIELD_COUNT> values{};
    for (std::size_t i = 0; i < values.size(); ++i)
        values[i] = static_cast<jlong>(snapshot[i]);
    env->SetLongArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
    return env->ExceptionCheck() ? -3 : 0;
}
#else
// Legacy builds intentionally export neither the metrics symbol nor the
// test harness: Java must use the optional null-return compatibility path.
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    return JNI_VERSION_1_6;
}
#endif
