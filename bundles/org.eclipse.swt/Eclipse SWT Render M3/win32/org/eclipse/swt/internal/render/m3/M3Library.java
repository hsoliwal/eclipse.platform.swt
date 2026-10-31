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
package org.eclipse.swt.internal.render.m3;

import org.eclipse.swt.internal.*;

/**
 * M3_LOAD_LATCH v1 (census L10, real-app fidelity leaf): whether the bound library's native part loads, asked once per JVM
 * before any M3 handler or row backend is made. A <code>LinkageError</code> (no <code>skija.dll</code> for this platform,
 * an unreadable or empty <code>skija.library.path</code>) is logged once and latched: canvases and composites then take the
 * stock path and drawn planes the GC backend, with no exception in any paint (before, every paint of a handled canvas threw
 * <code>UnsatisfiedLinkError</code> and then <code>NoClassDefFoundError</code>).
 */
public final class M3Library {
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.internal.render.m3.M3Library");
	private static volatile Boolean loaded;

	private M3Library () {
	}

	/** @return whether the native library is loaded (loading it on the first call) */
	public static boolean loaded () {
		Boolean value = loaded;
		if (value == null) loaded = value = load ();
		return value;
	}

	private static boolean load () {
		try {
			io.github.humbleui.skija.impl.Library.staticLoad ();
			return true;
		} catch (LinkageError e) {
			LOG.info (() -> "the M3 renderer's native library cannot load (" + e + "): canvases take the stock path, drawn planes the GC backend");
			return false;
		}
	}
}
