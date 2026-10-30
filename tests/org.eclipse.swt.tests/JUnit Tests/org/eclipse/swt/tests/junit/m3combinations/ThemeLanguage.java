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
 * The theme and language step of a row. <code>M3Theme</code> and <code>M3Language</code> come from the
 * theme-language branch (m3/theme-language-20261030); until they are on the class path the step answers
 * <code>NOT_RUN</code> and the row records that its theme/language part did not run. When present, the first public
 * static method <code>apply|set|select(Control or Widget, String)</code> of the class is called.
 */
final class ThemeLanguage {
	static final String [] PACKAGES = {"org.eclipse.swt.m3.", "org.eclipse.swt.widgets.", "org.eclipse.swt.internal.laf.", "org.eclipse.swt.custom."};
	static final Set<String> METHODS = Set.of ("apply", "set", "select", "setTheme", "setLanguage");

	private ThemeLanguage () {
	}

	/** Applies a value of the theme (class M3Theme) or language (class M3Language) factor; "none" does nothing. */
	static String apply (String className, String value, Widget widget) {
		if ("none".equals (value)) return "none";
		Optional<Class<?>> type = find (className);
		if (type.isEmpty ()) return "NOT_RUN no " + className + " on the class path";
		Optional<Method> method = Arrays.stream (type.get ().getMethods ()).filter (m -> applies (m, widget)).findFirst ();
		if (method.isEmpty ()) return "NOT_RUN no static apply|set|select(Control, String) in " + type.get ().getName ();
		try {
			method.get ().invoke (null, widget, value);
			return "applied " + value;
		} catch (InvocationTargetException e) {
			throw new AssertionError (className + " " + value, e.getCause ());
		} catch (IllegalAccessException e) {
			throw new AssertionError (e);
		}
	}

	static Optional<Class<?>> find (String simpleName) {
		for (String pkg : PACKAGES) {
			try {
				return Optional.of (Class.forName (pkg + simpleName));
			} catch (ClassNotFoundException e) {
				/* next package */
			}
		}
		return Optional.empty ();
	}

	static boolean applies (Method m, Widget widget) {
		Class<?> [] p = m.getParameterTypes ();
		return Modifier.isStatic (m.getModifiers ()) && METHODS.contains (m.getName ()) && p.length == 2 && widget != null && p [0].isInstance (widget) && p [1] == String.class;
	}

	/** True when the step did not run (the class or its method is missing). */
	static boolean notRun (String answer) {
		return answer.startsWith ("NOT_RUN");
	}
}
