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
package org.eclipse.swt.widgets;

import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;

/**
 * The part visibility of controls (brief rule 25, docs/m3/visibility/VISIBILITY_AND_VIEWPORT.md), shared by every platform:
 * {@link Control#isPartVisible()}, and the {@link SWT#PartVisible} and {@link SWT#PartHidden} events, which SWT fires
 * itself from hooks in its implementation (<code>Control.setVisible</code>, <code>setParent</code>,
 * <code>setPartHidden</code>, the Resize, Iconify and Deiconify events, and the platform's restore of a minimized
 * shell).
 * <p>
 * A control that has a PartVisible or PartHidden listener is watched: it sits in the display's dense registry
 * (<code>Control.partIndex</code>) and remembers the state it was last told (<code>Control.partState</code>); every
 * control counts the watched controls of its subtree up to its shell (<code>Control.partCount</code>). A transition at
 * an origin returns at once when the origin's count is zero; otherwise it visits the registry (O(watched controls x
 * depth)), recomputes the watched controls under the origin, ancestors first, and fires to each one whose state
 * changed. The state is computed again at the moment of each firing, so a listener that changes visibility leaves
 * every later control with the right answer and no duplicate event.
 * </p>
 */
//FLLDCIM: HPCPOP - every hook is one int test when no control below it listens
final class PartVisibility {
	static final byte UNKNOWN = 0, VISIBLE = 1, HIDDEN = 2;
	private static final String KEY = "org.eclipse.swt.widgets.PartVisibility"; //$NON-NLS-1$

	/* the watched controls of a display, dense (a removal moves the last one into the hole) */
	static final class Registry {
		Control [] controls = new Control [8];
		int count;
	}

	private PartVisibility () {
	}

	/** {@link Control#isPartVisible()}: the control and its ancestors up to the shell visible, sized, not part-hidden, the shell not minimized. */
	static boolean visible (Control control) {
		for (Control c = control; c != null; c = c.parent) {
			if (c.isDisposed () || c.partHidden || !c.getVisible ()) return false;
			Point size = c.getSize ();
			if (size.x <= 0 || size.y <= 0) return false;
			if (c instanceof Shell shell) return !shell.getMinimized ();
		}
		return true;
	}

	/** A PartVisible or PartHidden listener was added: watch the control (its state now is recorded, no event). */
	static void watch (Control control) {
		if (control.partIndex >= 0 || control.isDisposed ()) return;
		Registry registry = registry (control.display, true);
		if (registry.count == registry.controls.length) {
			Control [] grown = new Control [registry.count * 2];
			System.arraycopy (registry.controls, 0, grown, 0, registry.count);
			registry.controls = grown;
		}
		control.partIndex = registry.count;
		registry.controls [registry.count++] = control;
		control.partState = visible (control) ? VISIBLE : HIDDEN;
		count (control, 1);
	}

	/* adds delta to the subtree counts of the control and its ancestors up to its shell */
	private static void count (Control control, int delta) {
		for (Control c = control; c != null; c = c.parent) {
			c.partCount += delta;
			if (c instanceof Shell) break;
		}
	}

	/** The control is being disposed (the root of the disposal): its watched subtree leaves its ancestors' counts. */
	static void released (Control control) {
		int watched = control.partCount;
		if (watched == 0) return;
		Composite parent = control instanceof Shell ? null : control.parent;
		if (parent != null) count (parent, -watched);
		/* the disposed watched controls leave the registry at the next visit (they answer isDisposed) */
	}

	/** Before setParent: the control's watched subtree leaves the counts of its old ancestors. */
	static void detach (Control control) {
		if (control.partCount != 0 && control.parent != null && !(control instanceof Shell)) count (control.parent, -control.partCount);
	}

	/** After setParent (or its refusal): the watched subtree joins the counts of its parent; a new parent is a transition. */
	static void attach (Control control, Composite oldParent) {
		if (control.partCount == 0 || control.isDisposed ()) return;
		if (control.parent != null && !(control instanceof Shell)) count (control.parent, control.partCount);
		if (control.parent != oldParent) changed (control, PartVisibilityEvent.REPARENT);
	}

	/** An event SWT is sending: Resize may cross zero, Iconify and Deiconify change a shell. */
	static void sent (Widget widget, int eventType) {
		if (widget instanceof Control control && control.partCount != 0) {
			changed (control, eventType == SWT.Resize ? PartVisibilityEvent.SIZE : PartVisibilityEvent.MINIMIZE);
		}
	}

	/** A shell's own setVisible (win32 Decorations, GTK Shell), which does not go through Control.setVisible. */
	static void shown (Control shell) {
		changed (shell, PartVisibilityEvent.VISIBILITY);
	}

	/** A shell's native size message after the platform restored or minimized it (win32 sends no Resize then). */
	static void restored (Control shell) {
		changed (shell, PartVisibilityEvent.MINIMIZE);
	}

	/**
	 * A change at the origin (its visibility, size, parent, part-hidden mark, or its shell's minimized state): every
	 * watched control at or under it whose state changed is told, ancestors first.
	 */
	static void changed (Control origin, int cause) {
		if (origin.partCount == 0 || origin.isDisposed ()) return;
		Registry registry = registry (origin.display, false);
		if (registry == null) return;
		Control [] affected = new Control [Math.min (origin.partCount, registry.count)];
		int [] depths = new int [affected.length];
		int found = 0;
		for (int i = 0; i < registry.count; i++) {
			Control control = registry.controls [i];
			if (control.isDisposed ()) {
				remove (registry, i--);
				continue;
			}
			int depth = depthBelow (control, origin);
			if (depth < 0 || found == affected.length) continue;
			/* insertion by depth keeps ancestors before descendants (and registry order among equals) */
			int at = found++;
			while (at > 0 && depths [at - 1] > depth) {
				affected [at] = affected [at - 1];
				depths [at] = depths [at - 1];
				at--;
			}
			affected [at] = control;
			depths [at] = depth;
		}
		for (int i = 0; i < found; i++) {
			Control control = affected [i];
			if (control.isDisposed () || control.partIndex < 0) continue;
			byte now = visible (control) ? VISIBLE : HIDDEN;
			if (control.partState == now) continue;
			control.partState = now;
			Event event = new Event ();
			event.detail = cause;
			event.item = origin;
			control.sendEvent (now == VISIBLE ? SWT.PartVisible : SWT.PartHidden, event);
		}
	}

	/* the number of parent steps from the control up to the origin, or -1 when the origin is not an ancestor-or-self */
	private static int depthBelow (Control control, Control origin) {
		int depth = 0;
		for (Control c = control; c != null; c = c.parent, depth++) {
			if (c == origin) return depth;
			if (c instanceof Shell) return -1;
		}
		return -1;
	}

	private static void remove (Registry registry, int index) {
		Control removed = registry.controls [index];
		removed.partIndex = -1;
		Control last = registry.controls [--registry.count];
		registry.controls [registry.count] = null;
		if (index != registry.count) {
			registry.controls [index] = last;
			last.partIndex = index;
		}
	}

	private static Registry registry (Display display, boolean create) {
		Registry registry = (Registry) display.getData (KEY);
		if (registry == null && create) display.setData (KEY, registry = new Registry ());
		return registry;
	}
}
