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

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executed Surefire/JUnit5 entrypoints; these tests do not qualify a mobile OS. */
public final class MobileCoreContractJUnitTest {
	@Test void explicitBevelApiDoesNotAliasMobileConstructorStyles() {
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			Composite composite = new Composite(shell, SWT.SHADOW_ETCHED_IN);
			assertEquals(SWT.NONE, composite.getBevelBorder());
			composite.setBevelBorder(SWT.NONE);
			composite.setBevelBorder(SWT.SHADOW_NONE);
			assertEquals(SWT.NONE, composite.getBevelBorder());
			assertThrows(IllegalArgumentException.class,
					() -> composite.setBevelBorder(SWT.SHADOW_IN | SWT.SHADOW_OUT));
			SWTError unsupported = assertThrows(SWTError.class,
					() -> composite.setBevelBorder(SWT.SHADOW_IN));
			assertEquals(SWT.ERROR_NOT_IMPLEMENTED, unsupported.code);
			assertEquals(SWT.NONE, composite.getBevelBorder());
		} finally {
			display.dispose();
		}
	}

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
	@Test void nativeSafeAreaSnapshotsAndThreadOwnership() throws Exception {
		MobileCoreContractTest.testNativeSafeAreaSnapshotContracts();
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
	@Test void originalMobileSashDragKeyboardAndAccessibility() {
		MobileCoreContractTest.testMobileSashContracts();
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

	@Test void childrenCannotEscapeDisposingParent() {
		MobileLifecycleContractTest.childrenCannotEscapeDisposingParent();
	}

	@Test void shellsCannotEscapeDisposingDisplay() {
		MobileLifecycleContractTest.shellsCannotEscapeDisposingDisplay();
	}

	@Test void lateDisposeCallbacksCannotEscapeCleanup() {
		MobileLifecycleContractTest.lateDisposeCallbacksCannotEscapeCleanup();
	}

	@Test void throwingFocusOutStillReleasesOwner() {
		MobileLifecycleContractTest.throwingFocusOutStillReleasesOwner();
	}

	@Test void disabledAndHiddenAncestorsClearFocusAndRejectInput() {
		MobileLifecycleContractTest.disabledAndHiddenAncestorsClearFocusAndRejectInput();
	}

	@Test void focusOutMayDisposeDuringDisableAndHide() {
		MobileLifecycleContractTest.focusOutMayDisposeDuringDisableAndHide();
	}

	@Test void reentrantFocusRedirectionKeepsFinalOwner() {
		MobileLifecycleContractTest.reentrantFocusRedirectionKeepsFinalOwner();
	}

	@Test void showMayDisposeShellWithoutStaleAccess() {
		MobileLifecycleContractTest.showMayDisposeShellWithoutStaleAccess();
	}

	@Test void throwingDisposeListenersStillReleaseOwner() {
		MobileLifecycleContractTest.throwingDisposeListenersStillReleaseOwner();
	}

	@Test void borderedClientCoordinatesMatchPaintingAndInput() {
		MobileLifecycleContractTest.borderedClientCoordinatesMatchPaintingAndInput();
	}

	@Test void modifierKeysReportStateBeforeTheirTransition() {
		MobileLifecycleContractTest.modifierKeysReportStateBeforeTheirTransition();
	}

	@Test void hideListenerMayRestoreVisibleFocus() {
		MobileLifecycleContractTest.hideListenerMayRestoreVisibleFocus();
	}

	@Test void arcDirectionAndNormalizedBounds() {
		MobileGraphicsContractTest.arcDirectionAndNormalizedBounds();
	}

	@Test void roundedRectangleClippingAndTransform() {
		MobileGraphicsContractTest.roundedRectangleClippingAndTransform();
	}

	@Test void textAntialiasStateAndDisposal() {
		MobileGraphicsContractTest.textAntialiasStateAndDisposal();
	}

	@Test void recoveredGraphicsRetainImageLifetime() {
		MobileGraphicsContractTest.recoveredGraphicsRetainImageLifetime();
	}

	@Test void queuedWorkRetainsFifoAndPaintFairness() {
		MobileLifecycleContractTest.queuedWorkRetainsFifoAndPaintFairness();
	}

	@Test void paintingVisibilityLocksAndExternalGcRemainOwned() {
		MobileLifecycleContractTest.paintingVisibilityLocksAndExternalGcRemainOwned();
	}

	@Test void childRemovalKeepsIdentityAndCommonEventContracts() {
		MobileLifecycleContractTest.childRemovalKeepsIdentityAndCommonEventContracts();
	}

	@Test void graphicsClippingAlphaAndImageSnapshotsRemainOwned() {
		MobileLifecycleContractTest.graphicsClippingAlphaAndImageSnapshotsRemainOwned();
	}

	@Test void queuedNativeFocusCannotOverrideReentrantOwner() {
		MobileLifecycleContractTest.queuedNativeFocusCannotOverrideReentrantOwner();
	}

	@Test void findDisplayRetainsOriginalThreadLookup() {
		MobileLifecycleContractTest.findDisplayRetainsOriginalThreadLookup();
	}
}
