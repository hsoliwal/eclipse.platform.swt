/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;

/**
 * Instances of this class provide synchronization support
 * for displays. A default instance is created automatically
 * for each display, and this instance is sufficient for almost
 * all applications.
 * <p>
 * <b>IMPORTANT:</b> Typical application code <em>never</em>
 * needs to deal with this class. It is provided only to
 * allow applications which require non-standard
 * synchronization behavior to plug in the support they
 * require. <em>Subclasses which override the methods in
 * this class must ensure that the superclass methods are
 * invoked in their implementations</em>
 * </p>
 *
 * @see Display#setSynchronizer
 * @see <a href="https://eclipse.dev/eclipse/swt/">Sample code and further information</a>
 */
public class Synchronizer {
	Display display;
	/* rule 28: the dispatcher (rings per priority class, keyed coalescing, frame ticks, reused syncExec locks) */
	final DispatchQueue queue = new DispatchQueue (this);

	/**
	 * Priority classes of {@link #asyncExec(int, Object, Runnable)}, highest first (docs/m3/SYNCHRONIZER.md): input,
	 * layout and paint, the default class (plain <code>asyncExec</code> and <code>syncExec</code>, first in, first
	 * out), model patches (applied together once per frame tick, then the {@link FrameListener}s), idle.
	 *
	 * @since 3.136
	 */
	public static final int INPUT = DispatchQueue.INPUT, LAYOUT = DispatchQueue.LAYOUT, DEFAULT = DispatchQueue.DEFAULT,
		MODEL = DispatchQueue.MODEL, IDLE = DispatchQueue.IDLE;
	Thread syncThread;
	static final int GROW_SIZE = 4;
	static final int MESSAGE_LIMIT = 64;

	//TEMPORARY CODE
	static final boolean IS_COCOA = "cocoa".equals (SWT.getPlatform ());
	static final boolean IS_GTK = "gtk".equals (SWT.getPlatform ());

/**
 * Constructs a new instance of this class.
 *
 * @param display the display to create the synchronizer on
 */
public Synchronizer (Display display) {
	this.display = display;
}

/**
 * Removes all pending events from the receiver and inserts them into the beginning of the given
 * synchronizer's queue
 *
 * @param toReceiveTheEvents the synchronizer that will receive the events
 */
void moveAllEventsTo (Synchronizer toReceiveTheEvents) {
	// inserted at the beginning of the target's queue, class by class, for backward compatibility
	queue.moveAllTo (toReceiveTheEvents.queue);
}

/**
 * Causes the <code>run()</code> method of the runnable to
 * be invoked by the user-interface thread at the next
 * reasonable opportunity. The caller of this method continues
 * to run in parallel, and is not notified when the
 * runnable has completed.
 *
 * @param runnable code to run on the user-interface thread.
 *
 * @see #syncExec
 */
protected void asyncExec (Runnable runnable) {
	if (runnable == null) {
		//TEMPORARY CODE
		if (!(IS_GTK || IS_COCOA)) {
			display.wake ();
			return;
		}
	}
	//FLLDCIM: rule 28 - no RunnableLock and no queue node per call: a slot of the default ring
	queue.add (DispatchQueue.DEFAULT, null, runnable, null);
}

boolean isMessagesEmpty() {
	return queue.isEmpty ();
}

void releaseSynchronizer () {
	display = null;
	queue.clear ();
	syncThread = null;
}

boolean runAsyncMessages () {
	return runAsyncMessages (false);
}

boolean runAsyncMessages (boolean all) {
	//FLLDCIM: rule 28 - stock order for the default class; higher classes first; a due frame counts as one message
	return queue.run (all);
}

/**
 * Causes the <code>run()</code> method of the runnable to
 * be invoked by the user-interface thread at the next
 * reasonable opportunity. The thread which calls this method
 * is suspended until the runnable completes.
 *
 * @param runnable code to run on the user-interface thread.
 *
 * @exception SWTException <ul>
 *    <li>ERROR_FAILED_EXEC - if an exception occurred when executing the runnable</li>
 * </ul>
 *
 * @see #asyncExec
 */
protected void syncExec (Runnable runnable) {
	DispatchQueue.SyncLock lock = null;
	synchronized (Device.class) {
        if (display == null || display.isDisposed()) {
            SWT.error(SWT.ERROR_DEVICE_DISPOSED);
        }
		if (!display.isValidThread ()) {
			if (runnable == null) {
				display.wake ();
				return;
			}
			//FLLDCIM: rule 28 - the calling thread's reused lock (it remembers the syncThread)
			lock = queue.addSync (runnable);
		}
	}
	if (lock == null) {
		if (runnable != null) {
			display.sendPreEvent(SWT.None);
			try {
				runnable.run();
			} catch (RuntimeException exception) {
				display.getRuntimeExceptionHandler ().accept (exception);
			} catch (Error error) {
				display.getErrorHandler ().accept (error);
			} finally {
				if (display != null && !display.isDisposed()) {
					display.sendPostEvent(SWT.None);
				}
			}
		}
		return;
	}
	//FLLDCIM: rule 28 - waits over swt.synchronizer.syncWarnMillis are logged once with both stacks
	queue.await (lock);
}

/**
 * Causes the <code>run()</code> method of the runnable to be invoked by the user-interface thread at the next
 * reasonable opportunity, coalesced by key: while a runnable posted with an equal key is still queued, it is replaced
 * in place by this one (like <code>EventQueue.coalesceEvents</code>). Runs in the default class, with the plain
 * <code>asyncExec</code> runnables (docs/m3/SYNCHRONIZER.md).
 *
 * @param key the coalescing key (compared with <code>equals</code>), or <code>null</code> for none
 * @param runnable code to run on the user-interface thread
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the runnable is null</li>
 * </ul>
 *
 * @see #asyncExec(int, Object, Runnable)
 * @since 3.136
 */
public void asyncExec (Object key, Runnable runnable) {
	asyncExec (DEFAULT, key, runnable);
}

/**
 * Causes the <code>run()</code> method of the runnable to be invoked by the user-interface thread in the given
 * priority class: {@link #INPUT} and {@link #LAYOUT} before the default class, {@link #MODEL} together once per frame
 * tick (then the {@link FrameListener}s), {@link #IDLE} when nothing else is queued. A runnable posted with a key equal
 * to one still queued in the same class replaces it in place (docs/m3/SYNCHRONIZER.md).
 *
 * @param priority one of {@link #INPUT}, {@link #LAYOUT}, {@link #DEFAULT}, {@link #MODEL}, {@link #IDLE}
 * @param key the coalescing key (compared with <code>equals</code>), or <code>null</code> for none
 * @param runnable code to run on the user-interface thread
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the runnable is null</li>
 *    <li>ERROR_INVALID_ARGUMENT - if the priority is not one of the classes</li>
 * </ul>
 *
 * @since 3.136
 */
public void asyncExec (int priority, Object key, Runnable runnable) {
	if (runnable == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
	if (priority < INPUT || priority > IDLE) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
	queue.add (priority, key, runnable, null);
}

/**
 * Adds a listener told once per frame tick, on the user-interface thread, after the frame's model patches ran.
 *
 * @param listener the listener
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the listener is null</li>
 * </ul>
 *
 * @since 3.136
 */
public void addFrameListener (FrameListener listener) {
	if (listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
	queue.addFrameListener (listener);
}

/**
 * Removes a frame listener.
 *
 * @param listener the listener
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the listener is null</li>
 * </ul>
 *
 * @since 3.136
 */
public void removeFrameListener (FrameListener listener) {
	if (listener == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
	queue.removeFrameListener (listener);
}

/**
 * Asks for a frame tick even when no model patch is queued (a drawn plane with a new snapshot to publish). At most
 * one tick is pending, paced to the frame rate.
 *
 * @since 3.136
 */
public void requestFrame () {
	queue.requestFrame ();
}

/**
 * Returns the number of frames applied so far on this display (the generation the last frame listeners saw).
 *
 * @return the frame generation, 0 before the first frame
 *
 * @since 3.136
 */
public long getFrameGeneration () {
	return queue.generation ();
}

}
