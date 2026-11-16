/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.*;

import org.eclipse.swt.*;

/**
 * Read-only discovery of UI capabilities implemented by an SWT platform.
 * <p>
 * The registry describes SWT API availability, not the features potentially
 * offered by the operating system or by another UI toolkit. In particular,
 * an unimplemented donor capability is {@link Support#UNKNOWN}, even if a
 * corresponding Android, UIKit, WinUI or GTK API exists. Hardware-dependent
 * features require further runtime probes before they can be advertised.
 * </p><p>
 * The result is immutable and shared per backend. Queries allocate no per-item
 * objects and never create native controls, item facades or event listeners.
 * Applications must not cache a capability result as proof that a particular
 * widget supports a feature: platform, widget style and lifecycle restrictions
 * still apply.
 * </p>
 *
 * @since 3.138
 */
public final class PlatformCapabilities {

	/** UI API families which can be implemented independently by a platform. */
	public enum Feature {
		UI_THREAD_DISPATCH,
		NATIVE_CONTROLS,
		CANVAS_DRAWING,
		VIRTUAL_TABLE_DATA,
		ASYNC_ITEM_PAGING,
		DIFFABLE_SNAPSHOTS,
		ITEM_RECYCLING,
		ADAPTIVE_LAYOUT,
		SAFE_AREA_INSETS,
		FOLDABLE_POSTURE,
		NAVIGATION_STACK,
		PREDICTIVE_BACK,
		MULTI_TOUCH_GESTURES,
		STYLUS_INPUT,
		HAPTIC_FEEDBACK,
		COMMANDS,
		OBSERVABLE_BINDINGS,
		DOCKING,
		DRAG_AND_DROP,
		NATIVE_FILE_PICKER,
		CLIPBOARD_TEXT,
		SYSTEM_NOTIFICATIONS,
		NATIVE_SHARE_SHEET,
		VIRTUAL_ACCESSIBILITY,
		REDUCED_MOTION,
		NATIVE_ANIMATION,
		GPU_COMPOSITING
	}

	/**
	 * Availability of a feature in this SWT implementation.
	 * <p>
	 * UNKNOWN means the feature has not been qualified for this backend; it
	 * must not be interpreted as operating-system or hardware incapability.
	 * SWT means that an SWT-owned implementation exists. NATIVE means SWT
	 * uses an operating-system implementation for the indicated API family.
	 * Both SWT and NATIVE can have widget-specific restrictions.
	 * </p>
	 */
	public enum Support {
		UNKNOWN, SWT, NATIVE
	}

	private static final Support [] LEVELS = Support.values ();
	private static final PlatformCapabilities WIN32 = known ("win32"); //$NON-NLS-1$
	private static final PlatformCapabilities GTK = known ("gtk"); //$NON-NLS-1$
	private static final PlatformCapabilities COCOA = known ("cocoa"); //$NON-NLS-1$
	private static final PlatformCapabilities ANDROID = known ("android"); //$NON-NLS-1$
	private static final PlatformCapabilities IOS = known ("ios"); //$NON-NLS-1$
	private static final PlatformCapabilities UNKNOWN = new PlatformCapabilities ("unknown", new byte [Feature.values ().length]); //$NON-NLS-1$

	private final String backend;
	private final byte [] levels;

	private PlatformCapabilities (String backend, byte [] levels) {
		this.backend = backend;
		this.levels = levels;
	}

	private static PlatformCapabilities known (String backend) {
		byte [] levels = new byte [Feature.values ().length];
		mark (levels, Support.SWT, Feature.UI_THREAD_DISPATCH, Feature.CANVAS_DRAWING);
		if ("android".equals (backend) || "ios".equals (backend)) { //$NON-NLS-1$ //$NON-NLS-2$
			/*
			 * Both mobile bindings expose an SWT-owned safe-area query.
			 * The transport may return zero insets for older native hosts;
			 * do not advertise the OS-specific inset API as NATIVE.
			 */
			mark (levels, Support.SWT, Feature.SAFE_AREA_INSETS);
		} else {
			/* The desktop widget and dialog owners have native peers. */
			mark (levels, Support.NATIVE, Feature.NATIVE_CONTROLS, Feature.NATIVE_FILE_PICKER);
			mark (levels, Support.SWT, Feature.VIRTUAL_TABLE_DATA,
					Feature.ASYNC_ITEM_PAGING, Feature.DRAG_AND_DROP, Feature.CLIPBOARD_TEXT);
		}
		return new PlatformCapabilities (backend, levels);
	}

	private static void mark (byte [] levels, Support support, Feature... features) {
		for (Feature feature : features) levels [feature.ordinal ()] = (byte) support.ordinal ();
	}

	/**
	 * Resolves the implementation's backend name, without inspecting operating
	 * system properties or guessing from the executing JVM. Unknown names fail
	 * closed; future backends must explicitly declare implemented features.
	 */
	static PlatformCapabilities of (String name) {
		if (name == null) return UNKNOWN;
		return switch (name.toLowerCase (Locale.ROOT)) {
			case "win32", "windows" -> WIN32; //$NON-NLS-1$ //$NON-NLS-2$
			case "gtk", "gtk3", "gtk4" -> GTK; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			case "cocoa", "macos" -> COCOA; //$NON-NLS-1$ //$NON-NLS-2$
			case "android" -> ANDROID; //$NON-NLS-1$
			case "ios", "uikit", "iphoneos" -> IOS; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			default -> UNKNOWN;
		};
	}

	/**
	 * Returns the implementation backend: win32, gtk, cocoa, android, ios,
	 * or unknown. This is not an operating-system version or device identifier.
	 *
	 * @return the SWT backend name
	 */
	public String getBackend () {
		return backend;
	}

	/**
	 * Returns the advertised availability of a UI API family.
	 *
	 * @param feature the queried feature
	 * @return UNKNOWN, SWT or NATIVE
	 * @exception IllegalArgumentException
	 *                ERROR_NULL_ARGUMENT - if feature is null
	 */
	public Support getSupport (Feature feature) {
		if (feature == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		return LEVELS [levels [feature.ordinal ()]];
	}

	/**
	 * Whether SWT implements the API family, natively or in SWT.
	 *
	 * @param feature the queried feature
	 * @return false if its implementation is not yet qualified
	 */
	public boolean supports (Feature feature) {
		return getSupport (feature) != Support.UNKNOWN;
	}

	/**
	 * Whether SWT uses the platform's native implementation for this family.
	 *
	 * @param feature the queried feature
	 * @return true only for features advertised as NATIVE
	 */
	public boolean isNative (Feature feature) {
		return getSupport (feature) == Support.NATIVE;
	}
}
