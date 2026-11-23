/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

/** Standalone -Xcheck:jni test of the production Java/native telemetry ABI. */
public final class MayaTelemetryJniSmoke {
    private MayaTelemetryJniSmoke() { }
    private static native void exercise();

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        System.load(args[0]);
        if (args.length > 1 && "legacy".equals(args[1])) {
            check(MayaComposition.readMetrics(123) == null,
                    "older libraries must not require the new export");
            System.out.println("MayaTelemetryJniSmoke PASS: old native library fallback");
            return;
        }
        check(MayaComposition.readMetrics(0) == null, "invalid composition");
        check(MayaComposition._nReadMetrics(123,
                new long[MayaComposition.METRIC_COUNT - 1]) != 0, "short array");
        exercise();
        long[] snapshot = MayaComposition.readMetrics(123);
        check(snapshot != null && snapshot.length == 16, "metrics length");
        check(snapshot[MayaComposition.METRIC_VERSION] == 1, "schema version");
        check(snapshot[MayaComposition.METRIC_FRAME_ATTEMPTS] == 3, "attempts");
        check(snapshot[MayaComposition.METRIC_FRAME_COMMITS] == 1, "committed");
        check(snapshot[MayaComposition.METRIC_FRAME_NOOPS] == 1, "noops");
        check(snapshot[MayaComposition.METRIC_Z_REBUILDS] == 1, "reorder");
        check(snapshot[MayaComposition.METRIC_FULL_UPLOADS] == 1, "full upload");
        check(snapshot[MayaComposition.METRIC_DIRTY_PATCHES] == 1, "dirty patch");
        check(snapshot[MayaComposition.METRIC_UPLOAD_BYTES] == 72, "bytes");
        check(snapshot[MayaComposition.METRIC_HEALTH_PROBES] == 2, "health probes");
        check(snapshot[MayaComposition.METRIC_HEALTH_FAILURES] == 1, "health failure");
        check(snapshot[MayaComposition.METRIC_LAYERS_CREATED] == 1, "created");
        check(snapshot[MayaComposition.METRIC_LIVE_LAYERS] == 1, "live");
        check(snapshot[MayaComposition.METRIC_FRAME_REJECTIONS] == 1, "rejected");
        check(snapshot[MayaComposition.METRIC_PENDING_SURFACE] == 0, "not pending");
        long[] spare = new long[MayaComposition.METRIC_COUNT + 1];
        spare[MayaComposition.METRIC_COUNT] = -987;
        check(MayaComposition._nReadMetrics(123, spare) == 0, "snapshot result");
        check(spare[MayaComposition.METRIC_COUNT] == -987,
                "JNI may overwrite only the fixed prefix");
        System.out.println("MayaTelemetryJniSmoke PASS: Java21 JNI snapshot semantics");
    }
}
