/*******************************************************************************
 * Copyright (c) 2026 Synexia contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.junit.jupiter.api.Test;

public class Test_org_eclipse_swt_custom_ControlEditor extends Test_org_eclipse_swt_widgets_Canvas {

	static final class TrackingCanvas extends Canvas {
		int moveAboveCalls;
		Control lastMoveAbove;

		TrackingCanvas(Composite parent, int style) {
			super(parent, style);
		}

		@Override
		public void moveAbove(Control control) {
			moveAboveCalls++;
			lastMoveAbove = control;
			super.moveAbove(control);
		}
	}

	@Test
	public void test_editorRemainsOnTopZPlaneAfterLayout() {
		Canvas sibling = new Canvas(shell, SWT.NONE);
		sibling.setBounds(0, 0, 100, 30);

		TrackingCanvas editorControl = new TrackingCanvas(shell, SWT.NONE);
		ControlEditor editor = new ControlEditor(shell);
		editor.minimumWidth = 100;
		editor.minimumHeight = 30;

		editor.setEditor(editorControl);
		assertTrue(editorControl.moveAboveCalls > 0,
				"assigning an editor must place it on the editor z-plane");
		assertNull(editorControl.lastMoveAbove,
				"null means top of the sibling drawing order");

		int calls = editorControl.moveAboveCalls;
		editor.layout();
		assertEquals(calls + 1, editorControl.moveAboveCalls,
				"relayout must reassert editor z-order after sibling/native changes");
		assertNull(editorControl.lastMoveAbove);

		editor.dispose();
		sibling.dispose();
		editorControl.dispose();
	}
}
