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

/**
 * The thread roles of brief rule 28 (docs/m3/RENDER_MODES.md, thread architecture), never mixed:
 * <ul>
 * <li>{@link #UI}: OS events, the resident window's native ops, one patch per widget per frame;</li>
 * <li>{@link #MODEL}: lanes, deltas, sort, filter, scope;</li>
 * <li>{@link #RENDER}: in the drawn modes, consumes immutable generation-numbered snapshots ({@link RowSnapshots}) and
 * never touches the model or SWT objects.</li>
 * </ul>
 * A thread declares its role ({@link #assume}); code that belongs to a role asserts it ({@link #check}). The assertions
 * run only with <code>-Dswt.m3.threadCheck=true</code>: otherwise {@link #CHECK} is a constant false and every check is
 * removed by the compiler, so the hot paths pay nothing. A failed check throws {@link Violation} naming the code, the
 * role it needs and the thread's role. A thread that declared no role is {@link #NONE}: role-owned code rejects it too,
 * so a forgotten declaration shows up the first time the mode runs.
 */
public enum ThreadRole {
	/** No declared role. */
	NONE,
	/** The UI thread of a Display. */
	UI,
	/** A model thread. */
	MODEL,
	/** The render thread of the drawn modes. */
	RENDER;

	/** True when the role assertions run (<code>-Dswt.m3.threadCheck=true</code>). */
	public static final boolean CHECK = Boolean.getBoolean ("swt.m3.threadCheck");
	private static final ThreadLocal<ThreadRole> ROLE = ThreadLocal.withInitial (() -> NONE);

	/** A cross-role access found by the assertion mode. */
	public static final class Violation extends IllegalStateException {
		private static final long serialVersionUID = 1L;

		Violation (String message) {
			super (message);
		}
	}

	/** Declares the calling thread's role (a no-op outside the assertion mode). */
	public static void assume (ThreadRole role) {
		if (CHECK) ROLE.set (role);
	}

	/** The calling thread's declared role ({@link #NONE} outside the assertion mode). */
	public static ThreadRole current () {
		return CHECK ? ROLE.get () : NONE;
	}

	/**
	 * Asserts that the calling thread has this role.
	 *
	 * @param what the code that needs it, for the message
	 * @throws Violation in the assertion mode, when the thread's role is another
	 */
	public void check (String what) {
		if (CHECK && ROLE.get () != this) throw violation (what, "needs the " + this + " thread");
	}

	/**
	 * Asserts that the calling thread does not have this role.
	 *
	 * @param what the code that must not run there, for the message
	 * @throws Violation in the assertion mode, when the thread has this role
	 */
	public void forbid (String what) {
		if (CHECK && ROLE.get () == this) throw violation (what, "must not run on the " + this + " thread");
	}

	private static Violation violation (String what, String rule) {
		return new Violation ("rule 28: " + what + " " + rule + "; thread '" + Thread.currentThread ().getName () + "' is " + ROLE.get ());
	}
}
