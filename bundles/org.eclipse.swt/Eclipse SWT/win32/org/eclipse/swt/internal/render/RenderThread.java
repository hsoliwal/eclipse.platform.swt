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
package org.eclipse.swt.internal.render;

import java.util.*;
import java.util.concurrent.locks.*;
import java.util.logging.*;

/**
 * The render thread of a Display's drawn planes (brief rule 28, leaf R28-3): one daemon thread with the
 * {@link ThreadRole#RENDER} role per UI thread, serving every {@link RenderTarget} of that Display. It draws each
 * target's newest published snapshot and parks when no target has a new one; the UI thread {@link #wake}s it after a
 * publication (an unpark: it never waits). It reads published snapshots only, never the model or a widget. Targets are
 * held in an array replaced on add and remove, so the loop allocates nothing.
 */
public final class RenderThread {
	private static final Logger LOG = Logger.getLogger (RenderThread.class.getName ());
	private static final Map<Object, RenderThread> THREADS = new IdentityHashMap<> ();
	private final Thread thread;
	private volatile RenderTarget [] targets = new RenderTarget [0];

	private RenderThread (String name) {
		thread = new Thread (this::loop, name);
		thread.setDaemon (true);
		thread.start ();
	}

	/** The render thread of a Display (any key object per UI thread), started on first use. */
	public static synchronized RenderThread of (Object display) {
		return THREADS.computeIfAbsent (display, key -> new RenderThread ("SWT drawn render " + THREADS.size ()));
	}

	/** Serves a target from now on. */
	public synchronized void add (RenderTarget target) {
		RenderTarget [] next = Arrays.copyOf (targets, targets.length + 1);
		next [targets.length] = target;
		targets = next;
	}

	/** Closes a target: the render thread frees its resources and drops it. */
	public void remove (RenderTarget target) {
		target.close ();
		wake ();
	}

	/** Lets the render thread look for new snapshots (an unpark; never waits). */
	public void wake () {
		LockSupport.unpark (thread);
	}

	/** The thread (tests: its name and state). */
	public Thread thread () {
		return thread;
	}

	private void loop () {
		ThreadRole.assume (ThreadRole.RENDER);
		while (true) {
			boolean drew = false;
			RenderTarget [] current = targets;
			for (RenderTarget target : current) {
				if (target.closed ()) {
					drop (target);
					continue;
				}
				try {
					drew |= target.render ();
				} catch (RuntimeException | Error e) {
					LOG.log (Level.SEVERE, "render thread: a frame failed; the target is dropped", e);
					target.close ();
					drop (target);
				}
			}
			if (!drew) LockSupport.park (this);
		}
	}

	private synchronized void drop (RenderTarget target) {
		target.dispose ();
		RenderTarget [] next = new RenderTarget [Math.max (0, targets.length - 1)];
		int n = 0;
		for (RenderTarget t : targets) {
			if (t != target && n < next.length) next [n++] = t;
		}
		targets = n == next.length ? next : Arrays.copyOf (next, n);
	}
}
