/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Cross-language, Java 21 JNI smoke; uses a standalone MayaComposition test shim. */
public final class MayaFramePacketSmoke {
    private static void eq(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable code) {
        try { code.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid frame should be rejected");
    }
    public static void main(String[] args) {
        System.load(args[0]);
        eq(MayaComposition._nFramePacketVersion() == 1, "packet ABI version");
        MayaFramePacket.Encoder codec = new MayaFramePacket.Encoder();
        long[] handles = { 0x8000_0000_0000_0001L, 19L };
        float[] props = { 3.5f, -7.25f, .5f, 1f, -1f, 2f, 1f, 0f };
        ByteBuffer packet = codec.encode(handles, props, 2, 101);
        eq(packet.isDirect() && packet.order() == ByteOrder.LITTLE_ENDIAN, "direct little endian");
        eq(packet.remaining() == 96, "exact encoded bytes");
        eq(packet.getInt(0) == MayaFramePacket.MAGIC && packet.getLong(16) == 101, "header");
        eq(packet.getLong(32) == handles[0] && packet.getInt(52) == 1, "first record");
        eq(MayaComposition._nApplyPacked(999L, packet, packet.remaining()) == 777, "transport fields/ordering");
        eq(MayaComposition._nApplyPacked(999L, packet, packet.remaining() - 1) == -2, "bad length");
        ByteBuffer heap = ByteBuffer.wrap(new byte[96]);
        eq(MayaComposition._nApplyPacked(999L, heap, 96) == -2, "heap buffers rejected");
        packet.putInt(0, 0);
        eq(MayaComposition._nApplyPacked(999L, packet, 96) == -2, "magic checked natively");
        packet = codec.encode(handles, props, 2, 102);
        packet.putLong(32 + 32, handles[0]);
        eq(MayaComposition._nApplyPacked(999L, packet, 96) == -2, "duplicates checked natively");
        packet = codec.encode(handles, props, 2, 103);
        packet.putInt(32 + 16, 0x7fc00000);
        eq(MayaComposition._nApplyPacked(999L, packet, 96) == -2, "NaN checked natively");
        rejects(() -> codec.encode(new long[] { 0, 9 }, props, 2, 1));
        rejects(() -> codec.encode(new long[] { 9, 9 }, props, 2, 1));
        rejects(() -> codec.encode(handles, new float[] { Float.NaN, 0, 1, 1, 0, 0, 1, 0 }, 2, 1));
        rejects(() -> codec.encode(handles, new float[] { 0, 0, 1, 5, 0, 0, 1, 0 }, 2, 1));
        eq(codec.encode(new long[0], new float[0], 0, 1).remaining() == 32, "zero layer frame");
        eq(MayaComposition._nApplyPacked(999L, codec.encode(new long[0], new float[0], 0, 1), 32) == 777,
                "zero layer native frame");
        long[] manyHandles = new long[4096];
        float[] manyProps = new float[4096 * 4];
        for (int i = 0; i < manyHandles.length; i++) {
            manyHandles[i] = i + 1;
            manyProps[i * 4 + 2] = 1;
            manyProps[i * 4 + 3] = i % 2;
        }
        ByteBuffer many = codec.encode(manyHandles, manyProps, manyHandles.length, 1034);
        eq(many.remaining() == 32 + 4096 * 32, "bulk payload");
        eq(MayaComposition._nApplyPacked(777L, many, many.remaining()) == 778, "bulk native transfer");
        System.out.println("MayaFramePacketSmoke PASS: JDK21 -Xcheck:jni, 4096-layer native transport");
    }
}
