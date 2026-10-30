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
package org.eclipse.swt.snippets.m3.gallery;

import java.io.*;
import java.util.*;
import java.util.stream.*;

/**
 * The gallery's model, without widgets: the entries of the generated index by category, their descriptions, the
 * feature rows of the M3Features table, and the command line that runs an entry in a child JVM with a render mode,
 * per-widget modes and feature switches.
 */
public final class M3GalleryModel {
	/** Category order of the gallery tree. */
	public static final List<String> CATEGORIES = List.of ("widgets", "modes", "features", "enhanced-api", "scale", "renderer", "demos");
	/** The five render modes (<code>-Dswt.widget.mode</code>). */
	public static final List<String> MODES = List.of ("viewport", "original", "custom", "m3", "gpu");
	/** Widget classes that have a mode switch of their own (<code>-Dswt.widget.mode.&lt;Widget&gt;</code>). */
	public static final List<String> MODE_WIDGETS = List.of ("Table", "Tree", "List", "Combo", "StyledText", "Canvas", "CTabFolder", "CoolBar", "ExpandBar", "Text", "Composite");

	/** One index entry. */
	public record Entry (int index, String category, String className, String args, String title, String apis, String widgets, String modes) {
		/** The class name without its package. */
		public String simpleName () {
			return className.substring (className.lastIndexOf ('.') + 1);
		}

		/** The tree label: the class and its arguments. */
		public String label () {
			return args.isEmpty () ? simpleName () : simpleName () + " " + args;
		}
	}

	private M3GalleryModel () {
	}

	/** Every entry of the generated index. */
	public static List<Entry> entries () {
		return entries (M3GalleryIndex.ENTRIES);
	}

	/** Entries of an index table (rows: category, class, args, title, apis, widgets, modes). */
	public static List<Entry> entries (String [][] table) {
		return IntStream.range (0, table.length).mapToObj (i -> new Entry (i, table [i] [0], table [i] [1], table [i] [2], table [i] [3], table [i] [4], table [i] [5], table [i] [6])).collect (Collectors.toList ());
	}

	/** Entries grouped by category, in category order; empty categories are kept. */
	public static Map<String, List<Entry>> byCategory (List<Entry> entries) {
		Map<String, List<Entry>> map = new LinkedHashMap<> ();
		CATEGORIES.forEach (c -> map.put (c, new ArrayList<> ()));
		entries.forEach (e -> map.computeIfAbsent (e.category (), c -> new ArrayList<> ()).add (e));
		return map;
	}

	/** Entries by the enhanced API rows they call (one node per shipped row in the gallery), rows sorted by id. */
	public static Map<String, List<Entry>> byApi (List<Entry> entries) {
		Map<String, List<Entry>> map = new TreeMap<> ();
		for (Entry e : entries) {
			for (String api : e.apis ().split (",")) if (!api.isEmpty ()) map.computeIfAbsent (api, a -> new ArrayList<> ()).add (e);
		}
		return map;
	}

	/** Arguments of an entry (whitespace separated). */
	public static String [] args (String args) {
		String trimmed = args == null ? "" : args.trim ();
		return trimmed.isEmpty () ? new String [0] : trimmed.split ("\\s+");
	}

	/** The description pane text of an entry. */
	public static String describe (Entry e) {
		return e.title () + "\n\nclass: " + e.className () + "\nargs: " + (e.args ().isEmpty () ? "(none)" : e.args ())
			+ "\ncategory: " + e.category () + "\nenhanced API: " + dash (e.apis ()) + "\nwidgets: " + dash (e.widgets ())
			+ "\nmodes with an implementation: " + e.modes () + " (every other mode falls back and must behave the same)";
	}

	static String dash (String s) {
		return s.isEmpty () ? "-" : s;
	}

	/**
	 * The command that runs an entry in a child JVM.
	 *
	 * @param java the java executable
	 * @param classPath the class path of the child (this JVM's)
	 * @param e the entry
	 * @param mode the global mode, or null for the default
	 * @param widgetModes per-widget modes (widget class to mode)
	 * @param features feature ids switched on for every widget
	 * @param inherited system properties passed on (for example <code>swt.library.path</code>)
	 * @param runner true to run under {@link M3ExampleRunner} (closes itself), false to run the example as it is
	 */
	public static List<String> command (String java, String classPath, Entry e, String mode, Map<String, String> widgetModes, Collection<String> features, Map<String, String> inherited, boolean runner) {
		List<String> command = new ArrayList<> (List.of (java, "-cp", classPath));
		inherited.forEach ((k, v) -> command.add ("-D" + k + "=" + v));
		if (mode != null && MODES.contains (mode)) command.add ("-Dswt.widget.mode=" + mode);
		widgetModes.forEach ((w, m) -> command.add ("-Dswt.widget.mode." + w + "=" + m));
		features.forEach (f -> command.add ("-Dswt.widget.feature." + f + "=true"));
		if (runner) command.add (M3ExampleRunner.class.getName ());
		command.add (e.className ());
		command.addAll (Arrays.asList (args (e.args ())));
		return command;
	}

	/** The properties of this JVM a child needs to find the SWT natives. */
	public static Map<String, String> inherited (Properties properties) {
		Map<String, String> map = new TreeMap<> ();
		for (String key : List.of ("java.library.path", "swt.library.path", "m3.runner.delay", "m3.runner.timeout")) {
			String value = properties.getProperty (key);
			if (value != null && !value.isEmpty ()) map.put (key, value);
		}
		return map;
	}

	/** The java executable of a Java home. */
	public static String java (String javaHome) {
		return javaHome + File.separator + "bin" + File.separator + "java";
	}

	/**
	 * Rows of the M3Features registry table (<code>M3Features.table ()</code>): bit, id, current state, default,
	 * shipped; the header line is skipped.
	 */
	public static List<String []> featureRows (String table) {
		return table.lines ().skip (1).filter (l -> !l.isBlank ()).map (l -> l.split ("\t", -1)).filter (r -> r.length >= 10)
			.map (r -> new String [] {r [0], r [1], r [4], r [5], r [9]}).collect (Collectors.toList ());
	}

	/** True when a feature row can be switched by a client (shipped and not a native default). */
	public static boolean switchable (String [] row) {
		return "yes".equals (row [4]) && !"NATIVE_HAS".equals (row [2]);
	}
}
