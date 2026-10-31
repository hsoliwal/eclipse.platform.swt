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
import java.util.stream.*;

import org.eclipse.swt.*;

/**
 * The slots of an {@link M3Theme}: dense int ids into its packed table (docs/m3/theme-language/README.md).
 * <ul>
 * <li>0..8: the web corpus slots (<code>--vs-bg</code> ... <code>--vs-pad</code>);</li>
 * <li>9..28: the SWT system colour roles (<code>SWT.COLOR_WIDGET_BACKGROUND</code> ... , {@link #ofSystemColor(int)});</li>
 * <li>29..40: the drawn-plane roles (row stripe, selection, hover, focus ring, header, grid line, status colours);</li>
 * <li>41..46: font and metric slots.</li>
 * </ul>
 * A colour slot holds an opaque or translucent ARGB int; a metric slot holds pixels, points or SWT font style bits,
 * with -1 (row height, indent) or 0 (font height) for "the platform's".
 *
 * @since 3.138
 */
public final class M3ThemeSlot {
	/** Web slots: page background, text, muted text, border, accent, second accent, surface (colours); radius, padding (pixels). */
	public static final int BG = 0, FG = 1, MUTED = 2, BORDER = 3, ACCENT = 4, ACCENT_2 = 5, SURFACE = 6, RADIUS = 7, PAD = 8;
	/** SWT system colour roles. */
	public static final int WIDGET_BACKGROUND = 9, WIDGET_FOREGROUND = 10, WIDGET_BORDER = 11, WIDGET_LIGHT_SHADOW = 12,
		WIDGET_NORMAL_SHADOW = 13, WIDGET_DARK_SHADOW = 14, WIDGET_HIGHLIGHT_SHADOW = 15, WIDGET_DISABLED_FOREGROUND = 16,
		LIST_BACKGROUND = 17, LIST_FOREGROUND = 18, LIST_SELECTION = 19, LIST_SELECTION_TEXT = 20, INFO_BACKGROUND = 21,
		INFO_FOREGROUND = 22, TITLE_BACKGROUND = 23, TITLE_FOREGROUND = 24, TITLE_INACTIVE_BACKGROUND = 25,
		TITLE_INACTIVE_FOREGROUND = 26, LINK_FOREGROUND = 27, TEXT_DISABLED_BACKGROUND = 28;
	/** Drawn-plane roles. */
	public static final int ROW_STRIPE = 29, SELECTION = 30, SELECTION_TEXT = 31, SELECTION_INACTIVE = 32, HOVER = 33, FOCUS_RING = 34,
		HEADER_BACKGROUND = 35, HEADER_FOREGROUND = 36, GRIDLINE = 37, ERROR = 38, WARNING = 39, SUCCESS = 40;
	/** Font height in points (0: the platform's), font style (SWT.NORMAL, BOLD, ITALIC). */
	public static final int FONT_HEIGHT = 41, FONT_STYLE = 42;
	/** Row height and indent in pixels (-1: the platform's), focus ring and border width in pixels. */
	public static final int ROW_HEIGHT = 43, INDENT = 44, FOCUS_WIDTH = 45, BORDER_WIDTH = 46;
	/** The number of slots. */
	public static final int COUNT = 47;
	/** Slot kinds. */
	public static final byte COLOR = 0, METRIC = 1;
	/** The prefix of the web slot names accepted in theme files (<code>--vs-bg</code> = <code>bg</code>). */
	public static final String WEB_PREFIX = "--vs-";

	private static final String [] NAMES = {"bg", "fg", "muted", "border", "accent", "accent-2", "surface", "radius", "pad",
		"widget-background", "widget-foreground", "widget-border", "widget-light-shadow", "widget-normal-shadow", "widget-dark-shadow",
		"widget-highlight-shadow", "widget-disabled-foreground", "list-background", "list-foreground", "list-selection",
		"list-selection-text", "info-background", "info-foreground", "title-background", "title-foreground", "title-inactive-background",
		"title-inactive-foreground", "link-foreground", "text-disabled-background", "row-stripe", "selection", "selection-text",
		"selection-inactive", "hover", "focus-ring", "header-background", "header-foreground", "gridline", "error", "warning", "success",
		"font-height", "font-style", "row-height", "indent", "focus-width", "border-width"};
	/* SWT.COLOR_* constants of slots 9..28, in slot order */
	private static final int [] SYSTEM = {SWT.COLOR_WIDGET_BACKGROUND, SWT.COLOR_WIDGET_FOREGROUND, SWT.COLOR_WIDGET_BORDER,
		SWT.COLOR_WIDGET_LIGHT_SHADOW, SWT.COLOR_WIDGET_NORMAL_SHADOW, SWT.COLOR_WIDGET_DARK_SHADOW, SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW,
		SWT.COLOR_WIDGET_DISABLED_FOREGROUND, SWT.COLOR_LIST_BACKGROUND, SWT.COLOR_LIST_FOREGROUND, SWT.COLOR_LIST_SELECTION,
		SWT.COLOR_LIST_SELECTION_TEXT, SWT.COLOR_INFO_BACKGROUND, SWT.COLOR_INFO_FOREGROUND, SWT.COLOR_TITLE_BACKGROUND,
		SWT.COLOR_TITLE_FOREGROUND, SWT.COLOR_TITLE_INACTIVE_BACKGROUND, SWT.COLOR_TITLE_INACTIVE_FOREGROUND, SWT.COLOR_LINK_FOREGROUND,
		SWT.COLOR_TEXT_DISABLED_BACKGROUND};
	/**
	 * Text over background pairs (foreground slot, background slot) that must reach WCAG 2 AA, 4.5:1, in every built-in theme
	 * (disabled text is exempt, as in WCAG 1.4.3). The drawn rows keep the selection text colour when the widget loses focus,
	 * so the inactive selection is paired with {@link #SELECTION_TEXT}.
	 */
	public static final int [][] TEXT_PAIRS = {{FG, BG}, {MUTED, BG}, {FG, SURFACE}, {WIDGET_FOREGROUND, WIDGET_BACKGROUND},
		{LIST_FOREGROUND, LIST_BACKGROUND}, {LIST_SELECTION_TEXT, LIST_SELECTION}, {INFO_FOREGROUND, INFO_BACKGROUND},
		{TITLE_FOREGROUND, TITLE_BACKGROUND}, {LINK_FOREGROUND, LIST_BACKGROUND}, {SELECTION_TEXT, SELECTION},
		{SELECTION_TEXT, SELECTION_INACTIVE}, {LIST_FOREGROUND, ROW_STRIPE}, {LIST_FOREGROUND, HOVER},
		{HEADER_FOREGROUND, HEADER_BACKGROUND}, {ERROR, LIST_BACKGROUND}, {WARNING, LIST_BACKGROUND}, {SUCCESS, LIST_BACKGROUND}};
	/** User-interface component pairs that must reach WCAG 2 1.4.11, 3:1 (the focus ring over list and page). */
	public static final int [][] UI_PAIRS = {{FOCUS_RING, LIST_BACKGROUND}, {FOCUS_RING, BG}};
	private static final Map<String, Integer> IDS = IntStream.range (0, COUNT).boxed ().collect (Collectors.toUnmodifiableMap (i -> NAMES [i], i -> i));

	private M3ThemeSlot () {
	}

	/**
	 * @param slot a slot id
	 * @return its name in theme files (<code>bg</code>, <code>list-selection</code>, <code>row-height</code> ...)
	 */
	public static String name (int slot) {
		check (slot);
		return NAMES [slot];
	}

	/**
	 * @param name a slot name, with or without the {@link #WEB_PREFIX}, any case
	 * @return the slot id, or -1 when no slot has the name
	 */
	public static int id (String name) {
		String key = name.strip ().toLowerCase (Locale.ROOT);
		if (key.startsWith (WEB_PREFIX)) key = key.substring (WEB_PREFIX.length ());
		return IDS.getOrDefault (key, -1);
	}

	/**
	 * @param slot a slot id
	 * @return {@link #COLOR} or {@link #METRIC}
	 */
	public static byte kind (int slot) {
		check (slot);
		return slot == RADIUS || slot == PAD || slot >= FONT_HEIGHT ? METRIC : COLOR;
	}

	/**
	 * @param swtColor an <code>SWT.COLOR_*</code> system colour constant
	 * @return the slot of that role, or -1 when it has none (the fixed colours such as <code>SWT.COLOR_RED</code>)
	 */
	public static int ofSystemColor (int swtColor) {
		return IntStream.range (0, SYSTEM.length).filter (i -> SYSTEM [i] == swtColor).map (i -> WIDGET_BACKGROUND + i).findFirst ().orElse (-1);
	}

	/**
	 * @param slot a slot id
	 * @return the <code>SWT.COLOR_*</code> constant of a system colour role slot, or -1 for the other slots
	 */
	public static int systemColor (int slot) {
		check (slot);
		return slot >= WIDGET_BACKGROUND && slot <= TEXT_DISABLED_BACKGROUND ? SYSTEM [slot - WIDGET_BACKGROUND] : -1;
	}

	static void check (int slot) {
		if (slot < 0 || slot >= COUNT) SWT.error (SWT.ERROR_INVALID_RANGE);
	}
}
