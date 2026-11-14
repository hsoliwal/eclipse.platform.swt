// SPDX-License-Identifier: EPL-2.0
package org.eclipse.swt.examples.mobile;

import org.eclipse.swt.SWT;
import org.eclipse.swt.android.SWTActivity;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Shell;

/** Android entry point executing the ordinary SWT event loop on its owning thread. */
public final class SWTMobileActivity extends SWTActivity {
    volatile int taps;
    volatile int downState, upState, keyDownState, keyUpState;
    volatile int lastKeyDown = -1, lastKeyUp = -1;
    volatile boolean shellDisposed;
    volatile Throwable failure;

    @Override protected void createContents(Shell shell) {
        Canvas canvas = new Canvas(shell, SWT.DOUBLE_BUFFERED);
        shell.addListener(SWT.Dispose, event -> shellDisposed = true);
        canvas.addListener(SWT.Paint, event -> {
            Rectangle area = canvas.getClientArea();
            event.gc.setBackground(shell.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            event.gc.fillRectangle(area);
            event.gc.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_BLACK));
            event.gc.drawText("SWT on Android", 20, 20, true);
            event.gc.drawText("Canvas taps: " + taps, 20, 52, true);
            event.gc.setBackground(shell.getDisplay().getSystemColor(SWT.COLOR_BLUE));
            event.gc.fillOval(24, 100, 96, 96);
        });
        canvas.addListener(SWT.MouseDown, event -> downState = event.stateMask);
        canvas.addListener(SWT.MouseUp, event -> { upState = event.stateMask; taps++; canvas.redraw(); });
        canvas.addListener(SWT.KeyDown, event -> { keyDownState = event.stateMask; lastKeyDown = event.keyCode; });
        canvas.addListener(SWT.KeyUp, event -> { keyUpState = event.stateMask; lastKeyUp = event.keyCode; });
        Runnable layout = () -> {
            Rectangle area = shell.getClientArea();
            canvas.setBounds(0, 0, area.width, area.height);
        };
        shell.addListener(SWT.Resize, event -> layout.run());
        layout.run();
    }

    @Override protected void onSWTFailure(Throwable error) { failure = error; }
}
