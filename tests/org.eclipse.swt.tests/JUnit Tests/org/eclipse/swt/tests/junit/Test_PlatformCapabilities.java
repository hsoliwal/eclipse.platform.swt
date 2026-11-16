/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.PlatformCapabilities.Feature;
import org.eclipse.swt.widgets.PlatformCapabilities.Support;
import org.junit.jupiter.api.*;

/** Headless contract tests for the cross-platform SWT capability registry. */
public class Test_PlatformCapabilities {

	private static PlatformCapabilities of (String backend) throws Exception {
		Method method = PlatformCapabilities.class.getDeclaredMethod ("of", String.class);
		method.setAccessible (true);
		return (PlatformCapabilities) method.invoke (null, backend);
	}

	@Test
	public void displayExposesAdditivePublicContract () throws Exception {
		Method method = Display.class.getMethod ("getPlatformCapabilities");
		assertEquals (PlatformCapabilities.class, method.getReturnType ());
		assertEquals (0, method.getParameterCount ());
	}

	@Test
	public void knownBackendsAreCachedAndNormalized () throws Exception {
		for (String backend : new String [] {"win32", "gtk", "cocoa", "android", "ios"}) {
			PlatformCapabilities capabilities = of (backend);
			assertEquals (backend, capabilities.getBackend ());
			assertSame (capabilities, of (backend));
			assertSame (capabilities, of (backend.toUpperCase (java.util.Locale.ROOT)));
		}
		assertSame (of ("win32"), of ("windows"));
		assertSame (of ("gtk"), of ("gtk4"));
		assertSame (of ("cocoa"), of ("macos"));
		assertSame (of ("ios"), of ("uikit"));
	}

	@Test
	public void desktopOwnersAdvertiseOnlyImplementedApiFamilies () throws Exception {
		for (String backend : new String [] {"win32", "gtk", "cocoa"}) {
			PlatformCapabilities capabilities = of (backend);
			assertEquals (Support.SWT, capabilities.getSupport (Feature.UI_THREAD_DISPATCH));
			assertEquals (Support.SWT, capabilities.getSupport (Feature.CANVAS_DRAWING));
			assertEquals (Support.SWT, capabilities.getSupport (Feature.VIRTUAL_TABLE_DATA));
			assertEquals (Support.SWT, capabilities.getSupport (Feature.ASYNC_ITEM_PAGING));
			assertEquals (Support.SWT, capabilities.getSupport (Feature.DRAG_AND_DROP));
			assertEquals (Support.NATIVE, capabilities.getSupport (Feature.NATIVE_CONTROLS));
			assertEquals (Support.NATIVE, capabilities.getSupport (Feature.NATIVE_FILE_PICKER));
			assertTrue (capabilities.supports (Feature.CANVAS_DRAWING));
			assertTrue (capabilities.isNative (Feature.NATIVE_CONTROLS));
			assertFalse (capabilities.isNative (Feature.VIRTUAL_TABLE_DATA));
		}
	}

	@Test
	public void mobileOwnersDoNotOverpromiseDonorFeatures () throws Exception {
		for (String backend : new String [] {"android", "ios"}) {
			PlatformCapabilities capabilities = of (backend);
			assertEquals (Support.SWT, capabilities.getSupport (Feature.UI_THREAD_DISPATCH));
			assertEquals (Support.SWT, capabilities.getSupport (Feature.CANVAS_DRAWING));
			assertEquals (Support.SWT, capabilities.getSupport (Feature.SAFE_AREA_INSETS));
			assertEquals (Support.UNKNOWN, capabilities.getSupport (Feature.PREDICTIVE_BACK));
			assertEquals (Support.UNKNOWN, capabilities.getSupport (Feature.HAPTIC_FEEDBACK));
			assertEquals (Support.UNKNOWN, capabilities.getSupport (Feature.NATIVE_CONTROLS));
			assertFalse (capabilities.supports (Feature.PREDICTIVE_BACK));
			assertFalse (capabilities.isNative (Feature.SAFE_AREA_INSETS));
		}
	}

	@Test
	public void unknownPlatformAndUnqualifiedFeaturesFailClosed () throws Exception {
		PlatformCapabilities unknown = of ("not-an-swt-backend");
		assertSame (unknown, of (null));
		assertEquals ("unknown", unknown.getBackend ());
		for (Feature feature : Feature.values ()) {
			assertEquals (Support.UNKNOWN, unknown.getSupport (feature));
			assertFalse (unknown.supports (feature));
			assertFalse (unknown.isNative (feature));
		}
		PlatformCapabilities desktop = of ("win32");
		for (Feature feature : new Feature [] {
				Feature.DIFFABLE_SNAPSHOTS, Feature.PREDICTIVE_BACK,
				Feature.MULTI_TOUCH_GESTURES, Feature.HAPTIC_FEEDBACK,
				Feature.VIRTUAL_ACCESSIBILITY, Feature.GPU_COMPOSITING }) {
			assertEquals (Support.UNKNOWN, desktop.getSupport (feature));
		}
	}

	@Test
	public void nullQueriesAreRejectedConsistently () throws Exception {
		PlatformCapabilities capabilities = of ("gtk");
		assertThrows (IllegalArgumentException.class, () -> capabilities.getSupport (null));
		assertThrows (IllegalArgumentException.class, () -> capabilities.supports (null));
		assertThrows (IllegalArgumentException.class, () -> capabilities.isNative (null));
	}

	@Test
	public void featureReadsDoNotChangeSharedSnapshots () throws Exception {
		PlatformCapabilities one = of ("android");
		PlatformCapabilities two = of ("android");
		assertSame (one, two);
		for (int i = 0; i < 10_000; i++) {
			assertSame (Support.SWT, one.getSupport (Feature.SAFE_AREA_INSETS));
			assertSame (Support.UNKNOWN, two.getSupport (Feature.NAVIGATION_STACK));
		}
	}
}
