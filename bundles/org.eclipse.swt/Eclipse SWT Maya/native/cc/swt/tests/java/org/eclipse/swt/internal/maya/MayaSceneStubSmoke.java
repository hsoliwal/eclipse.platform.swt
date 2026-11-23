/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

import org.eclipse.swt.internal.render.MayaNativeLayers;

/** Verifies original Java declarations against the real non-Windows JNI owner. */
public final class MayaSceneStubSmoke {

    public static void main(String[] args) {
        System.load(args[0]);
        MayaNativeLayers backend = new MayaNativeLayers();
        if (MayaComposition._nCapabilities(0) != 0 || backend.capabilities(123) != 0) {
            throw new AssertionError("unsupported native backend advertised rendering features");
        }
        if (MayaComposition._nApplyScene(123, new long[0], new float[0], new int[0], 0) != -1) {
            throw new AssertionError("non-Windows scene export must report unsupported");
        }
        if (backend.applyScene(123, new long[0], new float[0], new int[0], 0) != -1) {
            throw new AssertionError("adapter must preserve scene unsupported result");
        }
        if (backend.apply(123, new long[0], new float[0], 0) != -1) {
            throw new AssertionError("legacy non-Windows apply contract changed");
        }
        if (MayaComposition._nFramePacketVersion() != MayaFramePacket.VERSION) {
            throw new AssertionError("scene extension changed packed transport version");
        }
        if (MayaComposition.PROPS != 4 || MayaComposition.SCENE_FLOATS != 14
                || MayaComposition.SCENE_CLIP != 1 || MayaComposition.CAP_AFFINE_2D != 1
                || MayaComposition.CAP_LOCAL_RECT_CLIP != 2 || MayaComposition.CAP_DIRTY_UPLOAD != 4) {
            throw new AssertionError("native scene ABI constants changed");
        }
        System.out.println("MayaSceneStubSmoke PASS: native symbols, unsupported backend and legacy packet version");
    }
}
