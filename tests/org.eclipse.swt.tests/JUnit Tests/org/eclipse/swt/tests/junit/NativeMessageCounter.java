/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.win32.*;

/**
 * Counts the native control messages a widget operation sends to one window (win32), for "native work O(window)"
 * checks: a WH_CALLWNDPROC hook on the UI thread sees every message sent to a window of the thread, also the ones
 * SWT sends to its own controls (TVM_INSERTITEM, LVM_SETITEMCOUNT, ...). Only messages in [first, last] sent to the
 * given window are counted. Not thread safe: install, run the operation, read, close, all on the UI thread.
 */
final class NativeMessageCounter implements AutoCloseable {
	static final int WH_CALLWNDPROC = 4;
	/* CWPSTRUCT (64-bit): lParam, wParam, message (UINT, padded), hwnd */
	static final int CWP_LONGS = 4;

	private static NativeMessageCounter active;
	private final Callback callback;
	private final long hook;
	private final long hwnd;
	private final int first, last;
	private final long [] cwp = new long [CWP_LONGS];
	private long count;
	/* per message (offset from first) */
	private final long [] perMessage;

	/** Counts messages first..last (for example OS.TV_FIRST..OS.TV_FIRST + 0xFF) sent to hwnd. */
	NativeMessageCounter (long hwnd, int first, int last) {
		if (active != null) throw new IllegalStateException ("one counter at a time");
		this.hwnd = hwnd;
		this.first = first;
		this.last = last;
		perMessage = new long [last - first + 1];
		active = this;
		callback = new Callback (NativeMessageCounter.class, "hookProc", 3);
		hook = OS.SetWindowsHookEx (WH_CALLWNDPROC, callback.getAddress (), 0, OS.GetCurrentThreadId ());
		if (hook == 0) {
			active = null;
			callback.dispose ();
			throw new IllegalStateException ("SetWindowsHookEx failed");
		}
	}

	static long hookProc (long code, long wParam, long lParam) {
		NativeMessageCounter counter = active;
		if (counter != null && code >= 0 && lParam != 0) {
			OS.MoveMemory (counter.cwp, lParam, CWP_LONGS * 8);
			int message = (int) counter.cwp [2];
			if (counter.cwp [3] == counter.hwnd && message >= counter.first && message <= counter.last) {
				counter.count++;
				counter.perMessage [message - counter.first]++;
			}
			/* WM_PAINT sent (UpdateWindow) */
			if (counter.cwp [3] == counter.hwnd && message == OS.WM_PAINT) counter.paints++;
		}
		return OS.CallNextHookEx (0, (int) code, wParam, lParam);
	}

	static final int WH_GETMESSAGE = 3, PM_REMOVE = 1;
	/* MSG (64-bit): hwnd, message (UINT, padded), wParam, lParam */
	private final long [] msg = new long [4];
	private Callback paintCallback;
	private long paintHook;
	private long paints;

	/* WM_PAINT retrieved from the queue (GetMessage, PeekMessage with PM_REMOVE) */
	static long getMessageProc (long code, long wParam, long lParam) {
		NativeMessageCounter counter = active;
		if (counter != null && code >= 0 && lParam != 0 && wParam == PM_REMOVE) {
			OS.MoveMemory (counter.msg, lParam, 4 * 8);
			if (counter.msg [0] == counter.hwnd && (int) counter.msg [1] == OS.WM_PAINT) counter.paints++;
		}
		return OS.CallNextHookEx (0, (int) code, wParam, lParam);
	}

	/** Also counts paint passes of the window: WM_PAINT sent to it or dispatched from the queue. */
	NativeMessageCounter countPaints () {
		if (paintHook == 0) {
			paintCallback = new Callback (NativeMessageCounter.class, "getMessageProc", 3);
			paintHook = OS.SetWindowsHookEx (WH_GETMESSAGE, paintCallback.getAddress (), 0, OS.GetCurrentThreadId ());
		}
		return this;
	}

	/** Paint passes counted so far (see {@link #countPaints()}). */
	long paints () {
		return paints;
	}

	/** Messages counted so far. */
	long count () {
		return count;
	}

	/** Resets the counts (the hook stays installed). */
	void reset () {
		count = paints = 0;
		java.util.Arrays.fill (perMessage, 0);
	}

	/** The counted messages as "0x1132=12 0x113E=3", most frequent first. */
	String breakdown () {
		StringBuilder out = new StringBuilder ();
		boolean [] used = new boolean [perMessage.length];
		for (int n = 0; n < perMessage.length; n++) {
			int best = -1;
			for (int i = 0; i < perMessage.length; i++) {
				if (!used [i] && perMessage [i] > 0 && (best < 0 || perMessage [i] > perMessage [best])) best = i;
			}
			if (best < 0) break;
			used [best] = true;
			out.append (out.length () == 0 ? "" : " ").append ("0x").append (Integer.toHexString (first + best).toUpperCase ()).append ('=').append (perMessage [best]);
		}
		return out.toString ();
	}

	@Override
	public void close () {
		OS.UnhookWindowsHookEx (hook);
		callback.dispose ();
		if (paintHook != 0) {
			OS.UnhookWindowsHookEx (paintHook);
			paintCallback.dispose ();
		}
		active = null;
	}
}
