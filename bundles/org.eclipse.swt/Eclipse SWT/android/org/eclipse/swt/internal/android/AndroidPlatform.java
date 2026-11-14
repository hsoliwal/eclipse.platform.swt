/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.android;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Callable;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.internal.mobile.MobilePlatform;

/** Android OS binding. Each SWT Control owns one actual Android view. */
public final class AndroidPlatform implements MobilePlatform {
	private final Handler main = new Handler(Looper.getMainLooper());
	private final Handles<PeerView> controls = new Handles<>();
	private final Handles<Bitmap> images = new Handles<>();
	private final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
	private volatile EventSink sink;
	private volatile Thread displayThread;
	private Activity activity;
	private FrameLayout root;
	private float density;
	private boolean active;
	private boolean hostTerminated;

	public AndroidPlatform(Activity activity) { bind(activity); }

	/** Rebuild native views after Activity recreation without replacing SWT owners. */
	public void bind(Activity next) {
		if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Android main thread required");
		activity = Objects.requireNonNull(next, "activity");
		density = next.getResources().getDisplayMetrics().density;
		root = new FrameLayout(next);
		root.setBackgroundColor(0xffffffff);
		root.setOnApplyWindowInsetsListener((view, insets) -> {
			view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
				insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
			return insets;
		});
		root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
			for (int i = 0; i < controls.size(); i++) {
				PeerView shell = controls.at(i);
				if (shell != null && shell.fillViewport) {
					int width = logical(Math.max(0, root.getWidth() - root.getPaddingLeft() - root.getPaddingRight()));
					int height = logical(Math.max(0, root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()));
					if (width > 0 && height > 0 && (width != shell.logicalWidth || height != shell.logicalHeight)) {
						shell.logicalWidth = width; shell.logicalHeight = height; shell.setLayoutParams(shell.layoutParams());
					}
				}
			}
		});
		next.setContentView(root);
		long focused = 0;
		for (int i = 0; i < controls.size(); i++) {
			PeerView old = controls.at(i);
			if (old == null) continue;
			if (old.hasFocus()) focused = old.id;
			PeerView view = new PeerView(old.kind, old.parent, old.style);
			view.id = old.id;
			view.logicalX = old.logicalX; view.logicalY = old.logicalY;
			view.logicalWidth = old.logicalWidth; view.logicalHeight = old.logicalHeight;
			view.fillViewport = old.fillViewport;
			view.front = old.front; old.front = null;
			view.setVisibility(old.getVisibility()); view.setEnabled(old.isEnabled());
			view.setContentDescription(old.getContentDescription());
			view.role = old.role; view.accessibleState = old.accessibleState;
			controls.replace(view.id, view);
			// The detached Activity may deliver delayed focus/layout callbacks.
			// Only its replacement may publish events for this SWT owner.
			old.id = 0;
		}
		for (int i = 0; i < controls.size(); i++) {
			PeerView view = controls.at(i);
			if (view == null) continue;
			ViewGroup parent = view.kind == SHELL ? root : controls.get(view.parent);
			parent.addView(view, view.layoutParams());
		}
		if (focused != 0) controls.get(focused).requestFocus();
	}

	public void active(boolean value) {
		active = value;
		EventSink receiver = sink;
		if (receiver != null) callback(() -> receiver.lifecycle(value));
	}

	public void closeActivity() {
		hostTerminated = true;
		active(false);
		EventSink receiver = sink;
		if (receiver != null) callback(receiver::terminate);
	}
	/** Native system back is a vetoable Shell.Close; host destruction is separate. */
	public boolean requestClose() {
		if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Android main thread required");
		EventSink receiver = sink;
		if (receiver == null) return false;
		PeerView shell = null;
		View focused = root.findFocus();
		if (focused instanceof PeerView view && view.id != 0) shell = controls.get(view.shellId());
		if (shell == null) {
			for (int i = root.getChildCount() - 1; i >= 0; i--) {
				PeerView view = (PeerView) root.getChildAt(i);
				if (view.kind == SHELL && view.getVisibility() == View.VISIBLE) { shell = view; break; }
			}
		}
		if (shell == null) return false;
		long peer = shell.id;
		callback(() -> receiver.close(peer));
		return true;
	}

	public void finishActivity() {
		ui(() -> { if (!activity.isFinishing() && !activity.isDestroyed()) activity.finish(); return null; });
	}

	@Override public String name() { return "android"; }
	@Override public void connect(EventSink receiver) {
		if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("SWT Display must use its application thread");
		if (sink != null) throw new IllegalStateException("An SWT Display is already connected");
		displayThread = Thread.currentThread();
		sink = Objects.requireNonNull(receiver, "sink");
		ui(() -> { receiver.lifecycle(active); if (hostTerminated) receiver.terminate(); return null; });
	}
	@Override public void disconnect() {
		if (displayThread != Thread.currentThread()) throw new IllegalStateException("SWT Display thread required");
		Throwable failure = callbackFailure.getAndSet(null);
		try {
			ui(() -> {
				Throwable first = null;
				for (int i = controls.size() - 1; i >= 0; i--) {
					PeerView view = controls.at(i);
					if (view == null) continue;
					try { releaseTree(view); }
					catch (RuntimeException | Error problem) { if (first == null) first = problem; else first.addSuppressed(problem); }
				}
				rethrow(first);
				return null;
			});
		} catch (RuntimeException | Error problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
		try {
			for (int i = images.size() - 1; i >= 0; i--) {
				Bitmap bitmap = images.at(i);
				if (bitmap != null) { bitmap.recycle(); images.remove(images.handle(i)); }
			}
		} catch (RuntimeException | Error problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
		finally { sink = null; displayThread = null; }
		rethrow(failure);
	}
	private static void rethrow(Throwable failure) {
		if (failure instanceof Error error) throw error;
		if (failure instanceof RuntimeException exception) throw exception;
		if (failure != null) throw new IllegalStateException("Android SWT callback failed", failure);
	}
	@Override public void checkFailure() {
		Throwable failure = callbackFailure.getAndSet(null);
		if (failure instanceof Error error) throw error;
		if (failure instanceof RuntimeException exception) throw exception;
		if (failure != null) throw new IllegalStateException("Android SWT callback failed", failure);
	}
	private void checkThread() {
		if (displayThread != Thread.currentThread()) throw new IllegalStateException("SWT Display thread required");
		checkFailure();
	}
	@Override public int[] displayBounds() {
		return ui(() -> {
			android.util.DisplayMetrics m = activity.getResources().getDisplayMetrics();
			int w = root.getWidth() == 0 ? m.widthPixels : root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
			int h = root.getHeight() == 0 ? m.heightPixels : root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
			return new int[] {0, 0, logical(w), logical(h)};
		});
	}
	@Override public int[] dpi() { return new int[] {96, 96}; }
	@Override public long createControl(int kind, long parent, int style) {
		checkThread();
		if (kind != SHELL && kind != COMPOSITE && kind != CANVAS) throw new UnsupportedOperationException("Unsupported SWT mobile control kind: " + kind);
		return ui(() -> {
			if (kind != SHELL && parent == 0) throw new IllegalArgumentException("Control requires a parent");
			if (kind == SHELL && parent != 0 && controls.get(parent).kind != SHELL) throw new IllegalArgumentException("Shell requires a Shell parent");
			ViewGroup host = kind == SHELL ? root : controls.get(parent);
			PeerView view = new PeerView(kind, parent, style);
			view.id = controls.add(view);
			try { host.addView(view, view.layoutParams()); }
			catch (RuntimeException | Error failure) { controls.remove(view.id); view.id = 0; throw failure; }
			return view.id;
		});
	}
	@Override public void disposeControl(long peer) { checkThread(); ui(() -> { releasePeer(controls.get(peer)); return null; }); }
	private void releaseTree(PeerView view) {
		// Reused registry slots do not imply native parent/child ordering.
		while (view.getChildCount() != 0) releaseTree((PeerView) view.getChildAt(view.getChildCount() - 1));
		releasePeer(view);
	}
	private void releasePeer(PeerView view) {
		if (view.getChildCount() != 0) throw new IllegalStateException("Dispose child SWT controls first");
		controls.remove(view.id);
		view.id = 0;
		if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
		if (view.front != null) { view.front.recycle(); view.front = null; }
	}
	@Override public void setBounds(long peer, int x, int y, int width, int height) {
		checkThread();
		if (width < 0 || height < 0) throw new IllegalArgumentException("Negative extent");
		ui(() -> {
			PeerView view = controls.get(peer);
			view.logicalX = x; view.logicalY = y; view.logicalWidth = width; view.logicalHeight = height;
			int[] viewport = displayBounds();
			view.fillViewport = view.kind == SHELL && x == 0 && y == 0 && width == viewport[2] && height == viewport[3];
			view.setLayoutParams(view.layoutParams());
			view.requestLayout();
			return null;
		});
	}
	@Override public void setVisible(long peer, boolean value) { checkThread(); ui(() -> { controls.get(peer).setVisibility(value ? View.VISIBLE : View.INVISIBLE); return null; }); }
	@Override public void setEnabled(long peer, boolean value) { checkThread(); ui(() -> { controls.get(peer).setEnabled(value); return null; }); }
	@Override public void setTitle(long peer, String title) { checkThread(); ui(() -> { PeerView view = controls.get(peer); if (view.kind == SHELL) activity.setTitle(title); view.setContentDescription(title); return null; }); }
	@Override public boolean requestFocus(long peer) { checkThread(); return ui(() -> controls.get(peer).requestFocus()); }
	@Override public void setAccessibility(long peer, String label, int role, int state) {
		checkThread(); ui(() -> { PeerView view = controls.get(peer); view.setContentDescription(label); view.role = role; view.accessibleState = state; return null; });
	}
	@Override public void present(long peer, long image) {
		checkThread();
		Bitmap copy = images.get(image).copy(Bitmap.Config.ARGB_8888, false);
		if (copy == null) throw new OutOfMemoryError("Cannot snapshot SWT image");
		try {
			ui(() -> { PeerView view = controls.get(peer); Bitmap old = view.front; view.front = copy; view.invalidate(); if (old != null) old.recycle(); return null; });
		} catch (RuntimeException | Error failure) { copy.recycle(); throw failure; }
	}
	@Override public long createImage(int width, int height, int[] argb) {
		checkThread();
		if (width <= 0 || height <= 0) throw new IllegalArgumentException("Image extent must be positive");
		int count = Math.multiplyExact(width, height);
		if (argb != null && argb.length != count) throw new IllegalArgumentException("Image pixel count mismatch");
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		try { if (argb != null) bitmap.setPixels(argb, 0, width, 0, 0, width, height); return images.add(bitmap); }
		catch (RuntimeException | Error failure) { bitmap.recycle(); throw failure; }
	}
	@Override public void disposeImage(long image) { checkThread(); images.remove(image).recycle(); }
	@Override public int[] imagePixels(long image) { checkThread(); return pixels(images.get(image)); }
	/** Preserves the native capture result, including the not-yet-presented first frame. */
	public static final class ScreenCaptureException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		public final int resultCode;
		private ScreenCaptureException(int resultCode) { super("Android PixelCopy failed: " + resultCode); this.resultCode = resultCode; }
	}
	private static int[] pixels(Bitmap bitmap) {
		int[] data = new int[Math.multiplyExact(bitmap.getWidth(), bitmap.getHeight())];
		bitmap.getPixels(data, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
		return data;
	}
	@Override public int[] captureScreen(int x, int y, int width, int height) {
		checkThread();
		if (width <= 0 || height <= 0) throw new IllegalArgumentException("Capture extent must be positive");
		CountDownLatch completed = new CountDownLatch(1);
		AtomicBoolean abandoned = new AtomicBoolean();
		AtomicBoolean recycled = new AtomicBoolean();
		int[] status = {PixelCopy.ERROR_UNKNOWN};
		Bitmap target = ui(() -> {
			if (!active || !root.isShown() || !root.hasWindowFocus()) throw new IllegalStateException("Visible focused Android window required");
			int[] location = new int[2]; root.getLocationInWindow(location);
			int left = Math.addExact(location[0] + root.getPaddingLeft(), physical(x));
			int top = Math.addExact(location[1] + root.getPaddingTop(), physical(y));
			int right = Math.addExact(left, physical(width));
			int bottom = Math.addExact(top, physical(height));
			if (x < 0 || y < 0 || right > location[0] + root.getWidth() - root.getPaddingRight() || bottom > location[1] + root.getHeight() - root.getPaddingBottom()) throw new IllegalArgumentException("Capture outside visible SWT window");
			Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			try { PixelCopy.request(activity.getWindow(), new Rect(left, top, right, bottom), bitmap,
				result -> { status[0] = result; completed.countDown();
					if (abandoned.get() && recycled.compareAndSet(false, true)) bitmap.recycle(); }, main); }
			catch (RuntimeException | Error failure) { bitmap.recycle(); throw failure; }
			return bitmap;
		});
		boolean finished = false;
		try {
			finished = completed.await(5, TimeUnit.SECONDS);
			if (!finished) throw new IllegalStateException("Android screen capture timed out");
			if (status[0] != PixelCopy.SUCCESS) throw new ScreenCaptureException(status[0]);
			return pixels(target);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted screen capture", failure);
		} finally {
			// PixelCopy may still own the bitmap after interruption/timeout. Recycle only after completion.
			abandoned.set(true);
			if (completed.getCount() == 0 && recycled.compareAndSet(false, true)) target.recycle();
		}
	}
	@Override public Graphics graphics(long image) { checkThread(); return new AndroidGraphics(images.get(image)); }

	private int physical(int logical) { return Math.round(logical * density); }
	private int logical(int physical) { return Math.round(physical / density); }
	private void callback(Runnable task) { try { task.run(); } catch (Throwable failure) { callbackFailure.compareAndSet(null, failure); } }
	private <T> T ui(Callable<T> task) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			try { return task.call(); } catch (RuntimeException | Error failure) { throw failure; } catch (Exception failure) { throw new IllegalStateException(failure); }
		}
		FutureTask<T> future = new FutureTask<>(task);
		if (!main.post(future)) throw new IllegalStateException("Android main looper is closed");
		boolean interrupted = false;
		try {
			for (;;) {
				try { return future.get(); }
				catch (InterruptedException failure) { interrupted = true; }
				catch (java.util.concurrent.ExecutionException failure) {
					Throwable cause = failure.getCause();
					if (cause instanceof Error error) throw error;
					if (cause instanceof RuntimeException exception) throw exception;
					throw new IllegalStateException(cause);
				}
			}
		} finally { if (interrupted) Thread.currentThread().interrupt(); }
	}

	private final class PeerView extends ViewGroup {
		long id;
		final int kind, style;
		final long parent;
		int logicalX, logicalY, logicalWidth, logicalHeight;
		int role, accessibleState;
		boolean fillViewport;
		Bitmap front;
		int pointer = -1;
		PeerView(int kind, long parent, int style) {
			super(activity);
			this.kind = kind; this.parent = parent; this.style = style;
			setWillNotDraw(false);
            boolean canFocus = (style & SWT.NO_FOCUS) == 0;
            setFocusable(canFocus);
            setFocusableInTouchMode(canFocus);
			setClipChildren(true); setClipToPadding(true);
			setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
			setVisibility(kind == SHELL ? View.INVISIBLE : View.VISIBLE);
		}
		ViewGroup.LayoutParams layoutParams() {
			if (kind == SHELL) {
				FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(physical(logicalWidth), physical(logicalHeight));
				p.leftMargin = physical(logicalX); p.topMargin = physical(logicalY); return p;
			}
			return new ViewGroup.LayoutParams(physical(logicalWidth), physical(logicalHeight));
		}
		@Override protected void onMeasure(int width, int height) {
			setMeasuredDimension(MeasureSpec.getSize(width), MeasureSpec.getSize(height));
			for (int i = 0; i < getChildCount(); i++) {
				PeerView child = (PeerView) getChildAt(i);
				child.measure(MeasureSpec.makeMeasureSpec(physical(child.logicalWidth), MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(physical(child.logicalHeight), MeasureSpec.EXACTLY));
			}
		}
		@Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
			for (int i = 0; i < getChildCount(); i++) {
				PeerView child = (PeerView) getChildAt(i);
				int x = physical(child.logicalX), y = physical(child.logicalY);
				child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
			}
		}
		@Override protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			if (front != null && !front.isRecycled()) canvas.drawBitmap(front, null, new Rect(0, 0, getWidth(), getHeight()), null);
		}
		@Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w, h, oldw, oldh);
			EventSink receiver = sink;
			if (receiver != null && id != 0) callback(() -> { receiver.resize(id, logical(w), logical(h)); receiver.paint(id); });
		}
		private void event(int type, int x, int y, int button, int key, char ch, int mask, int count, int time) {
			EventSink receiver = sink; long handle = id;
			if (receiver != null && handle != 0) callback(() -> receiver.input(handle, type, x, y, button, key, ch, mask, count, time));
		}
		@Override public boolean onTouchEvent(MotionEvent event) {
			if (id == 0 || !isEnabled()) return false;
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN) { pointer = event.getPointerId(0); requestFocus(); }
			int index = event.findPointerIndex(pointer);
			if (index < 0) { if (action == MotionEvent.ACTION_CANCEL) pointer = -1; return true; }
			int x = logical(Math.round(event.getX(index))), y = logical(Math.round(event.getY(index)));
			int mask = modifiers(event.getMetaState());
			if (action != MotionEvent.ACTION_DOWN && pointer != -1) mask |= SWT.BUTTON1;
			int time = (int) event.getEventTime();
			if (action == MotionEvent.ACTION_DOWN) event(SWT.MouseDown, x, y, 1, 0, '\0', mask, 1, time);
			else if (action == MotionEvent.ACTION_MOVE) event(SWT.MouseMove, x, y, 0, 0, '\0', mask, 0, time);
			else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer) { event(SWT.MouseUp, x, y, 1, 0, '\0', mask, 1, time); pointer = -1; performClick(); }
			else if (action == MotionEvent.ACTION_CANCEL) { event(SWT.MouseExit, x, y, 0, 0, '\0', mask, 0, time); pointer = -1; }
			return true;
		}
		@Override public boolean performClick() { super.performClick(); return true; }
		@Override public boolean onHoverEvent(MotionEvent event) {
			if (id == 0 || !isEnabled()) return false;
			int type = switch (event.getActionMasked()) { case MotionEvent.ACTION_HOVER_ENTER -> SWT.MouseEnter; case MotionEvent.ACTION_HOVER_EXIT -> SWT.MouseExit; default -> SWT.MouseMove; };
			event(type, logical(Math.round(event.getX())), logical(Math.round(event.getY())), 0, 0, '\0', modifiers(event.getMetaState()), 0, (int) event.getEventTime());
			return true;
		}
		@Override public boolean onGenericMotionEvent(MotionEvent event) {
			if (id == 0 || !isEnabled()) return false;
			if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) return super.onGenericMotionEvent(event);
			int count = Math.round(event.getAxisValue(MotionEvent.AXIS_VSCROLL));
			if (count != 0) event(SWT.MouseVerticalWheel, logical(Math.round(event.getX())), logical(Math.round(event.getY())), 0, 0, '\0', modifiers(event.getMetaState()), count, (int) event.getEventTime());
			return true;
		}
		@Override public boolean onKeyDown(int code, KeyEvent event) { return key(SWT.KeyDown, code, event); }
		@Override public boolean onKeyUp(int code, KeyEvent event) { return key(SWT.KeyUp, code, event); }
		private boolean key(int type, int code, KeyEvent event) {
			if (id == 0 || !isEnabled()) return false;
			int character = event.getUnicodeChar();
			int key = switch (code) {
				case KeyEvent.KEYCODE_DPAD_UP -> SWT.ARROW_UP; case KeyEvent.KEYCODE_DPAD_DOWN -> SWT.ARROW_DOWN;
				case KeyEvent.KEYCODE_DPAD_LEFT -> SWT.ARROW_LEFT; case KeyEvent.KEYCODE_DPAD_RIGHT -> SWT.ARROW_RIGHT;
				case KeyEvent.KEYCODE_ENTER -> SWT.CR; case KeyEvent.KEYCODE_TAB -> SWT.TAB;
				case KeyEvent.KEYCODE_DEL -> SWT.BS; case KeyEvent.KEYCODE_FORWARD_DEL -> SWT.DEL;
				case KeyEvent.KEYCODE_ESCAPE -> SWT.ESC; case KeyEvent.KEYCODE_MOVE_HOME -> SWT.HOME;
				case KeyEvent.KEYCODE_MOVE_END -> SWT.END; case KeyEvent.KEYCODE_PAGE_UP -> SWT.PAGE_UP;
				case KeyEvent.KEYCODE_PAGE_DOWN -> SWT.PAGE_DOWN; default -> Character.toLowerCase(character);
			};
			if (code == KeyEvent.KEYCODE_BACK) { if (type == SWT.KeyUp) { EventSink receiver = sink; if (receiver != null) callback(() -> receiver.close(shellId())); } return true; }
			event(type, 0, 0, 0, key, character <= Character.MAX_VALUE ? (char) character : '\0', modifiers(event.getMetaState()), event.getRepeatCount() + 1, (int) event.getEventTime());
			return true;
		}
		private long shellId() { PeerView view = this; while (view.kind != SHELL) view = controls.get(view.parent); return view.id; }
		@Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
			super.onFocusChanged(focused, direction, previous);
			event(focused ? SWT.FocusIn : SWT.FocusOut, 0, 0, 0, 0, '\0', 0, 0, (int) SystemClock.uptimeMillis());
		}
		@Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
			super.onInitializeAccessibilityNodeInfo(info);
			info.setEnabled(isEnabled() && (accessibleState & ACC.STATE_DISABLED) == 0);
			info.setFocusable(isFocusable()); info.setClickable(kind == CANVAS && role != ACC.ROLE_SLIDER);
			info.setSelected((accessibleState & ACC.STATE_SELECTED) != 0);
			if (role == ACC.ROLE_CHECKBUTTON || role == ACC.ROLE_RADIOBUTTON) {
				info.setCheckable(true); info.setChecked((accessibleState & ACC.STATE_CHECKED) != 0);
			}
			String className = switch (role) {
				case ACC.ROLE_CHECKBUTTON -> "android.widget.CheckBox";
				case ACC.ROLE_RADIOBUTTON -> "android.widget.RadioButton";
				case ACC.ROLE_PUSHBUTTON -> "android.widget.Button";
				case ACC.ROLE_LABEL -> "android.widget.TextView";
				case ACC.ROLE_TEXT -> "android.widget.EditText";
				case ACC.ROLE_PROGRESSBAR -> "android.widget.ProgressBar";
				case ACC.ROLE_SLIDER -> "android.widget.SeekBar";
				default -> "android.view.View";
			};
			info.setClassName(className);
			if (kind == CANVAS && role != ACC.ROLE_SLIDER)
				info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
			if (role == ACC.ROLE_SLIDER) {
				info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
				info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
			}
		}
		@Override public boolean performAccessibilityAction(int action, Bundle arguments) {
			if (role == ACC.ROLE_SLIDER && isEnabled()
					&& (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
					|| action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
				EventSink receiver = sink;
				if (receiver == null || id == 0) return false;
				int code = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
						? ACTION_INCREMENT : ACTION_DECREMENT;
				callback(() -> receiver.accessibility(id, code));
				return true;
			}
			if (role != ACC.ROLE_SLIDER && action == AccessibilityNodeInfo.ACTION_CLICK && isEnabled()) {
				EventSink receiver = sink;
				if (receiver == null || id == 0) return false;
				callback(() -> receiver.accessibility(id, ACTION_ACTIVATE)); return true;
			}
			return super.performAccessibilityAction(action, arguments);
		}
	}

	private static int modifiers(int meta) {
		int result = 0;
		if ((meta & KeyEvent.META_SHIFT_ON) != 0) result |= SWT.SHIFT;
		if ((meta & KeyEvent.META_CTRL_ON) != 0) result |= SWT.CTRL;
		if ((meta & KeyEvent.META_ALT_ON) != 0) result |= SWT.ALT;
		if ((meta & KeyEvent.META_META_ON) != 0) result |= SWT.COMMAND;
		return result;
	}

	private final class AndroidGraphics implements Graphics {
		final Bitmap bitmap;
		final Canvas canvas;
		final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
		int foreground = 0xff000000, background = 0xffffffff, alpha = 255;
		float lineWidth = 1;
		Matrix transform = new Matrix();
		Rect clip;
		final int save;
		boolean closed;
		AndroidGraphics(Bitmap bitmap) { this.bitmap = bitmap; canvas = new Canvas(bitmap); save = canvas.save(); pen.setTextSize(12); }
		private void check() { checkThread(); if (closed || bitmap.isRecycled()) throw new IllegalStateException("Disposed SWT drawing resource"); }
		private void paint(boolean fill, boolean back) { check(); int color = back ? background : foreground; pen.setColor(color); pen.setAlpha(((color >>> 24) * alpha + 127) / 255); pen.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE); pen.setStrokeWidth(lineWidth); }
		@Override public void setForeground(int argb) { check(); foreground = argb; }
		@Override public void setBackground(int argb) { check(); background = argb; }
		@Override public void setAlpha(int value) { check(); if (value < 0 || value > 255) throw new IllegalArgumentException("alpha"); alpha = value; }
		@Override public void setLineWidth(float width) { check(); if (!(width >= 0) || !Float.isFinite(width)) throw new IllegalArgumentException("line width"); lineWidth = width; }
		@Override public void setAntialias(boolean enabled) { check(); pen.setAntiAlias(enabled); }
		@Override public void setFont(String family, float size, int style) { check(); if (!(size > 0) || !Float.isFinite(size)) throw new IllegalArgumentException("font size"); pen.setTypeface(Typeface.create(family, (style & SWT.BOLD) != 0 ? ((style & SWT.ITALIC) != 0 ? Typeface.BOLD_ITALIC : Typeface.BOLD) : ((style & SWT.ITALIC) != 0 ? Typeface.ITALIC : Typeface.NORMAL))); pen.setTextSize(size); }
		private void apply() { canvas.restoreToCount(save); canvas.save(); canvas.concat(transform); if (clip != null) canvas.clipRect(clip); }
		@Override public void setTransform(float[] m) { check(); if (m == null || m.length != 6) throw new IllegalArgumentException("affine matrix"); for (float v : m) if (!Float.isFinite(v)) throw new IllegalArgumentException("nonfinite matrix"); transform.setValues(new float[] {m[0],m[2],m[4],m[1],m[3],m[5],0,0,1}); apply(); }
		@Override public void setClipping(int x, int y, int w, int h) { check(); if (w < 0 || h < 0) throw new IllegalArgumentException("clip extent"); clip = new Rect(x,y,Math.addExact(x,w),Math.addExact(y,h)); apply(); }
		@Override public void resetClipping() { check(); clip = null; apply(); }
		@Override public void drawLine(int x1, int y1, int x2, int y2) { paint(false,false); canvas.drawLine(x1,y1,x2,y2,pen); }
		@Override public void drawRectangle(int x, int y, int w, int h) { paint(false,false); canvas.drawRect(x,y,(float)x+w,(float)y+h,pen); }
		@Override public void fillRectangle(int x, int y, int w, int h) { paint(true,true); canvas.drawRect(x,y,(float)x+w,(float)y+h,pen); }
		@Override public void drawOval(int x, int y, int w, int h) { paint(false,false); canvas.drawOval(new RectF(x,y,(float)x+w,(float)y+h),pen); }
		@Override public void fillOval(int x, int y, int w, int h) { paint(true,true); canvas.drawOval(new RectF(x,y,(float)x+w,(float)y+h),pen); }
		@Override public void drawPolygon(int[] points, boolean fill) { if (points == null || points.length < 4 || (points.length & 1) != 0) throw new IllegalArgumentException("polygon"); paint(fill,fill); android.graphics.Path path = new android.graphics.Path(); path.moveTo(points[0],points[1]); for (int i=2;i<points.length;i+=2) path.lineTo(points[i],points[i+1]); path.close(); canvas.drawPath(path,pen); }
		private String[] lines(String text, int flags) {
			Objects.requireNonNull(text,"text");
			if ((flags & SWT.DRAW_MNEMONIC) != 0) { StringBuilder b=new StringBuilder(); for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='&'&&i+1<text.length()){char n=text.charAt(++i);b.append(n);}else if(c!='&')b.append(c);} text=b.toString(); }
			if ((flags & SWT.DRAW_TAB) != 0) { StringBuilder b=new StringBuilder();int column=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='\t'){int n=8-column%8;for(int j=0;j<n;j++)b.append(' ');column+=n;}else{b.append(c);column=(c=='\n'||c=='\r')?0:column+1;}}text=b.toString(); }
			return (flags & SWT.DRAW_DELIMITER) != 0 ? text.replace("\r\n","\n").replace('\r','\n').split("\n",-1) : new String[]{text};
		}
		@Override public void drawText(String text, int x, int y, int flags) {
			check();String[] lines=lines(text,flags);Paint.FontMetrics fm=pen.getFontMetrics();float height=fm.descent-fm.ascent+fm.leading;
			if((flags&SWT.DRAW_TRANSPARENT)==0){int[] size=textExtent(text,flags);paint(true,true);canvas.drawRect(x,y,(float)x+size[0],(float)y+size[1],pen);}
			paint(true,false);for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],x,y-fm.ascent+i*height,pen);
		}
		@Override public void drawImage(long image,int sx,int sy,int sw,int sh,int dx,int dy,int dw,int dh){paint(true,false);Bitmap source=images.get(image);if(sx<0||sy<0||sw<=0||sh<=0||dw<=0||dh<=0||(long)sx+sw>source.getWidth()||(long)sy+sh>source.getHeight())throw new IllegalArgumentException("image rectangle");pen.setAlpha(alpha);canvas.drawBitmap(source,new Rect(sx,sy,sx+sw,sy+sh),new Rect(dx,dy,Math.addExact(dx,dw),Math.addExact(dy,dh)),pen);}
		@Override public int[] textExtent(String text,int flags){check();String[] rows=lines(text,flags);float width=0;for(String row:rows)width=Math.max(width,pen.measureText(row));Paint.FontMetrics m=pen.getFontMetrics();return new int[]{(int)Math.ceil(width),(int)Math.ceil(rows.length*(m.descent-m.ascent+m.leading))};}
		@Override public int[] fontMetrics(){check();Paint.FontMetrics m=pen.getFontMetrics();return new int[]{(int)Math.ceil(-m.ascent),(int)Math.ceil(m.descent),Math.round(pen.measureText("n")),(int)Math.ceil(m.leading),(int)Math.ceil(m.descent-m.ascent+m.leading)};}
		@Override public void close(){if(closed)return;checkThread();closed=true;canvas.restoreToCount(save);}
	}

	/** Array registry with generation-tagged handles; no retained per-entry wrappers. */
	private static final class Handles<T> {
		private Object[] values = new Object[32];
		private int[] generation = new int[32];
		private int length;
		synchronized int size(){return length;}
		@SuppressWarnings("unchecked") synchronized T at(int index){return (T)values[index];}
		synchronized long handle(int index){return ((long)generation[index]<<32)|(index+1L);}
		synchronized long add(T value){int index=0;while(index<length&&values[index]!=null)index++;if(index==values.length){values=Arrays.copyOf(values,Math.multiplyExact(values.length,2));generation=Arrays.copyOf(generation,values.length);}if(index==length)length++;if(generation[index]==Integer.MAX_VALUE)throw new IllegalStateException("Native handle generation exhausted");generation[index]++;values[index]=value;return handle(index);}
		private int index(long handle){int index=(int)handle-1;if(index<0||index>=length||generation[index]!=(int)(handle>>>32)||values[index]==null)throw new IllegalStateException("Stale SWT native handle");return index;}
		@SuppressWarnings("unchecked") synchronized T get(long handle){return (T)values[index(handle)];}
		synchronized T remove(long handle){int index=index(handle);T value=get(handle);values[index]=null;return value;}
		synchronized void replace(long handle,T value){values[index(handle)]=value;}
	}
}
