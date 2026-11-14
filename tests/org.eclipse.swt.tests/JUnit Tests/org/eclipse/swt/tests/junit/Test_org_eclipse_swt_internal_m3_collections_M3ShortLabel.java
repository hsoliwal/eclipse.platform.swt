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

import org.eclipse.swt.internal.residency.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Layout, round trip and canonicity of {@link ShortLabel}. */
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
			long label = ShortLabel.pack (text);
			assertEquals (expected (text), label, text);
			assertTrue (ShortLabel.isImmediate (label));
			assertFalse (ShortLabel.isPoolId (label));
			assertEquals (text.length (), ShortLabel.length (label));
			assertEquals (text, ShortLabel.toString (label));
			assertEquals (text.hashCode (), ShortLabel.hash (label));
			for (int i = 0; i < text.length (); i++) assertEquals (text.charAt (i), ShortLabel.charAt (label, i));
			char [] buffer = new char [12];
			assertEquals (text.length (), ShortLabel.getChars (label, buffer, 2));
			assertEquals (text, new String (buffer, 2, text.length ()));
			assertTrue (label > 0);
		}
	}

	@Test
	void refusesTextThatNeedsAPoolEntry () {
		assertEquals (ShortLabel.EMPTY, ShortLabel.pack (""));
		for (String text : new String [] {"अ", "12345678901", "with space", "dot.java!", "éééééééé", "abcdefgĀ"}) {
			assertEquals (ShortLabel.NONE, ShortLabel.pack (text), text);
		}
		assertFalse (ShortLabel.isImmediate (ShortLabel.NONE));
		assertFalse (ShortLabel.isPoolId (ShortLabel.NONE));
		assertFalse (ShortLabel.isPoolId (1L << 31));
		assertThrows (IllegalArgumentException.class, () -> ShortLabel.poolId (ShortLabel.pack ("x")));
		assertThrows (IllegalArgumentException.class, () -> ShortLabel.ofPoolId (-1));
		assertEquals (7L, ShortLabel.ofPoolId (7));
		assertEquals (7, ShortLabel.poolId (7L));
		assertTrue (ShortLabel.isPoolId (ShortLabel.EMPTY));
		assertThrows (IllegalArgumentException.class, () -> ShortLabel.length (5L));
		assertThrows (IllegalArgumentException.class, () -> ShortLabel.length (3L << 61));
		assertThrows (IndexOutOfBoundsException.class, () -> ShortLabel.charAt (ShortLabel.pack ("ab"), 2));
		assertThrows (IndexOutOfBoundsException.class, () -> ShortLabel.getChars (ShortLabel.pack ("abcdefghij"), new char [9], 0));
	}

	@Test
	void equalTextIsEqualValueAndBack () {
		Random random = new Random (11);
		Map<Long, String> seen = new HashMap<> ();
		for (int i = 0; i < 200_000; i++) {
			int length = 1 + random.nextInt (10);
			StringBuilder text = new StringBuilder ();
			for (int k = 0; k < length; k++) text.append (random.nextInt (4) == 0 ? (char) random.nextInt (256) : IDENT.charAt (random.nextInt (64)));
			long label = ShortLabel.pack (text);
			if (label == ShortLabel.NONE) continue;
			String previous = seen.putIfAbsent (label, text.toString ());
			if (previous != null) assertEquals (previous, text.toString ());
			assertEquals (text.toString (), ShortLabel.toString (label));
		}
		assertTrue (seen.size () > 50_000);
	}
}
