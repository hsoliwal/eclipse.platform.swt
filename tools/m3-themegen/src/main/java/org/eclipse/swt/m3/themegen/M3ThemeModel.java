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

/**
 * A language model that proposes theme data (docs/m3/look-and-feel/GENERATOR.md): it answers a user prompt under a
 * system prompt, with an optional reference image, as text that must be the closed theme JSON. The answer is untrusted
 * data: the caller parses it strictly ({@link org.eclipse.swt.internal.laf.M3ThemeSpec#parse}).
 */
public interface M3ThemeModel {
	/** The system prompt every backend sends: the schema and the rules. */
	String SYSTEM = """
		You design user interface colour themes. Answer with one JSON object and nothing else, exactly this schema:
		{"name": string, "dark": boolean, "palette": {"background": "#rrggbb", "foreground": "#rrggbb", "accent": "#rrggbb",
		 "selection": "#rrggbb", "border": "#rrggbb", "error": "#rrggbb", "warning": "#rrggbb", "info": "#rrggbb"},
		 "font": {"family": string, "size": integer 6..32}, "density": "compact" | "normal" | "comfortable"}
		Every member is optional except dark. Use only these members. Colours are six-digit hex. Choose a background and
		foreground that read well together and an accent that stands out on the background; the caller fixes contrast.
		""";

	/** A reference image for the model: PNG or JPEG bytes. */
	record Image (byte [] bytes, String mediaType) {
	}

	/** The backend's name for logs and the generated file header. */
	String name ();

	/**
	 * Answers a prompt.
	 *
	 * @param prompt the user's description, with the brand colours appended by the caller
	 * @param image a reference image, or <code>null</code>
	 * @return the answer text (expected: the theme JSON)
	 * @throws IOException when the backend cannot be reached or answers an error
	 */
	String answer (String prompt, Image image) throws IOException;
}
