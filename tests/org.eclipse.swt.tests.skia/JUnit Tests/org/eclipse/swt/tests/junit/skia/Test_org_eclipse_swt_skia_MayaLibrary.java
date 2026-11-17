/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License 2.0 or the Eclipse Public License 2.0.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit.skia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.eclipse.swt.internal.maya.Paint;
import org.eclipse.swt.internal.maya.impl.Library;
import org.junit.jupiter.api.Test;

/**
 * maya, the native library of the Skia backend: its name, the files the fragment ships beside the classes, the version
 * that names the library's bytes, and the folder it loads from, where Skia's text shaping finds its ICU data.
 */
public class Test_org_eclipse_swt_skia_MayaLibrary {

	private static final String FOLDER = "org/eclipse/swt/internal/maya/windows/x64/";

	private static final String LIBRARY = "maya.dll";

	private static final String ICU_DATA = "icudtl.dat";

	private static final String VERSION = "maya.version";

	private static final String VERSION_PREFIX = "0.116.3-";

	private static final int VERSION_DIGITS = 12;

	@Test
	public void libraryIsMaya() {
		assertEquals("maya", Library.LIBRARY_NAME);
		assertEquals(LIBRARY, System.mapLibraryName(Library.LIBRARY_NAME));
	}

	@Test
	public void fragmentShipsMayaAndNoSkija() {
		for (String name : new String[] { LIBRARY, ICU_DATA, VERSION }) {
			assertNotNull(resource(name), name);
		}
		ClassLoader loader = Library.class.getClassLoader();
		assertNull(loader.getResource("io/github/humbleui/skija/windows/x64/skija.dll"));
		assertThrows(ClassNotFoundException.class, () -> Class.forName("io.github.humbleui.skija.impl.Library"));
	}

	@Test
	public void versionNamesTheLibraryBytes() throws IOException, NoSuchAlgorithmException {
		String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(LIBRARY)));
		assertEquals(VERSION_PREFIX + digest.substring(0, VERSION_DIGITS), Library.readResource(resource(VERSION)));
	}

	/*
	 * The loader takes the library in place from a folder on the class path and extracts it into
	 * <tmp>/maya_<version>_<arch> from a jar; Skia loads icudtl.dat from the folder of the library, so both files must
	 * be there, unchanged.
	 */
	@Test
	public void loadsWithItsIcuDataBesideIt() throws IOException, URISyntaxException {
		try (Paint paint = new Paint()) {
			assertTrue(Library._loaded);
			paint.setStrokeWidth(2);
			assertEquals(2f, paint.getStrokeWidth());
		}
		File folder = loadFolder();
		assertArrayEquals(bytes(LIBRARY), java.nio.file.Files.readAllBytes(new File(folder, LIBRARY).toPath()));
		assertArrayEquals(bytes(ICU_DATA), java.nio.file.Files.readAllBytes(new File(folder, ICU_DATA).toPath()));
	}

	private static File loadFolder() throws URISyntaxException {
		URL library = resource(LIBRARY);
		if ("file".equals(library.getProtocol())) {
			return new File(library.toURI()).getParentFile();
		}
		String version = Library.readResource(resource(VERSION));
		String arch = System.getProperty("os.arch").contains("64") ? "x64" : "x86";
		return new File(System.getProperty("java.io.tmpdir"), "maya_" + version + "_" + arch);
	}

	private static URL resource(String name) {
		return Library.class.getClassLoader().getResource(FOLDER + name);
	}

	private static byte[] bytes(String name) throws IOException {
		try (InputStream in = resource(name).openStream()) {
			return in.readAllBytes();
		}
	}
}
