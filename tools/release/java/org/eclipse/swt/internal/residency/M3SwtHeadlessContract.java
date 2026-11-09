/*******************************************************************************
 * Copyright (c) 2026 Contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.residency;

import java.util.BitSet;
import java.util.Random;

import org.eclipse.swt.internal.PackedHeapGrowth;

/**
 * Java 21 executable, JUnit-free differential proof of the SWT M3 sparse
 * storage subset. This tests exact existing sources, not an extracted rewrite.
 * It does not claim JNI, GUI, OSGi or native-platform correctness.
 */
public final class M3SwtHeadlessContract {
    private static final int LIMIT = 3 * 65536 + 97;
    private static final long SEED = 20261107L;
    private static long checks;

    private M3SwtHeadlessContract() { }

    private static void eq(long expected, long actual, String what) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(what + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void check(boolean result, String what) {
        checks++;
        if (!result) throw new AssertionError(what);
    }

    private static void probe(ItemBitSet set, BitSet reference, int from, int to) {
        eq(reference.get(from) ? 1 : 0, set.get(from) ? 1 : 0, "get@" + from);
        eq(reference.get(to - 1) ? 1 : 0, set.get(to - 1) ? 1 : 0, "get@" + (to - 1));
        eq(reference.get(from, to).cardinality(), set.count(from, to),
                "count[" + from + "," + to + ")");
        int expectedNext = reference.nextSetBit(from);
        if (expectedNext >= to) expectedNext = -1;
        eq(expectedNext, set.next(from, to), "next[" + from + "," + to + ")");
    }

    private static void differential() {
        ItemBitSet set = new ItemBitSet();
        BitSet reference = new BitSet();
        Random random = new Random(SEED);
        eq(-1, set.next(0, LIMIT), "initial next");
        for (int step = 0; step < 2500; step++) {
            int from = random.nextInt(LIMIT);
            int to = Math.min(LIMIT, from + 1 + random.nextInt(2048));
            switch (random.nextInt(4)) {
                case 0 -> { set.set(from, true); reference.set(from); }
                case 1 -> { set.set(from, false); reference.clear(from); }
                case 2 -> { set.setRange(from, to, true); reference.set(from, to); }
                default -> { set.setRange(from, to, false); reference.clear(from, to); }
            }
            if (step % 19 == 0) {
                probe(set, reference, from, to);
                probe(set, reference, 0, LIMIT);
                probe(set, reference, 65530, 65566);
                probe(set, reference, 131066, 131102);
            }
        }
        for (int from = 0; from < LIMIT; from += 4001) {
            probe(set, reference, from, Math.min(LIMIT, from + 4001));
        }
    }

    private static void maximumBoundary() {
        int max = Integer.MAX_VALUE;
        ItemBitSet set = new ItemBitSet();
        set.set(max, true);
        check(set.get(max), "MAX_VALUE point survives");
        eq(-1, set.next(max - 1, max), "exclusive MAX_VALUE limit");
        set.setRange(max - 7, max, true);
        eq(7, set.count(max - 7, max), "last chunk range");
        eq(max - 7, set.next(max - 7, max), "last chunk next");
        set.setRange(max - 7, max, false);
        eq(0, set.count(max - 7, max), "last chunk clear");
        check(set.get(max), "exclusive-limit sentinel retained");
    }

    private static void memoryGrowth() {
        int normal = PackedHeapGrowth.choose(8, 9, 4, 512L << 20, 0);
        check(normal >= 9, "normal allocation preserves minimum");
        int pressure = PackedHeapGrowth.choose(8, 9, 4, 16L << 20, 15L << 20);
        eq(9, pressure, "heap-pressure allocation avoids geometric slack");
    }

    public static void main(String[] args) {
        differential();
        maximumBoundary();
        memoryGrowth();
        System.out.println("M3_SWT_HEADLESS_JAVA21_PASS checks=" + checks
                + " seed=" + SEED + " scope=ItemBitSet,PackedHeapGrowth");
    }
}
