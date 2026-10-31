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
package org.eclipse.swt.tests.junit.m3features;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * The feature-switch registry, headless (no Display, no Device, no natives): the ledger defaults, the property precedence
 * (one widget class over every widget over the alias over the default, most specific class first), the read-only
 * <code>NATIVE_HAS</code> rows, the unshipped rows, the set/isOn kernel with its null-at-defaults bitset and its refusals,
 * discovery, and the generated table against the generated view <code>FEATURES.tsv</code> (the generator's fixed point:
 * <code>-Dm3.features.tsv</code> names the file, default the worktree-relative path).
 */
@SuppressWarnings("restriction")
public class Test_org_eclipse_swt_internal_render_M3Features {
	private static final String WIDGET = "CoverageWidget", PARENT = "CoverageParent";
	private static final int FEATURE = M3Features.FRAME_OVERLAY;
	private static final String GLOBAL = M3Features.key (FEATURE);
	private static final String SPECIFIC = M3Features.key (WIDGET, FEATURE);
	private static final String PARENT_KEY = M3Features.key (PARENT, FEATURE);
	private static final String ALIAS = M3Features.alias (FEATURE);
	private static final String NATIVE_KEY = M3Features.key (M3Features.BUTTON);
	private static final String UNSHIPPED_KEY = M3Features.key (M3Features.CARD);
	private static final String INTEGER_KEY = M3Features.key (Integer.class.getSimpleName (), FEATURE);
	private static final String NUMBER_KEY = M3Features.key (Number.class.getSimpleName (), FEATURE);
	private static final List<String> KEYS = List.of (GLOBAL, SPECIFIC, PARENT_KEY, ALIAS, NATIVE_KEY, UNSHIPPED_KEY, INTEGER_KEY, NUMBER_KEY);
	private static final int FRAME_OVERLAY_BIT = 178;
	private static final int BIT = 0, FEATURE_ID = 1, CONCEPT = 3, FAMILY = 4, STATE = 5, DEFAULT = 6, OWNER = 7, API = 8, ALIAS_COLUMN = 9, SHIPPED = 10;
	private static final int [] DESCRIBED = {BIT, FEATURE_ID, CONCEPT, FAMILY, STATE, DEFAULT, OWNER, API, ALIAS_COLUMN, SHIPPED};
	private final Map<String, String> before = new HashMap<> ();

	@BeforeEach
	public void setUp () {
		KEYS.forEach (key -> {
			before.put (key, System.getProperty (key));
			System.clearProperty (key);
		});
	}

	@AfterEach
	public void tearDown () {
		KEYS.forEach (key -> {
			String value = before.get (key);
			if (value == null) System.clearProperty (key); else System.setProperty (key, value);
		});
	}

	private static boolean overlay (String... widgets) {
		return M3Features.isOn (M3Features.resolve (widgets), FEATURE);
	}

	@Test
	public void test_defaults () {
		long [] bits = M3Features.resolve (WIDGET);
		assertFalse (M3Features.isOn (bits, FEATURE));
		assertTrue (M3Features.isOn (bits, M3Features.BUTTON));
		assertFalse (M3Features.isOn (bits, M3Features.CARD));
		IntStream.of (M3Features.ids ()).forEach (id -> {
			String [] cells = M3Features.describe (id).split ("\t");
			assertEquals ("NATIVE_HAS".equals (cells [4]), M3Features.isOn (bits, id), cells [1] + " on iff NATIVE_HAS");
			assertEquals ("on".equals (cells [5]), M3Features.isOn (bits, id), cells [1] + " on iff default on");
		});
	}

	@Test
	public void test_globalProperty () {
		System.setProperty (GLOBAL, "true");
		assertTrue (overlay (WIDGET));
		System.setProperty (GLOBAL, " TRUE ");
		assertTrue (overlay (WIDGET));
		System.setProperty (GLOBAL, "false");
		assertFalse (overlay (WIDGET));
		System.setProperty (GLOBAL, "yes");
		assertFalse (overlay (WIDGET), "only true switches on");
	}

	@Test
	public void test_widgetOverrideWins () {
		System.setProperty (GLOBAL, "true");
		System.setProperty (SPECIFIC, "false");
		assertFalse (overlay (WIDGET));
		assertTrue (overlay (PARENT), "another class keeps the global value");
		System.setProperty (GLOBAL, "false");
		System.setProperty (SPECIFIC, "true");
		assertTrue (overlay (WIDGET));
	}

	@Test
	public void test_mostSpecificClassWins () {
		System.setProperty (PARENT_KEY, "true");
		assertTrue (overlay (WIDGET, PARENT), "the superclass key applies to the subclass");
		assertFalse (overlay (WIDGET), "but not to a class outside the chain");
		System.setProperty (SPECIFIC, "false");
		assertFalse (overlay (WIDGET, PARENT));
	}

	@Test
	public void test_classChain () {
		System.setProperty (NUMBER_KEY, "true");
		assertTrue (M3Features.isOn (M3Features.resolve (Integer.class), FEATURE));
		assertFalse (M3Features.isOn (M3Features.resolve (String.class), FEATURE));
		System.setProperty (INTEGER_KEY, "false");
		assertFalse (M3Features.isOn (M3Features.resolve (Integer.class), FEATURE));
		assertTrue (M3Features.isOn (M3Features.resolve (Number.class), FEATURE));
	}

	@Test
	public void test_aliasKeepsWorking () {
		assertEquals ("swt.drawn.overlay", ALIAS);
		System.setProperty (ALIAS, "true");
		assertTrue (overlay (WIDGET));
		System.setProperty (GLOBAL, "false");
		assertFalse (overlay (WIDGET), "the switch key beats its alias");
		System.clearProperty (GLOBAL);
		System.setProperty (ALIAS, "false");
		assertFalse (overlay (WIDGET));
		assertEquals ("-", M3Features.alias (M3Features.CARD));
	}

	@Test
	public void test_nativeRowNotSwitchable () {
		System.setProperty (NATIVE_KEY, "false");
		assertTrue (M3Features.isOn (M3Features.resolve (WIDGET), M3Features.BUTTON));
	}

	@Test
	public void test_unshippedRowIgnored () {
		System.setProperty (UNSHIPPED_KEY, "true");
		assertFalse (M3Features.isOn (M3Features.resolve (WIDGET), M3Features.CARD));
	}

	@Test
	public void test_setAndIsOn () {
		long [] defaults = M3Features.resolve (WIDGET);
		long [] bits = M3Features.set (null, defaults, FEATURE, true);
		assertNotNull (bits);
		assertTrue (M3Features.isOn (bits, FEATURE));
		assertFalse (M3Features.isOn (defaults, FEATURE), "the defaults are not written");
		assertTrue (M3Features.isOn (bits, M3Features.BUTTON), "the other switches are copied");
		assertNull (M3Features.set (bits, defaults, FEATURE, false), "back at the defaults: no bitset");
		assertNull (M3Features.set (null, defaults, FEATURE, false), "a default value allocates nothing");
		assertNull (M3Features.set (null, defaults, M3Features.BUTTON, true), "a NATIVE_HAS row on is a no-op");
		assertSame (bits, M3Features.set (bits, defaults, M3Features.BUTTON, true));
	}

	@Test
	public void test_setRefusals () {
		long [] defaults = M3Features.resolve (WIDGET);
		/* SWT.error: ERROR_INVALID_ARGUMENT and ERROR_INVALID_RANGE are IllegalArgumentExceptions, ERROR_NOT_IMPLEMENTED an SWTError */
		assertThrows (IllegalArgumentException.class, () -> M3Features.set (null, defaults, M3Features.BUTTON, false), "a NATIVE_HAS row off");
		SWTError unshipped = assertThrows (SWTError.class, () -> M3Features.set (null, defaults, M3Features.CARD, true));
		assertEquals (SWT.ERROR_NOT_IMPLEMENTED, unshipped.code);
		assertThrows (IllegalArgumentException.class, () -> M3Features.describe (-1));
		assertThrows (IllegalArgumentException.class, () -> M3Features.isOn (defaults, M3Features.COUNT));
		assertThrows (IllegalArgumentException.class, () -> M3Features.key (M3Features.COUNT));
	}

	@Test
	public void test_discovery () {
		assertEquals (M3Features.COUNT, M3Features.ids ().length);
		assertEquals (0, M3Features.CARD, "bit index = ledger row order, from the first row");
		assertEquals (FRAME_OVERLAY_BIT, FEATURE, "append-only: the first switch keeps its bit");
		assertTrue (M3Features.describe (FEATURE).startsWith (FRAME_OVERLAY_BIT + "\tframe-overlay\t"));
		assertEquals (OptionalInt.of (FEATURE), M3Features.id ("frame-overlay"));
		assertTrue (M3Features.id ("no-such-feature").isEmpty ());
		assertEquals ("swt.widget.feature.frame-overlay", GLOBAL);
		assertEquals ("swt.widget.feature.CoverageWidget.frame-overlay", SPECIFIC);
		String [] lines = M3Features.table ().split ("\n");
		assertEquals (M3Features.COUNT + 1, lines.length);
		assertEquals (M3Features.TABLE_HEADER, lines [0]);
		assertEquals (M3Features.describe (FEATURE), lines [FEATURE + 1]);
	}

	@Test
	public void test_generatedTableMatchesGeneratedView () throws IOException {
		Path view = Path.of (System.getProperty ("m3.features.tsv", "docs/m3/feature-switches/FEATURES.tsv"));
		Assumptions.assumeTrue (Files.exists (view), "FEATURES.tsv of the worktree: " + view.toAbsolutePath ());
		List<String> lines = Files.readAllLines (view, StandardCharsets.UTF_8);
		assertEquals (M3Features.COUNT + 1, lines.size (), "one row per feature");
		IntStream.of (M3Features.ids ()).forEach (id -> {
			String [] cells = lines.get (id + 1).split ("\t", -1);
			String described = IntStream.of (DESCRIBED).mapToObj (column -> cells [column]).collect (Collectors.joining ("\t"));
			assertEquals (described, M3Features.describe (id), "row " + id);
			assertEquals (id, constant (cells [2]), cells [2] + " = bit " + id);
		});
	}

	private static int constant (String name) {
		try {
			return M3FeatureIds.class.getField (name).getInt (null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError ("generated constant " + name, e);
		}
	}
}
