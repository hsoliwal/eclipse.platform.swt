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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.logging.*;

import org.eclipse.swt.m3.*;
import org.junit.jupiter.api.*;

/**
 * Contract of M3Log (enhanced M3 API, M3-LOG): a level change takes effect, the ring holds the last N records in
 * order, and a blocking handler never blocks the writer (records are published by SWT's log thread). Headless.
 */
public class Test_M3Log {
	static int serial;
	String name;
	M3Log log;

	@BeforeEach
	public void setUp () {
		name = "org.eclipse.swt.tests.m3log." + serial++;
		log = M3Log.get (name);
	}

	@AfterEach
	public void tearDown () {
		log.keep (0);
		Logger.getLogger (name).setLevel (null);
	}

	/* waits until the log thread published the records (or fails after 5 s) */
	private String [] await (int count) throws InterruptedException {
		long end = System.currentTimeMillis () + 5000;
		while (log.events ().length < count && System.currentTimeMillis () < end) Thread.sleep (5);
		return log.events ();
	}

	@Test
	public void test_levelChangeTakesEffect () throws InterruptedException {
		assertEquals (name, log.getName ());
		log.setLevel (M3Log.INFO);
		assertTrue (log.isEnabled (M3Log.INFO));
		assertFalse (log.isEnabled (M3Log.DEBUG));
		assertFalse (log.isEnabled (M3Log.OFF));
		int [] computed = new int [1];
		log.keep (8);
		log.log (M3Log.DEBUG, () -> "hidden " + computed [0]++);
		assertEquals (0, computed [0], "a disabled level does not compute the message");
		log.setLevel (M3Log.TRACE);
		assertTrue (log.isEnabled (M3Log.TRACE));
		log.log (M3Log.TRACE, () -> "t");
		log.log (M3Log.DEBUG, () -> "d");
		log.log (M3Log.INFO, () -> "i");
		assertArrayEquals (new String [] {"TRACE t", "DEBUG d", "INFO i"}, await (3));
		log.setLevel (M3Log.OFF);
		assertFalse (log.isEnabled (M3Log.INFO));
	}

	@Test
	public void test_ringHoldsTheLastRecordsInOrder () throws InterruptedException {
		log.setLevel (M3Log.DEBUG);
		assertEquals (0, log.events ().length);
		log.keep (3);
		for (int i = 0; i < 5; i++) {
			int n = i;
			log.log (M3Log.DEBUG, () -> "r" + n);
		}
		log.log (M3Log.INFO, () -> "last");
		long end = System.currentTimeMillis () + 5000;
		while (!"INFO last".equals (last ()) && System.currentTimeMillis () < end) Thread.sleep (5);
		assertArrayEquals (new String [] {"DEBUG r3", "DEBUG r4", "INFO last"}, log.events ());
		/* the ring is a JUL handler: flush and close (LogManager reset) are no-ops that keep the records */
		for (Handler handler : Logger.getLogger (name).getHandlers ()) {
			handler.flush ();
			handler.close ();
		}
		assertEquals (3, log.events ().length);
		log.keep (0);
		assertEquals (0, log.events ().length);
	}

	private String last () {
		String [] events = log.events ();
		return events.length == 0 ? null : events [events.length - 1];
	}

	@Test
	public void test_aBlockingHandlerNeverBlocksTheWriter () throws InterruptedException {
		log.setLevel (M3Log.INFO);
		CountDownLatch release = new CountDownLatch (1);
		Handler slow = new Handler () {
			@Override
			public void publish (LogRecord record) {
				try {
					release.await (10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread ().interrupt ();
				}
			}

			@Override
			public void flush () {
			}

			@Override
			public void close () {
			}
		};
		Logger logger = Logger.getLogger (name);
		logger.addHandler (slow);
		try {
			log.keep (4);
			long start = System.nanoTime ();
			for (int i = 0; i < 100; i++) log.log (M3Log.INFO, () -> "x");
			long millis = (System.nanoTime () - start) / 1_000_000;
			assertTrue (millis < 1000, "100 writes behind a blocked handler took " + millis + " ms");
			release.countDown ();
			assertEquals (4, await (4).length);
		} finally {
			release.countDown ();
			logger.removeHandler (slow);
		}
	}

	@Test
	public void test_swtRecordsOfTheSameNameAreKept () throws InterruptedException {
		M3Log tree = M3Log.get ("org.eclipse.swt.widgets.M3LogProbe");
		tree.setLevel (M3Log.DEBUG);
		tree.keep (2);
		try {
			org.eclipse.swt.internal.SwtLog.getLog ("org.eclipse.swt.widgets.M3LogProbe").debug (() -> "from swt");
			long end = System.currentTimeMillis () + 5000;
			while (tree.events ().length == 0 && System.currentTimeMillis () < end) Thread.sleep (5);
			assertArrayEquals (new String [] {"DEBUG from swt"}, tree.events ());
		} finally {
			tree.keep (0);
			Logger.getLogger ("org.eclipse.swt.widgets.M3LogProbe").setLevel (null);
		}
	}

	@Test
	public void test_argumentsAreChecked () {
		assertThrows (IllegalArgumentException.class, () -> M3Log.get (null));
		assertThrows (IllegalArgumentException.class, () -> log.setLevel (-1));
		assertThrows (IllegalArgumentException.class, () -> log.setLevel (4));
		assertThrows (IllegalArgumentException.class, () -> log.isEnabled (9));
		assertThrows (IllegalArgumentException.class, () -> log.log (M3Log.OFF, () -> "x"));
		assertThrows (IllegalArgumentException.class, () -> log.log (M3Log.INFO, null));
		assertThrows (IllegalArgumentException.class, () -> log.keep (-1));
	}
}
