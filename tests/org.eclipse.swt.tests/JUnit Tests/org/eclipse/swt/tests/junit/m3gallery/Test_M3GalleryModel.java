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
import java.util.*;

import org.eclipse.swt.snippets.m3.gallery.*;
import org.junit.jupiter.api.*;

/** Headless tests of the gallery model: entries, grouping, descriptions, child command lines and feature rows. */
public class Test_M3GalleryModel {
	static final String [][] TABLE = {{"widgets", "a.b.W", "x y", "first", "M3-A,M3-B", "Table", "viewport,original"}, {"demos", "a.D", "", "demo", "", "", "original"}, {"toys", "a.T", "", "t", "M3-A", "", "original"}};

	@Test
	void entriesAndGroups () {
		List<M3GalleryModel.Entry> entries = M3GalleryModel.entries (TABLE);
		assertEquals ("W x y", entries.get (0).label ());
		assertEquals ("D", entries.get (1).label ());
		assertEquals ("W", entries.get (0).simpleName ());
		Map<String, List<M3GalleryModel.Entry>> groups = M3GalleryModel.byCategory (entries);
		assertEquals (List.of ("widgets", "modes", "features", "enhanced-api", "scale", "renderer", "demos", "toys"), new ArrayList<> (groups.keySet ()));
		assertEquals (0, groups.get ("modes").size ());
		Map<String, List<M3GalleryModel.Entry>> apis = M3GalleryModel.byApi (entries);
		assertEquals (List.of ("M3-A", "M3-B"), new ArrayList<> (apis.keySet ()));
		assertEquals (2, apis.get ("M3-A").size ());
	}

	@Test
	void generatedIndexLoads () {
		List<M3GalleryModel.Entry> entries = M3GalleryModel.entries ();
		assertEquals (M3GalleryIndex.SIZE, entries.size ());
		assertTrue (M3GalleryModel.byApi (entries).size () >= 22);
	}

	@Test
	void argsAndDescription () {
		assertArrayEquals (new String [0], M3GalleryModel.args (""));
		assertArrayEquals (new String [0], M3GalleryModel.args (null));
		assertArrayEquals (new String [] {"Table", "VIRTUAL"}, M3GalleryModel.args (" Table  VIRTUAL "));
		String text = M3GalleryModel.describe (M3GalleryModel.entries (TABLE).get (1));
		assertTrue (text.contains ("args: (none)") && text.contains ("enhanced API: -") && text.contains ("widgets: -"), text);
		assertTrue (M3GalleryModel.describe (M3GalleryModel.entries (TABLE).get (0)).contains ("args: x y"));
	}

	@Test
	void commandLines () {
		M3GalleryModel.Entry e = M3GalleryModel.entries (TABLE).get (0);
		List<String> plain = M3GalleryModel.command ("java", "cp", e, null, Map.of (), List.of (), Map.of (), false);
		assertEquals (List.of ("java", "-cp", "cp", "a.b.W", "x", "y"), plain);
		List<String> full = M3GalleryModel.command ("java", "cp", e, "m3", new TreeMap<> (Map.of ("Tree", "custom")), List.of ("frame-overlay"), Map.of ("swt.library.path", "L"), true);
		assertEquals (List.of ("java", "-cp", "cp", "-Dswt.library.path=L", "-Dswt.widget.mode=m3", "-Dswt.widget.mode.Tree=custom", "-Dswt.widget.feature.frame-overlay=true",
			M3ExampleRunner.class.getName (), "a.b.W", "x", "y"), full);
		assertFalse (M3GalleryModel.command ("java", "cp", e, "bogus", Map.of (), List.of (), Map.of (), false).toString ().contains ("swt.widget.mode="));
	}

	@Test
	void inheritedPropertiesAndJava () {
		Properties p = new Properties ();
		p.setProperty ("swt.library.path", "N");
		p.setProperty ("java.library.path", "");
		p.setProperty ("other", "x");
		assertEquals (Map.of ("swt.library.path", "N"), M3GalleryModel.inherited (p));
		assertEquals ("J" + File.separator + "bin" + File.separator + "java", M3GalleryModel.java ("J"));
	}

	@Test
	void featureRows () {
		String table = "bit\tfeature_id\tconcept\tfamily\tstate\tdefault\towner\tapi\talias\tshipped\n0\tcard\tc\tpanel\tGAP\toff\t-\t-\t-\tno\n"
			+ "\n1\tnative\tc\tf\tNATIVE_HAS\ton\tx\t-\t-\tyes\n2\tframe-overlay\tc\tf\tVIEWPORT_HAS\toff\tx\t-\talias\tyes\nshort\trow\n";
		List<String []> rows = M3GalleryModel.featureRows (table);
		assertEquals (3, rows.size ());
		assertArrayEquals (new String [] {"2", "frame-overlay", "VIEWPORT_HAS", "off", "yes"}, rows.get (2));
		assertFalse (M3GalleryModel.switchable (rows.get (0)));
		assertFalse (M3GalleryModel.switchable (rows.get (1)));
		assertTrue (M3GalleryModel.switchable (rows.get (2)));
	}
}
