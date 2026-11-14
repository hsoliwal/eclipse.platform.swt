/* SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.swt.internal.mobile.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime.*;

/** Android View host for SWT's internal mobile ownership/runtime primitive. */
public final class AndroidMobileHost implements Host {
	@FunctionalInterface public interface Painter { void paint(long handle, Canvas canvas); }
	private final Activity activity;
	private final FrameLayout root;
	private final Handler main = new Handler(Looper.getMainLooper());
	private final LongSparseArray<View> views = new LongSparseArray<>();
	private final LongSparseArray<ViewGroup> containers = new LongSparseArray<>();
	private final LongSparseArray<Painter> painters = new LongSparseArray<>();
	private final Set<Long> settingText = new HashSet<>();
	private EventSink sink;
	private boolean closed;

	public AndroidMobileHost(Activity activity, FrameLayout root) {
		this.activity = Objects.requireNonNull(activity);
		this.root = Objects.requireNonNull(root);
		guardUi();
	}
	@Override public boolean isUiThread() { return Looper.myLooper() == Looper.getMainLooper(); }
	private void guardUi() {
		if (!isUiThread()) throw new IllegalStateException("Android UI thread required");
		if (closed) throw new IllegalStateException("Android SWT mobile host is closed");
	}
	@Override public void post(Runnable task) { main.post(Objects.requireNonNull(task)); }
	@Override public void attach(EventSink sink) { guardUi(); this.sink = Objects.requireNonNull(sink); }
	private void send(long id, EventType type, String text, float x, float y) {
		if (!closed && sink != null) sink.accept(new Event(id, type, text, x, y));
	}
	private View require(long id) {
		View view = views.get(id);
		if (view == null) throw new IllegalStateException("unknown native mobile handle: " + id);
		return view;
	}
	@Override public void create(long id, long parentId, Kind kind) {
		guardUi();
		if (views.get(id) != null) throw new IllegalStateException("duplicate handle");
		ViewGroup parent = parentId == 0 ? root : containers.get(parentId);
		if (parent == null) throw new IllegalArgumentException("parent cannot contain views: " + parentId);
		View view;
		ViewGroup content = null;
		switch (kind) {
			case SHELL, COMPOSITE -> { FrameLayout group = new FrameLayout(activity); view = group; content = group; }
			case LABEL -> view = new TextView(activity);
			case BUTTON -> {
				Button button = new Button(activity);
				button.setOnClickListener(v -> send(id, EventType.SELECT, null, 0, 0));
				view = button;
			}
			case TEXT -> {
				EditText editor = new EditText(activity);
				editor.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
					@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
					@Override public void afterTextChanged(Editable s) {
						if (!settingText.contains(id)) send(id, EventType.MODIFY, s.toString(), 0, 0);
					}
				});
				view = editor;
			}
			case CANVAS -> {
				DrawLayout canvas = new DrawLayout(activity, id);
				view = canvas;
				content = canvas; // SWT Canvas is a Composite and may own child controls.
			}
			case SCROLLER, TABLE, TREE -> {
				ScrollView scroller = new ScrollView(activity);
				FrameLayout body = new FrameLayout(activity);
				scroller.addView(body, new ScrollView.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				scroller.setOnScrollChangeListener((v, sx, sy, ox, oy) ->
						send(id, EventType.SCROLL, null, sx, sy));
				view = scroller;
				content = body;
			}
			default -> throw new IllegalArgumentException("unsupported widget kind " + kind);
		}
		view.setOnFocusChangeListener((v, hasFocus) ->
				send(id, hasFocus ? EventType.FOCUS_IN : EventType.FOCUS_OUT, null, 0, 0));
		view.setOnTouchListener((v, e) -> {
			EventType event = switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN -> EventType.TOUCH_DOWN;
				case MotionEvent.ACTION_MOVE -> EventType.TOUCH_MOVE;
				case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> EventType.TOUCH_UP;
				default -> null;
			};
			if (event != null) send(id, event, null, e.getX(), e.getY());
			return false; // preserve native click, focus and scrolling semantics
		});
		parent.addView(view, new FrameLayout.LayoutParams(0, 0));
		views.put(id, view);
		if (content != null) containers.put(id, content);
	}
	@Override public void setBounds(long id, Bounds bounds) {
		guardUi();
		View view = require(id);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(bounds.width(), bounds.height());
		params.leftMargin = bounds.x();
		params.topMargin = bounds.y();
		view.setLayoutParams(params);
	}
	@Override public void setText(long id, String text) {
		guardUi();
		View view = require(id);
		if (view instanceof TextView label) {
			settingText.add(id);
			try { label.setText(text); } finally { settingText.remove(id); }
		}
	}
	@Override public void setEnabled(long id, boolean enabled) { guardUi(); require(id).setEnabled(enabled); }
	@Override public void setVisible(long id, boolean visible) {
		guardUi(); require(id).setVisibility(visible ? View.VISIBLE : View.GONE);
	}
	@Override public void setAccessibilityLabel(long id, String label) {
		guardUi(); require(id).setContentDescription(label);
	}
	@Override public void requestKeyboard(long id, boolean shown) {
		guardUi();
		View view = require(id);
		if (!(view instanceof EditText)) throw new IllegalArgumentException("IME only for text");
		InputMethodManager input = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (input == null) return;
		if (shown) { view.requestFocus(); input.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT); }
		else input.hideSoftInputFromWindow(view.getWindowToken(), 0);
	}
	/** Attach an SWT GC/Canvas renderer; the painting owner supplies its own resource lifetime. */
	public void setPainter(long id, Painter painter) {
		guardUi();
		if (!(require(id) instanceof DrawLayout)) throw new IllegalArgumentException("CANVAS required");
		if (painter == null) painters.remove(id); else painters.put(id, painter);
		require(id).invalidate();
	}
	@Override public void remove(long id) {
		guardUi();
		View view = views.get(id);
		if (view == null) return;
		views.remove(id); containers.remove(id); painters.remove(id); settingText.remove(id);
		if (view.getParent() instanceof ViewGroup group) group.removeView(view);
	}
	@Override public void close() {
		guardUi();
		while (views.size() != 0) remove(views.keyAt(views.size() - 1));
		closed = true;
		sink = null;
	}
	private final class DrawLayout extends FrameLayout {
		private final long handle;
		DrawLayout(Context context, long id) { super(context); handle = id; setWillNotDraw(false); }
		@Override protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			Painter painter = painters.get(handle);
			if (painter != null) painter.paint(handle, canvas);
		}
	}
}
