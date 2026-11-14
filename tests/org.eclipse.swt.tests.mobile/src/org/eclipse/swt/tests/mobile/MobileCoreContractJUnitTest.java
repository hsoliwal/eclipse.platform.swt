/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.mobile;

import org.junit.jupiter.api.Test;

/** Executed Surefire/JUnit5 entrypoints; these tests do not qualify a mobile OS. */
public final class MobileCoreContractJUnitTest {
	@Test void unchangedNebulaWidgetAndPainting() throws Exception {
		MobileCoreContractTest.testOriginalWidgetAndPainting();
	}
	@Test void originalThreadAndEventLoopContracts() throws Exception {
		MobileCoreContractTest.testThreadAndQueueContracts();
	}
	@Test void graphicsAndReentrantPaintDisposal() {
		MobileCoreContractTest.testGraphicsAndDisposalDuringPaint();
	}
	@Test void hostTerminationAndBlockedSynchronousCaller() throws Exception {
		MobileCoreContractTest.testDestructionAndBlockedSyncExec();
	}
	@Test void ownedShellCoordinatesUseNativeRoot() {
		MobileCoreContractTest.testOwnedShellCoordinateMapping();
	}
	@Test void originalMobileButtonLabelPaintingAndSelection() {
		MobileCoreContractTest.testMobileButtonLabelContracts();
	}

}
