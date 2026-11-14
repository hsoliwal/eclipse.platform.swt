/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import org.eclipse.swt.internal.android.AndroidPlatform;
import org.eclipse.swt.internal.mobile.MobilePlatform;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Android lifecycle host for SWT. Override {@link #createContents(Shell)} to create
 * ordinary SWT/Nebula controls on their dedicated Display thread. Android's main
 * thread renders immutable frames and enqueues input; it never waits for SWT.
 */
public abstract class SWTActivity extends Activity {
    private AndroidPlatform platform;

    @Override protected final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platform = new AndroidPlatform(this);
        setContentView(platform.view());
        AndroidPlatform applicationPlatform = platform;
        Thread thread = new Thread(() -> runApplication(applicationPlatform), "SWT-Display");
        thread.start();
    }

    private void runApplication(AndroidPlatform applicationPlatform) {
        Display display = null;
        MobilePlatform.bind(applicationPlatform);
        try {
            display = new Display();
            Shell shell = new Shell(display);
            createContents(shell);
            if (!shell.isDisposed() && !display.isDisposed()) shell.open();
            while (!display.isDisposed() && !shell.isDisposed()) {
                if (!display.readAndDispatch()) display.sleep();
            }
        } catch (Throwable failure) {
            Log.e("SWT", "SWT application failed", failure);
            onSWTFailure(failure);
        } finally {
            try {
                if (display != null && !display.isDisposed()) display.dispose();
            } finally {
                applicationPlatform.dispose();
                MobilePlatform.unbind(applicationPlatform);
            }
        }
    }

    /** Called on the SWT application thread, not Android's main thread. */
    protected abstract void createContents(Shell shell);

    /** Failure hook on the SWT application thread; the host always disposes afterward. */
    protected void onSWTFailure(Throwable failure) {}

    /** The native rendering surface, for embedding and native instrumentation. */
    public final View getSWTView() { return platform == null ? null : platform.view(); }

    @Override protected void onResume() {
        super.onResume();
        if (platform != null) platform.suspendFromActivity(false);
    }

    @Override protected void onPause() {
        if (platform != null) platform.suspendFromActivity(true);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (platform != null) platform.closeFromActivity();
        super.onDestroy();
    }
}
