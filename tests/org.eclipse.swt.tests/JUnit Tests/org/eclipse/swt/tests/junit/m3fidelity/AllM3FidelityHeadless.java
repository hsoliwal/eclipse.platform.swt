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
package org.eclipse.swt.tests.junit.m3fidelity;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/** The headless classes of the real-app fidelity leaf, and the render tests its row atoms touch (tools/test_m3fidelity.sh headless). */
@Suite
@SelectClasses({ Test_org_eclipse_swt_internal_render_RowFidelity.class, //
		Test_org_eclipse_swt_internal_render_m3_M3GdiEllipsis.class, //
		org.eclipse.swt.tests.junit.Test_org_eclipse_swt_internal_render_RowRenderer.class, //
		org.eclipse.swt.tests.junit.Test_org_eclipse_swt_internal_render_RowPaintKey.class })
public class AllM3FidelityHeadless {
}
