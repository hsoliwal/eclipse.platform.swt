/*
 * Copyright (c) 2026 Synexia contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.swt.internal.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CancellationException;

/**
 * Platform-neutral SWT mobile widget ownership and event-dispatch substrate.
 * This internal SPI is not a substitute for SWT Display/Widget/Control APIs.
 * Android and UIKit ports must connect their original SWT widget implementations
 * to these primitives before claiming SWT API compatibility.
 *
 * All mutable state is owned by the platform UI thread. Handles are stable,
 * never reused and indexed through a compact array, not a per-handle map.
 */
public final class MobileWidgetRuntime implements AutoCloseable {
	public enum Kind { SHELL, COMPOSITE, LABEL, BUTTON, TEXT, CANVAS, SCROLLER, TABLE, TREE }
	public enum EventType { SELECT, MODIFY, TOUCH_DOWN, TOUCH_MOVE, TOUCH_UP, FOCUS_IN, FOCUS_OUT, SCROLL }
	public enum Lifecycle { FOREGROUND, BACKGROUND, DISPOSED }

	/** Java 8 runtime shape avoids the missing java.lang.Record on older Android. */
	public static final class Bounds {
		private final int x, y, width, height;
		public Bounds(int x, int y, int width, int height) {
			if (width < 0 || height < 0) throw new IllegalArgumentException("negative extent");
			this.x = x; this.y = y; this.width = width; this.height = height;
		}
		public int x() { return x; }
		public int y() { return y; }
		public int width() { return width; }
		public int height() { return height; }
		@Override public boolean equals(Object other) {
			return other instanceof Bounds b && b.x == x && b.y == y
					&& b.width == width && b.height == height;
		}
		@Override public int hashCode() {
			return Objects.hash(x, y, width, height);
		}
	}

	public static final class Event {
		private final long id;
		private final EventType type;
		private final String text;
		private final float x, y;
		public Event(long id, EventType type, String text, float x, float y) {
			this.id = id;
			this.type = Objects.requireNonNull(type);
			this.text = text;
			this.x = x;
			this.y = y;
		}
		public long id() { return id; }
		public EventType type() { return type; }
		public String text() { return text; }
		public float x() { return x; }
		public float y() { return y; }
	}

	@FunctionalInterface
	public interface EventSink { void accept(Event event); }
	@FunctionalInterface
	public interface Listener { void handle(Event event); }

	/** Native backend must deliver all calls on the UI thread except post(). */
	public interface Host {
		boolean isUiThread();
		void post(Runnable task);
		void attach(EventSink sink);
		void create(long id, long parentId, Kind kind);
		void setBounds(long id, Bounds bounds);
		void setText(long id, String text);
		void setEnabled(long id, boolean enabled);
		void setVisible(long id, boolean visible);
		void setAccessibilityLabel(long id, String label);
		void remove(long id);
		default void lifecycle(Lifecycle state) { }
		default void requestKeyboard(long id, boolean shown) { }
		default void close() { }
	}

	public final class Node implements AutoCloseable {
		private final long id;
		private final Kind kind;
		private final Node parent;
		private final ArrayList<Node> children = new ArrayList<>();
		private final EnumMap<EventType, ArrayList<Listener>> listeners =
				new EnumMap<>(EventType.class);
		private Bounds bounds = new Bounds(0, 0, 0, 0);
		private String text = "";
		private boolean enabled = true;
		private boolean visible = true;
		private boolean disposed;

		private Node(long id, Node parent, Kind kind) {
			this.id = id;
			this.parent = parent;
			this.kind = kind;
		}
		public long id() { return id; }
		public Kind kind() { return kind; }
		public Node parent() { return parent; }
		public boolean isDisposed() { return disposed; }
		public Bounds bounds() { checkLive(); return bounds; }
		public String text() { checkLive(); return text; }
		public boolean isEnabled() { checkLive(); return enabled; }
		public boolean isVisible() { checkLive(); return visible; }
		public List<Node> children() { checkLive(); return Collections.unmodifiableList(new ArrayList<>(children)); }
		private void checkLive() {
			checkThread();
			if (disposed) throw new IllegalStateException("disposed mobile widget " + id);
		}
		public Node createChild(Kind childKind) {
			checkLive();
			if (childKind == Kind.SHELL) throw new IllegalArgumentException("nested shell");
			return create(this, childKind);
		}
		public void setBounds(Bounds next) {
			checkLive();
			Objects.requireNonNull(next);
			if (!bounds.equals(next)) { host.setBounds(id, next); bounds = next; }
		}
		public void setText(String next) {
			checkLive();
			Objects.requireNonNull(next);
			if (!text.equals(next)) { host.setText(id, next); text = next; }
		}
		public void setEnabled(boolean next) {
			checkLive();
			if (enabled != next) { host.setEnabled(id, next); enabled = next; }
		}
		public void setVisible(boolean next) {
			checkLive();
			if (visible != next) { host.setVisible(id, next); visible = next; }
		}
		public void setAccessibilityLabel(String label) {
			checkLive();
			host.setAccessibilityLabel(id, Objects.requireNonNull(label));
		}
		public void requestKeyboard(boolean shown) {
			checkLive();
			if (kind != Kind.TEXT) throw new IllegalStateException("IME requires TEXT");
			host.requestKeyboard(id, shown);
		}
		public void on(EventType type, Listener listener) {
			checkLive();
			listeners.computeIfAbsent(Objects.requireNonNull(type), k -> new ArrayList<>())
					.add(Objects.requireNonNull(listener));
		}
		private void receive(Event event) {
			if (disposed) return;
			if (event.type() == EventType.MODIFY && event.text() != null) text = event.text();
			ArrayList<Listener> list = listeners.get(event.type());
			if (list == null) return;
			// Snapshot: listeners may add children, remove listeners, or dispose this widget.
			for (Listener listener : new ArrayList<>(list)) {
				if (disposed) return;
				listener.handle(event);
			}
		}
		@Override public void close() {
			checkThread();
			if (disposed) return;
			disposed = true; // reentrancy fence before native removal
			for (Node child : new ArrayList<>(children)) child.close();
			children.clear();
			listeners.clear();
			slots[(int) id] = null;
			if (parent == null) roots.remove(this);
			else parent.children.remove(this);
			host.remove(id);
		}
	}

	private final Host host;
	private final Thread owner;
	private Node[] slots = new Node[16];
	private long nextId = 1;
	private final ArrayList<Node> roots = new ArrayList<>();
	private volatile Lifecycle lifecycle = Lifecycle.FOREGROUND;

	public MobileWidgetRuntime(Host host) {
		this.host = Objects.requireNonNull(host);
		if (!host.isUiThread()) throw new IllegalStateException("mobile host must start on UI thread");
		owner = Thread.currentThread();
		host.attach(this::receive);
	}
	public Lifecycle lifecycle() { checkThread(); return lifecycle; }
	public List<Node> roots() { checkThread(); return new ArrayList<>(roots); }
	public void checkThread() {
		if (Thread.currentThread() != owner || !host.isUiThread())
			throw new IllegalStateException("SWT mobile widget access requires owner UI thread");
	}
	private void checkOpen() {
		checkThread();
		if (lifecycle == Lifecycle.DISPOSED) throw new IllegalStateException("disposed mobile display");
	}
	public Node createShell() { checkOpen(); return create(null, Kind.SHELL); }
	private Node create(Node parent, Kind kind) {
		checkOpen();
		Objects.requireNonNull(kind);
		if (nextId >= Integer.MAX_VALUE) throw new IllegalStateException("mobile handle capacity reached");
		long id = nextId++;
		int index = (int) id;
		if (index >= slots.length) slots = Arrays.copyOf(slots,
				Math.max(index + 1, slots.length + (slots.length >>> 1)));
		Node result = new Node(id, parent, kind);
		slots[index] = result;
		if (parent == null) roots.add(result);
		else parent.children.add(result);
		try {
			host.create(id, parent == null ? 0 : parent.id, kind);
		} catch (RuntimeException | Error failure) {
			slots[index] = null;
			if (parent == null) roots.remove(result);
			else parent.children.remove(result);
			try { host.remove(id); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
			throw failure;
		}
		return result;
	}
	public void setForeground(boolean foreground) {
		checkOpen();
		Lifecycle next = foreground ? Lifecycle.FOREGROUND : Lifecycle.BACKGROUND;
		if (lifecycle != next) {
			host.lifecycle(next);
			lifecycle = next;
		}
	}
	/** Called only by the native host UI event dispatcher, never by a background worker. */
	public void receive(Event event) {
		checkThread();
		Objects.requireNonNull(event);
		if (lifecycle != Lifecycle.FOREGROUND || event.id() < 1 || event.id() >= nextId) return;
		Node node = slots[(int) event.id()];
		if (node != null) node.receive(event);
	}
	/** SWT asyncExec semantics: never run the callback after this display is closed. */
	public void asyncExec(Runnable task) {
		Objects.requireNonNull(task);
		if (lifecycle == Lifecycle.DISPOSED) return;
		host.post(() -> {
			checkThread();
			if (lifecycle != Lifecycle.DISPOSED) task.run();
		});
	}
	public void syncExec(Runnable task) {
		Objects.requireNonNull(task);
		if (Thread.currentThread() == owner) { checkOpen(); task.run(); return; }
		if (lifecycle == Lifecycle.DISPOSED) throw new IllegalStateException("disposed mobile display");
		FutureTask<Void> work = new FutureTask<>(task, null);
		host.post(() -> {
			checkThread();
			if (lifecycle == Lifecycle.DISPOSED) work.cancel(false);
			else work.run();
		});
		try { work.get(); }
		catch (CancellationException e) { throw new IllegalStateException("disposed mobile display", e); }
		catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
		catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) throw runtime;
			if (cause instanceof Error error) throw error;
			throw new IllegalStateException(cause);
		}
	}
	@Override public void close() {
		checkThread();
		if (lifecycle == Lifecycle.DISPOSED) return;
		for (Node root : new ArrayList<>(roots)) root.close();
		lifecycle = Lifecycle.DISPOSED;
		host.lifecycle(Lifecycle.DISPOSED);
		host.close();
		Arrays.fill(slots, null);
	}
}
