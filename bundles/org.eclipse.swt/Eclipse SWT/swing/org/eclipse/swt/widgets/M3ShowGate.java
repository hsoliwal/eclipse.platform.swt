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

import java.awt.Component;
import java.awt.event.HierarchyEvent;

/**
 * Hidden viewports (brief rule 24): runs the patch of a viewport when its Swing component starts showing, on the SWT
 * thread, where every other change of the viewport's model runs. A show made by SWT (<code>setVisible(true)</code>,
 * a selected tab) reaches the listener on the SWT thread and patches before Swing paints, so the show paints once; a
 * show that Swing makes on its own thread (a window mapped later) is handed to the SWT thread.
 */
final class M3ShowGate {
	private M3ShowGate () {
	}

	/**
	 * Runs <code>patch</code> whenever <code>swing</code> starts showing.
	 *
	 * @param control the SWT control of the viewport
	 * @param swing the Swing component whose showing state gates the viewport
	 * @param patch what the viewport tells Swing for the changes made while it was hidden
	 */
	static void install (Control control, Component swing, Runnable patch) {
		Display display = control.display;
		swing.addHierarchyListener (e -> {
			if ((e.getChangeFlags () & HierarchyEvent.SHOWING_CHANGED) == 0 || !swing.isShowing ()) return;
			if (display.getThread () == Thread.currentThread ()) {
				patch.run ();
			} else if (!display.isDisposed ()) {
				display.asyncExec (() -> {
					if (!control.isDisposed ()) patch.run ();
				});
			}
		});
	}
}
