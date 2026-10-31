/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;

/**
 * Handles the creation of external canvas extensions based on the style of the
 * canvas and the availability of an extension factory.
 */
public class ExternalCanvasHandler {

	private static boolean FAILED_WITH_ERRORS = false;
	private static boolean ExternalCanvasWasLogged = false;

	// DISABLE the external canvas extension. This is necessary in case the
	// extension causes problems on a platform. In this case, the user can set this
	// property to disable the extension and use the default canvas implementation.
	private final static String DISABLE_EXTERNAL_CANVAS = "org.eclipse.swt.external.canvas:disabled";

	// This is only for test cases in order to check whether the software works with
	// the canvas extension.
	// NEVER USE THIS IN PRODUCTIVE CODE!!
	private final static String FORCE_ENABLE_EXTERNAL_CANVAS = "org.eclipse.swt.external.canvas:forceEnabled";

	// In order to know whether an external canvas was activated.
	private final static String LOG_EXTERNAL_CANVAS_ACTIVATION = "org.eclipse.swt.external.canvas:logActivation";

	private static IExternalCanvasFactory externalFactory = null;
	private static boolean factoryLoaded = false;

	private static void loadFactory() {

		if (factoryLoaded)
			return;

		factoryLoaded = true;
		try {
			// it is possible that the loading of external libraries fails.
			externalFactory = ServiceLoader.load(IExternalCanvasFactory.class).findFirst().orElse(null);
		} catch (Throwable t) {
			FAILED_WITH_ERRORS = true;
			t.printStackTrace(System.err);
		}
	}

	/**
	 * M3 (GC seam, leaf 4): a factory registered by class name before the first <code>Canvas</code>, in place of the
	 * ServiceLoader lookup (which keeps precedence when it already ran). The win32 <code>Display</code> registers the M3
	 * factory this way when the Canvas startup mode is <code>m3</code> or <code>gpu</code>: the M3 render tree is
	 * compiled and loaded apart from the fragment (as <code>RowBackends</code> loads the planes' backend by name), so no
	 * <code>META-INF/services</code> entry of the fragment can name it, and an entry naming an absent class would raise a
	 * <code>ServiceConfigurationError</code> for every canvas. A class that cannot start leaves the stock path in place.
	 */
	public static void registerFactory(String className) {
		if (factoryLoaded)
			return;
		try {
			externalFactory = (IExternalCanvasFactory) Class.forName(className).getConstructor().newInstance();
			factoryLoaded = true;
		} catch (ReflectiveOperationException | LinkageError e) {
			if (isLogActive()) {
				System.out.println("External canvas factory " + className + " cannot start: " + e);
			}
		}
	}

	private static boolean isDisabled() {
		var disable = System.getProperty(DISABLE_EXTERNAL_CANVAS);
		if (disable != null) {
			return true;
		}
		return false;
	}

	private static boolean isForcedEnabled() {
		var forceEnable = System.getProperty(FORCE_ENABLE_EXTERNAL_CANVAS);
		if (forceEnable != null) {
			return true;
		}
		return false;
	}

	private static boolean isLogActive() {
		var log = System.getProperty(LOG_EXTERNAL_CANVAS_ACTIVATION);
		if (log != null) {
			return true;
		}
		return false;
	}

	public static boolean isActive(Canvas canvas, int style) {

		if (FAILED_WITH_ERRORS)
			return false;

		if (isDisabled()) {

			if (!ExternalCanvasWasLogged && isLogActive()) {
				System.out.println("External canvas disabled.");
				ExternalCanvasWasLogged = true;
			}

			return false;
		}

		if (canvas instanceof Decorations)
			return false;

		loadFactory();

		// M3 (GC seam, leaf 5): the factory decides (IExternalCanvasFactory.accepts); its default is the I31 rule this method
		// had (SWT.M3, never a StyledText); the M3 factory accepts every Canvas subclass of an m3 or gpu mode
		if (externalFactory != null && externalFactory.accepts(canvas, style))
			return true;

		if (canvas instanceof StyledText)
			return false;

		if (externalFactory == null) {
			if (!ExternalCanvasWasLogged && isLogActive()) {
				System.out.println("No external canvas factory found. External canvas disabled.");
				ExternalCanvasWasLogged = true;
			}
			return false;
		}

		if (isForcedEnabled()) {

			if (!ExternalCanvasWasLogged && isLogActive()) {
				System.out.println("Force enabled, external canvas factory found. External canvas will be activated.");
				ExternalCanvasWasLogged = true;
			}

			return true;
		}

		return false;
	}

	public static IExternalCanvasHandler createHandler(Canvas c) {

		if (FAILED_WITH_ERRORS)
			return null;

		if (isDisabled())
			return null;

		loadFactory();

		try {
			// it is possible that the loading of external libraries fails.
			var externalCanvas = externalFactory.createCanvasExtension(c);

			if (!ExternalCanvasWasLogged && isLogActive()) {
				System.out.println("External canvas activated.");
				ExternalCanvasWasLogged = true;
			}

			return externalCanvas;
		} catch (Throwable t) {
			FAILED_WITH_ERRORS = true;
			t.printStackTrace(System.err);
			return null;
		}
	}

	/**
	 * M3 (GC seam, leaf 7; pattern COMPOSITE_PAINT_HOOK v1): the handler of a composite that paints itself, asked once by the
	 * win32 <code>Composite</code> at its first <code>WM_PAINT</code> with a Paint listener or filter (the subclass constructor
	 * has run, so the listener that makes it draw is known), or null for the stock path: the seam disabled or failed, a
	 * <code>Canvas</code> (its constructor hook decided), no factory, or a factory that does not accept the composite
	 * ({@link IExternalCanvasFactory#acceptsComposite}). A factory that throws disables the seam, as for canvases.
	 */
	public static IExternalCanvasHandler createCompositeHandler(Composite composite) {
		if (FAILED_WITH_ERRORS || isDisabled() || composite instanceof Canvas)
			return null;
		loadFactory();
		if (externalFactory == null || !externalFactory.acceptsComposite(composite, composite.getStyle()))
			return null;
		try {
			return externalFactory.createCompositeExtension(composite);
		} catch (RuntimeException | LinkageError t) {
			FAILED_WITH_ERRORS = true;
			org.eclipse.swt.internal.SwtLog.getLog("org.eclipse.swt.internal.canvasext").info(() -> "composite handler failed, external canvas off: " + t);
			return null;
		}
	}
}
