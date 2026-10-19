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

/**
 * A recorded answer (docs/m3/look-and-feel/GENERATOR.md): the tests and the examples use it, so the suite never needs
 * the network. The file holds the model's answer text as it was recorded; the prompt is logged and ignored.
 */
public final class M3FixtureModel implements M3ThemeModel {
	private final Path file;
	private String prompt;

	public M3FixtureModel (Path file) {
		this.file = file;
	}

	/** The last prompt asked. */
	public String lastPrompt () {
		return prompt;
	}

	@Override
	public String name () {
		return "fixture " + file.getFileName ();
	}

	@Override
	public String answer (String prompt, Image image) throws IOException {
		this.prompt = prompt;
		return Files.readString (file, StandardCharsets.UTF_8);
	}
}
