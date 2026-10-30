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
package org.eclipse.swt.internal;

import java.util.*;
import java.util.stream.*;

/**
 * Startup strategy of SWT widgets. Every mode keeps the public API contract; they differ in how a
 * widget is realized.
 * <ul>
 * <li>{@link #VIEWPORT} (default): the native control holds only the rows (items, children, lines)
 * on screen; the widget mediates the rest and keeps the native look and behaviour.</li>
 * <li>{@link #ORIGINAL}: the native SWT implementation, unchanged.</li>
 * <li>{@link #CUSTOM}: the widget is drawn by SWT itself with {@link org.eclipse.swt.graphics.GC}.</li>
 * <li>{@link #M3}: the widget is drawn by SWT itself through the M3 renderer.</li>
 * <li>{@link #GPU}: the widget is drawn by SWT itself on the graphics hardware (Direct2D on Windows,
 * OpenGL elsewhere).</li>
 * </ul>
 * The mode is chosen at startup with system properties, read once when a widget class initializes:
 * <ul>
 * <li><code>-Dswt.widget.mode=viewport|original|custom|m3|gpu</code> for every widget;</li>
 * <li><code>-Dswt.widget.mode.&lt;Widget&gt;=...</code> (for example <code>swt.widget.mode.Tree</code>)
 * for one widget class, overriding the global value.</li>
 * </ul>
 * A widget that does not implement the requested mode falls back to {@link #VIEWPORT} when it has a
 * viewport, otherwise to {@link #ORIGINAL}.
 */
public enum WidgetMode {
	/** Mediated viewport over the native control (the default). */
	VIEWPORT,
	/** The original native implementation. */
	ORIGINAL,
	/** Drawn by SWT with a GC. */
	CUSTOM,
	/** Drawn by SWT through the M3 renderer. */
	M3,
	/** Drawn by SWT on the graphics hardware (Direct2D on Windows, OpenGL elsewhere). */
	GPU;

	/** Name of the global property; a widget property appends <code>"." + simpleClassName</code>. */
	public static final String PROPERTY = "swt.widget.mode";

	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.internal.WidgetMode");

	/**
	 * Answers the mode of a widget class.
	 *
	 * @param widget the simple class name of the widget, for example <code>"Tree"</code>
	 * @param supported the modes the widget implements; {@link #ORIGINAL} is always implied
	 * @return the requested mode when supported, otherwise the fallback
	 */
	public static WidgetMode of (String widget, WidgetMode... supported) {
		WidgetMode requested = requested (widget);
		EnumSet<WidgetMode> modes = EnumSet.of (ORIGINAL);
		modes.addAll (Arrays.asList (supported));
		WidgetMode mode = modes.contains (requested) ? requested : modes.contains (VIEWPORT) ? VIEWPORT : ORIGINAL;
		LOG.debug (() -> widget + ": mode " + mode + (mode == requested ? "" : " (requested " + requested + ")"));
		return mode;
	}

	/**
	 * Answers the mode of a widget class that inherits its realization from <code>root</code> (GC seam, leaf 5: every
	 * <code>Canvas</code> subclass). The most derived class with its own property decides
	 * (<code>swt.widget.mode.StyledText</code> before <code>swt.widget.mode.Canvas</code>), otherwise the global property;
	 * anonymous classes have no property of their own.
	 *
	 * @param type the runtime class of the widget
	 * @param root the class whose realization the widget inherits, for example <code>Canvas.class</code>
	 * @param supported the modes the root implements; {@link #ORIGINAL} is always implied
	 * @return the requested mode when supported, otherwise the fallback
	 */
	public static WidgetMode of (Class<?> type, Class<?> root, WidgetMode... supported) {
		String owner = Stream.<Class<?>> iterate (type, c -> c != null && root.isAssignableFrom (c), c -> c.getSuperclass ())
			.map (Class::getSimpleName)
			.filter (name -> !name.isEmpty () && System.getProperty (PROPERTY + "." + name) != null)
			.findFirst ().orElse (root.getSimpleName ());
		return of (owner, supported);
	}

	/**
	 * Whether any startup property, global or per widget, asks for one of the modes (GC seam, leaf 5: the Display registers
	 * the M3 canvas factory when one does, so <code>swt.widget.mode.StyledText=m3</code> alone reaches it).
	 */
	public static boolean anyRequested (WidgetMode... modes) {
		List<WidgetMode> wanted = Arrays.asList (modes);
		return System.getProperties ().stringPropertyNames ().stream ()
			.filter (key -> key.equals (PROPERTY) || key.startsWith (PROPERTY + "."))
			.map (key -> System.getProperty (key, "").trim ().toUpperCase (Locale.ROOT))
			.anyMatch (value -> wanted.stream ().anyMatch (mode -> mode.name ().equals (value)));
	}

	/** The mode the startup properties ask for, before any fallback. */
	public static WidgetMode requested (String widget) {
		String specific = System.getProperty (PROPERTY + "." + widget);
		String value = specific != null ? specific : System.getProperty (PROPERTY);
		if (value == null) return VIEWPORT;
		try {
			return valueOf (value.trim ().toUpperCase (Locale.ROOT));
		} catch (IllegalArgumentException e) {
			LOG.info (() -> "ignoring " + PROPERTY + " value '" + value + "' for " + widget);
			return VIEWPORT;
		}
	}
}
