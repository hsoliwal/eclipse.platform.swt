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
package org.eclipse.swt.internal;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Loads the classes of the M3 fragment of <code>org.eclipse.swt</code> by name (bundle <code>org.eclipse.swt.m3</code>:
 * the Render M3 and GPU M3 trees, with the graphics library jars nested under <code>lib/</code>). Every host site that starts an M3
 * backend goes through here: <code>RowBackends</code>, <code>ExternalCanvasHandler.registerFactory</code> and
 * <code>PlaneBackend.load</code>.
 * <p>
 * Leaf 18 (pattern FRAGMENT_CLASSPATH v1). Under OSGi the fragment joins the class space of the host and its
 * <code>Bundle-ClassPath</code> lists the nested jars, so the SWT class loader already sees M3 and jdk.m3.graphics; a plain
 * class path that already holds jdk.m3.graphics behaves the same, and both keep the SWT class loader. On a plain class path
 * with only the built jars (<code>swt.jar</code> and the fragment jar) no JDK class loader opens nested jars: the
 * fragment's own <code>Bundle-ClassPath</code> is read, each nested jar is extracted once into a directory under
 * <code>java.io.tmpdir</code> named by the entries' CRC-32 and size, and the M3 packages load child-first from the
 * fragment jar, linked against the extracted library jars; host packages always come from the SWT class loader. Any
 * failure keeps the SWT class loader, so the lookup fails as it did before and the caller falls back to stock.
 * </p>
 */
public final class M3Fragment {
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.internal.M3Fragment");
	/** A class that every M3 fragment holds: its location names the fragment jar. */
	static final String MARKER = "org/eclipse/swt/internal/render/m3/M3CanvasHandler.class";
	/** A jdk.m3.graphics class: when the SWT class loader sees it, the class path already provides the library. */
	static final String M3_GRAPHICS = "jdk.m3.graphics.M3Surface";
	/** The packages of the fragment; they load child-first so that they link against the nested library jars. */
	static final String [] FRAGMENT_PACKAGES = {"org.eclipse.swt.internal.render.m3.", "org.eclipse.swt.internal.gpu.m3."};
	private static ClassLoader loader;

	private M3Fragment () {
	}

	/**
	 * The initialized class of the M3 fragment with this name.
	 *
	 * @param className the binary class name
	 * @return the class
	 * @throws ClassNotFoundException when no fragment provides the class
	 */
	public static Class<?> forName (String className) throws ClassNotFoundException {
		return Class.forName (className, true, loader ());
	}

	static synchronized ClassLoader loader () {
		if (loader == null) loader = resolve (M3Fragment.class.getClassLoader ());
		return loader;
	}

	static ClassLoader resolve (ClassLoader swt) {
		if (visible (swt)) return swt;
		URL marker = swt.getResource (MARKER);
		if (marker == null || !"jar".equals (marker.getProtocol ())) return swt;
		try {
			return nested (swt, marker);
		} catch (IOException | URISyntaxException | RuntimeException e) {
			LOG.info (() -> "M3 fragment: no nested class path (" + e + "); M3 classes load from the SWT class loader");
			return swt;
		}
	}

	static boolean visible (ClassLoader swt) {
		try {
			Class.forName (M3_GRAPHICS, false, swt);
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	static ClassLoader nested (ClassLoader swt, URL marker) throws IOException, URISyntaxException {
		Path fragment = Path.of (((JarURLConnection) marker.openConnection ()).getJarFileURL ().toURI ());
		try (JarFile jar = new JarFile (fragment.toFile ())) {
			List<JarEntry> entries = bundleClassPath (jar);
			if (entries.isEmpty ()) return swt;
			Path dir = Path.of (System.getProperty ("java.io.tmpdir"), "swt-m3-" + key (entries));
			List<URL> urls = new ArrayList<> ();
			urls.add (fragment.toUri ().toURL ());
			for (JarEntry entry : entries) urls.add (extract (jar, entry, dir).toUri ().toURL ());
			LOG.info (() -> "M3 fragment: " + fragment + " Bundle-ClassPath " + entries.size () + " nested jars in " + dir);
			return new FragmentLoader (urls.toArray (URL []::new), swt);
		}
	}

	/** The nested jars on the fragment's <code>Bundle-ClassPath</code>, in order (<code>.</code> excluded). */
	static List<JarEntry> bundleClassPath (JarFile jar) throws IOException {
		Manifest manifest = jar.getManifest ();
		String value = manifest == null ? null : manifest.getMainAttributes ().getValue ("Bundle-ClassPath");
		List<JarEntry> entries = new ArrayList<> ();
		if (value == null) return entries;
		for (String element : value.split (",")) {
			String name = element.split (";") [0].trim ();
			if (name.isEmpty () || ".".equals (name)) continue;
			JarEntry entry = jar.getJarEntry (name);
			if (entry == null) throw new FileNotFoundException (name + " (Bundle-ClassPath) in " + jar.getName ());
			entries.add (entry);
		}
		return entries;
	}

	/** Content address of the nested jars: CRC-32 over each entry's name, CRC-32 and size. */
	static String key (List<JarEntry> entries) {
		CRC32 crc = new CRC32 ();
		for (JarEntry entry : entries) {
			crc.update ((entry.getName () + '\0' + entry.getCrc () + '\0' + entry.getSize () + '\0').getBytes (StandardCharsets.UTF_8));
		}
		return Long.toHexString (crc.getValue ());
	}

	/** The nested jar as a file in {@code dir}: reused when present with the entry's size, else written and moved in. */
	static Path extract (JarFile jar, JarEntry entry, Path dir) throws IOException {
		Path target = dir.resolve (Path.of (entry.getName ()).getFileName ().toString ());
		if (complete (target, entry)) return target;
		Files.createDirectories (dir);
		Path part = Files.createTempFile (dir, target.getFileName ().toString (), ".part");
		try (InputStream in = jar.getInputStream (entry)) {
			Files.copy (in, part, StandardCopyOption.REPLACE_EXISTING);
			Files.move (part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (FileSystemException e) {
			/* another process extracted the same jar first and holds it open */
			if (!complete (target, entry)) throw e;
		} finally {
			Files.deleteIfExists (part);
		}
		return target;
	}

	static boolean complete (Path file, JarEntry entry) throws IOException {
		return Files.isRegularFile (file) && Files.size (file) == entry.getSize ();
	}

	static boolean fragmentPackage (String name) {
		for (String prefix : FRAGMENT_PACKAGES) {
			if (name.startsWith (prefix)) return true;
		}
		return false;
	}

	/** Child-first for the fragment's packages (the fragment jar is also on the parent's class path), parent-first else. */
	static final class FragmentLoader extends URLClassLoader {
		static {
			registerAsParallelCapable ();
		}

		FragmentLoader (URL [] urls, ClassLoader parent) {
			super ("org.eclipse.swt.m3", urls, parent);
		}

		@Override
		protected Class<?> loadClass (String name, boolean resolve) throws ClassNotFoundException {
			if (!fragmentPackage (name)) return super.loadClass (name, resolve);
			synchronized (getClassLoadingLock (name)) {
				Class<?> type = findLoadedClass (name);
				if (type == null) type = findClass (name);
				if (resolve) resolveClass (type);
				return type;
			}
		}
	}
}
