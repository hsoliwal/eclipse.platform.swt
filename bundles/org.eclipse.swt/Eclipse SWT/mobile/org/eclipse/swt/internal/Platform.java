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

import org.eclipse.swt.internal.mobile.MobileRuntime;

/** Platform identity comes from the installed, OS-specific application host. */
public final class Platform {
	public static final String PLATFORM = MobileRuntime.platform().name();
	private Platform() { }
	public static boolean isLoadable() { MobileRuntime.platform().checkFailure(); return true; }
	public static void exitIfNotLoadable() { if (!isLoadable()) throw new IllegalStateException("SWT mobile native host is unavailable"); }
}
