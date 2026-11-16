/* SPDX-FileCopyrightText: 2026 Synexia contributors
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package org.eclipse.swt.widgets;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;
import java.util.function.IntSupplier;

/** Checks the actual SWT owner code against a boolean oracle and a high-watermark work budget. */
public final class VirtualBitLanesLogicalPrefixContract {
    private static volatile int sink;
    private VirtualBitLanesLogicalPrefixContract() { }
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
    private static int[][] backing(VirtualBitLanes v) throws Exception {
        Field f = VirtualBitLanes.class.getDeclaredField("lanes");
        f.setAccessible(true);
        return (int[][]) f.get(v);
    }
    private static void retained() throws Exception {
        VirtualBitLanes v = new VirtualBitLanes(2);
        v.setLength(10_000_000);
        v.set(0, 9_999_999, true);
        v.set(1, 7_000_000, true);
        int[][] rows = backing(v);
        int[] first = rows[0], second = rows[1];
        check(first.length >= 312_500, "allocated physical capacity");
        v.setLength(17);
        check(rows[0] == first && rows[1] == second, "retained identity");
        check(v.count(0) == 0 && v.nextSet(1, 0) == -1, "shrunk count/next");
        v.set(0, 16, true);
        check(v.count(0) == 1 && v.nextSet(0, -3) == 16, "final partial word");
        v.setLength(10_000_000);
        check(v.count(0) == 1 && v.nextSet(0, 17) == -1, "regrowth must not revive bits");
    }
    private static void differential() {
        Random r = new Random(0x20261116L);
        int comparisons = 0;
        for (int round = 0; round < 70; round++) {
            VirtualBitLanes v = new VirtualBitLanes(2);
            boolean[][] expected = new boolean[2][512];
            int length = 0;
            for (int step = 0; step < 220; step++) {
                int lane = r.nextInt(2);
                switch (r.nextInt(4)) {
                    case 0 -> {
                        int next = r.nextInt(513);
                        if (next < length) for (boolean[] x : expected) Arrays.fill(x, next, length, false);
                        v.setLength(next);
                        length = next;
                    }
                    case 1 -> {
                        if (length > 0) {
                            int index = r.nextInt(length);
                            boolean bit = r.nextBoolean();
                            v.set(lane, index, bit);
                            expected[lane][index] = bit;
                        }
                    }
                    case 2 -> {
                        int p = r.nextInt(length + 1), q = r.nextInt(length + 1);
                        int from = Math.min(p, q), to = Math.max(p, q);
                        boolean bit = r.nextBoolean();
                        v.setRange(lane, from, to, bit);
                        Arrays.fill(expected[lane], from, to, bit);
                    }
                    default -> {
                        int p = r.nextInt(length + 1), q = r.nextInt(length + 1);
                        int from = Math.min(p, q), to = Math.max(p, q);
                        v.flipRange(lane, from, to);
                        for (int i = from; i < to; i++) expected[lane][i] = !expected[lane][i];
                    }
                }
                for (int l = 0; l < 2; l++) {
                    int total = 0;
                    for (int i = 0; i < length; i++) if (expected[l][i]) total++;
                    check(v.count(l) == total && v.count(l, 0, length) == total, "count");
                    for (int from : new int[] {-3, 0, length / 2, length, length + 3}) {
                        int begin = Math.max(0, from), set = -1, clear = length;
                        for (int i = begin; i < length; i++) {
                            if (set < 0 && expected[l][i]) set = i;
                            if (clear == length && !expected[l][i]) clear = i;
                        }
                        check(v.nextSet(l, from) == set, "nextSet");
                        check(v.nextClear(l, from) == clear, "nextClear");
                        comparisons++;
                    }
                }
            }
        }
        System.out.println("BITLANE_DIFFERENTIAL comparisons=" + comparisons + " result=PASS");
    }
    private static int originalCount(int[] words) {
        int sum = 0;
        for (int x : words) sum += Integer.bitCount(x);
        return sum;
    }
    private static int originalNextSet(int[] words, int length) {
        int i = 0, bits = words[0];
        while (true) {
            if (bits != 0) {
                int found = (i << 5) + Integer.numberOfTrailingZeros(bits);
                return found < length ? found : -1;
            }
            if (++i >= words.length) return -1;
            bits = words[i];
        }
    }
    private static long bestNs(IntSupplier source) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            int aggregate = 0;
            long start = System.nanoTime();
            for (int i = 0; i < 650; i++) aggregate ^= source.getAsInt();
            long duration = System.nanoTime() - start;
            sink = aggregate;
            best = Math.min(best, duration);
        }
        return best;
    }
    private static void resourceBudget() throws Exception {
        VirtualBitLanes v = new VirtualBitLanes(1);
        v.setLength(10_000_000);
        v.set(0, 9_999_999, true);
        v.setLength(17);
        int[] words = backing(v)[0];
        check(words.length >= 312_500 && v.length() == 17, "high-watermark setup");
        long baselineCount = bestNs(() -> originalCount(words));
        long ownerCount = bestNs(() -> v.count(0));
        long baselineNext = bestNs(() -> originalNextSet(words, v.length()));
        long ownerNext = bestNs(() -> v.nextSet(0, 0));
        System.out.println("BITLANE_RESOURCE physicalWords=" + words.length
                + " logicalWords=1 baselineCountNs=" + baselineCount + " ownerCountNs=" + ownerCount
                + " baselineNextNs=" + baselineNext + " ownerNextNs=" + ownerNext);
        check(ownerCount * 8 < baselineCount, "count scanned unused physical words");
        check(ownerNext * 8 < baselineNext, "nextSet scanned unused physical words");
    }
    public static void main(String[] args) throws Exception {
        retained();
        differential();
        resourceBudget();
        System.out.println("BITLANE_PREFIX_CONTRACT result=PASS");
    }
}
