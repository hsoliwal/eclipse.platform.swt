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
package m3.i31.harness;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stops a harness that makes no progress: when {@link #tick(String)} has not been called for the given time, prints the
 * stack of every thread (the evidence of a hang) and halts the JVM with status 3, so the desktop slot is freed.
 */
final class Watchdog extends Thread {
	private static final Logger LOG = Logger.getLogger ("m3.i31.harness.Watchdog");
	private final long limitMillis;
	private volatile long last = System.currentTimeMillis ();
	private volatile String where = "start";

	private Watchdog (long limitMillis) {
		super ("harness watchdog");
		this.limitMillis = limitMillis;
		setDaemon (true);
	}

	static Watchdog start (long limitMillis) {
		Watchdog watchdog = new Watchdog (limitMillis);
		watchdog.start ();
		return watchdog;
	}

	/** Records progress. */
	void tick (String step) {
		where = step;
		last = System.currentTimeMillis ();
	}

	private static String stacks () {
		StringBuilder text = new StringBuilder ();
		for (Map.Entry<Thread, StackTraceElement []> entry : Thread.getAllStackTraces ().entrySet ()) {
			text.append ('"').append (entry.getKey ().getName ()).append ("\" ").append (entry.getKey ().getState ()).append ('\n');
			for (StackTraceElement frame : entry.getValue ()) {
				text.append ("\tat ").append (frame).append ('\n');
			}
		}
		return text.toString ();
	}

	@Override
	public void run () {
		while (true) {
			try {
				Thread.sleep (5_000);
			} catch (InterruptedException e) {
				return;
			}
			if (System.currentTimeMillis () - last > limitMillis) {
				LOG.log (Level.SEVERE, "no progress for " + limitMillis + " ms after " + where + "; thread stacks:\n" + stacks ());
				for (java.util.logging.Handler handler : Logger.getLogger ("").getHandlers ()) {
					handler.flush ();
				}
				Runtime.getRuntime ().halt (3);
			}
		}
	}
}
