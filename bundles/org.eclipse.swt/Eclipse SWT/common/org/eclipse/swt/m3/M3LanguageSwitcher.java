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
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * A language switcher for any shell or tool bar: a label, a drop-down of languages (native name and English name, from
 * {@link M3Languages}) and a preview (native name, script, direction). Choosing an entry switches the language at once
 * ({@link M3Language#setLocale}: registered labels relabelled, right-to-left shells flipped); a switch made elsewhere updates
 * the drop-down. By default it offers the {@link #DEFAULT_COUNT} languages with the most translation models plus the active
 * one; {@link #setLanguages(String...)} names others. The label follows {@link M3Language} (key
 * <code>swt.m3.switcher.language</code>, English fallback).
 * <p>
 * Usable by reflection: <code>new M3LanguageSwitcher (Composite, int)</code>.
 * </p>
 *
 * @since 3.138
 */
public class M3LanguageSwitcher extends Composite {
	/** How many languages the default list offers. */
	public static final int DEFAULT_COUNT = 24;
	static final String LABEL_KEY = "swt.m3.switcher.language";
	private final Combo combo;
	private final Label preview;
	private int [] languages;
	private final M3Language.Listener languageListener = (from, to) -> syncSoon ();

	/**
	 * @param parent the parent
	 * @param style the composite style
	 */
	public M3LanguageSwitcher (Composite parent, int style) {
		super (parent, style);
		setLayout (new GridLayout (3, false));
		Label label = new Label (this, SWT.NONE);
		M3Language.register (label, M3Language.TEXT, LABEL_KEY, "Language");
		combo = new Combo (this, SWT.READ_ONLY | SWT.DROP_DOWN);
		combo.setVisibleItemCount (16);
		combo.addListener (SWT.Selection, event -> choose (combo.getSelectionIndex ()));
		preview = new Label (this, SWT.NONE);
		languages = defaults (M3Language.getLocale ());
		M3Language.addListener (languageListener);
		addListener (SWT.Dispose, event -> M3Language.removeListener (languageListener));
		sync ();
	}

	/* the most translated languages, and the active one when it is not among them */
	static int [] defaults (Locale active) {
		int own = M3Languages.indexOf (active.toLanguageTag ());
		IntStream top = IntStream.of (M3Languages.top (DEFAULT_COUNT));
		return own < 0 ? top.toArray () : IntStream.concat (top, IntStream.of (own)).distinct ().toArray ();
	}

	/**
	 * Names the languages offered.
	 *
	 * @param tags language tags of {@link M3Languages}
	 * @exception IllegalArgumentException <ul><li>ERROR_INVALID_ARGUMENT - if a tag is not in {@link M3Languages}</li></ul>
	 */
	public void setLanguages (String... tags) {
		checkWidget ();
		int [] entries = Arrays.stream (tags).mapToInt (M3Languages::indexOf).toArray ();
		if (Arrays.stream (entries).anyMatch (entry -> entry < 0)) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		languages = entries;
		sync ();
	}

	/** @return the language tags offered, in drop-down order */
	public List<String> getLanguages () {
		checkWidget ();
		return Arrays.stream (languages).mapToObj (M3Languages::tag).toList ();
	}

	/** @return the drop-down (tests and toolbars that size it) */
	public Combo getCombo () {
		checkWidget ();
		return combo;
	}

	/** @return the preview label */
	public Label getPreview () {
		checkWidget ();
		return preview;
	}

	/**
	 * Chooses an entry as a user would.
	 *
	 * @param index the entry index
	 * @return the number of labels relabelled on this display
	 */
	public int choose (int index) {
		checkWidget ();
		if (index < 0 || index >= languages.length) SWT.error (SWT.ERROR_INVALID_RANGE);
		int set = M3Language.setLocale (M3Languages.locale (languages [index]));
		sync ();
		return set;
	}

	private void syncSoon () {
		if (!isDisposed ()) getDisplay ().asyncExec (() -> { if (!isDisposed ()) sync (); });
	}

	void sync () {
		combo.setItems (Arrays.stream (languages).mapToObj (M3LanguageSwitcher::label).toArray (String []::new));
		int active = M3Languages.indexOf (M3Language.getLocale ().toLanguageTag ());
		int at = IntStream.range (0, languages.length).filter (i -> languages [i] == active).findFirst ().orElse (-1);
		if (at >= 0) combo.select (at);
		preview.setText (at < 0 ? "" : preview (languages [at]));
		requestLayout ();
	}

	static String label (int entry) {
		String nativeName = M3Languages.nativeName (entry), english = M3Languages.name (entry);
		return nativeName.equals (english) ? english : nativeName + " — " + english;
	}

	static String preview (int entry) {
		return M3Languages.nativeName (entry) + " · " + M3Languages.script (entry) + " · " + (M3Languages.rightToLeft (entry) ? "RTL" : "LTR");
	}
}
