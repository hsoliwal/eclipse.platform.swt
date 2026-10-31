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

import org.eclipse.swt.widgets.*;

/**
 * The theme and language step of a row, through the API of the theme-language branch (m3/theme-language-20261030,
 * PR #286), called by reflection so that this branch builds without it: a theme is
 * <code>M3Themes.apply (display, M3Themes.find (name))</code>, a language is
 * <code>M3Language.setLocale (Locale.forLanguageTag (tag))</code>. Both are global (display, JVM), so {@link #restore}
 * puts them back after every row. Without the classes the step answers <code>NOT_RUN</code> and the rest of the row runs.
 */
final class ThemeLanguage {
	static final String THEMES = "org.eclipse.swt.m3.M3Themes", THEME = "org.eclipse.swt.m3.M3Theme", LANGUAGE = "org.eclipse.swt.m3.M3Language";
	static Locale before;
	static boolean themed;

	private ThemeLanguage () {
	}

	/** Applies the row's theme and language; answers what was applied (or NOT_RUN without the classes). */
	static String apply (CombinationRow row, Widget widget) {
		return theme (row.theme ()) + "; " + language (row.language ());
	}

	static String theme (String name) {
		if ("none".equals (name)) return "none";
		Optional<Class<?>> themes = find (THEMES);
		if (themes.isEmpty ()) return "NOT_RUN no M3Themes on the class path";
		Object theme = invoke (themes.get (), "find", new Class<?> [] {String.class}, name);
		if (theme == null) return "theme " + name + " not registered";
		invoke (themes.get (), "apply", new Class<?> [] {Display.class, find (THEME).orElseThrow ()}, Display.getCurrent (), theme);
		themed = true;
		return "theme " + name + " applied";
	}

	static String language (String tag) {
		if ("none".equals (tag)) return "none";
		Optional<Class<?>> language = find (LANGUAGE);
		if (language.isEmpty ()) return "NOT_RUN no M3Language on the class path";
		Locale locale = Locale.forLanguageTag (tag);
		if (before == null) before = (Locale) invoke (language.get (), "getLocale", new Class<?> [0]);
		invoke (language.get (), "setLocale", new Class<?> [] {Locale.class}, locale);
		return "language " + tag + " rtl=" + invoke (language.get (), "isRightToLeft", new Class<?> [] {Locale.class}, locale);
	}

	/** Puts the display theme and the JVM language back to what they were before the row. */
	static void restore () {
		if (themed && Display.getCurrent () != null) invoke (find (THEMES).get (), "reset", new Class<?> [] {Display.class}, Display.getCurrent ());
		if (before != null) invoke (find (LANGUAGE).get (), "setLocale", new Class<?> [] {Locale.class}, before);
		themed = false;
		before = null;
	}

	static Optional<Class<?>> find (String className) {
		try {
			return Optional.of (Class.forName (className));
		} catch (ClassNotFoundException e) {
			return Optional.empty ();
		}
	}

	static Object invoke (Class<?> type, String name, Class<?> [] parameters, Object... arguments) {
		try {
			return type.getMethod (name, parameters).invoke (null, arguments);
		} catch (InvocationTargetException e) {
			throw new AssertionError (type.getSimpleName () + "." + name, e.getCause ());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError ("the theme-language API changed: " + type.getName () + "." + name, e);
		}
	}

	/** True when the step did not run (the classes are missing). */
	static boolean notRun (String answer) {
		return answer.contains ("NOT_RUN");
	}
}
