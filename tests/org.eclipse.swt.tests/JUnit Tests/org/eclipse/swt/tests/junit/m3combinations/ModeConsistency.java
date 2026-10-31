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
package org.eclipse.swt.tests.junit.m3combinations;

import java.lang.reflect.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.widgets.*;

/**
 * The mode-neutral answer of <code>M3-MODE-QUERY</code>: <code>M3Mode.of</code> answers the mode by design, so the
 * matrix asks whether that answer agrees with what the widget's implementation runs (a drawn plane installed, the
 * viewport gate of its class on), which must hold in every mode. The implementation is read by reflection from the
 * gates the widgets themselves use (<code>drawnPlane</code>, <code>*Viewport.ENABLED</code>, <code>VIEWPORT</code>).
 */
final class ModeConsistency {
	static final String [] NAMES = {"viewport", "original", "custom", "m3", "gpu"};
	/** Viewport gates: widget class to (class, static boolean field). */
	static final Map<String, String []> GATES = Map.of ("Table", new String [] {"org.eclipse.swt.widgets.TableViewport", "ENABLED"}, "Tree",
		new String [] {"org.eclipse.swt.widgets.TreeViewport", "ENABLED"}, "List", new String [] {"org.eclipse.swt.widgets.ListViewport", "ENABLED"}, "Combo",
		new String [] {"org.eclipse.swt.widgets.ComboViewport", "ENABLED"}, "CTabFolder", new String [] {"org.eclipse.swt.custom.CTabFolder", "VIEWPORT"}, "StyledText",
		new String [] {"org.eclipse.swt.custom.StyledText", "VIEWPORT"});

	private ModeConsistency () {
	}

	static String answer (Subject s) {
		Widget widget = s.widget ();
		String reported = NAMES [M3Mode.of (widget)], implemented = implemented (widget);
		return reported.equals (implemented) ? "consistent" : "INCONSISTENT M3Mode=" + reported + " implementation=" + implemented;
	}

	/** The mode the widget's implementation runs: its drawn plane's mode, else viewport when its gate is on, else original. */
	static String implemented (Widget widget) {
		Object plane = field (widget, "drawnPlane");
		if (plane != null) return staticField (plane.getClass (), "MODE").toString ().toLowerCase (Locale.ROOT);
		if (widget instanceof Canvas && field (widget, "externalCanvasHandler") != null) return canvasMode ();
		String [] gate = GATES.get (widget.getClass ().getSimpleName ());
		if (gate == null) return "original";
		try {
			return Boolean.TRUE.equals (staticField (Class.forName (gate [0]), gate [1])) ? "viewport" : "original";
		} catch (ClassNotFoundException e) {
			return "original";
		}
	}

	/** A Canvas with an external (M3) canvas handler runs the Canvas startup mode (m3 or gpu, Display.registerM3Canvas). */
	static String canvasMode () {
		String mode = System.getProperty ("swt.widget.mode.Canvas", System.getProperty ("swt.widget.mode", "m3"));
		return "gpu".equalsIgnoreCase (mode.trim ()) ? "gpu" : "m3";
	}

	/** An instance field of the widget's class chain, or null when it has none. */
	static Object field (Object target, String name) {
		for (Class<?> type = target.getClass (); type != null; type = type.getSuperclass ()) {
			try {
				Field field = type.getDeclaredField (name);
				field.setAccessible (true);
				return field.get (target);
			} catch (NoSuchFieldException e) {
				/* try the superclass */
			} catch (IllegalAccessException e) {
				throw new AssertionError (e);
			}
		}
		return null;
	}

	static Object staticField (Class<?> type, String name) {
		try {
			Field field = type.getDeclaredField (name);
			field.setAccessible (true);
			return field.get (null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (type.getName () + "." + name, e);
		}
	}
}
