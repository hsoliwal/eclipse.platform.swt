// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
package org.eclipse.swt.internal.maya;

/* ABI-only class: identical JNI owner and signatures, not the product loader. */
final class MayaComposition {
    static native long _nCreate(long window);
    static native long _nAddLayer(long composition, int width, int height);
    static native int _nUpload(long layer, int[] pixels);
    static native int _nUploadDirty(long layer, int[] pixels, int[] rects, int count);
    static native int _nApply(long composition, long[] handles, float[] props, int count);
    static native void _nRemoveLayer(long composition, long layer);
    static native int _nWaitForCommit(long composition);
    static native void _nDispose(long composition);
}

/** Resolves the original seven and optional new JNI symbols against Linux stubs. */
public final class MayaCompositionStubProof {
    private MayaCompositionStubProof() { }
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("native library file required");
        System.load(args[0]);
        if (MayaComposition._nCreate(0) != 0) throw new AssertionError("no compositor on Linux");
        if (MayaComposition._nAddLayer(0, 1, 1) != 0) throw new AssertionError("no native layer");
        if (MayaComposition._nUpload(0, new int[1]) != -1) throw new AssertionError("legacy upload stub");
        if (MayaComposition._nUploadDirty(0, new int[1], new int[] {0,0,1,1}, 1) != -1)
            throw new AssertionError("new upload stub");
        if (MayaComposition._nApply(0, new long[0], new float[0], 0) != -1)
            throw new AssertionError("apply stub");
        MayaComposition._nRemoveLayer(0, 0);
        if (MayaComposition._nWaitForCommit(0) != -1) throw new AssertionError("wait stub");
        MayaComposition._nDispose(0);
        System.out.println("PASS 8 Java21/-Xcheck:jni Maya composition ABI symbol calls (Linux unsupported stubs)");
    }
}
