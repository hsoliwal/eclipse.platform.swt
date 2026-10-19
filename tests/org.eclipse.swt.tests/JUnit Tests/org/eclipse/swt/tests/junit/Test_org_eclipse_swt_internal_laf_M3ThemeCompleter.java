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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.*;
import java.security.*;
import java.util.*;

import org.eclipse.swt.internal.laf.*;
import org.junit.jupiter.api.*;

/**
 * The CPU side of the look and feel generator (docs/m3/look-and-feel/GENERATOR.md, LF7): the strict reading of an
 * LLM's proposal ({@link M3ThemeSpec}), its completion with WCAG contrast and derived states
 * ({@link M3ThemeCompleter}, OKLCH by {@link M3Oklch}) on seeded and adversarial proposals, the content hash, the
 * twin, and the three emitters ({@link M3ThemeExport}) read back through the importers. No display.
 */
public class Test_org_eclipse_swt_internal_laf_M3ThemeCompleter {
	static final String FULL = """
		{ "name": "Harbour \\"Night\\"", "dark": true,
		  "palette": { "background": "#1b1f24", "foreground": "#d8dee9", "accent": "#5e81ac", "selection": "#3b4252",
		               "border": "#434c5e", "error": "#bf616a", "warning": "#ebcb8b", "info": "#88c0d0" },
		  "font": { "family": "Segoe UI", "size": 10 }, "density": "comfortable" }
		""";

	static LookAndFeelDefaults load (String text) {
		return new LookAndFeelLoader (n -> n.equals ("generated") ? text : LookAndFeelThemes.text (n), name -> 0x808080).load ("generated");
	}

	static int colour (M3ThemeCompleter.Theme theme, String key) {
		return 0xFF000000 | Integer.parseInt (theme.table ().get (key).substring (1), 16);
	}

	/* every text pair at least 4.5:1 and every element at least 3:1 */
	static void assertReadable (M3ThemeCompleter.Theme theme) {
		String [][] text = {{"Component.foreground", "Component.background"}, {"Table.foreground", "Table.background"}, {"List.foreground", "List.background"},
			{"Tree.foreground", "Tree.background"}, {"Text.foreground", "Text.background"}, {"Button.foreground", "Button.background"},
			{"ToolTip.foreground", "ToolTip.background"}, {"Label.foreground", "Component.background"}, {"Link.foreground", "Component.background"},
			{"Link.hoverForeground", "Component.background"}, {"Component.selectionForeground", "Component.selectionBackground"},
			{"Component.selectionInactiveForeground", "Component.selectionInactiveBackground"}, {"Table.headerForeground", "Table.headerBackground"}};
		for (String [] pair : text) {
			double ratio = M3Palette.contrast (colour (theme, pair [0]), colour (theme, pair [1]));
			assertTrue (ratio >= M3ThemeCompleter.TEXT - 1e-9, pair [0] + " on " + pair [1] + ": " + ratio + " " + theme.notes ());
		}
		for (String element : new String [] {"Component.focusColor", "Component.borderColor", "TabFolder.underlineColor"}) {
			double ratio = M3Palette.contrast (colour (theme, element), colour (theme, "Component.background"));
			assertTrue (ratio >= M3ThemeCompleter.UI - 1e-9, element + ": " + ratio);
		}
		assertTrue (M3Palette.contrast (colour (theme, "ProgressBar.foreground"), colour (theme, "ProgressBar.background")) >= M3ThemeCompleter.UI - 1e-9);
		assertEquals (LookAndFeelThemes.KEYS, new ArrayList<> (theme.table ().keySet ()), "every catalogue key, in order");
	}

	@Test
	public void aFullProposalIsReadAndCanonical () {
		M3ThemeSpec spec = M3ThemeSpec.parse (FULL);
		assertEquals ("Harbour \"Night\"", spec.name ());
		assertEquals (Boolean.TRUE, spec.dark ());
		assertEquals (0xFF1B1F24, spec.colour (0));
		assertEquals (0xFF88C0D0, spec.colour (7));
		assertEquals ("Segoe UI", spec.family ());
		assertEquals (10, spec.size ());
		assertEquals (2, spec.density ());
		assertEquals (List.of (), spec.repairs ());
		assertEquals ("{\"dark\":true,\"density\":\"comfortable\",\"font\":{\"family\":\"Segoe UI\",\"size\":10},\"name\":\"Harbour \\\"Night\\\"\","
			+ "\"palette\":{\"accent\":\"#5e81ac\",\"background\":\"#1b1f24\",\"border\":\"#434c5e\",\"error\":\"#bf616a\",\"foreground\":\"#d8dee9\","
			+ "\"info\":\"#88c0d0\",\"selection\":\"#3b4252\",\"warning\":\"#ebcb8b\"}}", spec.canonical ());
		M3ThemeSpec small = M3ThemeSpec.parse ("{\"dark\": false, \"font\": {\"size\": 12}, \"palette\": {\"background\": \"#fff\"}}");
		assertEquals ("{\"dark\":false,\"font\":{\"size\":12},\"palette\":{\"background\":\"#ffffff\"}}", small.canonical ());
		assertNull (small.name ());
		assertNull (small.family ());
		assertEquals (-1, small.density ());
		assertEquals ("{\"font\":{\"family\":\"A\\u0001\\\\\"},\"palette\":{\"background\":\"#000000\"}}",
			M3ThemeSpec.parse ("{\"font\": {\"family\": \"A\\u0001\\\\\"}, \"palette\": {\"background\": \"#000\"}}").canonical ());
		assertEquals ("{\"dark\":true}", M3ThemeSpec.parse ("{\"dark\": true}").canonical ());
	}

	@Test
	public void theSchemaIsClosed () {
		String [][] bad = {{"[]", "a theme is a JSON object"}, {"{\"dark\": true, \"theme\": 1}", "unknown key theme"},
			{"{\"dark\": true, \"palette\": {\"text\": \"#fff\"}}", "unknown key palette.text"}, {"{\"dark\": true, \"font\": {\"weight\": 1}}", "unknown key font.weight"},
			{"{\"dark\": \"yes\"}", "dark is not a boolean"}, {"{\"dark\": true, \"palette\": []}", "palette is not a map"},
			{"{\"dark\": true, \"palette\": {\"accent\": 5}}", "palette.accent is not a string"}, {"{\"dark\": true, \"font\": {\"size\": \"9\"}}", "font.size is not a long"},
			{"{\"dark\": true, \"font\": {\"size\": 9.5}}", "font.size is not a long"}, {"{\"dark\": true, \"font\": {\"size\": 5}}", "font.size is not in 6..32"},
			{"{\"dark\": true, \"font\": {\"size\": 33}}", "font.size is not in 6..32"}, {"{\"dark\": true, \"density\": \"dense\"}", "density is one of [compact, normal, comfortable]"},
			{"{\"dark\": true, \"name\": \" \"}", "name is blank or longer than 80"}, {"{\"dark\": true, \"name\": \"" + "x".repeat (81) + "\"}", "name is blank or longer than 80"},
			{"{\"dark\": true, \"font\": {\"family\": \"" + "f".repeat (65) + "\"}}", "font.family is blank or longer than 64"},
			{"{\"name\": \"no base\"}", "a theme needs dark or palette.background"}, {"{\"palette\": {\"background\": \"navy\"}}", "a theme needs dark or palette.background"},
			{"{\"dark\": true, \"font\": []}", "font is not a map"}, {"{\"dark\": true, \"density\": 1}", "density is not a string"}, {"{\"dark\": true, \"name\": 1}", "name is not a string"}};
		for (String [] row : bad) {
			IllegalArgumentException e = assertThrows (IllegalArgumentException.class, () -> M3ThemeSpec.parse (row [0]), row [0]);
			assertEquals (row [1], e.getMessage (), row [0]);
		}
		assertThrows (IllegalArgumentException.class, () -> M3ThemeSpec.parse ("{dark: true}"), "not JSON");
	}

	@Test
	public void unreadableAndTranslucentColoursAreRepaired () {
		M3ThemeSpec spec = M3ThemeSpec.parse ("{\"dark\": false, \"palette\": {\"background\": \"#ffffff80\", \"accent\": \"blue\", \"border\": \"#12345\"}}");
		assertEquals (0xFFFFFFFF, spec.colour (0));
		assertEquals (M3ThemeSpec.MISSING, spec.colour (2));
		assertEquals (List.of ("background: #ffffff80 made opaque", "accent: unreadable colour \"blue\" dropped", "border: unreadable colour \"#12345\" dropped"), spec.repairs ());
		M3ThemeCompleter.Theme theme = M3ThemeCompleter.complete (spec);
		assertTrue (theme.notes ().containsAll (spec.repairs ()));
		assertReadable (theme);
	}

	@Test
	public void seededProposalsAreCompletedReadably () {
		Random random = new Random (0x5EEDL);
		for (int i = 0; i < 60; i++) {
			StringBuilder json = new StringBuilder ("{\"dark\": ").append (random.nextBoolean ()).append (", \"palette\": {");
			String separator = "";
			for (String slot : M3ThemeSpec.PALETTE) {
				if (random.nextInt (3) == 0) continue;
				json.append (separator).append ('"').append (slot).append ("\": \"").append (String.format ("#%06x", random.nextInt (0x1000000))).append ('"');
				separator = ", ";
			}
			json.append ("}, \"density\": \"").append (M3ThemeSpec.DENSITIES.get (random.nextInt (3))).append ("\", \"font\": {\"size\": ").append (6 + random.nextInt (27)).append ("}}");
			M3ThemeCompleter.Theme theme = M3ThemeCompleter.complete (M3ThemeSpec.parse (json.toString ()));
			assertReadable (theme);
			assertTrue (Integer.parseInt (theme.table ().get ("Table.rowHeight")) >= 12, json.toString ());
		}
	}

	@Test
	public void adversarialProposalsAreFixed () {
		M3ThemeCompleter.Theme same = M3ThemeCompleter.complete (M3ThemeSpec.parse ("{\"palette\": {\"background\": \"#777777\", \"foreground\": \"#777777\", \"accent\": \"#787878\"}}"));
		assertReadable (same);
		assertFalse (same.notes ().isEmpty (), "the moves are reported");
		M3ThemeCompleter.Theme contradicted = M3ThemeCompleter.complete (M3ThemeSpec.parse ("{\"dark\": true, \"palette\": {\"background\": \"#fafafa\"}}"));
		assertFalse (contradicted.dark (), "the background decides");
		assertTrue (contradicted.notes ().get (0).startsWith ("dark=true contradicts the background #fafafa"), contradicted.notes ().toString ());
		assertReadable (contradicted);
		M3ThemeCompleter.Theme bare = M3ThemeCompleter.complete (M3ThemeSpec.parse ("{\"dark\": true}"));
		assertTrue (bare.dark ());
		assertEquals ("Generated Dark", bare.name ());
		assertEquals ("Generated Light", M3ThemeCompleter.complete (M3ThemeSpec.parse ("{\"dark\": false}")).name ());
		assertReadable (bare);
		assertEquals ("1,4,1,4", M3ThemeCompleter.complete (M3ThemeSpec.parse ("{\"dark\": true, \"density\": \"compact\"}")).table ().get ("Text.margin"));
	}

	@Test
	public void theSameInputGivesTheSameThemeAndHash () throws Exception {
		M3ThemeSpec spec = M3ThemeSpec.parse (FULL);
		M3ThemeCompleter.Theme a = M3ThemeCompleter.complete (spec), b = M3ThemeCompleter.complete (M3ThemeSpec.parse (FULL.replace ("\n", " ")));
		assertEquals (a.table (), b.table ());
		assertArrayEquals (a.palette (), b.palette ());
		assertEquals (a.hash (), b.hash ());
		byte [] digest = MessageDigest.getInstance ("SHA-256").digest ((spec.canonical () + "\n" + M3ThemeCompleter.VERSION).getBytes (StandardCharsets.UTF_8));
		assertEquals (HexFormat.of ().formatHex (digest), a.hash ());
		assertNotEquals (a.hash (), M3ThemeCompleter.complete (M3ThemeSpec.parse (FULL.replace ("#5e81ac", "#5e81ad"))).hash ());
		assertEquals (M3ThemeExport.lf2 (a), M3ThemeExport.lf2 (b), "byte for byte");
	}

	@Test
	public void theTwinHasTheOtherBrightness () {
		M3ThemeSpec spec = M3ThemeSpec.parse (FULL);
		M3ThemeCompleter.Theme dark = M3ThemeCompleter.complete (spec), light = M3ThemeCompleter.twin (spec);
		assertTrue (dark.dark ());
		assertFalse (light.dark ());
		assertEquals ("Harbour \"Night\" Light", light.name ());
		assertTrue (M3Oklch.lightness (colour (light, "Component.background")) > 0.5, "light in OKLCH");
		assertReadable (light);
		M3ThemeCompleter.Theme back = M3ThemeCompleter.twin (M3ThemeSpec.parse ("{\"dark\": false}"));
		assertTrue (back.dark ());
		assertEquals ("Generated Dark", back.name ());
		assertReadable (back);
	}

	@Test
	public void contrastIsSolvedInOklch () {
		assertEquals (0xFF000000, M3ThemeCompleter.ensure (0xFF000000, 0xFFFFFFFF, 4.5), "already readable");
		int solved = M3ThemeCompleter.ensure (0xFF808080, 0xFF8A8A8A, 4.5);
		assertTrue (M3Palette.contrast (solved, 0xFF8A8A8A) >= 4.5);
		assertEquals (M3Palette.readableOn (0xFF808080), M3ThemeCompleter.ensure (0xFF808080, 0xFF808080, 22), "no lightness reaches 22:1");
		int blue = M3ThemeCompleter.ensure (0xFF2F6FD0, 0xFF1E1F22, 7);
		double [] before = M3Oklch.of (0xFF2F6FD0), after = M3Oklch.of (blue);
		assertTrue (after [0] > before [0], "lighter on a dark background");
		assertEquals (before [2], after [2], 2.0, "the hue is kept");
		int darkOnLight = M3ThemeCompleter.ensure (0xFFE0E0E0, 0xFFFFFFFF, 4.5);
		assertTrue (M3Oklch.lightness (darkOnLight) < M3Oklch.lightness (0xFFE0E0E0), "darker on white");
		int other = M3ThemeCompleter.ensure (0xFFF0F0F0, 0xFF767676, 4.6);
		assertTrue (M3Palette.contrast (other, 0xFF767676) >= 4.6, "the other direction when lighter cannot reach it");
		int start = M3ThemeCompleter.mix (0xFF123456, 0xFFABCDEF, 0);
		for (int shift = 0; shift < 24; shift += 8) assertTrue (Math.abs (((start >> shift) & 0xFF) - ((0xFF123456 >> shift) & 0xFF)) <= 1);
		int end = M3ThemeCompleter.mix (0xFF123456, 0xFFABCDEF, 1);
		for (int shift = 0; shift < 24; shift += 8) assertTrue (Math.abs (((end >> shift) & 0xFF) - ((0xFFABCDEF >> shift) & 0xFF)) <= 1);
	}

	@Test
	public void oklchRoundTripsAndFitsTheGamut () {
		Random random = new Random (7);
		for (int i = 0; i < 500; i++) {
			int argb = 0xFF000000 | random.nextInt (0x1000000);
			double [] lch = M3Oklch.of (argb);
			int back = M3Oklch.argb (lch [0], lch [1], lch [2]);
			for (int shift = 0; shift < 24; shift += 8) assertTrue (Math.abs (((back >> shift) & 0xFF) - ((argb >> shift) & 0xFF)) <= 1, Integer.toHexString (argb));
		}
		int outside = M3Oklch.argb (0.9, 0.4, 150);
		assertEquals (0xFF, outside >>> 24);
		assertEquals (0.9, M3Oklch.lightness (outside), 0.02, "chroma gave way, not lightness");
		assertEquals (0xFFFFFFFF, M3Oklch.argb (1.5, 0, 0), "lightness clamped");
		assertEquals (0xFF000000, M3Oklch.withLightness (0xFF2F6FD0, 0));
	}

	@Test
	public void theThreeFilesReadBack () {
		M3ThemeCompleter.Theme theme = M3ThemeCompleter.complete (M3ThemeSpec.parse (FULL));
		String lf2 = M3ThemeExport.lf2 (theme), flatLaf = M3ThemeExport.flatLaf (theme), intelliJ = M3ThemeExport.intelliJ (theme);
		assertTrue (lf2.startsWith ("# Harbour \"Night\"\n# M3ThemeCompleter " + M3ThemeCompleter.VERSION + " sha256 " + theme.hash () + "\n"), lf2);
		assertTrue (lf2.contains ("Component.focusColor = @accent"), "accent references keep setAccent working");
		assertTrue (flatLaf.contains ("@baseTheme = dark\n") && flatLaf.contains ("TableHeader.background = ") && flatLaf.contains ("Table.alternateRowColor = "), flatLaf);
		assertTrue (intelliJ.contains ("\"author\": \"M3ThemeCompleter " + M3ThemeCompleter.VERSION + " sha256:" + theme.hash () + "\""), intelliJ);
		LookAndFeelDefaults fromLf2 = load (lf2), fromFlatLaf = load (M3ThemeImport.flatLaf (flatLaf).text ()), fromIntelliJ = load (M3ThemeImport.intelliJ (intelliJ).text ());
		for (Map.Entry<String, String> entry : theme.table ().entrySet ()) {
			String key = entry.getKey ();
			assertEquals (entry.getValue (), text (fromLf2, key), "lf2 " + key);
			assertEquals (entry.getValue (), text (fromFlatLaf, key), "FlatLaf " + key);
			if (!key.endsWith (".rowHeight")) assertEquals (entry.getValue (), text (fromIntelliJ, key), "IntelliJ " + key);
		}
	}

	/* a value of a loaded table as the completer writes it */
	static String text (LookAndFeelDefaults table, String key) {
		String formatted = table.format (key);
		return formatted.startsWith ("#") ? "#" + formatted.substring (3) : formatted;
	}
}
