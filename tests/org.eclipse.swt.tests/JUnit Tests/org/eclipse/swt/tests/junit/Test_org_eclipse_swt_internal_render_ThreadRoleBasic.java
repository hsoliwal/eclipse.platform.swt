/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.internal.render.ThreadRole;
import org.junit.jupiter.api.Test;

/**
 * Self-contained, headless contract for the additively admitted render-role enum.
 * The role assertion mode is selected before class initialization with
 * -Dswt.m3.threadCheck=true; default mode has no role-checking overhead.
 */
public class Test_org_eclipse_swt_internal_render_ThreadRoleBasic {

    @Test
    public void roleChecksAreThreadLocalWhenEnabled() throws InterruptedException {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                ThreadRole.assume(ThreadRole.MODEL);
                if (ThreadRole.CHECK) {
                    assertEquals(ThreadRole.MODEL, ThreadRole.current());
                    assertThrows(ThreadRole.Violation.class,
                            () -> ThreadRole.UI.check("UI-only operation"));
                    assertThrows(ThreadRole.Violation.class,
                            () -> ThreadRole.MODEL.forbid("non-model operation"));
                    ThreadRole.MODEL.check("model operation");
                } else {
                    assertEquals(ThreadRole.NONE, ThreadRole.current());
                    ThreadRole.UI.check("disabled check");
                    ThreadRole.MODEL.forbid("disabled check");
                }
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "m3-role-smoke");
        worker.start();
        worker.join(10_000L);
        assertEquals(false, worker.isAlive(), "role contract worker terminated");
        assertNull(failure.get(), "role contract worker completed");
    }
}
