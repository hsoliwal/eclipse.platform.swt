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
package org.eclipse.swt.m3;

import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.*;

/*
 * The message lookup of M3Language: the bundle families in order, the fallback locales and the lookup chain (the candidate
 * locales of the active locale, then of each fallback, the root locale last).
 */
final class M3MessageLookup {
	record Bundle (String baseName, ClassLoader loader) {
	}

	private static final ResourceBundle.Control CONTROL = ResourceBundle.Control.getNoFallbackControl (ResourceBundle.Control.FORMAT_DEFAULT);
	private static final List<Bundle> BUNDLES = new CopyOnWriteArrayList<> ();
	private static volatile List<Locale> fallbacks = List.of ();

	private M3MessageLookup () {
	}

	static void add (String baseName, ClassLoader loader) {
		remove (baseName);
		BUNDLES.add (new Bundle (baseName, loader));
	}

	static void remove (String baseName) {
		BUNDLES.removeIf (bundle -> bundle.baseName ().equals (baseName));
	}

	static void fallbacks (List<Locale> chain) {
		fallbacks = chain;
	}

	static void reset () {
		fallbacks = List.of ();
		BUNDLES.clear ();
	}

	static List<Locale> chain (Locale of) {
		Stream<Locale> chain = Stream.concat (Stream.of (of), fallbacks.stream ()).flatMap (l -> CONTROL.getCandidateLocales ("", l).stream ());
		return Stream.concat (chain.filter (l -> !Locale.ROOT.equals (l)), Stream.of (Locale.ROOT)).distinct ().toList ();
	}

	static Optional<String> find (Locale active, String key) {
		return chain (active).stream ().flatMap (l -> BUNDLES.stream ().map (b -> lookup (b, l, key))).flatMap (Optional::stream).findFirst ();
	}

	/* the key in the bundle of exactly this locale, not one inherited from its parent (except for the root bundle) */
	static Optional<String> lookup (Bundle bundle, Locale of, String key) {
		try {
			ResourceBundle exact = ResourceBundle.getBundle (bundle.baseName (), of, bundle.loader (), CONTROL);
			if (!exact.getLocale ().equals (of) || !exact.containsKey (key)) return Optional.empty ();
			if (Locale.ROOT.equals (of)) return Optional.of (exact.getString (key));
			ResourceBundle root = ResourceBundle.getBundle (bundle.baseName (), Locale.ROOT, bundle.loader (), CONTROL);
			Object value = exact.getObject (key);
			return root.containsKey (key) && root.getObject (key) == value ? Optional.empty () : Optional.of (exact.getString (key));
		} catch (MissingResourceException e) {
			return Optional.empty ();
		}
	}
}
