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
 * Recorded differential of the item widgets' public behaviour against the official SWT jar (leaf 9): the
 * public state after every shared operation; the internal path is compared with the stock path in process by PrefetchTests ('Eclipse SWT Tests/win32') (a window of 2 rows
 * before and 3 after, the announced rows against the resident range, and the public state with and without
 * a listener).
 */
public class Test_org_eclipse_swt_widgets_PrefetchDifferential {
	static void run (String name, int style) throws IOException {
		ViewportDifferential<M3OfficialScene> harness = ViewportDifferential.of (name, 0x4D330009L, M3OfficialScene::next).widget ("Table")
			.probe ("counts", M3OfficialScene::counts)
			.beforeSeed (M3OfficialScene::reset)
			.afterStep (M3OfficialScene::drain);
		M3OfficialScene.run (harness, style, scene -> {});
	}

	@Test
	public void plainWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-prefetch-plain", SWT.NONE);
	}

	@Test
	public void virtualWidgetsAnswerLikeTheOfficialJar () throws IOException {
		run ("m3-prefetch-virtual", SWT.VIRTUAL);
	}
}
