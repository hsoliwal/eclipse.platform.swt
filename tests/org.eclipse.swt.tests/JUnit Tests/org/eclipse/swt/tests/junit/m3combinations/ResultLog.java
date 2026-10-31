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

/**
 * Appends one result line per executed row to <code>-Dm3.combinations.results</code> (TSV) and the full stack of a
 * failing row to <code>stacks/&lt;row&gt;-&lt;mode&gt;.txt</code> next to it. The location of a failure is its first frame
 * in SWT outside the tests and outside <code>SWT.error</code>, else its first test frame.
 */
final class ResultLog {
	static final String HEADER = "row\twidget\tmode\tstyle\tfeature\tapi\top\tcount\ttheme\tlanguage\tenv\tjvm_mode\trole\tstatus\ttheme_language\tleak\tmillis\tfailure\tlocation\tmessage";

	private ResultLog () {
	}

	static synchronized void write (CombinationRow row, String role, String status, String themeLanguage, String leak, long millis, Throwable failure) {
		String file = System.getProperty ("m3.combinations.results");
		if (file == null) return;
		String mode = System.getProperty ("swt.widget.mode", "viewport");
		String [] cause = failure == null || "NOT_RUN".equals (status) && failure.getMessage () == null ? new String [] {"-", "-", "-"} : describe (failure);
		String line = String.join ("\t", row.id (), row.widget (), row.mode (), row.style (), row.feature (), row.api (), row.op (), String.valueOf (row.count ()), row.theme (), row.language (),
			row.env (), mode, role, status, themeLanguage, leak, String.valueOf (millis), cause [0], cause [1], cause [2]);
		try {
			Path path = Paths.get (file);
			if (!Files.exists (path)) Files.writeString (path, HEADER + "\n", StandardCharsets.UTF_8);
			Files.writeString (path, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
			if ("FAIL".equals (status) || "TIMEOUT".equals (status)) stack (path, row.id () + "-" + mode, failure);
		} catch (IOException e) {
			throw new UncheckedIOException (e);
		}
	}

	/** Rows that already ran (PASS, FAIL or TIMEOUT) in a JVM mode and role, for a resumed leg. */
	static Set<String> done (String mode, String role) throws IOException {
		String file = System.getProperty ("m3.combinations.results");
		if (file == null || !Files.exists (Paths.get (file))) return Set.of ();
		Set<String> done = new HashSet<> ();
		for (String line : Files.readAllLines (Paths.get (file), StandardCharsets.UTF_8)) {
			String [] c = line.split ("\t", -1);
			if (c.length > 13 && mode.equals (c [11]) && role.equals (c [12]) && !"NOT_RUN".equals (c [13])) done.add (c [0]);
		}
		return done;
	}

	/** Failure class, location and first message line (tabs flattened, at most 400 characters). */
	static String [] describe (Throwable t) {
		Throwable root = t;
		while (root.getCause () != null && root instanceof AssertionError && root.getCause () != root) root = root.getCause ();
		String message = String.valueOf (t.getMessage ()).replaceAll ("[\t\r\n]+", " | ").strip ();
		return new String [] {root.getClass ().getName (), location (root), message.length () > 400 ? message.substring (0, 400) + "..." : message};
	}

	static String location (Throwable t) {
		StackTraceElement [] stack = t.getStackTrace ();
		Optional<StackTraceElement> swt = Arrays.stream (stack).filter (e -> e.getClassName ().startsWith ("org.eclipse.swt.") && !e.getClassName ().startsWith ("org.eclipse.swt.tests.")
			&& e.getLineNumber () >= 0 && !"org.eclipse.swt.SWT".equals (e.getClassName ())).findFirst ();
		Optional<StackTraceElement> test = Arrays.stream (stack).filter (e -> e.getClassName ().startsWith ("org.eclipse.swt.tests.")).findFirst ();
		return swt.or (() -> test).map (e -> e.getFileName () + ":" + e.getLineNumber () + " (" + e.getClassName () + "." + e.getMethodName () + ")").orElse ("-");
	}

	static void stack (Path results, String name, Throwable failure) throws IOException {
		Path dir = results.resolveSibling ("stacks");
		Files.createDirectories (dir);
		StringWriter out = new StringWriter ();
		failure.printStackTrace (new PrintWriter (out));
		Files.writeString (dir.resolve (name + ".txt"), out.toString (), StandardCharsets.UTF_8);
	}
}
