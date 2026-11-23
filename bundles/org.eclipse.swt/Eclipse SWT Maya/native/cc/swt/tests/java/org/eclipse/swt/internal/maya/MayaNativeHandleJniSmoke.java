/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 ******************************************************************************/
package org.eclipse.swt.internal.maya;

/** An exact production-registry JNI smoke without platform COM dependencies. */
public final class MayaNativeHandleJniSmoke {
    private MayaNativeHandleJniSmoke() { }
    private static native long issueComposition();
    private static native long issueLayer();
    private static native boolean liveComposition(long handle);
    private static native boolean liveLayer(long handle);
    private static native boolean retireComposition(long handle);
    private static native boolean retireLayer(long handle);

    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        System.load(args[0]);
        long comp = issueComposition();
        long layer = issueLayer();
        require(comp > 0 && layer > 0, "both kinds issue nonzero positive JNI longs");
        require(liveComposition(comp) && liveLayer(layer), "live handles resolve");
        require(!liveComposition(layer) && !liveLayer(comp), "kinds never alias");
        require(!liveLayer(0) && !liveComposition(Long.MAX_VALUE), "invalid inputs refused");
        require(retireLayer(layer), "first layer retired");
        require(!liveLayer(layer), "old layer token stays retired");
        long newer = issueLayer();
        require(newer != layer && newer > layer, "reused C++ address receives a fresh JNI token");
        require(!retireLayer(layer), "old token cannot retire the new occupant");
        require(liveLayer(newer), "new occupant still live");
        long previous = newer;
        for (int i = 0; i < 10_000; i++) {
            require(retireLayer(previous), "retire cycle " + i);
            long next = issueLayer();
            require(next > previous && !liveLayer(previous), "non-reusable identity " + i);
            previous = next;
        }
        require(retireLayer(previous), "final layer retired");
        require(retireComposition(comp), "original composition retired");
        long newerComposition = issueComposition();
        require(newerComposition != comp && !liveComposition(comp),
                "recreated composition has a new native token");
        require(!retireComposition(comp), "stale composition cannot retire new owner");
        require(retireComposition(newerComposition), "all native test resources retired");
        System.out.println("MayaNativeHandleJniSmoke PASS: -Xcheck:jni, 10000 layer reissues, exact address reuse");
    }
}
