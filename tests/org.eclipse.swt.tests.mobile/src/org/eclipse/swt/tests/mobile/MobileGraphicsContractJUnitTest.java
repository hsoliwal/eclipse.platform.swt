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
package org.eclipse.swt.tests.mobile;

import org.junit.jupiter.api.Test;

/** Executed graphics owner contracts; no Android/iOS qualification is implied. */
public final class MobileGraphicsContractJUnitTest {
	@Test void signedRoundedRectanglesRenderTheirCorners() { MobileGraphicsContractTest.testRoundedRectangles(); }
	@Test void arcsPreserveSwtAnglesAndFullEllipses() { MobileGraphicsContractTest.testArcs(); }
	@Test void textAntialiasRemainsIndependentAndResets() { MobileGraphicsContractTest.testTextAntialias(); }
	@Test void alphaUsesDesktopSwtByteMasking() { MobileGraphicsContractTest.testAlphaMasking(); }
	@Test void clippingReportsEffectiveBoundsAndCopiedPixels() { MobileGraphicsContractTest.testEffectiveClippingAndCopy(); }
	@Test void publicHandleDisposalAndThreadGuards() throws Exception { MobileGraphicsContractTest.testPublicHandleAndGuards(); }
}
