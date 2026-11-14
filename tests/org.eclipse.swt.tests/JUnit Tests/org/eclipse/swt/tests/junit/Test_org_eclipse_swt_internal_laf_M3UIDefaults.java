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

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The data layer of the Swing-style look and feel (docs/m3/LOOK_AND_FEEL.md, LF6): interned keys with kinds and
 * <code>Component</code> parents ({@link UIKeys}), the typed lanes of a theme with the developer layer
 * ({@link M3UIDefaults}), the diff of two themes, and the sparse per-widget lane ({@link UIOverrides}). Only
 * {@link #disposedWidgetsLeaveTheLane} needs a display.
 */
public class Test_org_eclipse_swt_internal_laf_M3UIDefaults {
	static final LookAndFeelLoader.SystemColors SYSTEM = name -> 0x808080;

	static final String THEME = """
		Tree.selectionBackground = #102030
		Component.selectionBackground = #405060
		Tree.rowHeight = 18
		Table.showGrid = true
		Button.margin = 1,2,3,4
		""";

	static LookAndFeelDefaults table (String name, String text) {
		return new LookAndFeelLoader (Map.of (name, text)::get, SYSTEM).load (name);
	}

	static int color (String name) {
		return UIKeys.intern (name, UIKeys.COLOR);
	}

	@Test
	public void keysAreInternedOnceWithAKindAndAParent () {
		int tree = color ("Tree.selectionBackground");
		assertEquals (tree, color ("Tree.selectionBackground"));
		assertEquals (tree, UIKeys.id ("Tree.selectionBackground"));
		assertEquals (tree, UIKeys.of ("Tree", "selectionBackground", UIKeys.COLOR));
		assertEquals ("Tree.selectionBackground", UIKeys.name (tree));
		assertEquals (UIKeys.COLOR, UIKeys.kind (tree));
		int component = UIKeys.parent (tree);
		assertEquals ("Component.selectionBackground", UIKeys.name (component));
		assertEquals (-1, UIKeys.parent (component), "a Component key has no parent");
		assertEquals (-1, UIKeys.parent (color ("CoverageNoDot")));
		assertEquals (-1, UIKeys.parent (color ("CoverageTrailing.")));
		assertEquals (-1, UIKeys.parent (color (".coverageLeading")));
		assertEquals (-1, UIKeys.id ("Coverage.neverInterned"));
		IllegalArgumentException kind = assertThrows (IllegalArgumentException.class, () -> UIKeys.intern ("Tree.selectionBackground", UIKeys.INTEGER));
		assertEquals ("Tree.selectionBackground is interned as colour, not integer", kind.getMessage ());
		assertThrows (IllegalArgumentException.class, () -> UIKeys.intern ("Coverage.k", (byte) 0));
		assertThrows (IllegalArgumentException.class, () -> UIKeys.intern ("Coverage.k", (byte) 6));
		UIKeys.intern ("Component.coverageMixed", UIKeys.INTEGER);
		assertEquals (-1, UIKeys.parent (color ("Tree.coverageMixed")), "a parent of another kind is no parent");
		int parentFirst = UIKeys.intern ("Component.coverageShared", UIKeys.BOOLEAN);
		assertEquals (parentFirst, UIKeys.parent (UIKeys.intern ("List.coverageShared", UIKeys.BOOLEAN)));
		assertEquals ("colour integer boolean insets font", String.join (" ", UIKeys.kindName (UIKeys.COLOR), UIKeys.kindName (UIKeys.INTEGER),
			UIKeys.kindName (UIKeys.BOOLEAN), UIKeys.kindName (UIKeys.INSETS), UIKeys.kindName (UIKeys.FONT)));
	}

	@Test
	public void theRegistryGrows () {
		int first = UIKeys.count ();
		int [] ids = new int [700];
		for (int i = 0; i < ids.length; i++) ids [i] = UIKeys.intern ("CoverageGrowth" + i + ".value", UIKeys.INTEGER);
		for (int i = 0; i < ids.length; i++) {
			assertEquals ("CoverageGrowth" + i + ".value", UIKeys.name (ids [i]));
			assertEquals (ids [i], UIKeys.id ("CoverageGrowth" + i + ".value"));
		}
		assertTrue (UIKeys.count () >= first + 700);
	}

	@Test
	public void lanesAnswerTheKeyThenItsParentThenTheFallback () {
		M3UIDefaults defaults = new M3UIDefaults (table ("lanes", THEME), new M3UIDefaults.Lanes ());
		assertEquals ("lanes", defaults.name ());
		assertEquals ("M3UIDefaults {lanes}", defaults.toString ());
		assertEquals (0xFF102030, defaults.color (color ("Tree.selectionBackground"), 0));
		assertEquals (0xFF405060, defaults.color (color ("List.selectionBackground"), 0), "the Component parent");
		assertEquals (7, defaults.color (color ("List.coverageMissing"), 7), "no key and no parent value");
		assertEquals (7, defaults.color (color ("Coverage.noParent"), 7));
		int rowHeight = UIKeys.intern ("Tree.rowHeight", UIKeys.INTEGER);
		assertEquals (18, defaults.integer (rowHeight, 0));
		assertEquals (5, defaults.color (rowHeight, 5), "a key read as another kind answers the fallback");
		assertTrue (defaults.bool (UIKeys.intern ("Table.showGrid", UIKeys.BOOLEAN), false));
		assertFalse (defaults.bool (UIKeys.intern ("Table.coverageFlag", UIKeys.BOOLEAN), false));
		int [] insets = new int [4];
		int margin = UIKeys.intern ("Button.margin", UIKeys.INSETS);
		assertTrue (defaults.insets (margin, insets));
		assertArrayEquals (new int [] {1, 2, 3, 4}, insets);
		assertFalse (defaults.insets (rowHeight, insets), "not insets");
		assertFalse (defaults.insets (UIKeys.intern ("Button.coveragePadding", UIKeys.INSETS), insets));
		assertTrue (defaults.contains (margin));
		assertFalse (defaults.contains (color ("Coverage.noParent")));
		assertEquals (9, defaults.font (UIKeys.intern ("Tree.font", UIKeys.FONT), 9));
	}

	@Test
	public void theDeveloperLayerAnswersFirstAndOutlivesTheTheme () {
		M3UIDefaults.Lanes developer = new M3UIDefaults.Lanes ();
		M3UIDefaults first = new M3UIDefaults (table ("first", THEME), developer);
		int selection = color ("Tree.selectionBackground"), margin = UIKeys.intern ("Button.margin", UIKeys.INSETS);
		int font = UIKeys.intern ("Tree.font", UIKeys.FONT);
		first.put (selection, 0xFFABCDEF);
		first.putInsets (margin, 9, 8, 7, 6);
		first.put (font, 3);
		M3UIDefaults second = new M3UIDefaults (table ("second", THEME + "Tree.selectionBackground = #000000\n"), developer);
		assertEquals (0xFFABCDEF, second.color (selection, 0), "the developer value answers before the theme");
		int [] insets = new int [4];
		assertTrue (second.insets (margin, insets));
		assertArrayEquals (new int [] {9, 8, 7, 6}, insets);
		assertEquals (3, second.font (font, 0));
		assertTrue (developer.has (selection));
		second.remove (selection);
		second.remove (selection);
		assertEquals (0xFF000000, second.color (selection, 0), "the theme value after the removal");
		assertThrows (IllegalArgumentException.class, () -> first.put (margin, 1));
		assertThrows (IllegalArgumentException.class, () -> first.putInsets (selection, 1, 2, 3, 4));
		int far = UIKeys.intern ("CoverageFar" + UIKeys.count () + ".value", UIKeys.COLOR);
		first.put (far, 1);
		assertEquals (1, first.color (far, 0), "the lanes grow to a new key id");
	}

	@Test
	public void theDiffNamesTheKeysAThemeChanges () {
		M3UIDefaults.Lanes developer = new M3UIDefaults.Lanes ();
		M3UIDefaults a = new M3UIDefaults (table ("a", THEME), developer);
		M3UIDefaults b = new M3UIDefaults (table ("b", THEME + "Tree.rowHeight = 20\nButton.margin = 1,2,3,5\nList.coverageOnlyB = #ffffff\n"), developer);
		long [] changed = M3UIDefaults.diff (a, b);
		Set<String> names = new TreeSet<> ();
		for (int key = 0; key < UIKeys.count (); key++) {
			if ((changed [key >>> 6] & (1L << key)) != 0) names.add (UIKeys.name (key));
		}
		assertEquals (new TreeSet<> (Set.of ("Tree.rowHeight", "Button.margin", "List.coverageOnlyB")), names);
		long [] none = M3UIDefaults.diff (a, a);
		assertEquals (0, Arrays.stream (none).filter (word -> word != 0).count ());
	}

	@Test
	public void overridesLiveInOneSparseLane () {
		UIOverrides lane = new UIOverrides ();
		Object first = new Object (), second = new Object ();
		int selection = color ("Tree.selectionBackground"), rowHeight = UIKeys.intern ("Tree.rowHeight", UIKeys.INTEGER);
		int margin = UIKeys.intern ("Button.margin", UIKeys.INSETS);
		assertEquals (UIOverrides.NONE, lane.lookup (first, selection), "an empty lane");
		lane.put (first, selection, 0);
		assertEquals (1L << 32, lane.lookup (first, selection), "a value 0 is found");
		assertEquals (UIOverrides.NONE, lane.lookup (second, selection), "another owner");
		assertEquals (UIOverrides.NONE, lane.lookup (first, rowHeight), "another key");
		lane.put (first, selection, 0xFF112233);
		assertEquals (0xFF112233, (int) lane.lookup (first, selection));
		M3UIDefaults defaults = new M3UIDefaults (table ("over", THEME), new M3UIDefaults.Lanes ());
		assertEquals (0xFF112233, defaults.color (lane, first, selection, 0));
		assertEquals (0xFF102030, defaults.color (lane, second, selection, 0));
		lane.put (second, rowHeight, 33);
		assertEquals (33, defaults.integer (lane, second, rowHeight, 0));
		assertEquals (18, defaults.integer (lane, first, rowHeight, 0));
		int [] insets = new int [4];
		assertFalse (lane.insets (first, margin, insets));
		assertFalse (lane.insets (new Object (), margin, insets));
		lane.putInsets (first, margin, 4, 3, 2, 1);
		assertTrue (lane.insets (first, margin, insets));
		assertArrayEquals (new int [] {4, 3, 2, 1}, insets);
		assertEquals (2, lane.owners ());
		assertEquals (6, lane.size ());
		assertThrows (IllegalArgumentException.class, () -> lane.put (first, margin, 1));
		assertThrows (IllegalArgumentException.class, () -> lane.putInsets (first, selection, 1, 2, 3, 4));
		lane.remove (first, margin);
		lane.remove (first, margin);
		lane.remove (new Object (), margin);
		assertFalse (lane.insets (first, margin, insets));
		lane.remove (first, selection);
		assertEquals (UIOverrides.NONE, lane.lookup (first, selection));
		lane.clear (second);
		lane.clear (second);
		assertEquals (UIOverrides.NONE, lane.lookup (second, rowHeight));
		assertEquals (1, lane.owners (), "the first owner keeps its slot");
		assertEquals (0, lane.size ());
		assertTrue (lane.toString ().contains ("1 owners"), lane.toString ());
		UIOverrides empty = new UIOverrides ();
		empty.remove (first, selection);
		empty.clear (first);
		assertFalse (empty.insets (first, margin, insets));
	}

	@Test
	public void theLaneGrowsAndKeepsEveryOwnerApart () {
		UIOverrides lane = new UIOverrides ();
		int [] keys = new int [3];
		for (int k = 0; k < keys.length; k++) keys [k] = UIKeys.intern ("CoverageLane.value" + k, UIKeys.INTEGER);
		Object [] owners = new Object [1000];
		for (int i = 0; i < owners.length; i++) {
			owners [i] = new Object ();
			for (int k = 0; k < keys.length; k++) lane.put (owners [i], keys [k], i * 10 + k);
		}
		for (int i = 0; i < owners.length; i += 2) lane.clear (owners [i]);
		for (int i = 0; i < owners.length; i++) {
			for (int k = 0; k < keys.length; k++) {
				long hit = lane.lookup (owners [i], keys [k]);
				if (i % 2 == 0) assertEquals (UIOverrides.NONE, hit);
				else assertEquals (i * 10 + k, (int) hit);
			}
		}
		for (int i = 0; i < owners.length; i += 2) lane.put (owners [i], keys [0], -i);
		for (int i = 0; i < owners.length; i += 2) assertEquals (-i, (int) lane.lookup (owners [i], keys [0]));
		assertEquals (1000, lane.owners ());
		assertEquals (500 * 3 + 500, lane.size ());
	}

	@Test
	public void disposedWidgetsLeaveTheLane () {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		try {
			UIOverrides lane = UIOverrides.of (display);
			assertSame (lane, UIOverrides.of (display));
			Label label = new Label (shell, SWT.NONE);
			int selection = color ("Tree.selectionBackground");
			lane.put (label, selection, 0xFF010203);
			lane.put (label, UIKeys.intern ("Tree.rowHeight", UIKeys.INTEGER), 3);
			int owners = lane.owners ();
			label.dispose ();
			assertEquals (owners - 1, lane.owners (), "the dispose listener cleared the label");
			assertEquals (UIOverrides.NONE, lane.lookup (label, selection));
			display.setData (UIOverrides.class.getName (), null);
			Label other = new Label (shell, SWT.NONE);
			lane.put (other, selection, 1);
			other.dispose ();
			assertEquals (1, (int) lane.lookup (other, selection), "a lane no longer kept by the display is left alone");
		} finally {
			display.setData (UIOverrides.class.getName (), null);
			shell.dispose ();
		}
	}
}
