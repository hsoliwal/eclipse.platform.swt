/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.android;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.mobile.MobileEventSink;
import org.eclipse.swt.internal.mobile.MobileFrame;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/** Android View/Canvas host. SWT widgets are accessed only through the queued event sink. */
public final class AndroidPlatform extends MobilePlatform {
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Surface surface;
    private final float density;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicBoolean invalidationQueued = new AtomicBoolean();
    private volatile MobileEventSink sink;
    private volatile MobileFrame frame;
    private volatile int width, height;
    private volatile boolean suspended = true;
    private volatile boolean closing;

    public AndroidPlatform(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Create the Android SWT host on Android's UI thread");
        }
        this.activity = Objects.requireNonNull(activity, "activity");
        density = activity.getResources().getDisplayMetrics().density;
        width = Math.max(0, Math.round(activity.getResources().getDisplayMetrics().widthPixels / density));
        height = Math.max(0, Math.round(activity.getResources().getDisplayMetrics().heightPixels / density));
        surface = new Surface();
    }

    public View view() { return surface; }
    @Override public String name() { return "android"; }
    @Override public int width() { return width; }
    @Override public int height() { return height; }

    @Override public void setEventSink(MobileEventSink eventSink) {
        sink = Objects.requireNonNull(eventSink, "eventSink");
        eventSink.resize(width, height);
        eventSink.suspend(suspended);
        if (closing || disposed.get()) eventSink.close();
    }

    @Override public void submit(MobileFrame nextFrame) {
        Objects.requireNonNull(nextFrame, "frame");
        if (closing || disposed.get()) return;
        frame = nextFrame;
        if (invalidationQueued.compareAndSet(false, true)) {
            main.post(() -> {
                invalidationQueued.set(false);
                if (!closing && !disposed.get()) surface.invalidate();
            });
        }
    }

    @Override public int[] measureText(String text, String fontName, int pixelSize, int style) {
        Objects.requireNonNull(text, "text");
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        configureFont(paint, fontName, pixelSize, style);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        int lineHeight = (int) Math.ceil(metrics.descent - metrics.ascent);
        String[] lines = text.split("\n", -1);
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, (int) Math.ceil(paint.measureText(line)));
        return new int[] { textWidth, Math.multiplyExact(lineHeight, lines.length), (int) Math.ceil(-metrics.ascent) };
    }

    @Override public void setVisible(boolean visible) {
        main.post(() -> {
            if (!closing && !disposed.get()) surface.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        });
    }

    /** Called by Activity lifecycle methods; no native callback waits for SWT. */
    public void suspendFromActivity(boolean value) {
        suspended = value;
        MobileEventSink current = sink;
        if (current != null && !disposed.get()) current.suspend(value);
    }

    public void closeFromActivity() {
        closing = true;
        frame = null;
        main.removeCallbacksAndMessages(null);
        MobileEventSink current = sink;
        if (current != null) current.close();
    }

    @Override public void dispose() {
        if (!disposed.compareAndSet(false, true)) return;
        frame = null;
        sink = null;
        main.removeCallbacksAndMessages(null);
        main.post(() -> {
            surface.setVisibility(View.INVISIBLE);
            if (!activity.isFinishing() && !activity.isDestroyed()) activity.finish();
        });
    }

    private static void configureFont(Paint paint, String name, int pixels, int style) {
        int nativeStyle = Typeface.NORMAL;
        if ((style & SWT.BOLD) != 0) nativeStyle |= Typeface.BOLD;
        if ((style & SWT.ITALIC) != 0) nativeStyle |= Typeface.ITALIC;
        paint.setTypeface(Typeface.create(name, nativeStyle));
        paint.setTextSize(pixels);
    }

    private static int modifiers(int metaState) {
        int result = 0;
        if ((metaState & KeyEvent.META_SHIFT_ON) != 0) result |= SWT.SHIFT;
        if ((metaState & KeyEvent.META_CTRL_ON) != 0) result |= SWT.CTRL;
        if ((metaState & KeyEvent.META_ALT_ON) != 0) result |= SWT.ALT;
        if ((metaState & KeyEvent.META_META_ON) != 0) result |= SWT.COMMAND;
        return result;
    }

    private static int buttonMask(int buttons) {
        int result = 0;
        if ((buttons & MotionEvent.BUTTON_PRIMARY) != 0) result |= SWT.BUTTON1;
        if ((buttons & MotionEvent.BUTTON_TERTIARY) != 0) result |= SWT.BUTTON2;
        if ((buttons & MotionEvent.BUTTON_SECONDARY) != 0) result |= SWT.BUTTON3;
        return result;
    }

    private static int keyCode(KeyEvent event) {
        switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_LEFT: return SWT.ARROW_LEFT;
        case KeyEvent.KEYCODE_DPAD_RIGHT: return SWT.ARROW_RIGHT;
        case KeyEvent.KEYCODE_DPAD_UP: return SWT.ARROW_UP;
        case KeyEvent.KEYCODE_DPAD_DOWN: return SWT.ARROW_DOWN;
        case KeyEvent.KEYCODE_MOVE_HOME: return SWT.HOME;
        case KeyEvent.KEYCODE_MOVE_END: return SWT.END;
        case KeyEvent.KEYCODE_PAGE_UP: return SWT.PAGE_UP;
        case KeyEvent.KEYCODE_PAGE_DOWN: return SWT.PAGE_DOWN;
        case KeyEvent.KEYCODE_INSERT: return SWT.INSERT;
        case KeyEvent.KEYCODE_DEL: return SWT.BS;
        case KeyEvent.KEYCODE_FORWARD_DEL: return SWT.DEL;
        case KeyEvent.KEYCODE_ESCAPE: return SWT.ESC;
        case KeyEvent.KEYCODE_TAB: return SWT.TAB;
        case KeyEvent.KEYCODE_ENTER: return SWT.CR;
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT: return SWT.SHIFT;
        case KeyEvent.KEYCODE_CTRL_LEFT:
        case KeyEvent.KEYCODE_CTRL_RIGHT: return SWT.CTRL;
        case KeyEvent.KEYCODE_ALT_LEFT:
        case KeyEvent.KEYCODE_ALT_RIGHT: return SWT.ALT;
        case KeyEvent.KEYCODE_META_LEFT:
        case KeyEvent.KEYCODE_META_RIGHT: return SWT.COMMAND;
        default: return event.getUnicodeChar(event.getMetaState());
        }
    }

    private final class Surface extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private int activePointer = -1;

        Surface() {
            super(activity);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            setContentDescription("SWT application canvas");
            setBackgroundColor(android.graphics.Color.WHITE);
        }

        @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            setPadding(left, top, right, bottom);
            resizeViewport();
            return super.onApplyWindowInsets(insets);
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            resizeViewport();
        }

        private void resizeViewport() {
            int w = Math.max(0, Math.round((getWidth() - getPaddingLeft() - getPaddingRight()) / density));
            int h = Math.max(0, Math.round((getHeight() - getPaddingTop() - getPaddingBottom()) / density));
            if (w == width && h == height) return;
            width = w; height = h;
            MobileEventSink current = sink;
            if (current != null && !closing && !disposed.get()) current.resize(w, h);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            MobileFrame current = frame;
            if (current == null || closing || disposed.get()) return;
            int base = canvas.save();
            try {
                canvas.translate(getPaddingLeft(), getPaddingTop());
                canvas.scale(density, density);
                canvas.clipRect(0, 0, width, height);
                for (int i = 0; i < current.size(); i++) drawCommand(canvas, current, i);
            } finally {
                canvas.restoreToCount(base);
            }
        }

        private void drawCommand(Canvas canvas, MobileFrame commands, int i) {
            int operation = commands.operation(i);
            float x = commands.coordinate(i, 0), y = commands.coordinate(i, 1);
            float w = commands.coordinate(i, 2), h = commands.coordinate(i, 3);
            float a = commands.coordinate(i, 4), b = commands.coordinate(i, 5);
            paint.setColor(commands.color(i));
            paint.setAntiAlias(commands.antialias(i) != SWT.OFF);
            paint.setStrokeWidth(commands.lineWidth(i));
            paint.setStyle(Paint.Style.FILL);
            switch (operation) {
            case MobileFrame.SAVE: canvas.save(); break;
            case MobileFrame.RESTORE: canvas.restore(); break;
            case MobileFrame.TRANSLATE: canvas.translate(x, y); break;
            case MobileFrame.CLIP: canvas.clipRect(x, y, x + w, y + h); break;
            case MobileFrame.FILL_RECT: canvas.drawRect(x, y, x + w, y + h, paint); break;
            case MobileFrame.DRAW_RECT:
                paint.setStyle(Paint.Style.STROKE); canvas.drawRect(x, y, x + w, y + h, paint); break;
            case MobileFrame.FILL_OVAL: canvas.drawOval(x, y, x + w, y + h, paint); break;
            case MobileFrame.DRAW_OVAL:
                paint.setStyle(Paint.Style.STROKE); canvas.drawOval(x, y, x + w, y + h, paint); break;
            case MobileFrame.DRAW_LINE:
                paint.setStyle(Paint.Style.STROKE); canvas.drawLine(x, y, w, h, paint); break;
            case MobileFrame.DRAW_TEXT:
                configureFont(paint, commands.font(i), commands.fontSize(i), commands.fontStyle(i));
                Paint.FontMetrics metrics = paint.getFontMetrics();
                int lineHeight = (int) Math.ceil(metrics.descent - metrics.ascent);
                for (String line : commands.text(i).split("\n", -1)) {
                    canvas.drawText(line, x, y - metrics.ascent, paint);
                    y += lineHeight;
                }
                break;
            case MobileFrame.FILL_ROUND_RECT:
                canvas.drawRoundRect(x, y, x + w, y + h, a / 2, b / 2, paint); break;
            case MobileFrame.DRAW_ROUND_RECT:
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRoundRect(x, y, x + w, y + h, a / 2, b / 2, paint); break;
            case MobileFrame.DRAW_ARC:
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawArc(x, y, x + w, y + h, -a, -b, false, paint); break;
            case MobileFrame.FILL_ARC:
                canvas.drawArc(x, y, x + w, y + h, -a, -b, true, paint); break;
            default: throw new IllegalArgumentException("Unknown SWT paint operation: " + operation);
            }
        }

        private void sendPointer(int type, MotionEvent event, int index, int button, boolean pressed) {
            MobileEventSink current = sink;
            if (current == null || closing || disposed.get()) return;
            int x = Math.round((event.getX(index) - getPaddingLeft()) / density);
            int y = Math.round((event.getY(index) - getPaddingTop()) / density);
            int state = modifiers(event.getMetaState()) | buttonMask(event.getButtonState());
            if (pressed && event.getButtonState() == 0) state |= SWT.BUTTON1;
            // Original Win32/Cocoa Widget#setInputState reports state BEFORE the transition.
            int changedButton = switch (button) {
                case 1 -> SWT.BUTTON1;
                case 2 -> SWT.BUTTON2;
                case 3 -> SWT.BUTTON3;
                default -> 0;
            };
            if (type == SWT.MouseDown || type == SWT.MouseDoubleClick) state &= ~changedButton;
            if (type == SWT.MouseUp) state |= changedButton;
            current.pointer(type, x, y, button, state, (int) event.getEventTime());
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (closing || disposed.get() || sink == null) return false;
            switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointer = event.getPointerId(0);
                requestFocus();
                sendPointer(SWT.MouseDown, event, 0, 1, true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(activePointer);
                if (index >= 0) sendPointer(SWT.MouseMove, event, index, 0, true);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int index = event.findPointerIndex(activePointer);
                if (index >= 0) {
                    sendPointer(SWT.MouseUp, event, index, 1, false);
                    sendPointer(SWT.MouseExit, event, index, 0, false);
                    performClick();
                }
                activePointer = -1;
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == activePointer) {
                    sendPointer(SWT.MouseExit, event, event.getActionIndex(), 0, false);
                    activePointer = -1;
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (activePointer != -1) {
                    int index = event.findPointerIndex(activePointer);
                    if (index >= 0) sendPointer(SWT.MouseExit, event, index, 0, false);
                }
                activePointer = -1;
                return true;
            default: return true;
            }
        }

        @Override public boolean performClick() { return super.performClick(); }

        @Override public boolean onHoverEvent(MotionEvent event) {
            int type;
            switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER: type = SWT.MouseEnter; break;
            case MotionEvent.ACTION_HOVER_EXIT: type = SWT.MouseExit; break;
            case MotionEvent.ACTION_HOVER_MOVE: type = SWT.MouseMove; break;
            default: return super.onHoverEvent(event);
            }
            sendPointer(type, event, 0, 0, false);
            return true;
        }

        @Override public boolean onKeyDown(int code, KeyEvent event) {
            return sendKey(SWT.KeyDown, event) || super.onKeyDown(code, event);
        }
        @Override public boolean onKeyUp(int code, KeyEvent event) {
            return sendKey(SWT.KeyUp, event) || super.onKeyUp(code, event);
        }
        private boolean sendKey(int type, KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                    || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
            MobileEventSink current = sink;
            if (current == null || closing || disposed.get()) return false;
            int unicode = event.getUnicodeChar(event.getMetaState());
            char character = unicode >= 0 && unicode <= Character.MAX_VALUE ? (char) unicode : 0;
            int code = keyCode(event);
            int state = modifiers(event.getMetaState());
            if (code == SWT.SHIFT || code == SWT.CTRL || code == SWT.ALT || code == SWT.COMMAND) {
                if (type == SWT.KeyDown) state &= ~code;
                if (type == SWT.KeyUp) state |= code;
            }
            current.key(type, code, character, state, (int) event.getEventTime());
            return true;
        }
    }
}
