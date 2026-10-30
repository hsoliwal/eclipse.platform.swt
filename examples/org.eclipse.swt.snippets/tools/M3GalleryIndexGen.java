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
package org.eclipse.swt.snippets.m3.tools;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Generates the index of the M3 example gallery from the example sources (CPU, fixed point).
 * <p>
 * An example is a class with a <code>main</code> method under <code>snippets/m3/</code> (any depth, except the
 * gallery's own package) or an <code>M3*</code> class in <code>snippets/</code>. Per example the generator reads the
 * first sentence of the class Javadoc (title), the <code>//GALLERY: &lt;args&gt; | &lt;title&gt;</code> anchors (one
 * entry each; none = one entry), the widget classes it creates, and the enhanced API rows it calls (the
 * <code>Class.method</code> tokens of the row's surface in <code>docs/m3/ENHANCED_API_MAP.tsv</code>, else its surface
 * class). The implemented modes of each widget class are read from the <code>WidgetMode.of</code> calls of the SWT
 * sources. Outputs: <code>M3GalleryIndex.java</code>, <code>tools/GALLERY_INDEX.tsv</code>,
 * <code>tools/API_EXAMPLES.tsv</code>; a second run prints <code>UNCHANGED</code> for each; <code>--check</code> exits 1
 * when an output would change.
 * </p>
 * Usage: <code>java M3GalleryIndexGen.java [--check] &lt;repository-root&gt;</code>
 */
public final class M3GalleryIndexGen {
	/** The snippets bundle, relative to the repository root. */
	public static final String BUNDLE = "examples/org.eclipse.swt.snippets";
	/** The source root of the snippets. */
	public static final String SRC = BUNDLE + "/src/org/eclipse/swt/snippets";
	/** The gallery package directory (its own classes are not entries). */
	public static final String GALLERY = SRC + "/m3/gallery";
	/** Category order of the gallery tree. */
	public static final List<String> CATEGORIES = List.of ("widgets", "modes", "features", "enhanced-api", "scale", "renderer", "demos");
	/** The five render modes. */
	public static final List<String> MODES = List.of ("viewport", "original", "custom", "m3", "gpu");
	static final Pattern MAIN = Pattern.compile ("static\\s+void\\s+main\\s*\\(");
	static final Pattern PACKAGE = Pattern.compile ("(?m)^package\\s+([\\w.]+)\\s*;");
	static final Pattern CLASS = Pattern.compile ("(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)");
	static final Pattern COMMENT = Pattern.compile ("/\\*((?:(?!\\*/).)*)\\*/", Pattern.DOTALL);
	static final Pattern ANCHOR = Pattern.compile ("(?m)^\\s*//GALLERY:\\s*(.*?)\\s*\\|\\s*(.*?)\\s*$");
	static final Pattern NEW_WIDGET = Pattern.compile ("new\\s+(Table|Tree|List|Combo|CCombo|StyledText|Canvas|CTabFolder|TabFolder|CoolBar|ExpandBar|ToolBar|Text|Composite|ScrolledComposite|SashForm|Button|Label|Spinner|Scale|Slider|ProgressBar|DateTime|Link|Group|Sash|CLabel|ViewForm|CBanner|Browser|Menu)\\s*\\(");
	static final Pattern WIDGET_MODE = Pattern.compile ("WidgetMode\\.of\\s*\\(\\s*\"(\\w+)\"((?:\\s*,\\s*(?:org\\.eclipse\\.swt\\.internal\\.)?WidgetMode\\.\\w+)*)");
	static final Pattern SURFACE_CLASS = Pattern.compile ("M3[A-Z][A-Za-z0-9]*");
	static final Pattern SURFACE_METHODS = Pattern.compile ("(M3[A-Z][A-Za-z0-9]*)\\.([a-z][A-Za-z0-9]*(?:/[a-z][A-Za-z0-9]*)*)");

	/** One gallery entry. */
	public record Entry (String category, String className, String args, String title, String apis, String widgets, String modes) {
		String tsv () {
			return String.join ("\t", category, className, args.isEmpty () ? "-" : args, title, apis.isEmpty () ? "-" : apis, widgets.isEmpty () ? "-" : widgets, modes);
		}
	}

	/** One shipped enhanced API row: its id and the tokens an example must use. */
	public record ApiRow (String id, List<String> tokens) {
	}

	final Path root;
	final List<Entry> entries = new ArrayList<> ();
	final List<ApiRow> apis = new ArrayList<> ();
	final Map<String, Set<String>> widgetModes = new TreeMap<> ();

	public M3GalleryIndexGen (Path root) {
		this.root = root;
	}

	public static void main (String [] args) throws IOException {
		int status = run (args);
		if (status != 0) System.exit (status);
	}

	/** Runs the generator: <code>[--check] [root]</code>; answers 1 when --check finds an output that would change. */
	public static int run (String [] args) throws IOException {
		boolean check = args.length > 0 && "--check".equals (args [0]);
		M3GalleryIndexGen generator = new M3GalleryIndexGen (root (args, check)).scan ();
		System.out.println (generator.summary ());
		return generator.write (check) && check ? 1 : 0;
	}

	/** The repository root argument (after --check), else the working directory. */
	public static Path root (String [] args, boolean check) {
		int at = check ? 1 : 0;
		return Paths.get (args.length > at ? args [at] : ".");
	}

	/** Reads the API map, the widget modes and every example. */
	public M3GalleryIndexGen scan () throws IOException {
		readApis ();
		readWidgetModes ();
		List<Path> sources = examples ();
		for (Path source : sources) entries.addAll (entriesOf (source));
		entries.sort (Comparator.comparingInt ((Entry e) -> CATEGORIES.indexOf (e.category ())).thenComparing (Entry::className));
		return this;
	}

	/** Shipped API rows: the surface class exists in the SWT bundle. */
	void readApis () throws IOException {
		Set<String> classes = bundleFiles ().map (p -> p.getFileName ().toString ()).filter (n -> n.endsWith (".java")).map (n -> n.substring (0, n.length () - 5)).collect (Collectors.toSet ());
		List<String> lines = Files.readAllLines (root.resolve ("docs/m3/ENHANCED_API_MAP.tsv"), StandardCharsets.UTF_8);
		for (String line : lines.subList (1, lines.size ())) {
			String [] row = line.split ("\t", -1);
			Matcher m = SURFACE_CLASS.matcher (row [12]);
			if (m.find () && classes.contains (m.group ())) apis.add (new ApiRow (row [0], tokens (row [12], m.group ())));
		}
	}

	/** The Class.method tokens of a surface, or its surface class when it lists none. */
	public static List<String> tokens (String surface, String surfaceClass) {
		List<String> tokens = new ArrayList<> ();
		Matcher m = SURFACE_METHODS.matcher (surface);
		while (m.find ()) {
			for (String method : m.group (2).split ("/")) tokens.add (m.group (1) + "." + method);
		}
		return tokens.isEmpty () ? List.of (surfaceClass) : tokens;
	}

	Stream<Path> bundleFiles () throws IOException {
		return Files.walk (root.resolve ("bundles/org.eclipse.swt")).filter (Files::isRegularFile);
	}

	/** The modes each widget class implements, from the WidgetMode.of calls of the SWT sources (ORIGINAL implied). */
	void readWidgetModes () throws IOException {
		List<Path> java = bundleFiles ().filter (p -> p.toString ().endsWith (".java")).sorted ().collect (Collectors.toList ());
		for (Path file : java) {
			Matcher m = WIDGET_MODE.matcher (Files.readString (file, StandardCharsets.UTF_8));
			while (m.find ()) {
				Set<String> modes = widgetModes.computeIfAbsent (m.group (1), k -> new TreeSet<> (List.of ("original")));
				for (String mode : m.group (2).split (",")) if (mode.contains ("WidgetMode.")) modes.add (mode.substring (mode.lastIndexOf ('.') + 1).trim ().toLowerCase (Locale.ROOT));
			}
		}
	}

	/** Example sources in a stable order. */
	List<Path> examples () throws IOException {
		Path src = root.resolve (SRC), gallery = root.resolve (GALLERY);
		try (Stream<Path> files = Files.walk (src)) {
			return files.filter (p -> p.toString ().endsWith (".java")).filter (p -> isExample (src, gallery, p)).sorted ().collect (Collectors.toList ());
		}
	}

	static boolean isExample (Path src, Path gallery, Path file) {
		boolean underM3 = file.startsWith (src.resolve ("m3")), rootM3 = file.getParent ().equals (src) && file.getFileName ().toString ().startsWith ("M3");
		return (underM3 || rootM3) && !file.getParent ().equals (gallery) && !file.startsWith (src.resolve ("m3/tools"));
	}

	/** The entries of one example source (none when it has no main method). */
	List<Entry> entriesOf (Path file) throws IOException {
		String text = Files.readString (file, StandardCharsets.UTF_8);
		Matcher type = CLASS.matcher (text), pkg = PACKAGE.matcher (text);
		if (!MAIN.matcher (text).find () || !type.find () || !pkg.find ()) return List.of ();
		String className = pkg.group (1) + "." + type.group (1), title = title (description (text.substring (0, type.start ())), type.group (1));
		String apiIds = apisOf (text), widgets = widgetsOf (text), category = category (pkg.group (1), type.group (1), apiIds);
		String modes = modesOf (widgets);
		List<Entry> list = new ArrayList<> ();
		Matcher anchor = ANCHOR.matcher (text);
		while (anchor.find ()) list.add (new Entry (category, className, anchor.group (1), anchor.group (2), apiIds, widgets, modes));
		if (list.isEmpty ()) list.add (new Entry (category, className, "", title, apiIds, widgets, modes));
		return list;
	}

	/** The last block comment before the class that is not the licence header (Javadoc or the SWT snippet comment). */
	static String description (String head) {
		String last = "";
		Matcher m = COMMENT.matcher (head);
		while (m.find ()) {
			if (!m.group (1).contains ("SPDX-License-Identifier") && !m.group (1).contains ("Copyright (c)")) last = m.group (1);
		}
		return last;
	}

	/** The first sentence of a description, without tags and markup; the class name when there is none. */
	public static String title (String comment, String simpleName) {
		String text = comment.replaceAll ("(?m)^\\s*\\*+", " ").replaceAll ("<[^>]+>", "").replaceAll ("\\{@\\w+\\s+[^}\\s]+\\s+([^}]*)\\}", "$1")
			.replaceAll ("\\{@\\w+\\s+([^}]*)\\}", "$1").replaceAll ("\\s+", " ").trim ();
		if (text.isEmpty ()) return simpleName;
		int at = text.indexOf (" @");
		if (at >= 0) text = text.substring (0, at);
		Matcher end = Pattern.compile ("\\.(\\s|$)").matcher (text);
		return (end.find () ? text.substring (0, end.start () + 1) : text).replace ('\t', ' ');
	}

	/** API rows an example calls: one of the row's Class.method tokens, or a constructor of one of its classes. */
	String apisOf (String text) {
		return apis.stream ().filter (row -> row.tokens ().stream ().anyMatch (t -> uses (text, t))).map (ApiRow::id).collect (Collectors.joining (","));
	}

	public static boolean uses (String text, String token) {
		int dot = token.indexOf ('.');
		String type = dot < 0 ? token : token.substring (0, dot);
		if (Pattern.compile ("new\\s+" + type + "\\s*\\(").matcher (text).find ()) return true;
		String call = dot < 0 ? "\\b" + type + "\\b" : "\\b" + type + "\\s*\\.\\s*" + token.substring (dot + 1) + "\\b";
		return Pattern.compile (call).matcher (text).find ();
	}

	static String widgetsOf (String text) {
		Set<String> widgets = new TreeSet<> ();
		Matcher m = NEW_WIDGET.matcher (text);
		while (m.find ()) widgets.add (m.group (1));
		return String.join (",", widgets);
	}

	/** Category: the gallery sub-package, else scale by name, enhanced-api by API use, else demos. */
	public static String category (String pkg, String simpleName, String apiIds) {
		String prefix = "org.eclipse.swt.snippets.m3.gallery.";
		if (pkg.startsWith (prefix)) {
			String category = pkg.substring (prefix.length ());
			if (!CATEGORIES.contains (category)) throw new IllegalArgumentException ("unknown gallery category " + category + " (" + simpleName + ")");
			return category;
		}
		if (simpleName.matches (".*(Million|1M).*")) return "scale";
		return apiIds.isEmpty () ? "demos" : "enhanced-api";
	}

	/** The modes in which at least one widget of the example changes implementation (original always). */
	String modesOf (String widgets) {
		Set<String> modes = new TreeSet<> (Comparator.comparingInt (MODES::indexOf));
		modes.add ("original");
		for (String widget : widgets.split (",")) modes.addAll (widgetModes.getOrDefault (widget, Set.of ()));
		return String.join (",", modes);
	}

	/** API rows and the examples that call them; MISSING rows have none. */
	public String apiExamples () {
		StringBuilder out = new StringBuilder ("api\tstatus\texamples\n");
		for (ApiRow row : apis) {
			String users = entries.stream ().filter (e -> Arrays.asList (e.apis ().split (",")).contains (row.id ())).map (e -> e.className ().substring (e.className ().lastIndexOf ('.') + 1)).distinct ().collect (Collectors.joining (","));
			out.append (row.id ()).append ('\t').append (users.isEmpty () ? "MISSING" : "COVERED").append ('\t').append (users.isEmpty () ? "-" : users).append ('\n');
		}
		return out.toString ();
	}

	/** The index as TSV. */
	public String indexTsv () {
		StringBuilder out = new StringBuilder ("category\tclass\targs\ttitle\tapis\twidgets\tmodes\n");
		entries.forEach (e -> out.append (e.tsv ()).append ('\n'));
		return out.toString ();
	}

	/** The index as the Java table the gallery reads. */
	public String indexJava () throws IOException {
		String header = Files.readString (root.resolve (BUNDLE + "/tools/M3GalleryIndexGen.java"), StandardCharsets.UTF_8);
		header = header.substring (0, header.indexOf ("*/") + 2);
		StringBuilder out = new StringBuilder (header).append ("\npackage org.eclipse.swt.snippets.m3.gallery;\n\n");
		out.append ("/**\n * The gallery index, generated by <code>tools/M3GalleryIndexGen.java</code> from the example sources; do not edit.\n");
		out.append (" * Columns: category, class, args, title, enhanced API rows, widgets, modes with an implementation.\n */\n");
		out.append ("public final class M3GalleryIndex {\n\t/** Number of entries. */\n\tpublic static final int SIZE = ").append (entries.size ()).append (";\n\n");
		out.append ("\t/** The entries. */\n\tpublic static final String [][] ENTRIES = {\n");
		entries.forEach (e -> out.append ("\t\t{").append (Stream.of (e.category (), e.className (), e.args (), e.title (), e.apis (), e.widgets (), e.modes ()).map (M3GalleryIndexGen::quote).collect (Collectors.joining (", "))).append ("},\n"));
		return out.append ("\t};\n\n\tprivate M3GalleryIndex () {\n\t}\n}\n").toString ();
	}

	static String quote (String s) {
		return "\"" + s.replace ("\\", "\\\\").replace ("\"", "\\\"") + "\"";
	}

	/** Counts per category, and the API rows without an example. */
	public String summary () {
		Map<String, Long> counts = entries.stream ().collect (Collectors.groupingBy (Entry::category, TreeMap::new, Collectors.counting ()));
		long classes = entries.stream ().map (Entry::className).distinct ().count ();
		long missing = apiExamples ().lines ().filter (l -> l.contains ("\tMISSING\t")).count ();
		return "entries " + entries.size () + " from " + classes + " example classes " + counts + "; shipped API rows " + apis.size () + ", without example " + missing;
	}

	/** Writes the outputs; true when one changed (or would change, with <code>check</code>). */
	public boolean write (boolean check) throws IOException {
		boolean a = write (root.resolve (GALLERY + "/M3GalleryIndex.java"), indexJava (), check);
		boolean b = write (root.resolve (BUNDLE + "/tools/GALLERY_INDEX.tsv"), indexTsv (), check);
		boolean c = write (root.resolve (BUNDLE + "/tools/API_EXAMPLES.tsv"), apiExamples (), check);
		return a | b | c;
	}

	/** Fixed-point write of one file: UNCHANGED, WROTE or WOULD_CHANGE. */
	static boolean write (Path file, String content, boolean check) throws IOException {
		byte [] bytes = content.getBytes (StandardCharsets.UTF_8);
		boolean same = Files.exists (file) && Arrays.equals (Files.readAllBytes (file), bytes);
		System.out.println ((same ? "UNCHANGED " : check ? "WOULD_CHANGE " : "WROTE ") + file.getFileName ());
		if (!same && !check) {
			Files.createDirectories (file.getParent ());
			Files.write (file, bytes);
		}
		return !same;
	}

	/** The entries in gallery order. */
	public List<Entry> entries () {
		return entries;
	}

	/** The shipped API rows. */
	public List<ApiRow> apis () {
		return apis;
	}

	/** Implemented modes per widget class. */
	public Map<String, Set<String>> widgetModes () {
		return widgetModes;
	}
}
