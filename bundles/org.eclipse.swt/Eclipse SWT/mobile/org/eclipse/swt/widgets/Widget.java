/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.internal.ExceptionStash;

import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Original SWT widget identity and event owner for the mobile source set. */
public abstract class Widget {
	final Display display;
	final int style;
	private volatile boolean disposed;
	private boolean disposing;
	private Object data;
	private Map<String, Object> keyedData;
	EventTable eventTable;

	protected Widget(Widget parent, int style) {
		this(parentDisplay(parent), style);
	}

	Widget(Display display, int style) {
		if (display == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		display.checkDevice();
		if (display.isDisposing()) SWT.error(SWT.ERROR_DEVICE_DISPOSED);
		this.display = display;
		this.style = style;
	}

	private static Display parentDisplay(Widget parent) {
		if (parent == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		parent.checkWidget();
		if (parent.disposing) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
		return parent.display;
	}

	protected void checkWidget() {
		if (disposed) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
		if (Thread.currentThread() != display.getThread())
			SWT.error(SWT.ERROR_THREAD_INVALID_ACCESS);
	}

	protected void checkSubclass() {}

	public Display getDisplay() {
		if (disposed) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
		return display;
	}

	public int getStyle() {
		checkWidget();
		return style;
	}

	public boolean isDisposed() {
		return disposed;
	}

	public Object getData() {
		checkWidget();
		return data;
	}

	public Object getData(String key) {
		checkWidget();
		if (key == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return keyedData == null ? null : keyedData.get(key);
	}

	public void setData(Object value) {
		checkWidget();
		data = value;
	}

	public void setData(String key, Object value) {
		checkWidget();
		if (key == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (keyedData == null) keyedData = new HashMap<>();
		if (value == null) keyedData.remove(key);
		else keyedData.put(key, value);
	}

	public void addListener(int type, Listener listener) {
		checkWidget();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable == null) eventTable = new EventTable();
		eventTable.hook(type, listener);
	}

	public void removeListener(int type, Listener listener) {
		checkWidget();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable != null) eventTable.unhook(type, listener);
	}

	protected void removeListener(int type, EventListener listener) {
		removeTypedListener(type, listener);
	}

	public Listener[] getListeners(int type) {
		checkWidget();
		return eventTable == null ? new Listener[0] : eventTable.getListeners(type);
	}

	public boolean isListening(int type) {
		checkWidget();
		return eventTable != null && eventTable.hooks(type);
	}

	@SuppressWarnings("removal")
	public void addTypedListener(EventListener listener, int... types) {
		checkWidget();
		if (listener == null || types == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		TypedListener typed = new TypedListener(listener);
		for (int type : types) addListener(type, typed);
	}

	public void removeTypedListener(int type, EventListener listener) {
		checkWidget();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable != null) eventTable.unhook(type, listener);
	}

	@SuppressWarnings("removal")
	public <T extends EventListener> Stream<T> getTypedListeners(int type, Class<T> listenerType) {
		checkWidget();
		if (listenerType == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return java.util.Arrays.stream(getListeners(type))
				.filter(TypedListener.class::isInstance)
				.map(TypedListener.class::cast)
				.map(TypedListener::getEventListener)
				.filter(listenerType::isInstance)
				.map(listenerType::cast);
	}

	public void addDisposeListener(DisposeListener listener) {
		addTypedListener(listener, SWT.Dispose);
	}

	public void removeDisposeListener(DisposeListener listener) {
		removeTypedListener(SWT.Dispose, listener);
	}

	public void notifyListeners(int type, Event event) {
		checkWidget();
		if (event == null) event = new Event();
		event.type = type;
		event.widget = this;
		event.display = display;
		if (event.time == 0) event.time = display.eventTime();
		display.filterEvent(event);
		if (event.type != SWT.None && eventTable != null) eventTable.sendEvent(event);
	}

	public void dispose() {
		if (disposed) return;
		checkWidget();
		if (disposing) return;
		disposing = true;
		try (ExceptionStash errors = new ExceptionStash()) {
			try {
				notifyListeners(SWT.Dispose, new Event());
			} catch (RuntimeException | Error e) {
				errors.stash(e);
			}
			try {
				releaseChildren();
			} catch (RuntimeException | Error e) {
				errors.stash(e);
			}
			try {
				releaseWidget();
			} catch (RuntimeException | Error e) {
				errors.stash(e);
			} finally {
				disposed = true;
				disposing = false;
				eventTable = null;
				data = null;
				if (keyedData != null) keyedData.clear();
			}
		}
	}

	void releaseChildren() {}

	void releaseWidget() {}

	@Override
	public String toString() {
		return getClass().getSimpleName() + (disposed ? " {*DISPOSED*}" : " {}");
	}
}
