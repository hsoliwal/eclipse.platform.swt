/*******************************************************************************
 * Copyright (c) 2018, 2023 Red Hat and others. All rights reserved.
 * The contents of this file are made available under the terms
 * of the GNU Lesser General Public License (LGPL) Version 2.1 that
 * accompanies this distribution (lgpl-v21.txt).  The LGPL is also
 * available at http://www.gnu.org/licenses/lgpl.html.  If the version
 * of the LGPL at http://www.gnu.org is different to the version of
 * the LGPL accompanying this distribution and there is any conflict
 * between the two license versions, the terms of the LGPL accompanying
 * this distribution shall govern.
 *
 * Contributors:
 *     Red Hat - initial API and implementation
 */
package org.eclipse.swt.tests.win32;

import org.eclipse.swt.graphics.ImageWin32Tests;
import org.eclipse.swt.tests.win32.widgets.TestTableTooltip;
import org.eclipse.swt.tests.win32.widgets.TestTreeColumn;
import org.eclipse.swt.tests.win32.widgets.Test_org_eclipse_swt_widgets_Composite;
import org.eclipse.swt.tests.win32.widgets.Test_org_eclipse_swt_widgets_Display;
import org.eclipse.swt.tests.win32.widgets.Test_org_eclipse_swt_widgets_Tree;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({ ImageWin32Tests.class, //
		Test_org_eclipse_swt_dnd_DND.class, //
		Test_org_eclipse_swt_events_KeyEvent.class, //
		Test_org_eclipse_swt_widgets_Composite.class, //
		Test_org_eclipse_swt_widgets_Display.class, //
		Test_org_eclipse_swt_widgets_Tree.class, //
		TestTreeColumn.class, //
		TestTableTooltip.class, //
		Win32DPIUtilTests.class, //
		NativeRegionBatchWin32Tests.class })
public class AllWin32Tests {

}

/** Native update-region verification for SWT's existing Win32 viewport. */
class NativeRegionBatchWin32Tests {
	@org.junit.jupiter.api.Test
	void nativeUnionAndInvalidInput() {
		org.eclipse.swt.widgets.Shell shell = new org.eclipse.swt.widgets.Shell(
				org.eclipse.swt.widgets.Display.getDefault());
		try {
			org.eclipse.swt.widgets.Canvas canvas = new org.eclipse.swt.widgets.Canvas(shell, org.eclipse.swt.SWT.NONE);
			canvas.setBounds(0, 0, 360, 250);
			shell.setSize(430, 330);
			shell.open();
			canvas.update();
			long hwnd = canvas.handle;
			org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.ValidateRect(hwnd, null));
			int[] regions = { 8, 12, 16, 14, 220, 160, 18, 16 };
			org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.RedrawWindowRegions(
					hwnd, regions, 2, org.eclipse.swt.internal.win32.OS.RDW_INVALIDATE));
			long rgn = org.eclipse.swt.internal.win32.OS.CreateRectRgn(0, 0, 0, 0);
			org.junit.jupiter.api.Assertions.assertTrue(rgn != 0);
			try {
				org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.GetUpdateRgn(hwnd, rgn, false)
						> org.eclipse.swt.internal.win32.OS.NULLREGION);
				org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.PtInRegion(rgn, 12, 18));
				org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.PtInRegion(rgn, 225, 166));
				org.junit.jupiter.api.Assertions.assertFalse(org.eclipse.swt.internal.win32.OS.PtInRegion(rgn, 115, 83));
				org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.ValidateRect(hwnd, null));
				int[] bad = { 8, 12, -1, 14, 220, 160, 18, 16 };
				org.junit.jupiter.api.Assertions.assertFalse(org.eclipse.swt.internal.win32.OS.RedrawWindowRegions(
						hwnd, bad, 2, org.eclipse.swt.internal.win32.OS.RDW_INVALIDATE));
				org.junit.jupiter.api.Assertions.assertEquals(org.eclipse.swt.internal.win32.OS.NULLREGION,
						org.eclipse.swt.internal.win32.OS.GetUpdateRgn(hwnd, rgn, false));
			} finally {
				org.eclipse.swt.internal.win32.OS.DeleteObject(rgn);
			}
		} finally {
			shell.dispose();
		}
	}

	@org.junit.jupiter.api.Test
	void viewportBatchPreservesDisjointRegion() {
		if ("true".equals(System.getenv("SWT_NATIVE_REGION_BATCH_REQUIRED"))) {
			org.junit.jupiter.api.Assertions.assertTrue(Boolean.getBoolean("swt.win32.nativeRegionBatch"),
					"native batch must be enabled in the actual test JVM");
			org.junit.jupiter.api.Assertions.assertTrue(Boolean.getBoolean("swt.win32.nativeRegionBatch.required"),
					"native batch failure must be fatal in the actual test JVM");
		}
		org.eclipse.swt.widgets.Shell shell = new org.eclipse.swt.widgets.Shell(
				org.eclipse.swt.widgets.Display.getDefault());
		try {
			org.eclipse.swt.widgets.Canvas canvas = new org.eclipse.swt.widgets.Canvas(shell, org.eclipse.swt.SWT.NONE);
			canvas.setBounds(0, 0, 360, 250);
			shell.setSize(430, 330);
			shell.open();
			canvas.update();
			long hwnd = canvas.handle;
			org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.ValidateRect(hwnd, null));
			canvas.beginBatch(org.eclipse.swt.SWT.PLANE_VIEWPORT);
			try {
				canvas.redraw(8, 12, 16, 14, false);
				canvas.redraw(220, 160, 18, 16, false);
			} finally {
				canvas.endBatch(org.eclipse.swt.SWT.PLANE_VIEWPORT);
			}
			long rgn = org.eclipse.swt.internal.win32.OS.CreateRectRgn(0, 0, 0, 0);
			org.junit.jupiter.api.Assertions.assertTrue(rgn != 0);
			try {
				org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.GetUpdateRgn(hwnd, rgn, false)
						> org.eclipse.swt.internal.win32.OS.NULLREGION);
				org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.PtInRegion(rgn, 12, 18));
				org.junit.jupiter.api.Assertions.assertTrue(org.eclipse.swt.internal.win32.OS.PtInRegion(rgn, 225, 166));
				org.junit.jupiter.api.Assertions.assertFalse(org.eclipse.swt.internal.win32.OS.PtInRegion(rgn, 115, 83));
			} finally {
				org.eclipse.swt.internal.win32.OS.DeleteObject(rgn);
			}
		} finally {
			shell.dispose();
		}
	}
}
