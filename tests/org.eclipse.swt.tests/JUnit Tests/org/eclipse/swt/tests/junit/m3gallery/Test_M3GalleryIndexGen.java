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
package org.eclipse.swt.tests.junit.m3gallery;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.snippets.m3.gallery.*;
import org.eclipse.swt.snippets.m3.tools.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

/**
 * Headless tests of the gallery index generator (examples/org.eclipse.swt.snippets/tools/M3GalleryIndexGen.java): the
 * checked-in index is its fixed point and equals the compiled M3GalleryIndex, every category has entries, every shipped
 * enhanced API row has an example, and titles, anchors, categories and API detection behave as documented on a
 * synthetic repository. Repository root: <code>-Dm3.repo.root</code>.
 */
public class Test_M3GalleryIndexGen {
	static final Path ROOT = Paths.get (System.getProperty ("m3.repo.root", "."));

	static M3GalleryIndexGen scanned () throws IOException {
		return new M3GalleryIndexGen (ROOT).scan ();
	}

	@Test
	void checkedInIndexIsTheFixedPoint () throws IOException {
		M3GalleryIndexGen g = scanned ();
		assertEquals (Files.readString (ROOT.resolve (M3GalleryIndexGen.BUNDLE + "/tools/GALLERY_INDEX.tsv"), StandardCharsets.UTF_8), g.indexTsv ());
		assertEquals (Files.readString (ROOT.resolve (M3GalleryIndexGen.BUNDLE + "/tools/API_EXAMPLES.tsv"), StandardCharsets.UTF_8), g.apiExamples ());
		assertFalse (g.write (true));
	}

	@Test
	void compiledIndexEqualsTheScan () throws IOException {
		List<M3GalleryIndexGen.Entry> entries = scanned ().entries ();
		assertEquals (entries.size (), M3GalleryIndex.SIZE);
		for (int i = 0; i < entries.size (); i++) {
			M3GalleryIndexGen.Entry e = entries.get (i);
			assertArrayEquals (new String [] {e.category (), e.className (), e.args (), e.title (), e.apis (), e.widgets (), e.modes ()}, M3GalleryIndex.ENTRIES [i]);
		}
	}

	@Test
	void everyCategoryAndEveryShippedApiRowHasAnExample () throws IOException {
		M3GalleryIndexGen g = scanned ();
		Set<String> categories = g.entries ().stream ().map (M3GalleryIndexGen.Entry::category).collect (Collectors.toSet ());
		assertEquals (new HashSet<> (M3GalleryIndexGen.CATEGORIES), categories);
		assertFalse (g.apiExamples ().contains ("\tMISSING\t"), g.apiExamples ());
		assertTrue (g.apis ().size () >= 22, "shipped rows " + g.apis ().size ());
		assertTrue (g.summary ().contains ("without example 0"), g.summary ());
	}

	@Test
	void widgetModesComeFromTheSources () throws IOException {
		Map<String, Set<String>> modes = scanned ().widgetModes ();
		assertEquals (Set.of ("original", "viewport", "custom", "m3", "gpu"), modes.get ("Table"));
		assertTrue (modes.get ("Canvas").containsAll (Set.of ("m3", "gpu")), modes.toString ());
		assertTrue (modes.get ("StyledText").contains ("viewport"), modes.toString ());
	}

	@Test
	void titlesAndTokens () {
		assertEquals ("Enhanced API: M3Mode.", M3GalleryIndexGen.title ("\n * Enhanced API: <code>M3Mode</code>. More text.\n * @since 1", "X"));
		assertEquals ("A virtual Table.", M3GalleryIndexGen.title ("\n * A {@link Table virtual Table}. Second.", "X"));
		assertEquals ("X", M3GalleryIndexGen.title ("", "X"));
		assertEquals ("no full stop", M3GalleryIndexGen.title (" no full stop ", "X"));
		assertEquals (List.of ("M3A.b", "M3A.c", "M3D.e"), M3GalleryIndexGen.tokens ("M3A.b/c(x) then M3D.e()", "M3A"));
		assertEquals (List.of ("M3Only"), M3GalleryIndexGen.tokens ("org.eclipse.swt.widgets.M3Only(rows)", "M3Only"));
		assertTrue (M3GalleryIndexGen.uses ("x = new M3Only (3);", "M3Only.size"));
		assertTrue (M3GalleryIndexGen.uses ("M3A . b (t)", "M3A.b"));
		assertFalse (M3GalleryIndexGen.uses ("M3A.bc (t)", "M3A.b"));
		assertTrue (M3GalleryIndexGen.uses ("use M3Only here", "M3Only"));
	}

	@Test
	void categories () {
		assertEquals ("widgets", M3GalleryIndexGen.category ("org.eclipse.swt.snippets.m3.gallery.widgets", "A", ""));
		assertEquals ("scale", M3GalleryIndexGen.category ("org.eclipse.swt.snippets.m3", "M3TreeMillionNodesExample", "M3-X"));
		assertEquals ("enhanced-api", M3GalleryIndexGen.category ("org.eclipse.swt.snippets.m3", "M3AExample", "M3-X"));
		assertEquals ("demos", M3GalleryIndexGen.category ("org.eclipse.swt.snippets", "M3Demo", ""));
		assertThrows (IllegalArgumentException.class, () -> M3GalleryIndexGen.category ("org.eclipse.swt.snippets.m3.gallery.toys", "A", ""));
	}

	static void write (Path file, String text) throws IOException {
		Files.createDirectories (file.getParent ());
		Files.writeString (file, text, StandardCharsets.UTF_8);
	}

	/** A repository with one API row, one widget gate, two examples (one with anchors) and one non-example. */
	static Path synthetic (Path dir) throws IOException {
		write (dir.resolve ("docs/m3/ENHANCED_API_MAP.tsv"), "api_id" + "\tc".repeat (12) + "\nM3-A" + "\tc".repeat (11) + "\tM3Alpha.of(Table)\nM3-Z" + "\tc".repeat (11) + "\tM3Zeta.of(Table)\n");
		write (dir.resolve ("bundles/org.eclipse.swt/src/M3Alpha.java"), "class M3Alpha {}");
		write (dir.resolve ("bundles/org.eclipse.swt/src/Table.java"), "static final X MODE = WidgetMode.of (\"Table\", WidgetMode.VIEWPORT, WidgetMode.CUSTOM);");
		write (dir.resolve (M3GalleryIndexGen.BUNDLE + "/tools/M3GalleryIndexGen.java"), "/* licence */\nclass G {}");
		String src = M3GalleryIndexGen.SRC;
		write (dir.resolve (src + "/m3/gallery/widgets/W.java"), "package org.eclipse.swt.snippets.m3.gallery.widgets;\n/**\n * Widgets demo. More.\n */\npublic class W {\n//GALLERY: a b | first\n//GALLERY: c | second\npublic static void main (String [] args) { new Table (s, 0); M3Alpha.of (t); }\n}\n");
		write (dir.resolve (src + "/M3Root.java"), "package org.eclipse.swt.snippets;\n/* Root demo \"quoted\" \\ slash. */\npublic final class M3Root {\npublic static void main (String [] args) {}\n}\n");
		write (dir.resolve (src + "/m3/NoMain.java"), "package org.eclipse.swt.snippets.m3;\npublic class NoMain {}\n");
		write (dir.resolve (src + "/m3/gallery/Infra.java"), "package org.eclipse.swt.snippets.m3.gallery;\npublic class Infra { public static void main (String [] a) {} }\n");
		write (dir.resolve (src + "/Snippet1.java"), "package org.eclipse.swt.snippets;\npublic class Snippet1 { public static void main (String [] a) {} }\n");
		return dir;
	}

	@Test
	void syntheticRepository (@TempDir Path dir) throws IOException {
		M3GalleryIndexGen g = new M3GalleryIndexGen (synthetic (dir)).scan ();
		assertEquals (3, g.entries ().size (), g.indexTsv ());
		M3GalleryIndexGen.Entry first = g.entries ().get (0);
		assertEquals (new M3GalleryIndexGen.Entry ("widgets", "org.eclipse.swt.snippets.m3.gallery.widgets.W", "a b", "first", "M3-A", "Table", "viewport,original,custom"), first);
		assertEquals ("demos", g.entries ().get (2).category ());
		assertEquals ("Root demo \"quoted\" \\ slash.", g.entries ().get (2).title ());
		assertTrue (g.apiExamples ().contains ("M3-A\tCOVERED\tW"), g.apiExamples ());
		assertTrue (g.indexJava ().contains ("\"Root demo \\\"quoted\\\" \\\\ slash.\""), g.indexJava ());
		assertTrue (g.indexJava ().startsWith ("/* licence */\npackage org.eclipse.swt.snippets.m3.gallery;"));
		assertTrue (g.indexTsv ().contains ("\t-\t"), "empty args and widgets are written as -");
	}

	@Test
	void writeCheckAndMain (@TempDir Path dir) throws IOException {
		Path root = synthetic (dir);
		M3GalleryIndexGen.main (new String [] {root.toString ()});
		Path index = root.resolve (M3GalleryIndexGen.GALLERY + "/M3GalleryIndex.java");
		String first = Files.readString (index, StandardCharsets.UTF_8);
		M3GalleryIndexGen.main (new String [] {"--check", root.toString ()});
		assertEquals (first, Files.readString (index, StandardCharsets.UTF_8));
		write (index, "stale");
		assertTrue (new M3GalleryIndexGen (root).scan ().write (true));
		assertEquals ("stale", Files.readString (index, StandardCharsets.UTF_8));
	}
}
