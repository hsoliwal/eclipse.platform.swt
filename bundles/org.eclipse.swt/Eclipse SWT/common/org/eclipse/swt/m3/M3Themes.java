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

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.widgets.*;

/**
 * The theme registry and the runtime switch of a display (docs/m3/theme-language/README.md).
 * <p>
 * {@link #apply(Display, M3Theme)} switches every control of the display at once: the theme becomes the look and feel
 * <code>m3-&lt;name&gt;</code> that the drawn planes, the M3 renderer and every look and feel delegate read (only delegates
 * that read a changed slot are reinstalled), native controls get the closest operating system treatment (the Windows dark
 * mode where available) and their background and foreground, custom widgets their selection and header colours, and each
 * shell repaints once with its redraw suspended during the switch: one damage pass, visible rows only, no item rebuilt.
 * Until <code>apply</code> is called nothing changes (stock); {@link #reset(Display)} goes back to stock colours.
 * </p>
 * <p>
 * Feature switch: <code>theme-provider</code> ({@link M3Features#THEME_PROVIDER}). A control follows the theme
 * ({@link #follows(Control)}) when the switch is on for it, or when no <code>swt.widget.feature[.&lt;Widget&gt;].theme-provider</code>
 * key is configured for its class chain: <code>-Dswt.widget.feature.Table.theme-provider=false</code> keeps every Table
 * stock while the rest follows.
 * </p>
 *
 * @since 3.138
 */
public final class M3Themes {
	/** Startup theme: <code>-Dswt.m3.theme=light|dark|high-contrast|system|&lt;registered name&gt;|&lt;theme file path&gt;</code>. */
	public static final String PROPERTY = "swt.m3.theme";
	/** The name of {@link #applySystem(Display)} in {@link #PROPERTY}. */
	public static final String SYSTEM = "system";
	/** The prefix of the look and feel name a theme is registered under. */
	public static final String LOOK_AND_FEEL_PREFIX = "m3-";

	/** Told after every switch, on the display's thread. */
	public interface Listener {
		/**
		 * @param display the display
		 * @param from the previous theme, or null for stock
		 * @param to the new theme, or null for stock
		 */
		void themeChanged (Display display, M3Theme from, M3Theme to);
	}

	private static final Map<String, M3Theme> REGISTRY = new ConcurrentHashMap<> ();
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<> ();
	private static final String STATE = M3Themes.class.getName ();

	static {
		M3Theme.builtIns ().forEach (theme -> REGISTRY.put (theme.name (), theme));
	}

	private M3Themes () {
	}

	/**
	 * Registers a user theme under its name (replacing an earlier user theme of that name).
	 *
	 * @param theme the theme
	 * @return false when the name is a built-in one (not replaced)
	 */
	public static boolean register (M3Theme theme) {
		if (theme == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (builtIn (theme.name ())) return false;
		REGISTRY.put (theme.name (), theme);
		return true;
	}

	/** @return false for a built-in or unknown name; true when the user theme was removed */
	public static boolean unregister (String name) {
		if (name == null || builtIn (key (name))) return false;
		return REGISTRY.remove (key (name)) != null;
	}

	/** @return the built-in or registered theme of a name (any case), or null */
	public static M3Theme find (String name) {
		return name == null ? null : REGISTRY.get (key (name));
	}

	/** @return the registered names: the built-ins first, then the user themes sorted */
	public static List<String> names () {
		Stream<String> user = REGISTRY.keySet ().stream ().filter (name -> !builtIn (name)).sorted ();
		return Stream.concat (M3Theme.builtIns ().stream ().map (M3Theme::name), user).toList ();
	}

	/**
	 * Reads a theme file ({@link M3Theme#parse(String)}, UTF-8) and registers it.
	 *
	 * @param file the file
	 * @return the theme
	 * @throws IOException when the file cannot be read
	 */
	public static M3Theme load (Path file) throws IOException {
		M3Theme theme = M3Theme.parse (Files.readString (file, StandardCharsets.UTF_8));
		if (!register (theme)) throw new IllegalArgumentException ("theme file " + file + " names the built-in theme '" + theme.name () + "'");
		return theme;
	}

	/** @return the theme applied to a display, empty while it is stock */
	public static Optional<M3Theme> current (Display display) {
		return Optional.ofNullable (state (display, false)).map (s -> s.theme);
	}

	/**
	 * Switches a display to a theme (see the class comment). Call on the display's thread.
	 *
	 * @param display the display
	 * @param theme the theme
	 * @return the number of controls themed
	 * @exception IllegalArgumentException <ul><li>ERROR_NULL_ARGUMENT - if the display or theme is null</li></ul>
	 * @exception SWTException <ul><li>ERROR_THREAD_INVALID_ACCESS - if not called from the display's thread</li><li>ERROR_DEVICE_DISPOSED - if the display is disposed</li></ul>
	 */
	public static int apply (Display display, M3Theme theme) {
		checkDisplay (display);
		if (theme == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		M3ThemeState state = state (display, true);
		M3Theme from = state.theme;
		state.switchTo (theme);
		int themed = M3ThemeApplier.apply (display, state);
		state.release ();
		LISTENERS.forEach (listener -> listener.themeChanged (display, from, theme));
		return themed;
	}

	/**
	 * Back to stock: default colours on every control that follows, the look and feel and the operating system treatment the
	 * display had before the first {@link #apply}.
	 *
	 * @param display the display
	 * @return the number of controls reset; 0 when the display was stock
	 */
	public static int reset (Display display) {
		checkDisplay (display);
		M3ThemeState state = state (display, false);
		if (state == null) return 0;
		M3Theme from = state.theme;
		int reset = M3ThemeApplier.reset (display, state);
		state.dispose ();
		display.setData (STATE, null);
		LISTENERS.forEach (listener -> listener.themeChanged (display, from, null));
		return reset;
	}

	/**
	 * Applies the theme the platform asks for: {@link M3Theme#HIGH_CONTRAST} in high contrast, else {@link M3Theme#DARK} when
	 * the operating system prefers dark applications, else {@link M3Theme#LIGHT}.
	 *
	 * @param display the display
	 * @return the number of controls themed
	 */
	public static int applySystem (Display display) {
		checkDisplay (display);
		return apply (display, system (display.getHighContrast (), M3SystemTheme.dark ()));
	}

	static M3Theme system (boolean highContrast, boolean dark) {
		return highContrast ? M3Theme.HIGH_CONTRAST : dark ? M3Theme.DARK : M3Theme.LIGHT;
	}

	/**
	 * Applies the startup theme of {@link #PROPERTY}, if set.
	 *
	 * @param display the display
	 * @return the number of controls themed; 0 when the property is unset
	 * @throws IOException when the property names a theme file that cannot be read
	 */
	public static int applyStartup (Display display) throws IOException {
		String value = System.getProperty (PROPERTY);
		if (value == null || value.isBlank ()) return 0;
		if (SYSTEM.equalsIgnoreCase (value.strip ())) return applySystem (display);
		M3Theme theme = find (value);
		return apply (display, theme != null ? theme : load (Path.of (value.strip ())));
	}

	/**
	 * The colour of a slot in the display's theme (in a stock display: the platform's system colour for a system role, the light
	 * theme's for the other slots). One <code>Color</code> per slot per display, created on first use; valid until the next
	 * switch, so read it when painting rather than keeping it.
	 *
	 * @param display the display
	 * @param slot a colour slot
	 * @return the colour
	 */
	public static Color color (Display display, int slot) {
		checkDisplay (display);
		if (M3ThemeSlot.kind (slot) != M3ThemeSlot.COLOR) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		M3ThemeState state = state (display, false);
		if (state != null) return state.color (display, slot);
		int system = M3ThemeSlot.systemColor (slot);
		return system >= 0 ? display.getSystemColor (system) : stock (display).color (display, slot);
	}

	/**
	 * A metric of a control under the display's theme; a metric the theme leaves to the platform (-1, or 0 for the font height)
	 * answers the control's own: the item height for {@link M3ThemeSlot#ROW_HEIGHT}, the font height in points for
	 * {@link M3ThemeSlot#FONT_HEIGHT}.
	 *
	 * @param control the control
	 * @param slot a metric slot
	 * @return the metric
	 */
	public static int metric (Control control, int slot) {
		if (control == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (M3ThemeSlot.kind (slot) != M3ThemeSlot.METRIC) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		M3Theme theme = current (control.getDisplay ()).orElse (M3Theme.LIGHT);
		int value = theme.get (slot);
		if (slot == M3ThemeSlot.ROW_HEIGHT && value < 0) return M3ThemeApplier.itemHeight (control);
		if (slot == M3ThemeSlot.FONT_HEIGHT && value == 0) return control.getFont ().getFontData () [0].getHeight ();
		return value;
	}

	/**
	 * @param control a control
	 * @return true when the control follows the theme: <code>theme-provider</code> is on for it, or no
	 *         <code>theme-provider</code> key is configured for its class chain
	 */
	public static boolean follows (Control control) {
		return M3Features.isOn (control, M3Features.THEME_PROVIDER) || !configured (control.getClass (), M3Features.THEME_PROVIDER);
	}

	/* true when a swt.widget.feature key of the feature is set for the class chain or globally (M3Features precedence) */
	static boolean configured (Class<?> type, int feature) {
		Stream<Class<?>> chain = Stream.iterate (type, c -> c != null && c != Object.class, Class::getSuperclass);
		Stream<String> keys = Stream.concat (chain.map (c -> M3Features.key (c.getSimpleName (), feature)), Stream.of (M3Features.key (feature)));
		return keys.anyMatch (key -> System.getProperty (key) != null);
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

	private static boolean builtIn (String name) {
		return M3Theme.builtIns ().stream ().anyMatch (theme -> theme.name ().equals (name));
	}

	private static String key (String name) {
		return name.strip ().toLowerCase (Locale.ROOT);
	}

	private static void checkDisplay (Display display) {
		if (display == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		if (display.isDisposed ()) SWT.error (SWT.ERROR_DEVICE_DISPOSED);
		if (display.getThread () != Thread.currentThread ()) SWT.error (SWT.ERROR_THREAD_INVALID_ACCESS);
	}

	private static M3ThemeState state (Display display, boolean create) {
		M3ThemeState state = (M3ThemeState) display.getData (STATE);
		if (state == null && create) display.setData (STATE, state = new M3ThemeState (M3LookAndFeel.getLookAndFeel ()));
		return state;
	}

	/* the colours of a stock display's non-system slots: the light theme, kept in the display's data until it is disposed */
	private static M3ThemeState stock (Display display) {
		String key = STATE + ".stock";
		M3ThemeState stock = (M3ThemeState) display.getData (key);
		if (stock == null) {
			display.setData (key, stock = new M3ThemeState (null));
			stock.theme = M3Theme.LIGHT;
		}
		return stock;
	}
}
