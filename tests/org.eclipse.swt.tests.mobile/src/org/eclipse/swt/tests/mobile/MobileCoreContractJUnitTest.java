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
	@Test void disposingParentCannotCreateAnUnownedChild() {
		MobileCoreContractTest.testDisposingParentRejectsNewChildren();
	}
	@Test void disposingDisplayCannotCreateUnownedWidgets() {
		MobileCoreContractTest.testDisposingDisplayRejectsNewOwners();
	}
	@Test void shellOpeningToleratesListenerDisposal() {
		MobileCoreContractTest.testShellOpenSurvivesListenerDisposal();
	}
	@Test void layoutCallbacksMayDisposeOwnersAndSiblings() {
		MobileCoreContractTest.testLayoutCallbacksMayDisposeControls();
	}
	@Test void hiddenOrDisabledAncestorReleasesFocus() {
		MobileCoreContractTest.testAncestorStateReleasesFocus();
	}
	@Test void nestedFocusChangesKeepTheirActualOwner() {
		MobileCoreContractTest.testFocusOutReentrancyPreservesOwner();
	}
	@Test void failingFocusOutDoesNotRetainNativeOwners() {
		MobileCoreContractTest.testFailingFocusOutStillReleasesControl();
	}
	@Test void disablingMayDisposeTheFocusOwnerOrAncestor() {
		MobileCoreContractTest.testDisableFocusOutMayDisposeOwners();
	}
	@Test void executeNullFollowsExecutorContract() {
		MobileCoreContractTest.testDisplayExecuteNullContract();
	}
	@Test void nullDisposeCallbackIsIgnored() {
		MobileCoreContractTest.testDisplayDisposeExecNullContract();
	}
	@Test void nativePostingRefusesUnsupportedInjectionWithoutSyntheticEvents() throws Exception {
		MobileCoreContractTest.testUnsupportedNativeEventPosting();
	}
	@Test void originalPublicResourceAccessorsRemainCompatible() {
		MobileCoreContractTest.testMobilePublicResourceAliases();
	}
	@Test void equivalentDefaultFontLocalesHaveOneValueIdentity() {
		MobileCoreContractTest.testFontDataDefaultLocaleNormalization();
	}
	@Test void bordersAlignClientCoordinatesImagesAndPrinting() {
		MobileCoreContractTest.testBorderClientGeometryAndPainting();
	}
	@Test void emptyBorderClientReleasesItsImageAfterGcLeases() {
		MobileCoreContractTest.testEmptyBorderClientReleasesImage();
	}
	@Test void ownedShellCoordinatesUseNativeRoot() {
		MobileCoreContractTest.testOwnedShellCoordinateMapping();
	}
	@Test void originalMobileButtonLabelPaintingAndSelection() {
		MobileCoreContractTest.testMobileButtonLabelContracts();
	}
	@Test void originalMobileProgressAndScalePaintingAndSelection() {
		MobileCoreContractTest.testMobileProgressAndScaleContracts();
	}
	@Test void originalMobileListInputAndSparseVirtualRows() {
		MobileCoreContractTest.testMobileListContracts();
	}
	@Test void originalMobileNativeTextVerifyModifyAndUtf16() {
		MobileCoreContractTest.testMobileOriginalTextContracts();
	}
	@Test void originalMobileComboTextListPopupAndSelection() {
		MobileCoreContractTest.testMobileComboContracts();
	}
	@Test void mobileListHighIndicesStayWithinSmallHeap() throws Exception {
		MobileListContractTest.testBoundedHeapProcess();
	}
	@Test void mobileListMatchesStructuralAndSelectionOracle() {
		MobileListContractTest.testStructuralAndSelectionOracle();
	}
	@Test void mobileListSetDataCanChangeItsOwner() {
		MobileListContractTest.testSetDataReentrancy();
	}

	@Test void originalMobileTableSparseVirtualRowsAndNativeSelection() {
		MobileCoreContractTest.testMobileOriginalTableContracts();
	}

}
