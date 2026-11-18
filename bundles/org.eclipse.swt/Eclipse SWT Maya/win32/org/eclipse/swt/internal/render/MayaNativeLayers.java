/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.render;

import org.eclipse.swt.internal.NativeLayers;
import org.eclipse.swt.internal.maya.MayaComposition;
import org.eclipse.swt.widgets.Control;

/**
 * maya's native layers: {@link NativeLayers} over {@link MayaComposition} (DirectComposition on Windows). SWT finds
 * this class by name ({@link NativeLayers#BACKEND}); without the maya fragment it is absent and layers stay painted.
 */
public final class MayaNativeLayers implements NativeLayers {

	@Override
	public long create(Control control) {
		return MayaComposition._nCreate(control.handle);
	}

	@Override
	public long addLayer(long composition, int width, int height) {
		return MayaComposition._nAddLayer(composition, width, height);
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
		return MayaComposition._nApply(composition, layers, props, count);
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
