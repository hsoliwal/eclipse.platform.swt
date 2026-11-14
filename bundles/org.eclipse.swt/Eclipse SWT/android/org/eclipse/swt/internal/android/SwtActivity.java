/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.android;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.eclipse.swt.internal.mobile.MobileRuntime;
import org.eclipse.swt.widgets.Display;

/**
 * Android application entry point for an ordinary SWT main(String[]) method.
 * The Android looper stays on the OS main thread. The application's Display owns
 * a separate thread and retains the normal readAndDispatch/sleep event loop.
 */
public class SwtActivity extends Activity {
	public static final String APPLICATION = "org.eclipse.swt.mobile.application";
	private Session session;
	private final OnBackInvokedCallback backCallback = () -> {
		if (session == null || !session.platform.requestClose()) finish();
	};

	@Override protected void onCreate(Bundle state) {
		super.onCreate(state);
		getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
		Object retained = getLastNonConfigurationInstance();
		if (retained instanceof Session previous) {
			session = previous;
			session.platform.bind(this);
			return;
		}
		try {
			Bundle metadata = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA).metaData;
			String mainClass = metadata == null ? null : metadata.getString(APPLICATION);
			if (mainClass == null || mainClass.trim().isEmpty()) throw new IllegalStateException("Missing SWT application main class in Activity metadata");
			session = new Session(new AndroidPlatform(this), mainClass);
			MobileRuntime.install(session.platform);
			session.thread.start();
		} catch (PackageManager.NameNotFoundException failure) {
			throw new IllegalStateException("Cannot read SWT Activity metadata", failure);
		}
	}

	@Override public Object onRetainNonConfigurationInstance() { return session; }
	@Override protected void onResume() { super.onResume(); if (session != null) session.platform.active(true); }
	@Override protected void onPause() { if (session != null) session.platform.active(false); super.onPause(); }
	@Override protected void onDestroy() {
		getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
		if (session != null && !isChangingConfigurations()) session.platform.closeActivity();
		super.onDestroy();
	}

	/** A failed app remains observable to instrumentation; it is never reported as a pass. */
	public final Throwable applicationFailure() { return session == null ? null : session.failure; }
	public final boolean applicationFinished() { return session != null && !session.thread.isAlive(); }
	public final Thread applicationThread() { return session == null ? null : session.thread; }

	private static final class Session implements Runnable {
		final AndroidPlatform platform;
		final String mainClass;
		final Thread thread;
		volatile Throwable failure;
		Session(AndroidPlatform platform, String mainClass) {
			this.platform = platform; this.mainClass = mainClass;
			thread = new Thread(this, "SWT Display");
		}
		@Override public void run() {
			try {
				Class<?> app = Class.forName(mainClass, true, SwtActivity.class.getClassLoader());
				Method main = app.getMethod("main", String[].class);
				if (!Modifier.isStatic(main.getModifiers()) || main.getReturnType() != void.class) throw new IllegalStateException("SWT entry must be public static void main(String[])");
				main.invoke(null, (Object) new String[0]);
			} catch (InvocationTargetException invocation) {
				failure = invocation.getCause();
			} catch (Throwable problem) {
				failure = problem;
			} finally {
				try { Display display = Display.getCurrent(); if (display != null && !display.isDisposed()) display.dispose(); }
				catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
				try { platform.finishActivity(); }
				catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
				try { MobileRuntime.uninstall(platform); }
				catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
			}
			if (failure != null) {
				Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
				if (handler != null) handler.uncaughtException(thread, failure);
			}
		}
	}
}
