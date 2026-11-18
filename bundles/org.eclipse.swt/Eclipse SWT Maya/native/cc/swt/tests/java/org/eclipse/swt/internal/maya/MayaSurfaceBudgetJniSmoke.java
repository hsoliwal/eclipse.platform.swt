/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

/** Cross-language smoke using the exact production C++ surface-budget header. */
public final class MayaSurfaceBudgetJniSmoke {
    private MayaSurfaceBudgetJniSmoke() { }
    private static native long reserve(int width, int height);
    private static native boolean release(long bytes);
    private static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        System.load(args[0]);
        if (args.length > 1 && "legacy".equals(args[1])) {
            ok(MayaComposition.readSurfaceBudget(731) == null, "old JNI export absent");
            ok(MayaComposition.setSurfaceBudget(731, 40) == -1,
                    "old library cannot silently satisfy a quota");
            System.out.println("MayaSurfaceBudgetJniSmoke PASS: old library fallback");
            return;
        }
        long[] first = MayaComposition.readSurfaceBudget(731);
        ok(first != null && first.length == 6 && first[MayaComposition.BUDGET_VERSION] == 1,
                "stable JNI snapshot");
        ok(first[MayaComposition.BUDGET_LIMIT_BYTES] == 0, "default unlimited");
        ok(MayaComposition.readSurfaceBudget(0) == null, "invalid handle");
        ok(MayaComposition.setSurfaceBudget(731, 40) == 0, "set quota");
        ok(reserve(3, 3) == 36, "initial reservation");
        ok(reserve(2, 1) == 0, "quota denial before native allocation");
        ok(MayaComposition.setSurfaceBudget(731, 35) != 0, "cannot shrink below live");
        long[] current = MayaComposition.readSurfaceBudget(731);
        ok(current[MayaComposition.BUDGET_LIMIT_BYTES] == 40, "failed resize unchanged");
        ok(current[MayaComposition.BUDGET_RESERVED_BYTES] == 36, "live bytes");
        ok(current[MayaComposition.BUDGET_PEAK_BYTES] == 36, "peak bytes");
        ok(current[MayaComposition.BUDGET_DENIALS] == 1, "denial count");
        ok(current[MayaComposition.BUDGET_ACCEPTED] == 1, "accept count");
        long[] prefix = new long[MayaComposition.SURFACE_BUDGET_FIELDS + 1];
        prefix[prefix.length - 1] = -99;
        ok(MayaComposition._nReadSurfaceBudget(731, prefix) == 0, "prefix snapshot");
        ok(prefix[prefix.length - 1] == -99, "excess array untouched");
        ok(MayaComposition._nReadSurfaceBudget(731,
                new long[MayaComposition.SURFACE_BUDGET_FIELDS - 1]) != 0, "short array");
        ok(MayaComposition._nReadSurfaceBudget(0,
                new long[MayaComposition.SURFACE_BUDGET_FIELDS]) != 0, "wrong handle");
        ok(!release(40), "cannot retire more than reserved");
        ok(release(36), "reservation released");
        ok(!release(36), "cannot release twice");
        ok(MayaComposition.setSurfaceBudget(731, 0) == 0, "unlimited again");
        ok(reserve(10, 10) == 400, "cap removed");
        ok(release(400), "retire after unlimited quota");
        ok(MayaComposition.readSurfaceBudget(731)[MayaComposition.BUDGET_RESERVED_BYTES] == 0,
                "live count restored to zero");
        System.out.println("MayaSurfaceBudgetJniSmoke PASS: JDK21 -Xcheck:jni native budget");
    }
}
