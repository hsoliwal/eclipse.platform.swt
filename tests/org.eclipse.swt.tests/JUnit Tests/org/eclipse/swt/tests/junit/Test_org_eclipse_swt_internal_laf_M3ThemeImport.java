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
import java.nio.file.*;
import java.util.Map;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Theme import and live reload (docs/m3/LOOK_AND_FEEL.md, LF6, improvement 4): an IntelliJ <code>.theme.json</code>
 * and a FlatLaf properties theme become LF2 tables (with FlatLaf's colour functions), and a watched theme file reloads
 * into the open widgets. Only {@link #aWatchedFileReloadsIntoTheWidgets} needs a display.
 */
public class Test_org_eclipse_swt_internal_laf_M3ThemeImport {
	static final String INTELLIJ = """
		{ "name": "Coverage Dark", "dark": true, "author": "test",
		  "colors": { "primary": "#3c3f41", "accent": "#4a88c7" },
		  "ui": {
		    "*": { "background": "primary", "selectionBackground": "accent", "borderColor": "#ff0000" },
		    "Tree": { "selectionBackground": "#112233", "rowHeight": 30 },
		    "TableHeader": { "background": "202020" },
		    "Table": { "stripeColor": "#010203", "showGrid": true, "border": "1,1,1,1,#ff0000" },
		    "Component": { "arc": 6, "focusWidth": 2.5 },
		    "ProgressBar": { "progressColor": { "os.default": "#00ff00", "os.windows": "#0000ff", "os.mac": "#0000ff", "os.linux": "#0000ff" } },
		    "Button": { "margin": "1,2,3,4", "font": "Arial", "foreground": { "os.mac": "#ffffff" } },
		    "Link": { "activeForeground": "#abcdef" },
		    "Label": { "foreground": "#nothex" }
		  } }
		""";

	static final String FLATLAF = """
		@baseTheme = dark
		@accentColor = #4a88c7
		Component.focusColor = lighten(@accentColor,10%,derived)
		Tree.selectionBackground = rgb(10, 20, 30)
		Table.selectionBackground = hsl(210, 50%, 40%)
		List.selectionBackground = hsla(210, 50%, 40%, 50%)
		Table.gridColor = rgba(255,0,0,50%)
		Tree.lineColor = saturate(#808080, 20%)
		Tree.expandIconColor = desaturate(#ff0000, 100%)
		Table.headerBackground = spin(#ff0000, 120)
		Table.stripeBackground = tint(#000000, 50%)
		Table.headerSeparatorColor = shade(#ffffff, 50%)
		ToolTip.background = fadeout(#ff000080, 10%)
		ToolTip.borderColor = fadein(#ff000080, 10%)
		Text.borderColor = fade(#ff0000, 25%, lazy)
		[light]Button.background = #ffffff
		[dark]Button.background = #000000
		[win]Button.foreground = #111111
		Button.hoverBackground = lighten($Button.background, 10%, relative)
		Text.background = darken($Missing.key, 10%)
		Button.font = bold +1
		Label.foreground = com.formdev.flatlaf.ui.SomeClass
		Text.margin = 2,2,2,2
		Link.foreground = if(@x, #fff, #000)
		Link.hoverForeground = {instance}
		""";

	static LookAndFeelDefaults load (M3ThemeImport.Imported imported) {
		return new LookAndFeelLoader (n -> n.equals ("imported") ? imported.text () : LookAndFeelThemes.text (n), name -> 0x808080).load ("imported");
	}

	@Test
	public void anIntelliJThemeBecomesATable () {
		M3ThemeImport.Imported imported = M3ThemeImport.intelliJ (INTELLIJ);
		assertTrue (imported.text ().startsWith ("# imported: Coverage Dark\n@extends = flatdark\n"), imported.text ());
		LookAndFeelDefaults table = load (imported);
		assertEquals (0xFF3C3F41, table.color ("Table.background", 0), "* background, a named colour");
		assertEquals (0xFF3C3F41, table.color ("Component.background", 0));
		assertEquals (0xFF4A88C7, table.color ("List.selectionBackground", 0), "* selectionBackground");
		assertEquals (0xFF112233, table.color ("Tree.selectionBackground", 0), "the specific key wins over *");
		assertEquals (0xFFFF0000, table.color ("Component.borderColor", 0));
		assertEquals (0xFF202020, table.color ("Table.headerBackground", 0), "TableHeader.background without #");
		assertEquals (0xFF010203, table.color ("Table.stripeBackground", 0), "Table.stripeColor");
		assertTrue (table.bool ("Table.showGrid", false));
		assertEquals (6, table.integer ("Component.arc", 0));
		assertEquals (0xFF0000FF, table.color ("ProgressBar.foreground", 0), "the os value of this platform");
		assertArrayEquals (new int [] {1, 2, 3, 4}, table.insets ("Button.margin"));
		assertEquals (0xFFABCDEF, table.color ("Link.foreground", 0), "Link.activeForeground");
		LookAndFeelDefaults flatdark = new LookAndFeelLoader (LookAndFeelThemes::text, name -> 0x808080).load ("flatdark");
		assertEquals (flatdark.integer ("Tree.rowHeight", -1), table.integer ("Tree.rowHeight", -2), "IntelliJ row heights are ignored");
		assertEquals (3, imported.dropped (), "focusWidth 2.5, Button.font, Label.foreground #nothex");
		assertEquals (imported.keys (), imported.text ().lines ().filter (line -> line.contains (" = ") && !line.startsWith ("@")).count ());
	}

	@Test
	public void aLightThemeAndEmptyParts () {
		M3ThemeImport.Imported light = M3ThemeImport.intelliJ ("{\"ui\": {\"Tree\": {\"foreground\": {\"os.mac\": \"#000000\"}}}, \"colors\": {}}");
		assertEquals ("@extends = flat\n", light.text ());
		assertEquals (0, light.keys ());
		assertEquals ("@extends = flat\n", M3ThemeImport.intelliJ ("{}").text ());
		assertEquals ("@extends = flat\n", M3ThemeImport.intelliJ ("{\"ui\": 3, \"colors\": []}").text ());
		IllegalArgumentException array = assertThrows (IllegalArgumentException.class, () -> M3ThemeImport.intelliJ ("[]"));
		assertEquals ("an IntelliJ theme is a JSON object", array.getMessage ());
	}

	@Test
	public void theJsonReader () {
		String json = "{\"name\": \"A\\u0042\\n\\t\\\\\\\"\\/\\b\\f\\r\", \"dark\": false, \"ui\": {\"Tree\": {\"indent\": -2}}, \"x\": [1, -2, 3.5e1, true, false, null, [], {}]}";
		M3ThemeImport.Imported imported = M3ThemeImport.intelliJ (json);
		assertTrue (imported.text ().startsWith ("# imported: AB\n\t\\\"/\b\f\r\n"), imported.text ());
		assertTrue (imported.text ().contains ("Tree.indent = -2"));
		for (String bad : new String [] {"{", "{\"a\" 1}", "{\"a\":}", "[1,]", "\"abc", "{\"a\":\"\\u12\"}", "{\"a\":\"\\uZZZZ\"}", "{} x", "tru", "-",
			"", "[1 2]", "{1: 2}", "{\"a\":\"x\\", "{\"a\":1,}"}) {
			IllegalArgumentException e = assertThrows (IllegalArgumentException.class, () -> M3ThemeImport.intelliJ (bad), bad);
			assertTrue (e.getMessage ().startsWith ("JSON at offset ") || e.getMessage ().equals ("an IntelliJ theme is a JSON object"), bad + ": " + e.getMessage ());
		}
	}

	@Test
	public void aFlatLafThemeImportsWithItsFunctions () {
		M3ThemeImport.Imported imported = M3ThemeImport.flatLaf (FLATLAF);
		assertTrue (imported.text ().startsWith ("@extends = flatdark\n"), imported.text ());
		LookAndFeelDefaults table = load (imported);
		assertEquals (0xFF0A141E, table.color ("Tree.selectionBackground", 0), "rgb");
		assertEquals (0xFF336699, table.color ("Table.selectionBackground", 0), "hsl (210, 50%, 40%)");
		assertEquals (0x80336699, table.color ("List.selectionBackground", 0), "hsla with 50% alpha");
		assertEquals (0x80FF0000, table.color ("Table.gridColor", 0), "rgba with a percentage alpha");
		int saturated = table.color ("Tree.lineColor", 0);
		assertTrue (((saturated >> 16) & 0xFF) > ((saturated >> 8) & 0xFF) && ((saturated >> 8) & 0xFF) == (saturated & 0xFF), Integer.toHexString (saturated));
		int grey = table.color ("Tree.expandIconColor", 0);
		assertTrue (((grey >> 16) & 0xFF) == ((grey >> 8) & 0xFF) && ((grey >> 8) & 0xFF) == (grey & 0xFF), Integer.toHexString (grey));
		assertEquals (0xFF00FF00, table.color ("Table.headerBackground", 0), "spin red by 120 degrees");
		assertEquals (0xFF808080, table.color ("Table.stripeBackground", 0), "tint");
		assertEquals (0xFF808080, table.color ("Table.headerSeparatorColor", 0), "shade");
		int faded = table.color ("ToolTip.background", 0) >>> 24, fadedIn = table.color ("ToolTip.borderColor", 0) >>> 24;
		assertTrue (faded >= 102 && faded <= 103, "fadeout: " + faded);
		assertTrue (fadedIn >= 153 && fadedIn <= 154, "fadein: " + fadedIn);
		assertEquals (0x40FF0000, table.color ("Text.borderColor", 0), "fade with FlatLaf's lazy option removed");
		assertEquals (0xFF000000, table.color ("Button.background", 0), "[dark] on a dark base");
		assertTrue (table.color ("Component.focusColor", 0) != 0xFF4A88C7, "lighten with the derived option removed");
		assertArrayEquals (new int [] {2, 2, 2, 2}, table.insets ("Text.margin"));
		assertEquals (8, imported.dropped (), "[light], [win], relative, $Missing.key, bold +1, a class name, if, {instance}");
		M3ThemeImport.Imported light = M3ThemeImport.flatLaf ("Tree.selectionBackground = #010203\n[light]Tree.lineColor = #040506\n");
		assertTrue (light.text ().startsWith ("@extends = flat\n"));
		assertEquals (0xFF040506, load (light).color ("Tree.lineColor", 0));
		assertEquals ("@extends = flatdark\n", M3ThemeImport.flatLaf ("@baseTheme = darcula\n").text ());
		IllegalArgumentException circular = assertThrows (IllegalArgumentException.class, () -> M3ThemeImport.flatLaf ("A.x = $A.y\nA.y = $A.x\n"));
		assertTrue (circular.getMessage ().contains ("circular"), circular.getMessage ());
	}

	@Test
	public void theFunctionsCheckTheirArguments () {
		Function<String, String> one = expression -> "A.x = " + expression + "\n";
		for (String bad : new String [] {"rgb(1, 2)", "rgba(1, 2, 3)", "hsl(1, 2)", "hsla(1, 2, 3)", "tint(#fff)", "fadein(#fff)", "spin(#fff)", "rgb(256, 0, 0)", "rgb(-1, 0, 0)"}) {
			IllegalArgumentException e = assertThrows (IllegalArgumentException.class,
				() -> new LookAndFeelLoader (Map.of ("t", one.apply (bad))::get, name -> 0).load ("t"), bad);
			assertNotNull (e.getMessage ());
		}
		LookAndFeelDefaults table = new LookAndFeelLoader (Map.of ("t", "A.x = rgb(100%, 0%, 50%)\nA.y = hsl(-90, 100%, 50%)\nA.z = spin(#ff0000, -120)\n")::get, name -> 0).load ("t");
		assertEquals (0xFFFF0080, table.color ("A.x", 0), "percentages of 255");
		assertEquals (0xFF8000FF, table.color ("A.y", 0), "hue -90 is 270");
		assertEquals (0xFF0000FF, table.color ("A.z", 0), "spin backwards");
	}

	@Test
	public void anImportIsInstalledAsALookAndFeel () {
		M3ThemeImport.install ("test-import-ij", M3ThemeImport.intelliJ (INTELLIJ));
		assertTrue (LookAndFeel.names ().contains ("test-import-ij"));
		int tree = M3UIKeys.intern ("Tree.selectionBackground", M3UIKeys.COLOR);
		assertEquals (0xFF112233, M3LookAndFeel.defaults ("test-import-ij").color (tree, 0));
	}

	@Test
	public void aWatchedFileReloadsIntoTheWidgets () throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		Path directory = Files.createTempDirectory ("m3-theme");
		Path file = directory.resolve ("live.properties"), json = directory.resolve ("live.theme.json");
		Files.writeString (file, "@extends = flat\nTable.selectionBackground = #102030\n", StandardCharsets.UTF_8);
		Files.writeString (json, INTELLIJ, StandardCharsets.UTF_8);
		int selection = M3UIKeys.intern ("Table.selectionBackground", M3UIKeys.COLOR);
		try (M3ThemeWatcher watcher = M3ThemeWatcher.watch (display, file, "test-live"); M3ThemeWatcher other = M3ThemeWatcher.watch (display, json, "test-live-ij")) {
			assertEquals (0xFF112233, M3LookAndFeel.defaults ("test-live-ij").color (M3UIKeys.intern ("Tree.selectionBackground", M3UIKeys.COLOR), 0));
			M3LookAndFeel.setLookAndFeel ("test-live");
			Canvas canvas = new Canvas (shell, SWT.NONE);
			Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe probe = new Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe (new int [] {selection}, M3ComponentUI.NO_KEYS);
			M3LookAndFeel.installUI (canvas, Test_org_eclipse_swt_internal_laf_M3LookAndFeel.Probe.class, probe);
			assertEquals (0xFF102030, probe.defaults.color (selection, 0));
			Files.writeString (file, "@extends = flat\nTable.selectionBackground = #405060\n", StandardCharsets.UTF_8);
			assertTrue (await (display, () -> watcher.reloads () > 0), "reloaded");
			assertEquals (0xFF405060, M3LookAndFeel.getDefaults ().color (selection, 0));
			assertEquals (0xFF405060, probe.defaults.color (selection, 0), "the delegate was installed again with the new table");
			assertTrue (probe.installs >= 2);
			Files.writeString (file, "A.x = $A.x\n", StandardCharsets.UTF_8);
			assertTrue (await (display, () -> watcher.failures () > 0), "a broken file is reported");
			assertEquals (0xFF405060, M3LookAndFeel.getDefaults ().color (selection, 0), "the last good theme stays");
			Files.writeString (directory.resolve ("other.txt"), "unrelated", StandardCharsets.UTF_8);
			assertEquals (0, other.reloads ());
		} finally {
			M3LookAndFeel.reset ();
			shell.dispose ();
		}
		assertEquals (0, M3LookAndFeel.reload ("test-live", "@extends = flat\n"), "no widget uses it any more");
	}

	/* runs the event loop until the condition holds (at most 10 s) */
	static boolean await (Display display, BooleanSupplier condition) {
		long end = System.currentTimeMillis () + 10_000;
		while (!condition.getAsBoolean () && System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) {
				try {
					Thread.sleep (20);
				} catch (InterruptedException e) {
					return false;
				}
			}
		}
		return condition.getAsBoolean ();
	}
}
