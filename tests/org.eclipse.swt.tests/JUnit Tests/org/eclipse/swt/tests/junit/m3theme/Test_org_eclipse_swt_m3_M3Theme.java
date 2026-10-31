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
package org.eclipse.swt.tests.junit.m3theme;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/**
 * Headless tests of the theme table (docs/m3/theme-language/README.md): slot completeness and names, WCAG AA contrast of the
 * built-in themes, the builder, the theme file (properties and JSON, extends, the --vs- prefix, every error), the round trip
 * and the look and feel table the drawn planes read.
 */
public class Test_org_eclipse_swt_m3_M3Theme {

	@Test
	public void test_slotsAreCompleteNamedAndKinded () {
		Set<String> names = IntStream.range (0, M3ThemeSlot.COUNT).mapToObj (M3ThemeSlot::name).collect (Collectors.toSet ());
		assertEquals (M3ThemeSlot.COUNT, names.size (), "slot names are unique");
		IntStream.range (0, M3ThemeSlot.COUNT).forEach (slot -> assertEquals (slot, M3ThemeSlot.id (M3ThemeSlot.name (slot))));
		assertEquals (M3ThemeSlot.BG, M3ThemeSlot.id ("--vs-bg"));
		assertEquals (M3ThemeSlot.ACCENT_2, M3ThemeSlot.id (" --VS-Accent-2 "));
		assertEquals (-1, M3ThemeSlot.id ("nope"));
		assertEquals (9, IntStream.rangeClosed (M3ThemeSlot.BG, M3ThemeSlot.PAD).count (), "the nine web slots");
		assertEquals (M3ThemeSlot.METRIC, M3ThemeSlot.kind (M3ThemeSlot.RADIUS));
		assertEquals (M3ThemeSlot.METRIC, M3ThemeSlot.kind (M3ThemeSlot.ROW_HEIGHT));
		assertEquals (M3ThemeSlot.COLOR, M3ThemeSlot.kind (M3ThemeSlot.SUCCESS));
		assertThrows (IllegalArgumentException.class, () -> M3ThemeSlot.name (M3ThemeSlot.COUNT));
		assertThrows (IllegalArgumentException.class, () -> M3ThemeSlot.kind (-1));
	}

	@Test
	public void test_systemColorRoles () {
		IntStream.rangeClosed (M3ThemeSlot.WIDGET_BACKGROUND, M3ThemeSlot.TEXT_DISABLED_BACKGROUND)
			.forEach (slot -> assertEquals (slot, M3ThemeSlot.ofSystemColor (M3ThemeSlot.systemColor (slot))));
		assertEquals (M3ThemeSlot.LIST_SELECTION, M3ThemeSlot.ofSystemColor (SWT.COLOR_LIST_SELECTION));
		assertEquals (-1, M3ThemeSlot.ofSystemColor (SWT.COLOR_RED));
		assertEquals (-1, M3ThemeSlot.systemColor (M3ThemeSlot.BG));
		assertEquals (-1, M3ThemeSlot.systemColor (M3ThemeSlot.ROW_STRIPE));
	}

	@Test
	public void test_builtInsDefineEverySlotAndReachWcagAA () {
		for (M3Theme theme : M3Theme.builtIns ()) {
			int [] slots = theme.slots ();
			assertEquals (M3ThemeSlot.COUNT, slots.length, theme.name ());
			IntStream.range (0, slots.length).filter (s -> M3ThemeSlot.kind (s) == M3ThemeSlot.COLOR)
				.forEach (s -> assertEquals (0xFF, slots [s] >>> 24, theme.name () + " " + M3ThemeSlot.name (s) + " is opaque"));
			assertEquals (List.of (), theme.contrastFailures (), theme.name ());
			for (int [] pair : M3ThemeSlot.TEXT_PAIRS) assertTrue (theme.contrast (pair [0], pair [1]) >= M3Theme.TEXT_CONTRAST);
		}
		assertFalse (M3Theme.LIGHT.dark ());
		assertTrue (M3Theme.DARK.dark ());
		assertTrue (M3Theme.HIGH_CONTRAST.dark ());
		assertEquals (2, M3Theme.HIGH_CONTRAST.get (M3ThemeSlot.FOCUS_WIDTH));
		assertTrue (M3Theme.HIGH_CONTRAST.contrast (M3ThemeSlot.FG, M3ThemeSlot.BG) >= 21.0 - 1e-9);
		assertEquals (-1, M3Theme.LIGHT.get (M3ThemeSlot.ROW_HEIGHT));
		assertEquals ("", M3Theme.LIGHT.font ());
	}

	@Test
	public void test_builder () {
		M3Theme ocean = M3Theme.DARK.derive (" Ocean ").set (M3ThemeSlot.ACCENT, 0xFF3366FF).set ("--vs-radius", 4).set ("row-height", 22)
			.font ("Segoe UI").dark (true).build ();
		assertEquals ("ocean", ocean.name ());
		assertEquals (0xFF3366FF, ocean.get (M3ThemeSlot.ACCENT));
		assertEquals (4, ocean.get (M3ThemeSlot.RADIUS));
		assertEquals (22, ocean.get (M3ThemeSlot.ROW_HEIGHT));
		assertEquals ("Segoe UI", ocean.font ());
		assertEquals (M3Theme.DARK.get (M3ThemeSlot.BG), ocean.get (M3ThemeSlot.BG), "every other slot from the base");
		assertEquals ("", M3Theme.builder ("x", M3Theme.LIGHT).font (null).build ().font ());
		int [] copy = ocean.slots ();
		copy [0] = 0;
		assertNotEquals (0, ocean.get (0), "slots () is a copy");
		M3Theme.Builder builder = M3Theme.builder ("b", M3Theme.LIGHT);
		assertThrows (IllegalArgumentException.class, () -> builder.set (M3ThemeSlot.ROW_HEIGHT, -2));
		assertThrows (IllegalArgumentException.class, () -> builder.set (M3ThemeSlot.FONT_STYLE, 4));
		assertThrows (IllegalArgumentException.class, () -> builder.set (M3ThemeSlot.FONT_HEIGHT, 201));
		assertThrows (IllegalArgumentException.class, () -> builder.set (M3ThemeSlot.PAD, 65));
		assertThrows (IllegalArgumentException.class, () -> builder.set ("unknown", 1));
		assertThrows (IllegalArgumentException.class, () -> builder.set (M3ThemeSlot.COUNT, 1));
		assertThrows (IllegalArgumentException.class, () -> M3Theme.builder (" ", M3Theme.LIGHT));
		assertThrows (IllegalArgumentException.class, () -> M3Theme.builder (null, M3Theme.LIGHT));
		assertThrows (IllegalArgumentException.class, () -> M3Theme.builder ("x", null));
	}

	@Test
	public void test_parseProperties () {
		M3Theme theme = M3Theme.parse ("""
			# a user theme
			! also a comment
			name = Sea
			extends = dark
			dark = TRUE
			font = Inter
			--vs-accent = #36f
			selection = #3366ffcc
			error : #fa0
			row-height = 20
			font-style = 1
			""");
		assertEquals ("sea", theme.name ());
		assertTrue (theme.dark ());
		assertEquals ("Inter", theme.font ());
		assertEquals (0xFF3366FF, theme.get (M3ThemeSlot.ACCENT));
		assertEquals (0xCC3366FF, theme.get (M3ThemeSlot.SELECTION));
		assertEquals (0xFFFFAA00, theme.get (M3ThemeSlot.ERROR));
		assertEquals (20, theme.get (M3ThemeSlot.ROW_HEIGHT));
		assertEquals (SWT.BOLD, theme.get (M3ThemeSlot.FONT_STYLE));
		assertEquals (M3Theme.DARK.get (M3ThemeSlot.HOVER), theme.get (M3ThemeSlot.HOVER));
		assertEquals (M3Theme.LIGHT.get (M3ThemeSlot.BG), M3Theme.parse ("name = l\n").get (M3ThemeSlot.BG), "extends light by default");
		assertEquals (0x88112233, M3Theme.parse ("name = a\nbg = #1238\n").get (M3ThemeSlot.BG), "#rgba");
	}

	@Test
	public void test_parseJson () {
		M3Theme theme = M3Theme.parse ("{\"name\": \"j\", \"extends\": \"high-contrast\", \"dark\": false, \"slots\": {\"bg\": \"#102030\", \"indent\": 12}, \"pad\": 4}");
		assertEquals ("j", theme.name ());
		assertFalse (theme.dark ());
		assertEquals (0xFF102030, theme.get (M3ThemeSlot.BG));
		assertEquals (12, theme.get (M3ThemeSlot.INDENT));
		assertEquals (4, theme.get (M3ThemeSlot.PAD));
		assertEquals (M3Theme.HIGH_CONTRAST.get (M3ThemeSlot.SELECTION), theme.get (M3ThemeSlot.SELECTION));
		assertThrows (IllegalArgumentException.class, () -> M3Theme.parse ("{\"name\": \"j\", \"bg\": [1]}"));
		assertThrows (IllegalArgumentException.class, () -> M3Theme.parse ("{\"name\": \"j\", \"slots\": {\"bg\": null}}"));
		assertThrows (IllegalArgumentException.class, () -> M3Theme.parse ("{\"name\": "));
		assertEquals (Map.of ("a", "1", "b", "true"), M3ThemeTable.read ("{\"a\": 1, \"b\": true}"));
	}

	@Test
	public void test_parseErrorsNameTheCause () {
		assertMessage ("without a name", "bg = #fff\n");
		assertMessage ("without a name", "name = \n");
		assertMessage ("unknown theme 'nope'", "name = x\nextends = nope\n");
		assertMessage ("unknown theme key 'colour'", "name = x\ncolour = #fff\n");
		assertMessage ("is not a colour", "name = x\nbg = red\n");
		assertMessage ("is not a colour", "name = x\nbg = #12345\n");
		assertMessage ("is not a colour", "name = x\nbg = #ggg\n");
		assertMessage ("is not an integer", "name = x\nradius = big\n");
		assertMessage ("out of range", "name = x\nradius = 99\n");
		assertMessage ("dark must be true or false", "name = x\ndark = yes\n");
		assertMessage ("without key = value", "name = x\njust text\n");
		assertMessage ("without key = value", "= #fff\n");
		assertThrows (NullPointerException.class, () -> M3ThemeTable.read (null));
	}

	private static void assertMessage (String expected, String text) {
		IllegalArgumentException e = assertThrows (IllegalArgumentException.class, () -> M3Theme.parse (text));
		assertTrue (e.getMessage ().contains (expected), e.getMessage ());
	}

	@Test
	public void test_textRoundTripAndEquality () {
		M3Theme custom = M3Theme.DARK.derive ("rt").set (M3ThemeSlot.SELECTION, 0x803366FF).font ("Inter").build ();
		for (M3Theme theme : List.of (M3Theme.LIGHT, M3Theme.DARK, M3Theme.HIGH_CONTRAST, custom)) {
			M3Theme back = M3Theme.parse (theme.toText ());
			assertEquals (theme, back, theme.name ());
			assertEquals (theme.hashCode (), back.hashCode ());
		}
		assertNotEquals (M3Theme.LIGHT, M3Theme.DARK);
		assertNotEquals (M3Theme.LIGHT, M3Theme.LIGHT.derive ("light").dark (true).build ());
		assertNotEquals (M3Theme.LIGHT, M3Theme.LIGHT.derive ("light").font ("x").build ());
		assertNotEquals (M3Theme.LIGHT, M3Theme.LIGHT.derive ("light").set (0, 0).build ());
		assertNotEquals (M3Theme.LIGHT, M3Theme.LIGHT.derive ("other").build ());
		assertNotEquals (M3Theme.LIGHT, "light");
		assertEquals ("M3Theme dark", M3Theme.DARK.toString ());
	}

	@Test
	public void test_contrastFailuresOfABadTheme () {
		M3Theme bad = M3Theme.LIGHT.derive ("bad").set (M3ThemeSlot.FG, 0xFFF0F0F0).set (M3ThemeSlot.FOCUS_RING, 0xFFFFFFFF).build ();
		List<String> failures = bad.contrastFailures ();
		assertTrue (failures.stream ().anyMatch (f -> f.startsWith ("fg/bg ")), failures.toString ());
		assertTrue (failures.stream ().anyMatch (f -> f.startsWith ("focus-ring/list-background ")), failures.toString ());
	}

	@Test
	public void test_lookAndFeelTableIsReadByTheDrawnPlanes () {
		for (M3Theme theme : M3Theme.builtIns ()) {
			String name = "m3-test-" + theme.name ();
			M3LookAndFeel.register (name, theme.toLookAndFeelTable (), null);
			M3UIDefaults defaults = M3LookAndFeel.defaults (name);
			assertEquals (theme.get (M3ThemeSlot.SELECTION), color (defaults, "Tree.selectionBackground"), name);
			assertEquals (theme.get (M3ThemeSlot.ROW_STRIPE), color (defaults, "Table.stripeBackground"), name);
			assertEquals (theme.get (M3ThemeSlot.HOVER), color (defaults, "Component.hoverBackground"), name);
			assertEquals (theme.get (M3ThemeSlot.GRIDLINE), color (defaults, "Table.gridColor"), name);
			assertEquals (theme.get (M3ThemeSlot.HEADER_BACKGROUND), color (defaults, "Table.headerBackground"), name);
			assertEquals (theme.get (M3ThemeSlot.FOCUS_RING), color (defaults, "Component.focusColor"), name);
			assertEquals (theme.get (M3ThemeSlot.LIST_BACKGROUND), color (defaults, "List.background"), name);
			assertTrue (theme.toLookAndFeelTable ().contains (theme.dark () ? "@extends = flatdark" : "@extends = flat\n"));
		}
		String sized = M3Theme.LIGHT.derive ("sized").set (M3ThemeSlot.ROW_HEIGHT, 24).set (M3ThemeSlot.INDENT, 10).build ().toLookAndFeelTable ();
		assertTrue (sized.contains ("Tree.rowHeight = 24") && sized.contains ("Tree.indent = 10") && sized.contains ("List.rowHeight = 24"));
		assertFalse (M3Theme.LIGHT.toLookAndFeelTable ().contains ("rowHeight"));
	}

	private static int color (M3UIDefaults defaults, String key) {
		return defaults.color (M3UIKeys.intern (key, M3UIKeys.COLOR), 0);
	}

	@Test
	public void test_moreFileEdges () {
		assertThrows (IllegalArgumentException.class, () -> M3ThemeTable.read ("[1]"));
		assertEquals (Map.of ("slots", "x"), M3ThemeTable.read ("{\"slots\": \"x\"}"));
		assertMessage ("is not a colour", "name = x\nbg = #1234567\n");
		assertMessage ("is not a colour", "name = x\nbg = #\n");
		assertMessage ("is not a colour", "name = x\nbg = 123456\n");
		assertEquals (M3Theme.LIGHT.get (M3ThemeSlot.BG), M3Theme.parse ("name = n\nfont = \n").get (M3ThemeSlot.BG));
	}
}
