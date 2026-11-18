/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render;

import java.nio.ByteBuffer;

import org.eclipse.swt.internal.NativeLayers;
import org.eclipse.swt.internal.maya.MayaComposition;
import org.eclipse.swt.internal.maya.MayaFramePacket;
import org.eclipse.swt.widgets.Control;

/**
 * maya's native layers: {@link NativeLayers} over {@link MayaComposition} (DirectComposition on Windows). SWT finds
 * this class by name ({@link NativeLayers#BACKEND}); without the maya fragment it is absent and layers stay painted.
 */
public final class MayaNativeLayers implements NativeLayers {

	// Off by default: v1 decodes into a native Frame and applies it directly
	// through the legacy compositor transaction owner, without staging JNI
	// arrays. Direct-buffer encoding/decoding still copies bytes; no speedup
	// or true end-to-end zero-copy transport is claimed.
	private static final boolean PACKED_FRAME = Boolean.getBoolean("swt.maya.packedFrame");
	private static volatile int packetSupport; // 0 unknown, 1 supported, -1 missing
	private static final ThreadLocal<MayaFramePacket.Encoder> ENCODER =
			ThreadLocal.withInitial(MayaFramePacket.Encoder::new);
	private long epoch;

	@Override
	public long create(Control control) {
		return MayaComposition._nCreate(control.handle);
	}

	@Override
	public long addLayer(long composition, int width, int height) {
		return MayaComposition._nAddLayer(composition, width, height);
	}

	@Override
	public long capabilities(long composition) {
		return MayaComposition.capabilities(composition);
	}

	@Override
	public int upload(long layer, int[] premultipliedArgb) {
		return MayaComposition._nUpload(layer, premultipliedArgb);
	}

	@Override
	public int uploadDirty(long layer, int[] packedArgb, int[] regions, int count) {
		return MayaComposition.uploadDirty(layer, packedArgb, regions, count);
	}

	@Override
	public int apply(long composition, long[] layers, float[] props, int count) {
		if (PACKED_FRAME && packetSupport >= 0 && count >= 64 && count <= MayaFramePacket.MAX_LAYERS) {
			try {
				if (packetSupport == 0) {
					packetSupport = MayaComposition._nFramePacketVersion() == MayaFramePacket.VERSION ? 1 : -1;
				}
				if (packetSupport == 1) {
					ByteBuffer packet = ENCODER.get().encode(layers, props, count, ++epoch);
					return MayaComposition._nApplyPacked(composition, packet, packet.remaining());
				}
			} catch (UnsatisfiedLinkError olderMayaBinary) {
				// Previously packaged seven-symbol Maya DLL/SO: preserve its old ABI.
				packetSupport = -1;
			}
		}
		return MayaComposition._nApply(composition, layers, props, count);
	}

	@Override
	public int applyScene(long composition, long[] layers, float[] packed, int[] flags, int count) {
		return MayaComposition.applyScene(composition, layers, packed, flags, count);
	}

	@Override
	public void removeLayer(long composition, long layer) {
		MayaComposition._nRemoveLayer(composition, layer);
	}

	@Override
	public int waitForCommit(long composition) {
		return MayaComposition._nWaitForCommit(composition);
	}

	@Override
	public void dispose(long composition) {
		MayaComposition._nDispose(composition);
	}
}
