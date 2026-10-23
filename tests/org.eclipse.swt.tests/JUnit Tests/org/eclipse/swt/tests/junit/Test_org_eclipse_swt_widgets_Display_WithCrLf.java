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

import java.lang.reflect.*;
import java.util.*;

import org.eclipse.swt.*;
import org.junit.jupiter.api.*;

/**
 * Optimization leaf X1 (docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): the counting line delimiter conversion of
 * the Text viewport gives the stock result for every string (seeded random strings of carriage returns, line feeds
 * and letters, and the edge cases), and the input itself when there is no bare line feed. Win32 only.
 */
public class Test_org_eclipse_swt_widgets_Display_WithCrLf {
	static Method counted;

	@BeforeAll
	public static void find () throws ReflectiveOperationException {
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "the conversion is win32's");
		counted = Class.forName ("org.eclipse.swt.widgets.Display").getDeclaredMethod ("withCrLfCounted", String.class);
		counted.setAccessible (true);
	}

	/* the stock algorithm of Display.withCrLf (String), as the reference */
	static String stock (String string) {
		int i = 0;
		int length = string.length ();
		StringBuilder result = new StringBuilder (length);
		while (i < length) {
			int j = string.indexOf ('\n', i);
			if (j > 0 && string.charAt (j - 1) == '\r') {
				result.append (string.substring (i, j + 1));
				i = j + 1;
			} else {
				if (j == -1) j = length;
				result.append (string.substring (i, j));
				if ((i = j) < length) {
					result.append ("\r\n");
					i++;
				}
			}
		}
		return string.length () == result.length () ? string : result.toString ();
	}

	static String counted (String string) throws ReflectiveOperationException {
		return (String) counted.invoke (null, string);
	}

	@Test
	public void edgeCases () throws ReflectiveOperationException {
		for (String string : new String [] {"", "a", "\n", "\r", "\r\n", "\n\n", "\r\r\n", "\n\r", "a\n", "\na", "a\r\nb\nc", "\n\r\n\n", "line 1\nline 2\n"}) {
			assertEquals (stock (string), counted (string), () -> "input " + string.replace ("\r", "\\r").replace ("\n", "\\n"));
		}
		String unchanged = "a\r\nb\r\n";
		assertSame (unchanged, counted (unchanged), "no bare line feed: the input itself, as stock");
	}

	@Test
	public void randomStringsMatchStock () throws ReflectiveOperationException {
		Random random = new Random (20261020);
		char [] alphabet = {'\r', '\n', 'a', 'b'};
		for (int n = 0; n < 20000; n++) {
			char [] chars = new char [random.nextInt (40)];
			for (int i = 0; i < chars.length; i++) chars [i] = alphabet [random.nextInt (alphabet.length)];
			String string = new String (chars);
			assertEquals (stock (string), counted (string), "seed 20261020 string " + n);
		}
	}
}
