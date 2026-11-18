/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

import org.eclipse.swt.internal.render.MayaNativeLayers;

/** The optional adapter must work with the previous Maya seven-symbol ABI. */
public final class MayaFrameFallbackSmoke {
    public static void main(String[] args) {
        System.load(args[0]);
        long[] ids = new long[4096];
        float[] props = new float[ids.length * 4];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i + 1;
            props[i * 4 + 2] = 1;
            props[i * 4 + 3] = 1;
        }
        MayaNativeLayers backend = new MayaNativeLayers();
        for (int i = 0; i < 3; i++) {
            if (backend.apply(333, ids, props, ids.length) != 909) {
                throw new AssertionError("old Maya ABI fallback failed at call " + i);
            }
        }
        System.out.println("MayaFrameFallbackSmoke PASS: legacy seven-symbol ABI preserved");
    }
}
