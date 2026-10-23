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
package org.eclipse.swt.m3.themegen;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

import org.eclipse.swt.internal.laf.*;

/**
 * The look and feel generator (docs/m3/look-and-feel/GENERATOR.md): a prompt (with optional brand colours and a
 * reference image) goes to a model ({@link M3ThemeModel}), the answer is read strictly ({@link M3ThemeSpec}),
 * completed on the CPU ({@link M3ThemeCompleter}) and written for SWT, Swing and IntelliJ ({@link M3ThemeExport}).
 * <pre>
 * java -cp ... org.eclipse.swt.m3.themegen.M3ThemeGenerator --prompt "..." --out &lt;directory&gt;
 *   [--brand #hex[,#hex]] [--image file.png] [--twin] [--name name]
 *   [--ollama [model] | --anthropic [model] | --fixture answer.json]
 * </pre>
 * Without a backend option: Ollama when it is reachable, else the Anthropic API when <code>ANTHROPIC_API_KEY</code>
 * is set (CPU-LAW order). Logs through <code>java.util.logging</code> (brief rule 22).
 */
public final class M3ThemeGenerator {
	private static final Logger LOG = Logger.getLogger (M3ThemeGenerator.class.getName ());

	/** The files of one generated theme. */
	public record Generated (M3ThemeCompleter.Theme theme, M3ThemeCompleter.Theme twin, M3ThemeSpec spec, String answer, Path directory) {
		/** The file name stem: the theme name in lower case with dashes. */
		public String stem () {
			return theme.name ().toLowerCase (Locale.ROOT).replaceAll ("[^a-z0-9]+", "-").replaceAll ("^-|-$", "");
		}
	}

	private final M3ThemeModel model;

	public M3ThemeGenerator (M3ThemeModel model) {
		this.model = model;
	}

	/**
	 * Generates a theme.
	 *
	 * @param prompt the description
	 * @param brand brand colours (ARGB) the prompt mentions, or empty
	 * @param image a reference image, or <code>null</code>
	 * @param name a name that overrides the model's, or <code>null</code>
	 * @param twin whether to complete the other brightness too
	 * @param directory where the files go, or <code>null</code> for none
	 * @throws IOException when the model fails or a file cannot be written
	 * @throws IllegalArgumentException when the answer breaks the schema
	 */
	public Generated generate (String prompt, List<Integer> brand, M3ThemeModel.Image image, String name, boolean twin, Path directory) throws IOException {
		String asked = prompt (prompt, brand);
		LOG.info (() -> "asking " + model.name ());
		String answer = model.answer (asked, image);
		if (LOG.isLoggable (Level.FINE)) LOG.fine (() -> "answer: " + answer);
		M3ThemeSpec spec = M3ThemeSpec.parse (strip (answer));
		if (name != null) spec = spec.withName (name);
		M3ThemeCompleter.Theme theme = M3ThemeCompleter.complete (spec);
		M3ThemeCompleter.Theme other = twin ? M3ThemeCompleter.twin (spec) : null;
		for (String note : theme.notes ()) LOG.info (() -> "completer: " + note);
		Generated generated = new Generated (theme, other, spec, answer, directory);
		if (directory != null) write (generated);
		return generated;
	}

	/** Writes the accepted JSON and the three theme files (and the twin's) into the directory. */
	public static void write (Generated generated) throws IOException {
		Path directory = generated.directory ();
		Files.createDirectories (directory);
		String stem = generated.stem ();
		Files.writeString (directory.resolve (stem + ".accepted.json"), generated.spec ().canonical () + "\n", StandardCharsets.UTF_8);
		files (directory, stem, generated.theme ());
		if (generated.twin () != null) files (directory, stem + (generated.twin ().dark () ? "-dark" : "-light"), generated.twin ());
		LOG.info (() -> "wrote " + stem + ".{lf2.properties,flatlaf.properties,theme.json,accepted.json} to " + directory);
	}

	private static void files (Path directory, String stem, M3ThemeCompleter.Theme theme) throws IOException {
		Files.writeString (directory.resolve (stem + ".lf2.properties"), M3ThemeExport.lf2 (theme), StandardCharsets.UTF_8);
		Files.writeString (directory.resolve (stem + ".flatlaf.properties"), M3ThemeExport.flatLaf (theme), StandardCharsets.UTF_8);
		Files.writeString (directory.resolve (stem + ".theme.json"), M3ThemeExport.intelliJ (theme), StandardCharsets.UTF_8);
	}

	/** The prompt sent: the description, then the brand colours as hex. */
	static String prompt (String prompt, List<Integer> brand) {
		if (brand == null || brand.isEmpty ()) return prompt;
		StringBuilder out = new StringBuilder (prompt).append ("\nBrand colours to build on: ");
		for (int i = 0; i < brand.size (); i++) out.append (i == 0 ? "" : ", ").append (M3Palette.text (brand.get (i)));
		return out.toString ();
	}

	/* a model that wraps the JSON in a code fence or prose: the first object is taken */
	static String strip (String answer) {
		int start = answer.indexOf ('{'), end = answer.lastIndexOf ('}');
		return start >= 0 && end > start ? answer.substring (start, end + 1) : answer;
	}

	/** The backend the options ask for, or the first available one (Ollama, then the Anthropic API). */
	public static M3ThemeModel model (Map<String, String> options) {
		if (options.containsKey ("fixture")) return new M3FixtureModel (Paths.get (options.get ("fixture")));
		if (options.containsKey ("ollama")) return ollama (options.get ("ollama"));
		if (options.containsKey ("anthropic")) return anthropic (options.get ("anthropic"));
		M3OllamaModel ollama = new M3OllamaModel ();
		if (ollama.reachable ()) return ollama;
		if (M3AnthropicModel.configured ()) return new M3AnthropicModel ();
		throw new IllegalStateException ("no backend: start Ollama (" + System.getProperty (M3OllamaModel.URL, "http://localhost:11434") + "), set "
			+ M3AnthropicModel.KEY + ", or pass --fixture <answer.json>");
	}

	private static M3ThemeModel ollama (String model) {
		return model == null || model.isEmpty () ? new M3OllamaModel () : new M3OllamaModel (System.getProperty (M3OllamaModel.URL, "http://localhost:11434"), model);
	}

	private static M3ThemeModel anthropic (String model) {
		return model == null || model.isEmpty () ? new M3AnthropicModel () : new M3AnthropicModel (model);
	}

	/** The options of a command line: <code>--key value</code> and <code>--flag</code> (value <code>""</code>). */
	static Map<String, String> options (String [] args) {
		Map<String, String> options = new LinkedHashMap<> ();
		for (int i = 0; i < args.length; i++) {
			if (!args [i].startsWith ("--")) throw new IllegalArgumentException ("unexpected argument " + args [i]);
			String key = args [i].substring (2);
			String value = i + 1 < args.length && !args [i + 1].startsWith ("--") ? args [++i] : "";
			options.put (key, value);
		}
		return options;
	}

	static List<Integer> brand (String text) {
		List<Integer> colours = new ArrayList<> ();
		if (text == null || text.isBlank ()) return colours;
		for (String part : text.split (",")) colours.add (hex (part.trim ()));
		return colours;
	}

	/* #rgb or #rrggbb as opaque ARGB */
	static int hex (String text) {
		String digits = text.startsWith ("#") ? text.substring (1) : text;
		if (digits.length () == 3) digits = "" + digits.charAt (0) + digits.charAt (0) + digits.charAt (1) + digits.charAt (1) + digits.charAt (2) + digits.charAt (2);
		if (!digits.matches ("[0-9a-fA-F]{6}")) throw new IllegalArgumentException ("not a colour: " + text);
		return 0xFF000000 | Integer.parseInt (digits, 16);
	}

	static M3ThemeModel.Image image (String file) throws IOException {
		if (file == null || file.isEmpty ()) return null;
		String lower = file.toLowerCase (Locale.ROOT);
		return new M3ThemeModel.Image (Files.readAllBytes (Paths.get (file)), lower.endsWith (".jpg") || lower.endsWith (".jpeg") ? "image/jpeg" : "image/png");
	}

	public static void main (String [] args) {
		if (System.getProperty ("java.util.logging.config.file") == null && System.getProperty ("java.util.logging.SimpleFormatter.format") == null) {
			System.setProperty ("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
		}
		Map<String, String> options = options (args);
		if (!options.containsKey ("prompt") || options.containsKey ("help")) {
			LOG.info ("usage: M3ThemeGenerator --prompt \"...\" --out <directory> [--brand #hex[,#hex]] [--image file.png] [--twin] [--name name]"
				+ " [--ollama [model] | --anthropic [model] | --fixture answer.json]");
			System.exit (options.containsKey ("help") ? 0 : 2);
			return;
		}
		try {
			M3ThemeGenerator generator = new M3ThemeGenerator (model (options));
			Path out = options.containsKey ("out") ? Paths.get (options.get ("out")) : null;
			Generated generated = generator.generate (options.get ("prompt"), brand (options.get ("brand")), image (options.get ("image")), options.get ("name"),
				options.containsKey ("twin"), out);
			LOG.info (() -> "theme " + generated.theme ().name () + " (" + (generated.theme ().dark () ? "dark" : "light") + "), sha256 " + generated.theme ().hash ());
		} catch (IOException | IllegalArgumentException | IllegalStateException e) {
			LOG.log (Level.SEVERE, e.getMessage (), LOG.isLoggable (Level.FINE) ? e : null);
			System.exit (1);
		}
	}
}
