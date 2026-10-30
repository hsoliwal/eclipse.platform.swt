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
package org.eclipse.swt.tests.m3.tools;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Deterministic, seedless IPOG covering-array generator of the M3 combination matrix.
 * <p>
 * Reads <code>FACTORS.tsv</code> (factor order; the leading <code>critical</code> factors are seeded with their full
 * product, so every critical triple is covered) and <code>CONSTRAINTS.tsv</code> (forbidden value pairs), expands the
 * derived values (<code>@shipped-features</code>, <code>@shipped-api</code>) and the derived API/widget constraints,
 * grows the array factor by factor (horizontal, then vertical growth), fills the free cells with the first allowed
 * value, verifies that every allowed pair and every allowed critical triple is covered and writes
 * <code>COMBINATION_MATRIX.tsv</code> and <code>API_WIDGETS.tsv</code>. A second run prints <code>UNCHANGED</code> for
 * each output; <code>--check</code> exits 1 when an output would change.
 * </p>
 * Usage: <code>java M3CoveringArray.java [--check] &lt;repository-root&gt;</code>
 */
public final class M3CoveringArray {
	/** The tool directory, relative to the repository root. */
	public static final String DIR = "tests/org.eclipse.swt.tests/tools/m3-combinations";
	/** A cell that no pair needs yet. */
	public static final int FREE = -1;
	static final Pattern WIDGET_WORD = Pattern.compile ("\\b(Table|List|Tree)\\b");
	static final Pattern SURFACE_CLASS = Pattern.compile ("M3[A-Z][A-Za-z0-9]*");
	static final List<String> REAL_WIDGETS = List.of ("Table", "Tree", "List", "Combo", "StyledText", "Canvas", "CTabFolder", "CCombo");

	final Path root;
	final List<String> names = new ArrayList<> ();
	final List<List<String>> values = new ArrayList<> ();
	final List<String> meanings = new ArrayList<> ();
	int critical;
	final Set<Long> forbidden = new HashSet<> ();
	final Map<String, List<String>> apiWidgets = new TreeMap<> ();
	final List<int []> rows = new ArrayList<> ();
	/** Allowed pairs that no complete row can hold (implied by the constraints); not required to be covered. */
	final Set<Long> implied = new TreeSet<> ();

	/** A generator over the tables of one repository checkout. */
	public M3CoveringArray (Path root) {
		this.root = root;
	}

	public static void main (String [] args) throws IOException {
		boolean check = args.length > 0 && "--check".equals (args [0]);
		Path root = Paths.get (args.length > (check ? 1 : 0) ? args [check ? 1 : 0] : ".");
		M3CoveringArray generator = new M3CoveringArray (root).load ().generate ();
		System.out.println (generator.summary ());
		boolean changed = generator.write (check);
		if (check && changed) System.exit (1);
	}

	/** Loads the factors, derived values and constraints. */
	public M3CoveringArray load () throws IOException {
		for (String [] row : tsv (root.resolve (DIR + "/FACTORS.tsv"))) {
			names.add (row [0]);
			meanings.add (row.length > 3 ? row [3] : "");
			if ("yes".equals (row [1])) critical++;
			List<String> list = new ArrayList<> ();
			for (String token : row [2].split (",")) list.addAll (token.startsWith ("@") ? derived (token) : List.of (token));
			values.add (list);
		}
		for (String [] row : tsv (root.resolve (DIR + "/CONSTRAINTS.tsv"))) forbid (row [0], row [1], row [2], row [3]);
		deriveApiConstraints ();
		return this;
	}

	/** Reads a TSV file without its header line; blank lines are skipped. */
	static List<String []> tsv (Path file) throws IOException {
		return Files.readAllLines (file, StandardCharsets.UTF_8).stream ().skip (1).filter (l -> !l.isBlank ()).map (l -> l.split ("\t", -1)).collect (Collectors.toList ());
	}

	/** Expands a derived value token. */
	public List<String> derived (String token) throws IOException {
		switch (token) {
			case "@shipped-features":
				return tsv (root.resolve ("docs/m3/feature-switches/SHIPPED.tsv")).stream ().map (r -> r [0]).collect (Collectors.toList ());
			case "@shipped-api":
				return shippedApi ();
			default:
				throw new IllegalArgumentException ("unknown derived value " + token);
		}
	}

	/** The enhanced API rows whose surface class exists in the SWT bundle; records each row's widgets. */
	List<String> shippedApi () throws IOException {
		Set<String> classes = bundleClasses ();
		List<String> shipped = new ArrayList<> ();
		for (String [] row : tsv (root.resolve ("docs/m3/ENHANCED_API_MAP.tsv"))) {
			Matcher m = SURFACE_CLASS.matcher (row [12]);
			if (!m.find () || !classes.contains (m.group ())) continue;
			shipped.add (row [0]);
			apiWidgets.put (row [0], widgetsOf (row [12]));
		}
		return shipped;
	}

	/** The simple names of the M3 classes in the SWT bundle sources. */
	Set<String> bundleClasses () throws IOException {
		try (Stream<Path> files = Files.walk (root.resolve ("bundles/org.eclipse.swt"))) {
			return files.map (p -> p.getFileName ().toString ()).filter (n -> n.startsWith ("M3") && n.endsWith (".java")).map (n -> n.substring (0, n.length () - 5)).collect (Collectors.toCollection (TreeSet::new));
		}
	}

	/** Widgets an API surface names (Table, List, Tree); a widget-free surface applies to every widget and the model. */
	public static List<String> widgetsOf (String surface) {
		Set<String> named = new TreeSet<> ();
		Matcher m = WIDGET_WORD.matcher (surface);
		while (m.find ()) named.add (m.group ());
		if (!named.isEmpty ()) return new ArrayList<> (named);
		List<String> all = new ArrayList<> (REAL_WIDGETS);
		all.add ("Model");
		return all;
	}

	/** Forbids every widget an API row does not apply to. */
	void deriveApiConstraints () {
		int api = names.indexOf ("api"), widget = names.indexOf ("widget");
		for (Map.Entry<String, List<String>> entry : apiWidgets.entrySet ()) {
			int a = values.get (api).indexOf (entry.getKey ());
			for (int w = 0; w < values.get (widget).size (); w++) {
				if (!entry.getValue ().contains (values.get (widget).get (w))) forbidden.add (key (widget, w, api, a));
			}
		}
	}

	/** Adds the forbidden pairs of one constraint row. */
	void forbid (String factorA, String valuesA, String factorB, String valuesB) {
		int fa = factor (factorA), fb = factor (factorB);
		for (int va : select (fa, valuesA)) {
			for (int vb : select (fb, valuesB)) forbidden.add (key (fa, va, fb, vb));
		}
	}

	public int factor (String name) {
		int index = names.indexOf (name);
		if (index < 0) throw new IllegalArgumentException ("unknown factor " + name);
		return index;
	}

	/** Value indices of a selector: <code>*</code>, <code>a,b</code> or the complement <code>!a,b</code>. */
	public int [] select (int factor, String selector) {
		List<String> all = values.get (factor);
		boolean not = selector.startsWith ("!");
		Set<String> listed = new HashSet<> (Arrays.asList ((not ? selector.substring (1) : selector).split (",")));
		for (String v : listed) {
			if (!"*".equals (v) && !all.contains (v)) throw new IllegalArgumentException ("unknown value " + v + " of " + names.get (factor));
		}
		return IntStream.range (0, all.size ()).filter (i -> listed.contains ("*") || listed.contains (all.get (i)) != not).toArray ();
	}

	/** The key of a value pair; the lower factor comes first. */
	public static long key (int fa, int va, int fb, int vb) {
		if (fa > fb) return key (fb, vb, fa, va);
		return (((long) fa * 4096 + va) * 64 + fb) * 4096 + vb;
	}

	/** True when the pair may appear in a row. */
	public boolean allowed (int fa, int va, int fb, int vb) {
		return !forbidden.contains (key (fa, va, fb, vb));
	}

	/** True when value v of factor k agrees with every assigned cell of a row. */
	boolean fits (int [] row, int k, int v) {
		for (int j = 0; j < row.length; j++) {
			if (j != k && row [j] != FREE && !allowed (j, row [j], k, v)) return false;
		}
		return true;
	}

	/** Builds the array: seed, then horizontal and vertical growth per factor, then fill. */
	public M3CoveringArray generate () {
		seed (new int [names.size ()], 0);
		for (int k = critical; k < names.size (); k++) vertical (k, horizontal (k));
		rows.forEach (this::fill);
		return this;
	}

	/** The full product of the critical factors, without forbidden pairs. */
	void seed (int [] row, int k) {
		if (k == critical) {
			int [] full = row.clone ();
			Arrays.fill (full, critical, full.length, FREE);
			rows.add (full);
			return;
		}
		for (int v = 0; v < values.get (k).size (); v++) {
			if (!fits (Arrays.copyOf (row, k), k, v)) continue;
			row [k] = v;
			seed (row, k + 1);
		}
	}

	/** The allowed pairs between factor k and the factors before it. */
	TreeSet<Long> pairsTo (int k) {
		TreeSet<Long> pairs = new TreeSet<> ();
		for (int j = 0; j < k; j++) {
			for (int vj = 0; vj < values.get (j).size (); vj++) {
				for (int vk = 0; vk < values.get (k).size (); vk++) {
					if (allowed (j, vj, k, vk)) pairs.add (key (j, vj, k, vk));
				}
			}
		}
		return pairs;
	}

	/** Horizontal growth: each row takes the allowed value of factor k that covers most uncovered pairs. */
	TreeSet<Long> horizontal (int k) {
		TreeSet<Long> uncovered = pairsTo (k);
		for (int [] row : rows) {
			int best = FREE, bestGain = -1;
			for (int v = 0; v < values.get (k).size (); v++) {
				int gain = fits (row, k, v) && completable (row, k, v) ? gain (row, k, v, uncovered) : -1;
				if (gain > bestGain) { best = v; bestGain = gain; }
			}
			row [k] = best;
			if (best != FREE) cover (row, k, uncovered);
		}
		return uncovered;
	}

	/** Uncovered pairs a value of factor k would cover in a row. */
	int gain (int [] row, int k, int v, Set<Long> uncovered) {
		int gain = 0;
		for (int j = 0; j < k; j++) {
			if (row [j] != FREE && uncovered.contains (key (j, row [j], k, v))) gain++;
		}
		return gain;
	}

	/** Removes the pairs a row covers with factor k. */
	void cover (int [] row, int k, Set<Long> uncovered) {
		for (int j = 0; j < row.length; j++) {
			if (j != k && row [j] != FREE && row [k] != FREE) uncovered.remove (key (j, row [j], k, row [k]));
		}
	}

	/** Vertical growth: each uncovered pair goes into the first row with room, else into a new row. */
	void vertical (int k, TreeSet<Long> uncovered) {
		while (!uncovered.isEmpty ()) {
			long pair = uncovered.pollFirst ();
			int j = (int) (pair / 4096 / 64 / 4096), vj = (int) (pair / 4096 / 64 % 4096), vk = (int) (pair % 4096);
			if (!completable (pairRow (j, vj, k, vk))) {
				implied.add (pair);
				continue;
			}
			int [] row = rows.stream ().filter (r -> room (r, j, vj, k, vk)).findFirst ().orElseGet (() -> newRow ());
			row [j] = vj;
			row [k] = vk;
			cover (row, k, uncovered);
		}
	}

	/** True when a row can take value vj of factor j and vk of factor k. */
	boolean room (int [] row, int j, int vj, int k, int vk) {
		if (row [j] != FREE && row [j] != vj || row [k] != FREE && row [k] != vk) return false;
		int [] trial = row.clone ();
		trial [j] = FREE;
		trial [k] = FREE;
		if (!fits (trial, j, vj)) return false;
		trial [j] = vj;
		if (!fits (trial, k, vk)) return false;
		trial [k] = vk;
		return completable (trial);
	}

	/** A row with only the two cells of a pair. */
	int [] pairRow (int j, int vj, int k, int vk) {
		int [] row = new int [names.size ()];
		Arrays.fill (row, FREE);
		row [j] = vj;
		row [k] = vk;
		return row;
	}

	/** True when value v of factor k leaves the row completable. */
	boolean completable (int [] row, int k, int v) {
		int [] trial = row.clone ();
		trial [k] = v;
		return completable (trial);
	}

	/** True when the free cells of a row can be filled (greedy, first allowed value in factor order). */
	boolean completable (int [] row) {
		int [] trial = row.clone ();
		for (int k = 0; k < trial.length; k++) {
			if (trial [k] != FREE) continue;
			int k0 = k;
			trial [k] = IntStream.range (0, values.get (k).size ()).filter (v -> fits (trial, k0, v)).findFirst ().orElse (FREE);
			if (trial [k] == FREE) return false;
		}
		return true;
	}

	int [] newRow () {
		int [] row = new int [names.size ()];
		Arrays.fill (row, FREE);
		rows.add (row);
		return row;
	}

	/** Fills the free cells of a row with the first allowed value. */
	void fill (int [] row) {
		for (int k = 0; k < row.length; k++) {
			if (row [k] != FREE) continue;
			int k0 = k;
			row [k] = IntStream.range (0, values.get (k).size ()).filter (v -> fits (row, k0, v)).findFirst ()
				.orElseThrow (() -> new IllegalStateException ("no allowed value of " + names.get (k0) + " for row " + Arrays.toString (row)));
		}
	}

	/** Allowed pairs that no row covers (empty when the array is a covering array). */
	public List<String> uncoveredPairs () {
		Set<Long> covered = new HashSet<> ();
		for (int [] row : rows) {
			for (int a = 0; a < row.length; a++) for (int b = a + 1; b < row.length; b++) covered.add (key (a, row [a], b, row [b]));
		}
		List<String> missing = new ArrayList<> ();
		for (int k = 1; k < names.size (); k++) {
			for (long pair : pairsTo (k)) if (!covered.contains (pair) && !implied.contains (pair)) missing.add (describe (pair));
		}
		return missing;
	}

	/** Allowed critical triples (pairwise allowed) that no row covers. */
	public int uncoveredCriticalTriples () {
		Set<List<Integer>> covered = rows.stream ().map (r -> IntStream.range (0, critical).mapToObj (i -> r [i]).collect (Collectors.toList ())).collect (Collectors.toSet ());
		int [] count = {0};
		seedCheck (new int [critical], 0, covered, count);
		return count [0];
	}

	void seedCheck (int [] row, int k, Set<List<Integer>> covered, int [] count) {
		if (k == critical) {
			if (!covered.contains (Arrays.stream (row).boxed ().collect (Collectors.toList ()))) count [0]++;
			return;
		}
		for (int v = 0; v < values.get (k).size (); v++) {
			if (!fits (Arrays.copyOf (row, k), k, v)) continue;
			row [k] = v;
			seedCheck (row, k + 1, covered, count);
		}
	}

	/** Rows that contain a forbidden pair (empty for a valid array). */
	public long rowsWithForbiddenPairs () {
		return rows.stream ().filter (r -> IntStream.range (0, r.length).anyMatch (k -> !fits (r, k, r [k]))).count ();
	}

	String describe (long pair) {
		int fa = (int) (pair / 4096 / 64 / 4096), va = (int) (pair / 4096 / 64 % 4096), fb = (int) (pair / 4096 % 64), vb = (int) (pair % 4096);
		return names.get (fa) + "=" + values.get (fa).get (va) + " x " + names.get (fb) + "=" + values.get (fb).get (vb);
	}

	/** The matrix as TSV: a row id, one column per factor and the environment (headless for the model layer). */
	public String matrix () {
		StringBuilder out = new StringBuilder ("row\t" + String.join ("\t", names) + "\tenv\n");
		int widget = names.indexOf ("widget");
		for (int i = 0; i < rows.size (); i++) {
			int [] row = rows.get (i);
			out.append (String.format ("r%04d", i + 1));
			for (int k = 0; k < row.length; k++) out.append ('\t').append (values.get (k).get (row [k]));
			out.append ('\t').append ("Model".equals (values.get (widget).get (row [widget])) ? "headless" : "display").append ('\n');
		}
		return out.toString ();
	}

	/** The derived widgets of each shipped API row. */
	public String apiWidgetsTable () {
		StringBuilder out = new StringBuilder ("api\twidgets\n");
		apiWidgets.forEach ((api, widgets) -> out.append (api).append ('\t').append (String.join (",", widgets)).append ('\n'));
		return out.toString ();
	}

	/** One line per measure of the generated array. */
	public String summary () {
		String sizes = IntStream.range (0, names.size ()).mapToObj (k -> names.get (k) + "=" + values.get (k).size ()).collect (Collectors.joining (" "));
		long headless = matrix ().lines ().filter (l -> l.endsWith ("\theadless")).count ();
		return "factors " + names.size () + " (" + sizes + "), critical " + critical + ", forbidden pairs " + forbidden.size () + "\n"
			+ "rows " + rows.size () + " (headless " + headless + ", display " + (rows.size () - headless) + "), uncovered pairs " + uncoveredPairs ().size ()
			+ ", uncovered critical triples " + uncoveredCriticalTriples () + ", rows with forbidden pairs " + rowsWithForbiddenPairs () + ", implied-forbidden pairs " + implied.size ();
	}

	/** Writes the outputs; true when one changed (or would change, with <code>check</code>). */
	public boolean write (boolean check) throws IOException {
		boolean a = write (root.resolve (DIR + "/COMBINATION_MATRIX.tsv"), matrix (), check);
		boolean b = write (root.resolve (DIR + "/API_WIDGETS.tsv"), apiWidgetsTable (), check);
		return a | b;
	}

	/** Fixed-point write of one file: UNCHANGED, WROTE or WOULD_CHANGE. */
	static boolean write (Path file, String content, boolean check) throws IOException {
		byte [] bytes = content.getBytes (StandardCharsets.UTF_8);
		boolean same = Files.exists (file) && Arrays.equals (Files.readAllBytes (file), bytes);
		System.out.println ((same ? "UNCHANGED " : check ? "WOULD_CHANGE " : "WROTE ") + file.getFileName ());
		if (!same && !check) Files.write (file, bytes);
		return !same;
	}

	/** The generated rows (value indices per factor). */
	public List<int []> rows () {
		return rows;
	}

	/** The factor names in order. */
	public List<String> names () {
		return names;
	}

	/** The values of each factor. */
	public List<List<String>> values () {
		return values;
	}
}
