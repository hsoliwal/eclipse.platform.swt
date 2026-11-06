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
package org.eclipse.swt.internal;

import java.util.*;
import java.util.concurrent.*;

/**
 * BACKEND_REPORT v1 (census L10, real-app fidelity leaf): one log line per widget class at its first drawn route, with the
 * mode it realizes and the backend that draws it (for example <code>Table: m3 via Table drawn plane, M3RowCanvas(raster)</code>).
 * The per-instance answer is <code>M3Mode.backend</code>.
 */
public final class M3BackendReport {
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.internal.M3BackendReport");
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet ();

	private M3BackendReport () {
	}

	/**
	 * Logs the route of a widget class once.
	 *
	 * @param widget the simple class name
	 * @param route the mode the backend realizes
	 * @param backend the backend's description
	 */
	public static void first (String widget, WidgetMode route, String backend) {
		if (SEEN.add (widget)) LOG.info (() -> widget + ": " + route.name ().toLowerCase (Locale.ROOT) + " via " + backend);
	}
}
