/*******************************************************************************
 * Copyright (c) 2026 Synexia and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.snippets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LayeredViewport;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/** Manual demo for alpha, Z reordering, pan, and bounding-box hit testing. */
public class SnippetLayeredViewport {
    public static void main(String[] args) {
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("SWT LayeredViewport");
        shell.setLayout(new FillLayout());
        LayeredViewport view = new LayeredViewport(shell, SWT.BORDER);
        view.addPaintLayer("paper", 0, 0, 1200, 850, (gc, context) -> {
            gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
            gc.fillRectangle(context.clip());
        });
        view.addPaintLayer("blue", 150, 110, 420, 270, (gc, context) -> {
            gc.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
            gc.fillRectangle(context.clip());
        });
        LayeredViewport.Layer yellow = view.addPaintLayer(
                "yellow", 260, 180, 400, 260, (gc, context) -> {
                    gc.setBackground(display.getSystemColor(SWT.COLOR_YELLOW));
                    gc.fillRectangle(context.clip());
                });
        yellow.setOpacity(160);
        view.setOrigin(90, 70);
        view.addListener(SWT.MouseDown, event -> {
            LayeredViewport.Layer picked = view.hitTest(event.x, event.y);
            shell.setText(picked == null ? "No layer" : "Selected: " + picked.getName());
        });
        shell.setSize(720, 520);
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        display.dispose();
    }
}
