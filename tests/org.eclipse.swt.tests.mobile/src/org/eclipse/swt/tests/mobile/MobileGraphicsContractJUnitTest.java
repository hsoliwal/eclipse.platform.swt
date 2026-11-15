/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
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
