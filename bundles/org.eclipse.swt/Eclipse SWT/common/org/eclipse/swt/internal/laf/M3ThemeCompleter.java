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
package org.eclipse.swt.internal.laf;

import java.nio.charset.*;
import java.security.*;
import java.util.*;

/**
 * Completes an LLM's theme proposal into a full look and feel (docs/m3/look-and-feel/GENERATOR.md), on the CPU and
 * deterministically: missing palette colours, WCAG 2.x contrast (4.5:1 for text, 3:1 for UI elements, solved by
 * bisection over OKLCH lightness, {@link M3Oklch}), hover, pressed, disabled, focus and inactive-selection states by
 * OKLCH arithmetic, metrics from the density and the font size, and a value for every key of the LF2 catalogue
 * ({@link LookAndFeelThemes#KEYS}). The same accepted input and {@link #VERSION} give the same theme and the same
 * {@link Theme#hash()}.
 */
public final class M3ThemeCompleter {
	/** The completer's version; part of the content hash. */
	public static final String VERSION = "1";
	/** WCAG 2.x contrast for text and for UI elements. */
	public static final double TEXT = 4.5, UI = 3.0;

	/**
	 * A completed theme: every catalogue key as LF2 table text, the solved palette ({@link M3ThemeSpec#PALETTE} order),
	 * the SHA-256 of the canonical input and the completer version, and what the completer changed.
	 */
	public record Theme (String name, boolean dark, Map<String, String> table, int [] palette, String hash, List<String> notes) {
	}

	private static final byte [] KINDS;
	static {
		LookAndFeelDefaults flat = new LookAndFeelLoader (LookAndFeelThemes::text, LookAndFeelThemes::fallbackSystemColor).load ("flat");
		KINDS = new byte [LookAndFeelThemes.KEYS.size ()];
		for (int i = 0; i < KINDS.length; i++) KINDS [i] = flat.kind (flat.index (LookAndFeelThemes.KEYS.get (i)));
	}

	private M3ThemeCompleter () {
	}

	/** Completes a proposal. */
	public static Theme complete (M3ThemeSpec spec) {
		Base base = new Base (spec);
		Map<String, String> table = new LinkedHashMap<> ();
		for (int i = 0; i < KINDS.length; i++) {
			String key = LookAndFeelThemes.KEYS.get (i);
			table.put (key, base.value (key, KINDS [i]));
		}
		int [] palette = {base.background, base.foreground, base.accent, base.selection, base.border, base.error, base.warning, base.info};
		String name = spec.name () != null ? spec.name () : base.dark ? "Generated Dark" : "Generated Light";
		return new Theme (name, base.dark, Collections.unmodifiableMap (table), palette, hash (spec.canonical ()), List.copyOf (base.notes));
	}

	/**
	 * The theme of the other brightness: the background and foreground lightness mirrored in OKLCH, the selection and
	 * border derived again, the accent and the status colours kept (contrast solved again).
	 */
	public static Theme twin (M3ThemeSpec spec) {
		Base base = new Base (spec);
		int [] palette = new int [M3ThemeSpec.PALETTE.size ()];
		palette [0] = mirror (base.background);
		palette [1] = mirror (base.foreground);
		palette [2] = base.accent;
		palette [5] = base.error;
		palette [6] = base.warning;
		palette [7] = base.info;
		String name = spec.name () == null ? null : spec.name () + (base.dark ? " Light" : " Dark");
		return complete (new M3ThemeSpec (name, !base.dark, palette, spec.family (), spec.size (), spec.density (), List.of ()));
	}

	/**
	 * A colour moved in OKLCH lightness (hue and chroma kept) just far enough from a background to reach a contrast
	 * ratio; black or white when no lightness of its hue reaches it.
	 */
	public static int ensure (int colour, int against, double ratio) {
		if (M3Palette.contrast (colour, against) >= ratio) return colour;
		boolean lighter = M3Palette.luminance (colour) >= M3Palette.luminance (against);
		int solved = solve (colour, against, ratio, lighter);
		if (solved == 0) solved = solve (colour, against, ratio, !lighter);
		return solved != 0 ? solved : M3Palette.readableOn (against);
	}

	/* the least lightness move towards white (lighter) or black that reaches the ratio, 0 when even the end fails */
	private static int solve (int colour, int against, double ratio, boolean lighter) {
		double [] lch = M3Oklch.of (colour);
		double end = lighter ? 1 : 0;
		if (M3Palette.contrast (M3Oklch.argb (end, lch [1], lch [2]), against) < ratio) return 0;
		double fail = lch [0], pass = end;
		for (int i = 0; i < 32; i++) {
			double middle = (fail + pass) / 2;
			if (M3Palette.contrast (M3Oklch.argb (middle, lch [1], lch [2]), against) >= ratio) pass = middle;
			else fail = middle;
		}
		return M3Oklch.argb (pass, lch [1], lch [2]);
	}

	/** A colour mixed with another in Oklab: <code>0</code> is the first, <code>1</code> the second. */
	public static int mix (int a, int b, double t) {
		double [] x = M3Oklch.of (a), y = M3Oklch.of (b);
		double xa = x [1] * Math.cos (Math.toRadians (x [2])), xb = x [1] * Math.sin (Math.toRadians (x [2]));
		double ya = y [1] * Math.cos (Math.toRadians (y [2])), yb = y [1] * Math.sin (Math.toRadians (y [2]));
		double ma = xa + (ya - xa) * t, mb = xb + (yb - xb) * t;
		return M3Oklch.argb (x [0] + (y [0] - x [0]) * t, Math.hypot (ma, mb), Math.toDegrees (Math.atan2 (mb, ma)));
	}

	private static int mirror (int colour) {
		double [] lch = M3Oklch.of (colour);
		return M3Oklch.argb (1 - lch [0], lch [1], lch [2]);
	}

	static String hash (String canonical) {
		try {
			byte [] digest = MessageDigest.getInstance ("SHA-256").digest ((canonical + "\n" + VERSION).getBytes (StandardCharsets.UTF_8));
			return HexFormat.of ().formatHex (digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException (e);
		}
	}

	/* the solved base colours and metrics of one proposal, and the value rule of every catalogue property */
	private static final class Base {
		final boolean dark;
		final int background, foreground, accent, selection, selectionText, inactive, inactiveText, border, error, warning, info;
		final int field, face, tooltip, track, disabled, density, rowHeight;
		final List<String> notes = new ArrayList<> ();

		Base (M3ThemeSpec spec) {
			notes.addAll (spec.repairs ());
			int given = spec.colour (0);
			boolean dark = spec.dark () != null ? spec.dark () : M3Oklch.lightness (given) < 0.5;
			if (given != M3ThemeSpec.MISSING && spec.dark () != null && dark != M3Oklch.lightness (given) < 0.5) {
				notes.add ("dark=" + dark + " contradicts the background " + M3Palette.text (given) + ": the background decides");
				dark = !dark;
			}
			this.dark = dark;
			background = given != M3ThemeSpec.MISSING ? given : dark ? 0xFF1E1F22 : 0xFFFFFFFF;
			density = spec.density () < 0 ? 1 : spec.density ();
			int fontPixels = Math.round ((spec.size () > 0 ? spec.size () : 9) * 4 / 3f);
			rowHeight = fontPixels + 4 + 4 * density;
			field = shift (background, dark ? 0.03 : -0.02);
			face = shift (background, dark ? 0.06 : 0.04);
			tooltip = shift (background, dark ? 0.08 : 0.03);
			track = shift (background, 0.04);
			foreground = text (or (spec.colour (1), dark ? 0xFFDFE1E5 : 0xFF1F1F1F), background, "foreground");
			accent = element (or (spec.colour (2), 0xFF2F6FD0), background, "accent");
			selection = or (spec.colour (3), accent);
			selectionText = text (M3Palette.readableOn (selection), selection, "selection text");
			inactive = mix (selection, background, 0.5);
			inactiveText = text (foreground, inactive, "inactive selection text");
			border = element (or (spec.colour (4), mix (background, foreground, 0.2)), background, "border");
			error = element (or (spec.colour (5), 0xFFD32F2F), background, "error");
			warning = element (or (spec.colour (6), 0xFFF9A825), background, "warning");
			info = element (or (spec.colour (7), 0xFF0288D1), background, "info");
			disabled = mix (foreground, background, 0.6);
		}

		String value (String key, byte kind) {
			String widget = key.substring (0, key.indexOf ('.')), property = key.substring (key.indexOf ('.') + 1);
			switch (kind) {
				case M3UIKeys.COLOR: return M3Palette.text (colour (widget, property));
				case M3UIKeys.INTEGER: return Integer.toString (integer (property));
				case M3UIKeys.BOOLEAN: return "false";
				default: return insets (widget);
			}
		}

		int colour (String widget, String property) {
			int under = backgroundOf (widget);
			switch (property) {
				case "background": return under;
				case "foreground": return widget.equals ("Link") ? text (accent, under, "link") : widget.equals ("ProgressBar") ? element (accent, under, "progress") : text (foreground, under, widget + " text");
				case "disabledForeground": return disabled;
				case "borderColor": case "headerSeparatorColor": return border;
				case "focusColor": case "focusedBorderColor": case "underlineColor": return accent;
				case "hoverBackground": return shift (under, 0.04);
				case "pressedBackground": return shift (under, 0.08);
				case "selectionBackground": return selection;
				case "selectionForeground": return selectionText;
				case "selectionInactiveBackground": return inactive;
				case "selectionInactiveForeground": return inactiveText;
				case "gridColor": return mix (field, foreground, 0.12);
				case "stripeBackground": return shift (field, 0.02);
				case "headerBackground": return shift (background, 0.03);
				case "headerForeground": return text (foreground, shift (background, 0.03), "header text");
				case "lineColor": return mix (field, foreground, 0.3);
				case "expandIconColor": return element (mix (foreground, field, 0.4), field, "expand icon");
				case "hoverForeground": return text (shift (accent, 0.1), background, "link hover");
				case "track": return track;
				case "thumb": return mix (track, foreground, 0.3);
				case "hoverThumb": return mix (track, foreground, 0.45);
				case "selectedBackground": return field;
				default: return background;
			}
		}

		int integer (String property) {
			switch (property) {
				case "rowHeight": return rowHeight;
				case "indent": return 16 + 4 * density;
				case "width": return 10 + 2 * density;
				case "thumbArc": return (10 + 2 * density) / 2;
				case "tabHeight": return 24 + 4 * density;
				case "focusWidth": return density == 2 ? 2 : 1;
				case "arc": return 2 + 2 * density;
				default: return 0;
			}
		}

		String insets (String widget) {
			int vertical = 1 + density * 2, horizontal = widget.equals ("Button") ? 8 + 3 * density : 4 + 2 * density;
			return vertical + "," + horizontal + "," + vertical + "," + horizontal;
		}

		int backgroundOf (String widget) {
			switch (widget) {
				case "List": case "Table": case "Tree": case "Text": return field;
				case "Button": return face;
				case "ToolTip": return tooltip;
				case "ProgressBar": case "ScrollBar": return track;
				default: return background;
			}
		}

		/* towards the foreground's side (lighter on dark themes, darker on light ones) by an OKLCH lightness amount; a
		 * negative amount moves away from it */
		int shift (int colour, double amount) {
			double lightness = M3Oklch.lightness (colour);
			double step = dark ? amount : -amount;
			return M3Oklch.withLightness (colour, lightness + step);
		}

		int text (int colour, int against, String what) {
			return noted (colour, ensure (colour, against, TEXT), against, TEXT, what);
		}

		int element (int colour, int against, String what) {
			return noted (colour, ensure (colour, against, UI), against, UI, what);
		}

		int noted (int before, int after, int against, double ratio, String what) {
			if (before != after) {
				String note = what + " " + M3Palette.text (before) + " -> " + M3Palette.text (after) + " for " + ratio + ":1 on " + M3Palette.text (against);
				if (!notes.contains (note)) notes.add (note);
			}
			return after;
		}

		static int or (int colour, int fallback) {
			return colour != M3ThemeSpec.MISSING ? colour : fallback;
		}
	}
}
