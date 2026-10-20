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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * Leaving a scroll-bar plane batch (<code>beginBatch / endBatch (SWT.PLANE_SCROLLBARS)</code>) repaints the frame
 * only: the scroll bar shows its new state and no client pixel is painted again (the defect SpliceProbe T9 measured:
 * every batch repainted every visible row). Win32 (the batch's frame redraw is win32's).
 */
public class Test_org_eclipse_swt_widgets_ViewportBatch_Frame {

	@Test
	public void leavingTheScrollBarPlaneRepaintsTheFrameOnly () {
		Assumptions.assumeTrue ("win32".equals (SWT.getPlatform ()), "the frame redraw is win32's");
		Display display = Display.getDefault ();
		Shell shell = new Shell (display, SWT.NO_TRIM | SWT.ON_TOP);
		try {
			shell.setBounds (40, 40, 300, 300);
			shell.setLayout (new FillLayout ());
			Canvas canvas = new Canvas (shell, SWT.V_SCROLL);
			int [] paints = {0};
			Rectangle [] painted = {null};
			canvas.addListener (SWT.Paint, e -> {
				paints [0]++;
				painted [0] = new Rectangle (e.x, e.y, e.width, e.height);
			});
			ScrollBar bar = canvas.getVerticalBar ();
			bar.setValues (0, 0, 1000, 100, 10, 100);
			shell.open ();
			ViewportDifferential.drain (display);
			canvas.update ();
			paints [0] = 0;
			canvas.beginBatch (SWT.PLANE_SCROLLBARS);
			bar.setValues (400, 0, 2000, 100, 10, 100);
			canvas.endBatch (SWT.PLANE_SCROLLBARS);
			ViewportDifferential.drain (display);
			canvas.update ();
			assertEquals (400, bar.getSelection ());
			assertEquals (2000, bar.getMaximum ());
			assertEquals (0, paints [0], () -> "client repainted by the frame redraw: " + painted [0]);
		} finally {
			shell.dispose ();
		}
	}
}
