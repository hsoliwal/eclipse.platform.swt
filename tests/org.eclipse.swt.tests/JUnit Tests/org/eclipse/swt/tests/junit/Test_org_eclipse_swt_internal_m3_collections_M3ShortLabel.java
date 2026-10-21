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

import org.eclipse.swt.internal.m3.collections.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Layout, round trip and canonicity of {@link M3ShortLabel}. */
public class Test_org_eclipse_swt_internal_m3_collections_M3ShortLabel {
	private static final String IDENT = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$";

	/** The layout of the M3 JDK's immediate String owner, written out independently. */
	private static long expected (String text) {
		if (text.length () <= 7) {
			long value = 1L << 61 | (long) text.length () << 56;
			for (int i = 0; i < text.length (); i++) value |= (long) text.charAt (i) << (8 * i);
			return value;
		}
		long digits = 0;
		for (int i = text.length () - 1; i >= 0; i--) digits = digits * 64 + IDENT.indexOf (text.charAt (i)) + 1;
		return 2L << 61 | digits;
	}

	@Test
	void packsLikeTheJdkImmediateOwner () {
		for (String text : new String [] {"a", "Error", "Warning", "12 KB", "café", "line 42", "identifier", "M3String", "abc_$XYZ09", "zzzzzzzzzz"}) {
			long label = M3ShortLabel.pack (text);
			assertEquals (expected (text), label, text);
			assertTrue (M3ShortLabel.isImmediate (label));
			assertFalse (M3ShortLabel.isPoolId (label));
			assertEquals (text.length (), M3ShortLabel.length (label));
			assertEquals (text, M3ShortLabel.toString (label));
			assertEquals (text.hashCode (), M3ShortLabel.hash (label));
			for (int i = 0; i < text.length (); i++) assertEquals (text.charAt (i), M3ShortLabel.charAt (label, i));
			char [] buffer = new char [12];
			assertEquals (text.length (), M3ShortLabel.getChars (label, buffer, 2));
			assertEquals (text, new String (buffer, 2, text.length ()));
			assertTrue (label > 0);
		}
	}

	@Test
	void refusesTextThatNeedsAPoolEntry () {
		assertEquals (M3ShortLabel.EMPTY, M3ShortLabel.pack (""));
		for (String text : new String [] {"अ", "12345678901", "with space", "dot.java!", "éééééééé", "abcdefgĀ"}) {
			assertEquals (M3ShortLabel.NONE, M3ShortLabel.pack (text), text);
		}
		assertFalse (M3ShortLabel.isImmediate (M3ShortLabel.NONE));
		assertFalse (M3ShortLabel.isPoolId (M3ShortLabel.NONE));
		assertFalse (M3ShortLabel.isPoolId (1L << 31));
		assertThrows (IllegalArgumentException.class, () -> M3ShortLabel.poolId (M3ShortLabel.pack ("x")));
		assertThrows (IllegalArgumentException.class, () -> M3ShortLabel.ofPoolId (-1));
		assertEquals (7L, M3ShortLabel.ofPoolId (7));
		assertEquals (7, M3ShortLabel.poolId (7L));
		assertTrue (M3ShortLabel.isPoolId (M3ShortLabel.EMPTY));
		assertThrows (IllegalArgumentException.class, () -> M3ShortLabel.length (5L));
		assertThrows (IllegalArgumentException.class, () -> M3ShortLabel.length (3L << 61));
		assertThrows (IndexOutOfBoundsException.class, () -> M3ShortLabel.charAt (M3ShortLabel.pack ("ab"), 2));
		assertThrows (IndexOutOfBoundsException.class, () -> M3ShortLabel.getChars (M3ShortLabel.pack ("abcdefghij"), new char [9], 0));
	}

	@Test
	void equalTextIsEqualValueAndBack () {
		Random random = new Random (11);
		Map<Long, String> seen = new HashMap<> ();
		for (int i = 0; i < 200_000; i++) {
			int length = 1 + random.nextInt (10);
			StringBuilder text = new StringBuilder ();
			for (int k = 0; k < length; k++) text.append (random.nextInt (4) == 0 ? (char) random.nextInt (256) : IDENT.charAt (random.nextInt (64)));
			long label = M3ShortLabel.pack (text);
			if (label == M3ShortLabel.NONE) continue;
			String previous = seen.putIfAbsent (label, text.toString ());
			if (previous != null) assertEquals (previous, text.toString ());
			assertEquals (text.toString (), M3ShortLabel.toString (label));
		}
		assertTrue (seen.size () > 50_000);
	}
}
