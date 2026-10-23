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
import java.util.*;
import java.util.logging.*;

import com.anthropic.client.*;
import com.anthropic.client.okhttp.*;
import com.anthropic.core.*;
import com.anthropic.errors.*;
import com.anthropic.models.messages.*;

/**
 * The Anthropic API through the official Java SDK (<code>com.anthropic:anthropic-java</code>;
 * docs/m3/look-and-feel/GENERATOR.md): one <code>messages.create</code> with the system prompt, the user prompt, the
 * optional image and the closed theme schema as the output format. The model id comes from
 * <code>-Dm3.themegen.anthropic.model</code> (default <code>claude-sonnet-5-5</code>); the key comes only from the
 * <code>ANTHROPIC_API_KEY</code> environment variable (never from code or a property).
 */
public final class M3AnthropicModel implements M3ThemeModel {
	/** The property of the model id. */
	public static final String MODEL = "m3.themegen.anthropic.model";
	/** The default model id. */
	public static final String DEFAULT_MODEL = "claude-sonnet-5-5";
	/** The environment variable of the key. */
	public static final String KEY = "ANTHROPIC_API_KEY";
	private static final Logger LOG = Logger.getLogger (M3AnthropicModel.class.getName ());

	private final AnthropicClient client;
	private final String model;

	/**
	 * The model of the property, with the key of the environment.
	 *
	 * @throws IllegalStateException when {@value #KEY} is not set
	 */
	public M3AnthropicModel () {
		this (System.getProperty (MODEL, DEFAULT_MODEL));
	}

	public M3AnthropicModel (String model) {
		String key = System.getenv (KEY);
		if (key == null || key.isBlank ()) throw new IllegalStateException ("set the environment variable " + KEY + " (the key is never read from code or a property)");
		this.client = AnthropicOkHttpClient.builder ().apiKey (key).build ();
		this.model = model;
	}

	/** True when the environment has a key. */
	public static boolean configured () {
		String key = System.getenv (KEY);
		return key != null && !key.isBlank ();
	}

	@Override
	public String name () {
		return "anthropic " + model;
	}

	@Override
	public String answer (String prompt, Image image) throws IOException {
		List<ContentBlockParam> content = new ArrayList<> ();
		if (image != null) {
			Base64ImageSource source = Base64ImageSource.builder ().data (Base64.getEncoder ().encodeToString (image.bytes ()))
				.mediaType (image.mediaType ().equals ("image/jpeg") ? Base64ImageSource.MediaType.IMAGE_JPEG : Base64ImageSource.MediaType.IMAGE_PNG).build ();
			content.add (ContentBlockParam.ofImage (ImageBlockParam.builder ().source (source).build ()));
		}
		content.add (ContentBlockParam.ofText (prompt));
		MessageCreateParams params = MessageCreateParams.builder ().model (model).maxTokens (2048L).system (SYSTEM)
			.addUserMessageOfBlockParams (content).outputConfig (OutputConfig.builder ().format (JsonOutputFormat.builder ().schema (schema ()).build ()).build ()).build ();
		Message message;
		try {
			message = client.messages ().create (params);
		} catch (AnthropicServiceException e) {
			throw new IOException (name () + " answered " + e.statusCode () + " " + e.errorType ().map (Object::toString).orElse ("") + ": " + e.getMessage (), e);
		} catch (AnthropicException e) {
			throw new IOException (name () + ": " + e.getMessage (), e);
		}
		StringBuilder text = new StringBuilder ();
		for (ContentBlock block : message.content ()) block.text ().ifPresent (t -> text.append (t.text ()));
		if (LOG.isLoggable (Level.FINE)) LOG.fine (name () + " answered " + text.length () + " characters, stop " + message.stopReason ().map (Object::toString).orElse ("none"));
		if (text.length () == 0) throw new IOException (name () + " answered no text (stop " + message.stopReason ().map (Object::toString).orElse ("none") + ")");
		return text.toString ();
	}

	/* the closed theme schema as JSON schema (the API validates the answer against it) */
	static JsonOutputFormat.Schema schema () {
		Map<String, Object> colour = Map.of ("type", "string", "pattern", "^#[0-9a-fA-F]{6}$");
		Map<String, Object> palette = new LinkedHashMap<> ();
		for (String slot : org.eclipse.swt.internal.laf.M3ThemeSpec.PALETTE) palette.put (slot, colour);
		Map<String, Object> properties = new LinkedHashMap<> ();
		properties.put ("name", Map.of ("type", "string"));
		properties.put ("dark", Map.of ("type", "boolean"));
		properties.put ("palette", Map.of ("type", "object", "properties", palette, "additionalProperties", false));
		properties.put ("font", Map.of ("type", "object", "properties", Map.of ("family", Map.of ("type", "string"), "size", Map.of ("type", "integer", "minimum", 6, "maximum", 32)), "additionalProperties", false));
		properties.put ("density", Map.of ("type", "string", "enum", org.eclipse.swt.internal.laf.M3ThemeSpec.DENSITIES));
		return JsonOutputFormat.Schema.builder ().putAdditionalProperty ("type", JsonValue.from ("object")).putAdditionalProperty ("properties", JsonValue.from (properties))
			.putAdditionalProperty ("required", JsonValue.from (List.of ("dark"))).putAdditionalProperty ("additionalProperties", JsonValue.from (false)).build ();
	}
}
