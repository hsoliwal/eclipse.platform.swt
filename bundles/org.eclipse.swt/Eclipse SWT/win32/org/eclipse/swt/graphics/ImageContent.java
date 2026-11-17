/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.graphics;

/**
 * Whether the pixels of an image may have changed, for drawing backends that keep a converted copy of an image (the
 * Skia GC). The pixels of an image change only while a GC draws into it.
 * <p>
 * <b>IMPORTANT:</b> This class is <em>not</em> part of the public API for SWT. It is marked public only so that it can be
 * shared within the packages provided by SWT. It is not available on all platforms, and should never be called from
 * application code.
 * </p>
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public final class ImageContent {

	private ImageContent() {
	}

	/**
	 * Returns a number that changes whenever a GC is created on the image and whenever it is disposed. A copy of the
	 * pixels taken while no GC draws into the image stays valid while the number does not change.
	 *
	 * @param image the image
	 * @return the number
	 */
	public static int changes(Image image) {
		return image.contentChanges;
	}

	/**
	 * Returns whether a GC draws into the image now.
	 *
	 * @param image the image
	 * @return <code>true</code> while a GC on the image is not disposed
	 */
	public static boolean isDrawnInto(Image image) {
		return image.memGC != null;
	}
}
