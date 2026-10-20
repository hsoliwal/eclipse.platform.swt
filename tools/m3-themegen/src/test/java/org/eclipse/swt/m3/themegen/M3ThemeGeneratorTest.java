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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import javax.swing.*;

import org.eclipse.swt.internal.laf.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.*;

/**
 * The generator pipeline with recorded answers (never the network), the Ollama client against a local HTTP server,
 * the Anthropic client's refusal to run without the environment key, the command line options and the Swing applier.
 */
public class M3ThemeGeneratorTest {
	static final Path FIXTURES = Paths.get ("src", "test", "resources", "fixtures");

	@Test
	public void aRecordedAnswerBecomesThreeFiles (@TempDir Path out) throws IOException {
		M3FixtureModel model = new M3FixtureModel (FIXTURES.resolve ("harbour-night.json"));
		M3ThemeGenerator.Generated generated = new M3ThemeGenerator (model).generate ("a calm harbour at night", List.of (0xFF5E81AC), null, null, true, out);
		assertTrue (model.lastPrompt ().endsWith ("\nBrand colours to build on: #5e81ac"), model.lastPrompt ());
		assertEquals ("Harbour Night", generated.theme ().name ());
		assertEquals ("harbour-night", generated.stem ());
		assertTrue (generated.theme ().dark ());
		assertFalse (generated.twin ().dark ());
		for (String file : new String [] {"harbour-night.accepted.json", "harbour-night.lf2.properties", "harbour-night.flatlaf.properties", "harbour-night.theme.json",
			"harbour-night-light.lf2.properties", "harbour-night-light.flatlaf.properties", "harbour-night-light.theme.json"}) {
			assertTrue (Files.exists (out.resolve (file)), file);
		}
		String lf2 = Files.readString (out.resolve ("harbour-night.lf2.properties"), StandardCharsets.UTF_8);
		assertTrue (lf2.contains ("sha256 " + generated.theme ().hash ()), lf2);
		assertEquals (generated.spec ().canonical () + "\n", Files.readString (out.resolve ("harbour-night.accepted.json"), StandardCharsets.UTF_8));
		M3LookAndFeel.register ("harbour-night", lf2, null);
		int selection = M3UIKeys.intern ("Table.selectionBackground", M3UIKeys.COLOR);
		assertEquals (0xFF3B4252, M3LookAndFeel.defaults ("harbour-night").color (selection, 0), "the SWT side reads the generated table");
	}

	@Test
	public void aFencedLowContrastAnswerIsStrippedAndFixed () throws IOException {
		M3ThemeGenerator.Generated generated = new M3ThemeGenerator (new M3FixtureModel (FIXTURES.resolve ("fenced-low-contrast.json"))).generate ("fog", List.of (), null, "Fog Fixed", false, null);
		assertEquals ("Fog Fixed", generated.theme ().name (), "the name option overrides the model's");
		assertNull (generated.twin ());
		assertTrue (generated.theme ().notes ().stream ().anyMatch (note -> note.startsWith ("border: unreadable colour")), generated.theme ().notes ().toString ());
		int foreground = 0xFF000000 | Integer.parseInt (generated.theme ().table ().get ("Component.foreground").substring (1), 16);
		int background = 0xFF000000 | Integer.parseInt (generated.theme ().table ().get ("Component.background").substring (1), 16);
		assertTrue (M3Palette.contrast (foreground, background) >= 4.5);
		assertEquals ("1,4,1,4", generated.theme ().table ().get ("Text.margin"), "compact");
	}

	@Test
	public void aBrokenAnswerIsRejected (@TempDir Path out) throws IOException {
		Path broken = out.resolve ("broken.json");
		Files.writeString (broken, "{\"dark\": true, \"palette\": {\"text\": \"#fff\"}}", StandardCharsets.UTF_8);
		IllegalArgumentException e = assertThrows (IllegalArgumentException.class,
			() -> new M3ThemeGenerator (new M3FixtureModel (broken)).generate ("x", List.of (), null, null, false, null));
		assertEquals ("unknown key palette.text", e.getMessage ());
		assertEquals ("no json here", M3ThemeGenerator.strip ("no json here"));
	}

	@Test
	public void theOllamaClientTalksToTheApi () throws IOException {
		HttpServer server = HttpServer.create (new InetSocketAddress ("127.0.0.1", 0), 0);
		List<String> bodies = new ArrayList<> ();
		server.createContext ("/api/tags", exchange -> {
			exchange.sendResponseHeaders (200, 2);
			exchange.getResponseBody ().write ("{}".getBytes (StandardCharsets.UTF_8));
			exchange.close ();
		});
		server.createContext ("/api/generate", exchange -> {
			String body = new String (exchange.getRequestBody ().readAllBytes (), StandardCharsets.UTF_8);
			bodies.add (body);
			byte [] answer = (body.contains ("\"fail\"") ? "{\"error\": \"model not found\"}" : body.contains ("\"nothing\"") ? "{}"
				: "{\"model\": \"m\", \"response\": \"{\\\"dark\\\": true, \\\"palette\\\": {\\\"accent\\\": \\\"#112233\\\"}}\", \"done\": true}").getBytes (StandardCharsets.UTF_8);
			exchange.sendResponseHeaders (body.contains ("\"http500\"") ? 500 : 200, answer.length);
			exchange.getResponseBody ().write (answer);
			exchange.close ();
		});
		server.start ();
		try {
			M3OllamaModel model = new M3OllamaModel ("http://127.0.0.1:" + server.getAddress ().getPort () + "/", "m");
			assertTrue (model.reachable ());
			assertEquals ("ollama m at http://127.0.0.1:" + server.getAddress ().getPort (), model.name ());
			String answer = model.answer ("a theme", new M3ThemeModel.Image (new byte [] {1, 2, 3}, "image/png"));
			assertEquals (0xFF112233, M3ThemeSpec.parse (answer).colour (2));
			assertTrue (bodies.get (0).contains ("\"images\":[\"AQID\"]"), bodies.get (0));
			assertTrue (bodies.get (0).contains ("\"format\":\"json\"") && bodies.get (0).contains ("\"stream\":false"), bodies.get (0));
			assertTrue (bodies.get (0).contains ("You design user interface colour themes"));
			assertEquals ("ollama m at http://127.0.0.1:" + server.getAddress ().getPort () + " error: model not found", assertThrows (IOException.class, () -> model.answer ("fail", null)).getMessage ());
			assertTrue (assertThrows (IOException.class, () -> model.answer ("nothing", null)).getMessage ().endsWith ("answered without a response member"));
			assertTrue (assertThrows (IOException.class, () -> model.answer ("http500", null)).getMessage ().contains ("HTTP 500"));
		} finally {
			server.stop (0);
		}
		M3OllamaModel down = new M3OllamaModel ("http://127.0.0.1:1", "m");
		assertFalse (down.reachable ());
		assertThrows (IOException.class, () -> down.answer ("x", null));
	}

	@Test
	public void theAnthropicClientNeedsTheEnvironmentKey () {
		Assumptions.assumeFalse (M3AnthropicModel.configured (), "a key is set in this environment");
		IllegalStateException e = assertThrows (IllegalStateException.class, M3AnthropicModel::new);
		assertTrue (e.getMessage ().startsWith ("set the environment variable ANTHROPIC_API_KEY"), e.getMessage ());
		assertEquals ("claude-sonnet-5-5", M3AnthropicModel.DEFAULT_MODEL);
		String schema = M3AnthropicModel.schema ().toString ();
		assertTrue (schema.contains ("additionalProperties") && schema.contains ("warning") && schema.contains ("comfortable"), schema);
		assertThrows (IllegalStateException.class, () -> M3ThemeGenerator.model (Map.of ("anthropic", "")));
	}

	@Test
	public void theCommandLineOptions () throws IOException {
		Map<String, String> options = M3ThemeGenerator.options (new String [] {"--prompt", "a b", "--twin", "--brand", "#123456,#abc", "--out", "x"});
		assertEquals ("a b", options.get ("prompt"));
		assertEquals ("", options.get ("twin"));
		assertEquals (List.of (0xFF123456, 0xFFAABBCC), M3ThemeGenerator.brand (options.get ("brand")));
		assertEquals (List.of (), M3ThemeGenerator.brand (null));
		assertThrows (IllegalArgumentException.class, () -> M3ThemeGenerator.options (new String [] {"prompt"}));
		assertNull (M3ThemeGenerator.image (""));
		assertEquals ("image/png", M3ThemeGenerator.image (FIXTURES.resolve ("harbour-night.json").toString ()).mediaType ());
		assertThrows (IllegalArgumentException.class, () -> M3ThemeGenerator.brand ("#12"));
		assertInstanceOf (M3FixtureModel.class, M3ThemeGenerator.model (Map.of ("fixture", "x.json")));
		assertInstanceOf (M3OllamaModel.class, M3ThemeGenerator.model (Map.of ("ollama", "")));
		assertEquals ("ollama phi at http://localhost:11434", M3ThemeGenerator.model (Map.of ("ollama", "phi")).name ());
		if (!M3AnthropicModel.configured () && !new M3OllamaModel ().reachable ()) {
			assertTrue (assertThrows (IllegalStateException.class, () -> M3ThemeGenerator.model (Map.of ())).getMessage ().startsWith ("no backend"));
		}
	}

	@Test
	public void theSwingApplierFillsUIManager () throws IOException {
		M3ThemeGenerator.Generated generated = new M3ThemeGenerator (new M3FixtureModel (FIXTURES.resolve ("harbour-night.json"))).generate ("x", List.of (), null, null, false, null);
		int entries = M3SwingTheme.apply (M3ThemeExport.flatLaf (generated.theme ()));
		assertTrue (entries > 100, "entries " + entries);
		assertEquals (new java.awt.Color (0x3B, 0x42, 0x52), UIManager.getColor ("Table.selectionBackground"));
		assertEquals (UIManager.getColor ("Table.selectionBackground"), UIManager.getColor ("textHighlight"));
		assertEquals (UIManager.getColor ("Table.headerBackground"), UIManager.getColor ("TableHeader.background"));
		assertNotNull (UIManager.get ("Button.margin"));
		assertEquals (Integer.valueOf (generated.theme ().table ().get ("Table.rowHeight")), UIManager.get ("Table.rowHeight"));
		assertEquals (Boolean.FALSE, UIManager.get ("Table.showGrid"));
		assertEquals (List.of ("Table.gridColor"), M3SwingTheme.targets ("Table.gridColor"));
		assertTrue (M3SwingTheme.targets ("Button.borderColor").contains ("Button.border"));
		assertFalse (M3SwingTheme.targets ("Component.background").contains ("Component.background"));
	}
}
