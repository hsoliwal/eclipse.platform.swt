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
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * A theme switcher for any shell or tool bar: a label, a drop-down of the themes ({@link M3Themes#names()} after "stock" and
 * "system") and a swatch preview of the active theme (page, surface, accent, selection and text). Choosing an entry applies it
 * to the display at once ({@link M3Themes#apply}, {@link M3Themes#reset}, {@link M3Themes#applySystem}); a switch made
 * elsewhere updates the drop-down. The label and the entries follow {@link M3Language} (keys <code>swt.m3.switcher.theme</code>,
 * <code>swt.m3.theme.&lt;name&gt;</code>; English fallbacks).
 * <p>
 * Usable by reflection: <code>new M3ThemeSwitcher (Composite, int)</code>.
 * </p>
 *
 * @since 3.138
 */
public class M3ThemeSwitcher extends Composite {
	/** The entries before the theme names: back to stock colours, and the theme the platform asks for. */
	public static final String STOCK = "stock", SYSTEM = M3Themes.SYSTEM;
	static final String LABEL_KEY = "swt.m3.switcher.theme", ENTRY_KEY = "swt.m3.theme.";
	private static final int [] SWATCHES = {M3ThemeSlot.BG, M3ThemeSlot.SURFACE, M3ThemeSlot.ACCENT, M3ThemeSlot.SELECTION, M3ThemeSlot.FG};
	private final Combo combo;
	private final Canvas preview;
	private List<String> entries = List.of ();
	private final M3Themes.Listener themeListener = (display, from, to) -> syncSoon ();
	private final M3Language.Listener languageListener = (from, to) -> syncSoon ();

	/**
	 * @param parent the parent
	 * @param style the composite style
	 */
	public M3ThemeSwitcher (Composite parent, int style) {
		super (parent, style);
		setLayout (new GridLayout (3, false));
		Label label = new Label (this, SWT.NONE);
		M3Language.register (label, M3Language.TEXT, LABEL_KEY, "Theme");
		combo = new Combo (this, SWT.READ_ONLY | SWT.DROP_DOWN);
		combo.addListener (SWT.Selection, event -> choose (combo.getSelectionIndex ()));
		preview = new Canvas (this, SWT.DOUBLE_BUFFERED);
		preview.setLayoutData (new GridData (16 * SWATCHES.length, 16));
		preview.addListener (SWT.Paint, this::paintPreview);
		M3Themes.addListener (themeListener);
		M3Language.addListener (languageListener);
		addListener (SWT.Dispose, event -> unhook ());
		sync ();
	}

	private void unhook () {
		M3Themes.removeListener (themeListener);
		M3Language.removeListener (languageListener);
	}

	/** @return the entries of the drop-down: {@link #STOCK}, {@link #SYSTEM}, then the theme names */
	public List<String> getEntries () {
		checkWidget ();
		return entries;
	}

	/** @return the drop-down (tests and toolbars that size it) */
	public Combo getCombo () {
		checkWidget ();
		return combo;
	}

	/**
	 * Chooses an entry as a user would.
	 *
	 * @param index the entry index
	 * @return the number of controls themed or reset
	 */
	public int choose (int index) {
		checkWidget ();
		if (index < 0 || index >= entries.size ()) SWT.error (SWT.ERROR_INVALID_RANGE);
		String entry = entries.get (index);
		Display display = getDisplay ();
		int touched = switch (entry) {
			case STOCK -> M3Themes.reset (display);
			case SYSTEM -> M3Themes.applySystem (display);
			default -> M3Themes.apply (display, M3Themes.find (entry));
		};
		sync ();
		return touched;
	}

	private void syncSoon () {
		if (!isDisposed ()) getDisplay ().asyncExec (() -> { if (!isDisposed ()) sync (); });
	}

	/* the entries in the active language and the selection of the display's theme */
	void sync () {
		entries = Stream.concat (Stream.of (STOCK, SYSTEM), M3Themes.names ().stream ()).toList ();
		combo.setItems (entries.stream ().map (M3ThemeSwitcher::label).toArray (String []::new));
		String active = M3Themes.current (getDisplay ()).map (M3Theme::name).orElse (STOCK);
		combo.select (Math.max (0, entries.indexOf (active)));
		preview.redraw ();
		requestLayout ();
	}

	static String label (String entry) {
		String english = Arrays.stream (entry.split ("-")).map (w -> Character.toUpperCase (w.charAt (0)) + w.substring (1)).collect (Collectors.joining (" "));
		return M3Language.find (ENTRY_KEY + entry).orElse (english);
	}

	/* the swatches of the display's theme, read when painting (one Color per slot per display, M3Themes.color) */
	private void paintPreview (Event event) {
		Display display = getDisplay ();
		event.gc.setForeground (M3Themes.color (display, M3ThemeSlot.BORDER));
		for (int i = 0; i < SWATCHES.length; i++) {
			event.gc.setBackground (M3Themes.color (display, SWATCHES [i]));
			event.gc.fillRectangle (i * 16, 0, 16, 16);
			event.gc.drawRectangle (i * 16, 0, 15, 15);
		}
	}
}
