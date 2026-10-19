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
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

import com.fasterxml.jackson.databind.*;

/**
 * A local Ollama model (docs/m3/look-and-feel/GENERATOR.md; CPU-LAW: local first): <code>POST /api/generate</code>
 * with <code>format: json</code> and <code>stream: false</code>, the image as base64 in <code>images</code>. The
 * endpoint comes from <code>-Dm3.themegen.ollama.url</code> (default <code>http://localhost:11434</code>), the model
 * from <code>-Dm3.themegen.ollama.model</code> (default <code>llama3.2</code>).
 */
public final class M3OllamaModel implements M3ThemeModel {
	/** The property of the endpoint. */
	public static final String URL = "m3.themegen.ollama.url";
	/** The property of the model name. */
	public static final String MODEL = "m3.themegen.ollama.model";
	private static final Logger LOG = Logger.getLogger (M3OllamaModel.class.getName ());
	private static final ObjectMapper JSON = new ObjectMapper ();

	private final HttpClient client;
	private final String url, model;

	/** The model of the properties. */
	public M3OllamaModel () {
		this (System.getProperty (URL, "http://localhost:11434"), System.getProperty (MODEL, "llama3.2"));
	}

	public M3OllamaModel (String url, String model) {
		this.url = url.endsWith ("/") ? url.substring (0, url.length () - 1) : url;
		this.model = model;
		this.client = HttpClient.newBuilder ().connectTimeout (Duration.ofSeconds (5)).build ();
	}

	/** True when the endpoint answers <code>GET /api/tags</code>. */
	public boolean reachable () {
		try {
			HttpRequest request = HttpRequest.newBuilder (URI.create (url + "/api/tags")).timeout (Duration.ofSeconds (5)).GET ().build ();
			return client.send (request, HttpResponse.BodyHandlers.discarding ()).statusCode () == 200;
		} catch (IOException | InterruptedException | IllegalArgumentException e) {
			if (LOG.isLoggable (Level.FINE)) LOG.fine ("Ollama at " + url + " not reachable: " + e);
			return false;
		}
	}

	@Override
	public String name () {
		return "ollama " + model + " at " + url;
	}

	@Override
	public String answer (String prompt, Image image) throws IOException {
		Map<String, Object> body = new LinkedHashMap<> ();
		body.put ("model", model);
		body.put ("system", SYSTEM);
		body.put ("prompt", prompt);
		body.put ("format", "json");
		body.put ("stream", Boolean.FALSE);
		if (image != null) body.put ("images", List.of (Base64.getEncoder ().encodeToString (image.bytes ())));
		HttpRequest request = HttpRequest.newBuilder (URI.create (url + "/api/generate")).timeout (Duration.ofMinutes (5))
			.header ("Content-Type", "application/json")
			.POST (HttpRequest.BodyPublishers.ofString (JSON.writeValueAsString (body), StandardCharsets.UTF_8)).build ();
		HttpResponse<String> response;
		try {
			response = client.send (request, HttpResponse.BodyHandlers.ofString (StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread ().interrupt ();
			throw new IOException ("interrupted while waiting for " + name ());
		}
		if (response.statusCode () != 200) throw new IOException (name () + " answered HTTP " + response.statusCode () + ": " + response.body ());
		JsonNode node = JSON.readTree (response.body ());
		if (node.hasNonNull ("error")) throw new IOException (name () + " error: " + node.get ("error").asText ());
		if (!node.hasNonNull ("response")) throw new IOException (name () + " answered without a response member");
		if (LOG.isLoggable (Level.FINE)) LOG.fine (name () + " answered " + node.get ("response").asText ().length () + " characters");
		return node.get ("response").asText ();
	}
}
