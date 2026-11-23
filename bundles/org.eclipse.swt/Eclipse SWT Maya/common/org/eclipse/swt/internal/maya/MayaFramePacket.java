/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * A versioned, fixed-stride, little-endian frame envelope for optional Maya
 * bulk JNI transport. The existing _nApply(long[], float[]) remains the
 * fallback and the compositor's semantic authority.
 *
 * <p>JNI reads the whole frame synchronously. An Encoder returns a borrowed
 * direct buffer: do not modify it or call encode again on that Encoder until
 * the native invocation finishes. The editor is UI-thread confined.</p>
 *
 * <p>Header: magic/u32, version/u16, headerSize/u16, recordSize/u16,
 * flags/u16, count/u32, epoch/u64, reserved/u64 (32 bytes).
 * Record: opaqueHandle/u64, x/f32, y/f32, opacity/f32,
 * visible/u32, reserved/u64 (32 bytes). No native pointers are dereferenced
 * by the codec, and the actual renderer still validates ownership.</p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class MayaFramePacket {
    public static final int MAGIC = 0x464c334d; // byte order spells M3LF
    public static final int VERSION = 1;
    public static final int HEADER_BYTES = 32;
    public static final int RECORD_BYTES = 32;
    public static final int PROPS_PER_LAYER = 4;
    public static final int MAX_LAYERS = 65_536;

    private MayaFramePacket() { }

    /** A reusable encoder without per-record objects or boxed handle IDs. */
    public static final class Encoder {
        private ByteBuffer buffer = ByteBuffer.allocateDirect(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        private long[] seen = new long[0];
        private int[] stamps = new int[0];
        private int generation;

        /** Validates all records before publishing any bytes to native code. */
        public ByteBuffer encode(long[] handles, float[] props, int count, long epoch) {
            Objects.requireNonNull(handles, "handles");
            Objects.requireNonNull(props, "properties");
            if (count < 0 || count > MAX_LAYERS) throw new IllegalArgumentException("invalid layer count");
            if (handles.length < count || props.length < (long) count * PROPS_PER_LAYER) {
                throw new IndexOutOfBoundsException("short native frame arrays");
            }
            int slots = 2;
            while (slots < count * 2) slots <<= 1;
            if (seen.length < slots) {
                seen = new long[slots];
                stamps = new int[slots];
                generation = 0;
            }
            generation++;
            if (generation == 0) {
                Arrays.fill(stamps, 0);
                generation = 1;
            }
            int mask = seen.length - 1;
            for (int i = 0; i < count; i++) {
                long handle = handles[i];
                int p = i * PROPS_PER_LAYER;
                if (handle == 0) throw new IllegalArgumentException("zero native layer handle at " + i);
                if (!Float.isFinite(props[p]) || !Float.isFinite(props[p + 1])
                        || !Float.isFinite(props[p + 2]) || props[p + 2] < 0 || props[p + 2] > 1
                        || (props[p + 3] != 0 && props[p + 3] != 1)) {
                    throw new IllegalArgumentException("invalid native layer property at " + i);
                }
                int slot = mix(handle) & mask;
                while (stamps[slot] == generation) {
                    if (seen[slot] == handle) throw new IllegalArgumentException("duplicate native layer handle");
                    slot = (slot + 1) & mask;
                }
                seen[slot] = handle;
                stamps[slot] = generation;
            }
            int bytes = HEADER_BYTES + count * RECORD_BYTES; // bounded by MAX_LAYERS
            if (buffer.capacity() < bytes) {
                int capacity = buffer.capacity();
                while (capacity < bytes) capacity = Math.min(HEADER_BYTES + MAX_LAYERS * RECORD_BYTES, capacity * 2);
                buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
            }
            buffer.clear();
            buffer.putInt(MAGIC).putShort((short) VERSION).putShort((short) HEADER_BYTES);
            buffer.putShort((short) RECORD_BYTES).putShort((short) 0).putInt(count);
            buffer.putLong(epoch).putLong(0);
            for (int i = 0; i < count; i++) {
                int p = i * PROPS_PER_LAYER;
                buffer.putLong(handles[i]).putFloat(props[p]).putFloat(props[p + 1]);
                buffer.putFloat(props[p + 2]).putInt((int) props[p + 3]).putLong(0);
            }
            buffer.flip();
            // Independent position/limit; storage itself remains borrowed.
            return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        }

        private static int mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            return (int) (value ^ value >>> 33);
        }
    }
}
