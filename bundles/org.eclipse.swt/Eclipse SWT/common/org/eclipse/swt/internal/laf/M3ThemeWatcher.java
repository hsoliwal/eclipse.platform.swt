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
package org.eclipse.swt.internal.laf;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;

/**
 * Live reload of a theme file during development (docs/m3/LOOK_AND_FEEL.md, LF6, improvement 4): the file is
 * installed as a look and feel at once and again whenever it changes on disk, and the widgets of the display follow
 * ({@link M3LookAndFeel#reload}: only the widgets whose delegate reads a changed key). A <code>.json</code> file is an
 * IntelliJ theme, any other a FlatLaf or LF2 properties theme ({@link M3ThemeImport}). A file that cannot be read keeps
 * the last good theme (logged).
 */
public final class M3ThemeWatcher implements AutoCloseable {
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.internal.laf.M3ThemeWatcher");

	private final Display display;
	private final Path file;
	private final String name;
	private final WatchService service;
	private final Thread thread;
	private volatile int reloads, failures;

	private M3ThemeWatcher (Display display, Path file, String name) throws IOException {
		this.display = display;
		this.file = file.toAbsolutePath ();
		this.name = name;
		this.service = this.file.getFileSystem ().newWatchService ();
		this.file.getParent ().register (service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
		this.thread = new Thread (this::watch, "SWT theme watcher " + name);
		thread.setDaemon (true);
	}

	/**
	 * Installs a theme file as a look and feel and reloads it whenever it changes. Call on the display's thread.
	 *
	 * @throws IOException when the file cannot be read or watched
	 * @throws IllegalArgumentException when the file is not a theme
	 */
	public static M3ThemeWatcher watch (Display display, Path file, String name) throws IOException {
		M3ThemeImport.install (name, imported (file, Files.readString (file, StandardCharsets.UTF_8)));
		M3ThemeWatcher watcher = new M3ThemeWatcher (display, file, name);
		watcher.thread.start ();
		return watcher;
	}

	/** The number of reloads applied on the display thread. */
	public int reloads () {
		return reloads;
	}

	/** The number of changes that could not be read (the last good theme stayed). */
	public int failures () {
		return failures;
	}

	/** Stops watching. */
	@Override
	public void close () throws IOException {
		service.close ();
	}

	private void watch () {
		try {
			while (true) {
				WatchKey key = service.take ();
				boolean changed = false;
				for (WatchEvent<?> event : key.pollEvents ()) {
					if (file.getFileName ().equals (event.context ())) changed = true;
				}
				key.reset ();
				if (changed) reload ();
			}
		} catch (ClosedWatchServiceException | InterruptedException e) {
			/* closed */
		}
	}

	private void reload () {
		String text;
		try {
			text = M3ThemeWatcher.imported (file, Files.readString (file, StandardCharsets.UTF_8)).text ();
		} catch (IOException | IllegalArgumentException e) {
			failures++;
			LOG.info (() -> "theme " + name + " not reloaded from " + file + ": " + e.getMessage ());
			return;
		}
		if (display.isDisposed ()) return;
		try {
			display.asyncExec (() -> {
				M3LookAndFeel.reload (name, text);
				reloads++;
			});
		} catch (org.eclipse.swt.SWTException e) {
			/* the display was disposed meanwhile */
		}
	}

	static M3ThemeImport.Imported imported (Path file, String text) {
		return file.getFileName ().toString ().endsWith (".json") ? M3ThemeImport.intelliJ (text) : M3ThemeImport.flatLaf (text);
	}
}
