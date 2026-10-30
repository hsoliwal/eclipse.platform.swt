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

import java.lang.Character.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;

/**
 * The runtime language switch (docs/m3/theme-language/README.md): the active locale, message lookup in
 * <code>ResourceBundle</code>s along a fallback chain, registered labels (texts, tooltips, menu items, messages) that a
 * switch sets again without recreating any widget, right-to-left shells for right-to-left languages, and the locale's
 * number, date and collation formats.
 * <p>
 * Lookup chain of a key: the candidate locales of the active locale (<code>ar-EG</code>, <code>ar</code>), then those of each
 * {@link #setFallbacks fallback}, then the root bundles; per locale every {@link #addBundle bundle} in order. A key found
 * nowhere answers itself. Nothing changes until {@link #setLocale(Locale)} or {@link #register} is called (stock).
 * </p>
 * <p>
 * Feature switch: <code>language-switch</code> ({@link M3Features#LANGUAGE_SWITCH}). A widget follows a switch
 * ({@link #follows(Widget)}) when the switch is on for it, or when no <code>swt.widget.feature[.&lt;Widget&gt;].language-switch</code>
 * key is configured for its class chain.
 * </p>
 *
 * @since 3.138
 */
public final class M3Language {
	/** Registration kinds: the widget's text, its tooltip, its message (the hint of an empty <code>Text</code>). */
	public static final byte TEXT = 0, TOOLTIP = 1, MESSAGE = 2;
	/** Startup locale: <code>-Dswt.m3.language=&lt;BCP 47 tag&gt;</code>. */
	public static final String PROPERTY = "swt.m3.language";

	/** Told after every switch, on the switching display's thread. */
	public interface Listener {
		/**
		 * @param from the previous locale
		 * @param to the new locale
		 */
		void languageChanged (Locale from, Locale to);
	}

	private record Bundle (String baseName, ClassLoader loader) {
	}

	private static final ResourceBundle.Control CONTROL = ResourceBundle.Control.getNoFallbackControl (ResourceBundle.Control.FORMAT_DEFAULT);
	private static final Set<UnicodeScript> RTL_SCRIPTS = EnumSet.of (UnicodeScript.ARABIC, UnicodeScript.HEBREW, UnicodeScript.SYRIAC,
		UnicodeScript.THAANA, UnicodeScript.NKO, UnicodeScript.SAMARITAN, UnicodeScript.MANDAIC, UnicodeScript.ADLAM, UnicodeScript.HANIFI_ROHINGYA,
		UnicodeScript.MENDE_KIKAKUI, UnicodeScript.IMPERIAL_ARAMAIC, UnicodeScript.OLD_SOUTH_ARABIAN, UnicodeScript.YEZIDI);
	private static final List<Bundle> BUNDLES = new CopyOnWriteArrayList<> ();
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<> ();
	private static volatile Locale locale;
	private static volatile List<Locale> fallbacks = List.of ();

	private M3Language () {
	}

	/** @return the active locale: the last {@link #setLocale}, else {@link #PROPERTY}, else <code>Locale.getDefault ()</code> */
	public static Locale getLocale () {
		Locale active = locale;
		if (active != null) return active;
		String tag = System.getProperty (PROPERTY);
		return tag == null || tag.isBlank () ? Locale.getDefault () : Locale.forLanguageTag (tag.strip ());
	}

	/**
	 * Switches the language: every label registered on the calling thread's display is set again, the display's shells flip to
	 * the locale's orientation, the other displays with registrations are relabelled on their threads, and the listeners are told.
	 *
	 * @param next the new locale
	 * @return the number of labels set on the calling thread's display
	 * @exception IllegalArgumentException <ul><li>ERROR_NULL_ARGUMENT - if the locale is null</li></ul>
	 */
	public static int setLocale (Locale next) {
		if (next == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		Locale from = getLocale ();
		locale = next;
		Display current = Display.getCurrent ();
		int set = current == null ? 0 : switchDisplay (current);
		M3LanguageDisplays.others (current).forEach (display -> display.asyncExec (() -> switchDisplay (display)));
		LISTENERS.forEach (listener -> listener.languageChanged (from, next));
		return set;
	}

	private static int switchDisplay (Display display) {
		if (display.isDisposed ()) return 0;
		int set = relabel (display);
		flip (display);
		return set;
	}

	/**
	 * Adds a bundle family to the lookup (after the ones added before).
	 *
	 * @param baseName the base name (<code>com.example.messages</code>)
	 * @param loader the class loader to load it with
	 */
	public static void addBundle (String baseName, ClassLoader loader) {
		if (baseName == null || loader == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		BUNDLES.removeIf (bundle -> bundle.baseName.equals (baseName));
		BUNDLES.add (new Bundle (baseName, loader));
	}

	/** Removes a bundle family from the lookup. */
	public static void removeBundle (String baseName) {
		BUNDLES.removeIf (bundle -> bundle.baseName.equals (baseName));
	}

	/** @param chain the locales tried after the active locale's candidates and before the root bundles (none by default) */
	public static void setFallbacks (Locale... chain) {
		if (chain == null || Arrays.asList (chain).contains (null)) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		fallbacks = List.of (chain);
	}

	/**
	 * @param of a locale
	 * @return the lookup chain of the locale: its candidate locales, the fallbacks' candidates, the root locale last
	 */
	public static List<Locale> chain (Locale of) {
		Stream<Locale> chain = Stream.concat (Stream.of (of), fallbacks.stream ()).flatMap (l -> CONTROL.getCandidateLocales ("", l).stream ());
		return Stream.concat (chain.filter (l -> !Locale.ROOT.equals (l)), Stream.of (Locale.ROOT)).distinct ().toList ();
	}

	/**
	 * @param key a message key
	 * @return the message of the key in the active locale, along the chain; the key itself when no bundle has it
	 */
	public static String message (String key) {
		return find (key).orElse (key);
	}

	/**
	 * @param key a message key whose message is a <code>MessageFormat</code> pattern
	 * @param arguments its arguments
	 * @return the formatted message, with the active locale's number and date formats
	 */
	public static String format (String key, Object... arguments) {
		return new MessageFormat (message (key), getLocale ()).format (arguments);
	}

	/**
	 * @param key a message key
	 * @return the message of the key along the chain of the active locale, empty when no bundle has it
	 */
	public static Optional<String> find (String key) {
		if (key == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		return chain (getLocale ()).stream ().flatMap (l -> BUNDLES.stream ().map (b -> lookup (b, l, key))).flatMap (Optional::stream).findFirst ();
	}

	/* the key in the bundle of exactly this locale, not one inherited from its parent (except for the root bundle) */
	private static Optional<String> lookup (Bundle bundle, Locale of, String key) {
		try {
			ResourceBundle exact = ResourceBundle.getBundle (bundle.baseName, of, bundle.loader, CONTROL);
			if (!exact.getLocale ().equals (of) || !exact.containsKey (key)) return Optional.empty ();
			if (Locale.ROOT.equals (of)) return Optional.of (exact.getString (key));
			ResourceBundle root = ResourceBundle.getBundle (bundle.baseName, Locale.ROOT, bundle.loader, CONTROL);
			Object value = exact.getObject (key);
			return root.containsKey (key) && root.getObject (key) == value ? Optional.empty () : Optional.of (exact.getString (key));
		} catch (MissingResourceException e) {
			return Optional.empty ();
		}
	}

	/**
	 * Registers a label: its text is set now and again by every switch.
	 *
	 * @param widget the widget
	 * @param kind {@link #TEXT}, {@link #TOOLTIP} or {@link #MESSAGE}
	 * @param key the message key
	 * @param fallback the text when no bundle has the key
	 * @exception IllegalArgumentException <ul><li>ERROR_NULL_ARGUMENT - if the widget, key or fallback is null</li><li>ERROR_INVALID_ARGUMENT - if the widget has no label of that kind</li></ul>
	 * @exception SWTException <ul><li>ERROR_WIDGET_DISPOSED - if the widget is disposed</li><li>ERROR_THREAD_INVALID_ACCESS - if not called from the widget's thread</li></ul>
	 */
	public static void register (Widget widget, byte kind, String key, String fallback) {
		if (widget == null || key == null || fallback == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (widget.isDisposed ()) SWT.error (SWT.ERROR_WIDGET_DISPOSED);
		Display display = widget.getDisplay ();
		if (display.getThread () != Thread.currentThread ()) SWT.error (SWT.ERROR_THREAD_INVALID_ACCESS);
		M3LabelRegistry.apply (widget, kind, find (key).orElse (fallback));
		M3LabelRegistry.of (display, true).add (widget, kind, key, fallback);
		M3LanguageDisplays.add (display);
	}

	/** Registers a label whose fallback is its key ({@link #register(Widget, byte, String, String)}). */
	public static void register (Widget widget, byte kind, String key) {
		register (widget, kind, key, key);
	}

	/** Registers the text of a widget ({@link #register(Widget, byte, String)} with {@link #TEXT}). */
	public static void register (Widget widget, String key) {
		register (widget, TEXT, key);
	}

	/** Removes every registration of a widget. */
	public static void unregister (Widget widget) {
		if (widget == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		M3LabelRegistry registry = widget.isDisposed () ? null : M3LabelRegistry.of (widget.getDisplay (), false);
		if (registry != null) registry.remove (widget);
	}

	/** @return the number of live registrations of a display */
	public static int registered (Display display) {
		M3LabelRegistry registry = M3LabelRegistry.of (display, false);
		return registry == null ? 0 : registry.count ();
	}

	/**
	 * Sets every registered label of a display from the active locale (what a switch does), on the display's thread.
	 *
	 * @param display the display
	 * @return the number of labels set
	 */
	public static int relabel (Display display) {
		M3LabelRegistry registry = M3LabelRegistry.of (display, false);
		return registry == null ? 0 : registry.relabel (M3Language::find, M3Language::follows);
	}

	/**
	 * Gives every shell of a display that follows the language the orientation of the active locale (the children follow, no
	 * handle is recreated); a descendant whose class does not follow keeps its orientation.
	 *
	 * @param display the display
	 * @return the number of shells flipped
	 */
	public static int flip (Display display) {
		return M3LanguageDisplays.flip (display, orientation (getLocale ()));
	}

	/** @return {@link SWT#RIGHT_TO_LEFT} for a right-to-left locale, else {@link SWT#LEFT_TO_RIGHT} */
	public static int orientation (Locale of) {
		return isRightToLeft (of) ? SWT.RIGHT_TO_LEFT : SWT.LEFT_TO_RIGHT;
	}

	/**
	 * @param of a locale
	 * @return true when its script subtag is a right-to-left script, else when its language is a right-to-left one in
	 *         {@link M3Languages}, else when its native language name is written right to left
	 */
	public static boolean isRightToLeft (Locale of) {
		if (of == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (!of.getScript ().isEmpty ()) return rightToLeftScript (of.getScript ());
		int entry = of.getLanguage ().isEmpty () ? -1 : M3Languages.indexOf (of.getLanguage ());
		return entry >= 0 ? M3Languages.rightToLeft (entry) : M3LanguageId.rightToLeft (M3LanguageId.detect (of.getDisplayLanguage (of)));
	}

	/* an ISO 15924 script code the JDK does not know (private use, Qaaa) is not right to left */
	static boolean rightToLeftScript (String code) {
		try {
			return RTL_SCRIPTS.contains (UnicodeScript.forName (code));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * @param widget a widget
	 * @return true when the widget follows a switch: <code>language-switch</code> is on for it, or no
	 *         <code>language-switch</code> key is configured for its class chain
	 */
	public static boolean follows (Widget widget) {
		return M3Features.isOn (widget, M3Features.LANGUAGE_SWITCH) || !M3Themes.configured (widget.getClass (), M3Features.LANGUAGE_SWITCH);
	}

	/** @return the active locale's number format */
	public static NumberFormat numberFormat () {
		return NumberFormat.getInstance (getLocale ());
	}

	/**
	 * @param style <code>DateFormat.SHORT</code>, <code>MEDIUM</code>, <code>LONG</code> or <code>FULL</code>
	 * @return the active locale's date format
	 */
	public static DateFormat dateFormat (int style) {
		return DateFormat.getDateInstance (style, getLocale ());
	}

	/**
	 * @param items the items
	 * @return their sort keys in the active locale ({@link M3Collation#keys(String[], Locale)})
	 */
	public static M3SortKeys collate (String [] items) {
		return M3Collation.keys (items, getLocale ());
	}

	/** Adds a switch listener. */
	public static void addListener (Listener listener) {
		if (listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		LISTENERS.add (listener);
	}

	/** Removes a switch listener. */
	public static void removeListener (Listener listener) {
		LISTENERS.remove (listener);
	}

	/** Back to the startup locale, no bundles and no fallbacks (tests, and applications that undo a switch). */
	public static void reset () {
		locale = null;
		fallbacks = List.of ();
		BUNDLES.clear ();
	}
}
