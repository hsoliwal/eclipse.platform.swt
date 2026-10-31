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
package org.eclipse.swt.m3;

import java.util.function.*;
import java.util.logging.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;

/**
 * Structured access to SWT's asynchronous log (enhanced M3 API): levels by name, writing, and a bounded ring of the
 * most recent records of a logger, including the records SWT's own components write under that name (for example
 * <code>"org.eclipse.swt.widgets.Tree"</code>).
 * <p>
 * Records are published by SWT's log thread (<code>SwtLog</code>): the calling thread computes the message text and
 * never waits for a handler, so nothing here does I/O on the UI thread. The ring keeps the last records as
 * "LEVEL message" strings, oldest first, in a fixed array (no per-record allocation beyond the message itself).
 * Levels map to <code>java.util.logging</code>: TRACE to FINEST, DEBUG to FINE, INFO to INFO.
 * </p>
 *
 * @since 3.138
 */
public final class M3Log {
	/** A level: the most detail. */
	public static final int TRACE = 0;
	/** A level: diagnostic records. */
	public static final int DEBUG = 1;
	/** A level: notable records. */
	public static final int INFO = 2;
	/** A level: nothing. */
	public static final int OFF = 3;

	private static final Level [] LEVELS = {Level.FINEST, Level.FINE, Level.INFO, Level.OFF};
	private static final String [] NAMES = {"TRACE", "DEBUG", "INFO", "OFF"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	private final Logger logger;
	private final SwtLog log;
	private Ring ring;

	/* The kept records: a fixed array written circularly by the log thread. */
	private static final class Ring extends Handler {
		final String [] records;
		int next, size;

		Ring (int capacity) {
			records = new String [capacity];
		}

		@Override
		public synchronized void publish (LogRecord record) {
			records [next] = name (record.getLevel ()) + ' ' + record.getMessage ();
			next = (next + 1) % records.length;
			size = Math.min (size + 1, records.length);
		}

		synchronized String [] snapshot () {
			String [] result = new String [size];
			for (int i = 0; i < size; i++) result [i] = records [(next - size + i + records.length) % records.length];
			return result;
		}

		@Override
		public void flush () {
		}

		@Override
		public void close () {
		}
	}

	private M3Log (String name) {
		logger = Logger.getLogger (name);
		log = SwtLog.getLog (name);
	}

	/**
	 * Returns the log of a name (a <code>java.util.logging</code> logger name).
	 *
	 * @param name the logger name
	 * @return a log of that name
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the name is null</li>
	 * </ul>
	 */
	public static M3Log get (String name) {
		return new M3Log (M3Checks.nonNull (name));
	}

	/**
	 * Returns the logger name.
	 *
	 * @return the name
	 */
	public String getName () {
		return logger.getName ();
	}

	/**
	 * Sets the level of the logger: records below it are not written.
	 *
	 * @param level TRACE, DEBUG, INFO or OFF
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the level is not one of the four</li>
	 * </ul>
	 */
	public void setLevel (int level) {
		logger.setLevel (LEVELS [checkLevel (level)]);
	}

	/**
	 * Returns whether records of the level are written, from the logger's own or inherited level.
	 *
	 * @param level TRACE, DEBUG or INFO
	 * @return <code>true</code> when such records are written
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the level is not one of the four</li>
	 * </ul>
	 */
	public boolean isEnabled (int level) {
		return checkLevel (level) != OFF && logger.isLoggable (LEVELS [level]);
	}

	/**
	 * Writes a record when the level is enabled. The message is computed on the calling thread and published by
	 * SWT's log thread.
	 *
	 * @param level TRACE, DEBUG or INFO
	 * @param message the message, computed only when the level is enabled
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the message is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the level is not TRACE, DEBUG or INFO</li>
	 * </ul>
	 */
	public void log (int level, Supplier<String> message) {
		M3Checks.nonNull (message);
		if (checkLevel (level) == OFF) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		if (level == TRACE) log.trace (message);
		else if (level == DEBUG) log.debug (message);
		else log.info (message);
	}

	/**
	 * Keeps the last records of the logger (written here or by SWT under the same name) in a ring of the capacity;
	 * 0 stops keeping them and forgets the ring.
	 *
	 * @param capacity the number of records to keep
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_ARGUMENT - if the capacity is negative</li>
	 * </ul>
	 */
	public void keep (int capacity) {
		if (capacity < 0) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		if (ring != null) logger.removeHandler (ring);
		ring = capacity == 0 ? null : new Ring (capacity);
		if (ring != null) logger.addHandler (ring);
	}

	/**
	 * Returns the kept records as "LEVEL message", oldest first; empty when nothing is kept. Records are kept when
	 * SWT's log thread publishes them, so a record written just before may not be there yet.
	 *
	 * @return a new array of the kept records
	 */
	public String [] events () {
		return ring == null ? new String [0] : ring.snapshot ();
	}

	static String name (Level level) {
		int value = level.intValue ();
		return value <= Level.FINEST.intValue () ? NAMES [TRACE] : value <= Level.FINE.intValue () ? NAMES [DEBUG] : NAMES [INFO];
	}

	private static int checkLevel (int level) {
		if (level < TRACE || level > OFF) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		return level;
	}
}
