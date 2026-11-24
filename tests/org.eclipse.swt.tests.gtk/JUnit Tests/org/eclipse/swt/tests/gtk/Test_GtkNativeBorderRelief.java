/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors and others.
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.gtk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.gtk.GTK;
import org.eclipse.swt.internal.gtk.GtkBorder;
import org.eclipse.swt.internal.gtk3.GTK3;
import org.eclipse.swt.internal.gtk4.GTK4;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

/** Native GTK3/GTK4 style-context contract for an explicit Group border override. */
@SuppressWarnings("restriction")
public class Test_GtkNativeBorderRelief {

    @Test
    public void test_explicitControlReliefOverridesConstructorGroupShadow() {
        Display display = Display.getDefault();
        Shell shell = new Shell(display);
        try {
            Group group = new Group(shell, SWT.SHADOW_IN);
            group.setBounds(12, 12, 160, 100);
            shell.setSize(220, 150);
            shell.open();
            drainEvents(display);

            long peer = group.handle;
            assertNotEquals(0, peer);
            int constructorStyle = group.getStyle();

            // The Group constructor's frame style is a named CSS selector.
            // An explicitly applied border must outrank that older provider.
            group.setBorderStyle(SWT.SHADOW_OUT);
            drainEvents(display);
            assertEquals(SWT.SHADOW_OUT, group.getBorderRelief());
            assertEquals(SWT.SHADOW_OUT, group.getBorderStyle());
            assertEquals(2, borderWidth(peer));

            group.setBorderRelief(SWT.SHADOW_NONE);
            drainEvents(display);
            assertEquals(SWT.SHADOW_NONE, group.getBorderStyle());
            assertEquals(0, borderWidth(peer),
                    "An explicit NONE must override the constructor's frame selector");

            group.setBorderStyle(SWT.SHADOW_IN);
            drainEvents(display);
            assertEquals(SWT.SHADOW_IN, group.getBorderRelief());
            assertEquals(2, borderWidth(peer));

            group.setBorderRelief(SWT.DEFAULT);
            drainEvents(display);
            assertEquals(SWT.DEFAULT, group.getBorderStyle());
            assertEquals(constructorStyle, group.getStyle());
            assertEquals(peer, group.handle, "No native peer may be replaced");
        } finally {
            shell.dispose();
        }
    }

    private static int borderWidth(long widget) {
        long context = GTK.gtk_widget_get_style_context(widget);
        assertNotEquals(0, context);
        GtkBorder metrics = new GtkBorder();
        if (GTK.GTK4) {
            GTK4.gtk_style_context_get_border(context, metrics);
        } else {
            GTK3.gtk_style_context_get_border(
                    context, GTK.gtk_widget_get_state_flags(widget), metrics);
        }
        assertEquals(metrics.left, metrics.right, "Horizontal native border is symmetric");
        assertEquals(metrics.top, metrics.bottom, "Vertical native border is symmetric");
        return Math.max(Math.max(metrics.left, metrics.right),
                Math.max(metrics.top, metrics.bottom));
    }

    private static void drainEvents(Display display) {
        for (int i = 0; i < 100 && display.readAndDispatch(); i++) {
            // Run GTK style and resize events without unbounded event-loop processing.
        }
    }
}
