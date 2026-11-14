/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.Arrays;
import java.util.EventListener;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.internal.ExceptionStash;

/** The mobile platform's original SWT widget owner and listener lifecycle. */
public class Widget {
	Display display;
	int style;
	volatile boolean disposed;
	boolean disposing;
	EventTable eventTable;
	private Object data;
	private String[] dataKeys = new String[0];
	private Object[] dataValues = new Object[0];

	public Widget(Widget parent, int style) {
		if (parent == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		parent.checkWidget();
		this.display = parent.display;
		this.style = style;
		checkSubclass();
	}

	Widget(Display display, int style) {
		if (display == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		display.checkThread();
		this.display = display;
		this.style = style;
		checkSubclass();
	}

	protected void checkSubclass() {
		if (!getClass().getName().startsWith("org.eclipse.swt.")) SWT.error(SWT.ERROR_INVALID_SUBCLASS);
	}

	protected void checkWidget() {
		if (disposed) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
		display.checkThread();
	}

	public void addListener(int eventType, Listener listener) {
		checkWidget();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable == null) eventTable = new EventTable();
		eventTable.hook(eventType, listener);
	}

	@SuppressWarnings("removal")
	protected void addTypedListener(EventListener listener, int... eventTypes) {
		checkWidget();
		if (listener == null || eventTypes == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		TypedListener typed = new TypedListener(listener);
		for (int type : eventTypes) addListener(type, typed);
	}

	public void addDisposeListener(DisposeListener listener) { addTypedListener(listener, SWT.Dispose); }
	public void removeDisposeListener(DisposeListener listener) { removeTypedListener(SWT.Dispose, listener); }

	public void removeListener(int eventType, Listener listener) {
		checkWidget();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable != null) eventTable.unhook(eventType, listener);
	}

	protected void removeListener(int eventType, EventListener listener) { removeTypedListener(eventType, listener); }

	@SuppressWarnings("removal")
	protected void removeListener(int eventType, org.eclipse.swt.internal.SWTEventListener listener) {
		removeTypedListener(eventType, listener);
	}

	protected void removeTypedListener(int eventType, EventListener listener) {
		checkWidget();
		if (listener == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable != null) eventTable.unhook(eventType, listener);
	}

	public Listener[] getListeners(int eventType) {
		checkWidget();
		return eventTable == null ? new Listener[0] : eventTable.getListeners(eventType);
	}

	@SuppressWarnings("removal")
	public <L extends EventListener> Stream<L> getTypedListeners(int eventType, Class<L> listenerType) {
		checkWidget();
		if (listenerType == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		return Arrays.stream(getListeners(eventType)).filter(TypedListener.class::isInstance)
				.map(TypedListener.class::cast).map(TypedListener::getEventListener)
				.filter(listenerType::isInstance).map(listenerType::cast);
	}

	public boolean isListening(int eventType) { checkWidget(); return eventTable != null && eventTable.hooks(eventType); }
	public int getStyle() { checkWidget(); return style; }
	public boolean isAutoDirection() { checkWidget(); return (style & SWT.AUTO_TEXT_DIRECTION) != 0; }
	public boolean isDisposed() { return disposed; }
	public Display getDisplay() {
		if (disposed) SWT.error(SWT.ERROR_WIDGET_DISPOSED);
		return display;
	}

	public void notifyListeners(int eventType, Event event) {
		checkWidget();
		sendEvent(eventType, event == null ? new Event() : event);
	}

	void sendEvent(int eventType, Event event) {
		event.type = eventType;
		event.display = display;
		event.widget = this;
		if (event.time == 0) event.time = display.getLastEventTime();
		if (!display.filterEvent(event) && eventTable != null) eventTable.sendEvent(event);
	}

	public void dispose() {
		if (disposed || disposing) return;
		checkWidget();
		disposing = true;
		try (ExceptionStash failures = new ExceptionStash()) {
			try { sendEvent(SWT.Dispose, new Event()); } catch (RuntimeException | Error error) { failures.stash(error); }
			try { releaseChildren(); } catch (RuntimeException | Error error) { failures.stash(error); }
			try { releaseWidget(); } catch (RuntimeException | Error error) { failures.stash(error); }
			try { releaseParent(); } catch (RuntimeException | Error error) { failures.stash(error); }
			finally {
				disposed = true;
				disposing = false;
				eventTable = null;
				data = null;
				dataKeys = null;
				dataValues = null;
			}
		}
	}

	void releaseChildren() { }
	void releaseWidget() { }
	void releaseParent() { }

	public Object getData() { checkWidget(); return data; }
	public void setData(Object value) { checkWidget(); data = value; }
	public Object getData(String key) {
		checkWidget();
		if (key == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		for (int i = 0; i < dataKeys.length; i++) if (key.equals(dataKeys[i])) return dataValues[i];
		return null;
	}

	public void setData(String key, Object value) {
		checkWidget();
		if (key == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		for (int i = 0; i < dataKeys.length; i++) {
			if (!key.equals(dataKeys[i])) continue;
			if (value != null) { dataValues[i] = value; return; }
			int tail = dataKeys.length - i - 1;
			System.arraycopy(dataKeys, i + 1, dataKeys, i, tail);
			System.arraycopy(dataValues, i + 1, dataValues, i, tail);
			dataKeys = Arrays.copyOf(dataKeys, dataKeys.length - 1);
			dataValues = Arrays.copyOf(dataValues, dataValues.length - 1);
			return;
		}
		if (value == null) return;
		int size = dataKeys.length;
		dataKeys = Arrays.copyOf(dataKeys, size + 1);
		dataValues = Arrays.copyOf(dataValues, size + 1);
		dataKeys[size] = key;
		dataValues[size] = value;
	}

	public void reskin(int flags) { checkWidget(); sendEvent(SWT.Skin, new Event()); }
	@Override public String toString() { return getClass().getSimpleName() + (disposed ? " {*DISPOSED*}" : " {}"); }
}
