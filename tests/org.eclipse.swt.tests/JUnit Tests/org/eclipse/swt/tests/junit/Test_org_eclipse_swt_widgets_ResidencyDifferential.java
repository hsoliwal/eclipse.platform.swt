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
package org.eclipse.swt.tests.junit;

import java.io.*;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Recorded differential of the item widgets' public behaviour against the official SWT jar (leaf 7): the
 * visible rows of the Table and List from public API; the internal path is compared with the stock path in process by ResidencyTests ('Eclipse SWT Tests/win32') (the resident
 * range covers those rows, the stats are ordered, <code>isMaterialized</code> never materializes).
 * Recording directory: <code>-Dswt.viewport.recording</code>.
 */
public class Test_org_eclipse_swt_widgets_ResidencyDifferential {
	static String resident (M3OfficialScene scene) {
		return "T " + scene.visibleTable () + " L " + scene.visibleList ();
	}

	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330007L, M3OfficialScene::next).widget ("Table")
			.probe ("resident", Test_org_eclipse_swt_widgets_ResidencyDifferential::resident)
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-residency-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-residency-virtual", SWT.VIRTUAL);
	}
}
