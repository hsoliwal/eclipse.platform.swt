// SPDX-License-Identifier: EPL-2.0
package org.eclipse.swt.examples.mobile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.function.BooleanSupplier;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import org.eclipse.swt.SWT;

/** Runs on a real Android device/emulator; screenshots come from UiAutomation. */
public final class SWTMobileInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        sendCaseStatus(1, null);
        SWTMobileActivity activity = null;
        try {
            activity = (SWTMobileActivity) startActivitySync(new Intent(getTargetContext(), SWTMobileActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SWTMobileActivity current = activity;
            await(() -> current.getSWTView() != null && current.getSWTView().getWidth() > 0, "native surface layout");
            int[] location = location(activity.getSWTView(), 72, 148);
            await(() -> pixelIsBlue(location), "native Canvas oval pixels");
            saveScreen("swt-android-before.png");
            tap(location);
            await(() -> current.taps == 1, "Android touch dispatch into SWT MouseUp");
            require((current.downState & SWT.BUTTON1) == 0, "MouseDown included the newly pressed button");
            require((current.upState & SWT.BUTTON1) != 0, "MouseUp omitted the just-released button");
            long keyTime = SystemClock.uptimeMillis();
            sendKeySync(new KeyEvent(keyTime, keyTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, KeyEvent.META_SHIFT_ON));
            sendKeySync(new KeyEvent(keyTime, keyTime + 20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0));
            await(() -> current.lastKeyDown == SWT.SHIFT && current.lastKeyUp == SWT.SHIFT, "hardware modifier-key mapping");
            require((current.keyDownState & SWT.SHIFT) == 0, "KeyDown included the newly pressed modifier");
            require((current.keyUpState & SWT.SHIFT) != 0, "KeyUp omitted the just-released modifier");
            require(activity.failure == null, "SWT application failure: " + activity.failure);
            saveScreen("swt-android-after.png");
            runOnMainSync(current::finish);
            await(() -> current.shellDisposed, "Activity destruction disposes SWT shell");
            result.putString("stream", "SWT_ANDROID_NATIVE_PASS: pixels, touch, pre-transition mouse/key states, lifecycle\n");
            result.putString("screenshotDirectory", getTargetContext().getExternalFilesDir(null).getAbsolutePath());
            sendCaseStatus(0, null);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            if (activity != null) { SWTMobileActivity current = activity; runOnMainSync(current::finish); }
            result.putString("stream", "SWT_ANDROID_NATIVE_FAIL: " + failure + "\n");
            sendCaseStatus(-2, failure);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private int[] location(View view, int x, int y) {
        int[] result = new int[2];
        runOnMainSync(() -> {
            view.getLocationOnScreen(result);
            float density = view.getResources().getDisplayMetrics().density;
            result[0] += view.getPaddingLeft() + Math.round(x * density);
            result[1] += view.getPaddingTop() + Math.round(y * density);
        });
        return result;
    }

    private boolean pixelIsBlue(int[] location) {
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) return false;
        try {
            if (location[0] >= image.getWidth() || location[1] >= image.getHeight()) return false;
            int color = image.getPixel(location[0], location[1]);
            return (color & 0xff) > 200 && ((color >>> 8) & 0xff) < 40 && ((color >>> 16) & 0xff) < 40;
        } finally { image.recycle(); }
    }

    private void tap(int[] location) {
        long down = SystemClock.uptimeMillis();
        MotionEvent first = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, location[0], location[1], 0);
        MotionEvent last = MotionEvent.obtain(down, down + 20, MotionEvent.ACTION_UP, location[0], location[1], 0);
        try { sendPointerSync(first); sendPointerSync(last); }
        finally { first.recycle(); last.recycle(); }
    }

    private void saveScreen(String filename) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        require(image != null, "Missing real screen capture");
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null), filename))) {
            require(image.compress(Bitmap.CompressFormat.PNG, 100, out), "PNG encoding failed");
        } finally { image.recycle(); }
    }

    private static void await(BooleanSupplier condition, String description) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        require(condition.getAsBoolean(), "Timed out: " + description);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void sendCaseStatus(int code, Throwable failure) {
        Bundle status = new Bundle();
        status.putString("id", "InstrumentationTestRunner");
        status.putString("class", getClass().getName());
        status.putString("test", "nativeCanvasTouchLifecycle");
        status.putInt("numtests", 1);
        status.putInt("current", 1);
        if (failure != null) status.putString("stack", android.util.Log.getStackTraceString(failure));
        sendStatus(code, status);
    }

}
