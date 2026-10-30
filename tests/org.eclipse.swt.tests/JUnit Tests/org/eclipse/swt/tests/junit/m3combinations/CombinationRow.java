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
package org.eclipse.swt.tests.junit.m3combinations;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.*;

/**
 * One row of the generated combination matrix (<code>tools/m3-combinations/COMBINATION_MATRIX.tsv</code>).
 */
public record CombinationRow (String id, String widget, String mode, String style, String feature, String api, String op, int count, String theme, String language, String env) {
	/** The matrix file: <code>-Dm3.combinations.matrix</code>, else the checked-in TSV relative to the repository root. */
	public static Path matrixFile () {
		String file = System.getProperty ("m3.combinations.matrix");
		return Paths.get (file != null ? file : "tests/org.eclipse.swt.tests/tools/m3-combinations/COMBINATION_MATRIX.tsv");
	}

	/** Every row of a matrix file (columns by header name, so new factors do not shift old ones). */
	public static List<CombinationRow> load (Path file) throws IOException {
		List<String> lines = Files.readAllLines (file, StandardCharsets.UTF_8);
		List<String> header = Arrays.asList (lines.get (0).split ("\t", -1));
		return lines.stream ().skip (1).filter (l -> !l.isBlank ()).map (l -> of (header, l.split ("\t", -1))).collect (Collectors.toList ());
	}

	static CombinationRow of (List<String> header, String [] cells) {
		java.util.function.Function<String, String> at = name -> header.contains (name) ? cells [header.indexOf (name)] : "none";
		return new CombinationRow (at.apply ("row"), at.apply ("widget"), at.apply ("mode"), at.apply ("style"), at.apply ("feature"), at.apply ("api"),
			at.apply ("op"), Integer.parseInt (at.apply ("count")), at.apply ("theme"), at.apply ("language"), at.apply ("env"));
	}

	/** The SWT style bits of the row's style column (names joined by <code>|</code>). */
	public int styleBits () {
		int bits = 0;
		for (String name : style.split ("\\|")) bits |= bit (name);
		return bits;
	}

	static int bit (String name) {
		return switch (name) {
			case "VIRTUAL" -> SWT.VIRTUAL;
			case "MULTI" -> SWT.MULTI;
			case "CHECK" -> SWT.CHECK;
			case "FULL_SELECTION" -> SWT.FULL_SELECTION;
			case "BORDER" -> SWT.BORDER;
			case "H_SCROLL" -> SWT.H_SCROLL;
			case "V_SCROLL" -> SWT.V_SCROLL;
			case "NO_SCROLL" -> SWT.NO_SCROLL;
			case "NONE" -> SWT.NONE;
			default -> throw new IllegalArgumentException ("unknown style " + name);
		};
	}

	/** True when the row needs the theme or language classes of the theme-language branch. */
	public boolean needsThemeOrLanguage () {
		return !"none".equals (theme) || !"none".equals (language);
	}

	@Override
	public String toString () {
		return String.join (" ", id, widget, mode, style, feature, api, op, String.valueOf (count), theme, language, env);
	}
}
