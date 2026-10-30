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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import org.eclipse.swt.tests.m3.tools.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

/**
 * Headless tests of the IPOG covering-array generator (tools/M3CoveringArray.java): the checked-in matrix is a covering
 * array of the checked-in tables (every allowed pair, every allowed critical triple, no forbidden pair), it is the
 * generator's fixed point, the output is deterministic, and the selectors, constraints and derived values behave as
 * documented on a small synthetic repository. Repository root: <code>-Dm3.repo.root</code> (default: the working
 * directory).
 */
public class Test_M3CoveringArray {
	static final Path ROOT = Paths.get (System.getProperty ("m3.repo.root", "."));

	static M3CoveringArray generated () throws IOException {
		return new M3CoveringArray (ROOT).load ().generate ();
	}

	@Test
	void checkedInMatrixIsACoveringArray () throws IOException {
		M3CoveringArray g = generated ();
		assertEquals (List.of (), g.uncoveredPairs ());
		assertEquals (0, g.uncoveredCriticalTriples ());
		assertEquals (0, g.rowsWithForbiddenPairs ());
		assertTrue (g.summary ().contains ("uncovered pairs 0"), g.summary ());
	}

	@Test
	void checkedInFilesAreTheFixedPoint () throws IOException {
		M3CoveringArray g = generated ();
		assertEquals (Files.readString (ROOT.resolve (M3CoveringArray.DIR + "/COMBINATION_MATRIX.tsv"), StandardCharsets.UTF_8), g.matrix ());
		assertEquals (Files.readString (ROOT.resolve (M3CoveringArray.DIR + "/API_WIDGETS.tsv"), StandardCharsets.UTF_8), g.apiWidgetsTable ());
		assertFalse (g.write (true));
	}

	@Test
	void outputIsDeterministic () throws IOException {
		assertEquals (generated ().matrix (), generated ().matrix ());
	}

	@Test
	void matrixRowsLoadAndModelRowsAreHeadless () throws IOException {
		List<CombinationRow> rows = CombinationRow.load (ROOT.resolve (M3CoveringArray.DIR + "/COMBINATION_MATRIX.tsv"));
		assertEquals (generated ().rows ().size (), rows.size ());
		for (CombinationRow row : rows) {
			assertEquals ("Model".equals (row.widget ()) ? "headless" : "display", row.env (), row.toString ());
			assertTrue (ApiCalls.has (row.api ()), "the matrix has an API without a call: " + row.api ());
			row.styleBits ();
		}
	}

	@Test
	void criticalFactorsComeFirst () throws IOException {
		M3CoveringArray g = generated ();
		assertEquals (List.of ("widget", "mode", "style"), g.names ().subList (0, 3));
		assertTrue (g.values ().get (g.names ().indexOf ("api")).contains ("M3-RANGE-SELECT"));
		assertTrue (g.values ().get (g.names ().indexOf ("feature")).contains ("frame-overlay"));
	}

	/** A small repository: three factors (two critical), one constraint, one derived feature list, a two-row API map. */
	static Path synthetic (Path dir) throws IOException {
		Path tools = Files.createDirectories (dir.resolve (M3CoveringArray.DIR));
		write (tools.resolve ("FACTORS.tsv"), "factor\tcritical\tvalues\tmeaning\nwidget\tyes\tTable,Tree,Model\tw\nmode\tyes\ta,b\tm\nfeature\tno\tnone,@shipped-features\tf\napi\tno\tnone,@shipped-api\tx\nop\tno\tp,q,r\n");
		write (tools.resolve ("CONSTRAINTS.tsv"), "factor_a\tvalues_a\tfactor_b\tvalues_b\treason\nwidget\tModel\tfeature\t!none\tno widget\nwidget\t*\top\tr\tnever r\n\nwidget\tTable\top\tq\tq is not for Table\n");
		write (Files.createDirectories (dir.resolve ("docs/m3/feature-switches")).resolve ("SHIPPED.tsv"), "feature_id\towner\nframe-overlay\tx\n");
		String header = "api_id" + "\tc".repeat (12) + "\n";
		String row1 = "M3-A" + "\tc".repeat (11) + "\tM3Alpha.of(Table|List)\n", row2 = "M3-B" + "\tc".repeat (11) + "\tM3Beta (text only)\n", row3 = "M3-C" + "\tc".repeat (11) + "\tM3Gamma.of(Tree)\n", row4 = "M3-D" + "\tc".repeat (11) + "\tno class named\n";
		write (dir.resolve ("docs/m3/ENHANCED_API_MAP.tsv"), header + row1 + row2 + row3 + row4);
		Path src = Files.createDirectories (dir.resolve ("bundles/org.eclipse.swt/src"));
		write (src.resolve ("M3Alpha.java"), "class M3Alpha {}");
		write (src.resolve ("M3Beta.java"), "class M3Beta {}");
		write (src.resolve ("M3Notes.txt"), "not a class");
		write (src.resolve ("Other.java"), "class Other {}");
		return dir;
	}

	static void write (Path file, String text) throws IOException {
		Files.writeString (file, text, StandardCharsets.UTF_8);
	}

	@Test
	void syntheticRepository (@TempDir Path dir) throws IOException {
		M3CoveringArray g = new M3CoveringArray (synthetic (dir)).load ().generate ();
		assertEquals (List.of ("none", "M3-A", "M3-B"), g.values ().get (3));
		assertEquals ("api\twidgets\nM3-A\tList,Table\nM3-B\tTable,Tree,List,Combo,StyledText,Canvas,CTabFolder,CCombo,Model\n", g.apiWidgetsTable ());
		assertEquals (List.of (), g.uncoveredPairs ());
		assertEquals (0, g.uncoveredCriticalTriples ());
		assertEquals (0, g.rowsWithForbiddenPairs ());
		assertFalse (g.matrix ().contains ("\tr\t"), "op r is forbidden for every widget");
		assertFalse (g.summary ().contains ("implied-forbidden pairs 0"), "the pairs of op r with mode, feature and api are implied: " + g.summary ());
		assertTrue (g.matrix ().lines ().filter (l -> l.contains ("\tModel\t")).allMatch (l -> l.contains ("\tnone\t") && l.endsWith ("\theadless")));
		assertFalse (g.allowed (0, 2, 3, 1), "Model x M3-A is derived as forbidden");
		assertTrue (g.allowed (0, 2, 3, 2), "the widget-free M3-B applies to the model");
	}

	@Test
	void writeAndCheck (@TempDir Path dir) throws IOException {
		Path root = synthetic (dir);
		M3CoveringArray.main (new String [] {root.toString ()});
		Path matrix = root.resolve (M3CoveringArray.DIR + "/COMBINATION_MATRIX.tsv");
		String first = Files.readString (matrix, StandardCharsets.UTF_8);
		M3CoveringArray.main (new String [] {"--check", root.toString ()});
		assertEquals (first, Files.readString (matrix, StandardCharsets.UTF_8));
		write (matrix, "stale\n");
		assertEquals (1, M3CoveringArray.run (new String [] {"--check", root.toString ()}), "a stale output would change");
		assertEquals ("stale\n", Files.readString (matrix, StandardCharsets.UTF_8), "--check writes nothing");
		assertEquals (0, M3CoveringArray.run (new String [] {root.toString ()}));
		assertEquals (Paths.get ("."), M3CoveringArray.root (new String [0], false));
		assertEquals (Paths.get ("."), M3CoveringArray.root (new String [] {"--check"}, true));
	}

	@Test
	void selectorsAndErrors (@TempDir Path dir) throws IOException {
		M3CoveringArray g = new M3CoveringArray (synthetic (dir)).load ();
		assertArrayEquals (new int [] {0, 1, 2}, g.select (0, "*"));
		assertArrayEquals (new int [] {0, 1}, g.select (0, "!Model"));
		assertArrayEquals (new int [] {1}, g.select (0, "Tree"));
		assertThrows (IllegalArgumentException.class, () -> g.select (0, "Grid"));
		assertThrows (IllegalArgumentException.class, () -> g.factor ("colour"));
		assertThrows (IllegalArgumentException.class, () -> g.derived ("@nothing"));
		assertEquals (List.of ("List", "Table"), M3CoveringArray.widgetsOf ("M3X.of(Table|List) and TableItem"));
		assertEquals (9, M3CoveringArray.widgetsOf ("text only").size ());
		assertEquals (M3CoveringArray.key (1, 2, 3, 4), M3CoveringArray.key (3, 4, 1, 2));
	}

	/** Without arguments the generator reads the working directory: the repository (0) or a directory without tables. */
	@Test
	void runWithoutArgumentsReadsTheWorkingDirectory () throws IOException {
		if (Files.exists (Paths.get (M3CoveringArray.DIR, "FACTORS.tsv"))) assertEquals (0, M3CoveringArray.run (new String [0]));
		else assertThrows (NoSuchFileException.class, () -> M3CoveringArray.run (new String [0]));
	}

	@Test
	void brokenArraysAreDetected (@TempDir Path dir) throws IOException {
		M3CoveringArray g = new M3CoveringArray (synthetic (dir)).load ().generate ();
		int [] first = g.rows ().remove (0);
		assertFalse (g.uncoveredPairs ().isEmpty ());
		assertTrue (g.uncoveredPairs ().get (0).contains ("=") && g.uncoveredPairs ().get (0).contains (" x "), g.uncoveredPairs ().get (0));
		g.rows ().clear ();
		assertEquals (6, g.uncoveredCriticalTriples (), "every widget x mode of the seed");
		first [g.names ().indexOf ("op")] = g.values ().get (g.names ().indexOf ("op")).indexOf ("r");
		g.rows ().add (first);
		assertEquals (1, g.rowsWithForbiddenPairs ());
	}

	@Test
	void unsatisfiableRowIsReported (@TempDir Path dir) throws IOException {
		Path root = synthetic (dir);
		write (root.resolve (M3CoveringArray.DIR + "/CONSTRAINTS.tsv"), "factor_a\tvalues_a\tfactor_b\tvalues_b\treason\nwidget\t*\top\t*\tnothing fits\n");
		M3CoveringArray g = new M3CoveringArray (root).load ();
		assertThrows (IllegalStateException.class, g::generate);
	}
}
