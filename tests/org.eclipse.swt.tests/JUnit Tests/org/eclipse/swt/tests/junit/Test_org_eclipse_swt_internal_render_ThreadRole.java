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

import java.util.concurrent.atomic.*;

import org.eclipse.swt.internal.render.*;
import org.junit.jupiter.api.*;

/**
 * The thread-role assertion mode of brief rule 28 (<code>-Dswt.m3.threadCheck=true</code>), headless: each role runs
 * what it owns and is stopped at what it must not touch. A model thread publishes snapshots but cannot render or
 * acquire them; a render thread acquires and renders but cannot publish; an undeclared thread fails role-owned code.
 */
public class Test_org_eclipse_swt_internal_render_ThreadRole {

	static Throwable on (ThreadRole role, Runnable work) throws InterruptedException {
		AtomicReference<Throwable> failure = new AtomicReference<> ();
		Thread thread = new Thread (() -> {
			ThreadRole.assume (role);
			try {
				work.run ();
			} catch (Throwable t) {
				failure.set (t);
			}
		}, role + "-thread");
		thread.start ();
		thread.join (10_000);
		return failure.get ();
	}

	@Test
	public void eachRoleRunsWhatItOwnsOnly () throws InterruptedException {
		Assumptions.assumeTrue (ThreadRole.CHECK, "run with -Dswt.m3.threadCheck=true");
		RowSnapshots snapshots = new RowSnapshots (8);
		RowRenderer renderer = new RowRenderer (Theme.SWING);
		RowPaint row = Test_org_eclipse_swt_internal_render_RowRenderer.row (0);
		assertNull (on (ThreadRole.MODEL, () -> snapshots.publish (snapshots.writable ())), "a model thread publishes");
		assertNull (on (ThreadRole.UI, () -> snapshots.publish (snapshots.writable ())), "the UI thread publishes");
		assertNull (on (ThreadRole.RENDER, () -> {
			snapshots.acquire ();
			renderer.paint (new Test_org_eclipse_swt_internal_render_RowRenderer.Recorder (), row);
		}), "the render thread acquires and renders");
		assertInstanceOf (ThreadRole.Violation.class, on (ThreadRole.RENDER, () -> snapshots.publish (snapshots.writable ())), "the render thread never publishes");
		assertInstanceOf (ThreadRole.Violation.class, on (ThreadRole.MODEL, snapshots::acquire), "a model thread never reads a frame");
		Throwable render = on (ThreadRole.MODEL, () -> renderer.paint (new Test_org_eclipse_swt_internal_render_RowRenderer.Recorder (), row));
		assertInstanceOf (ThreadRole.Violation.class, render, "a model thread never renders");
		assertTrue (render.getMessage ().contains ("RowRenderer.paint") && render.getMessage ().contains ("MODEL-thread"), render.getMessage ());
		assertInstanceOf (ThreadRole.Violation.class, on (ThreadRole.NONE, () -> ThreadRole.UI.check ("UI-owned code")), "an undeclared thread fails role-owned code");
	}

	@Test
	public void offByDefault () {
		Assumptions.assumeFalse (ThreadRole.CHECK, "the assertion mode is on");
		ThreadRole.assume (ThreadRole.RENDER);
		assertEquals (ThreadRole.NONE, ThreadRole.current (), "roles are not tracked without the mode");
		ThreadRole.UI.check ("anything");
		ThreadRole.RENDER.forbid ("anything");
	}
}
