/*******************************************************************************
 * Copyright (c) 2000, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Red Hat Inc. - Bug 462631
 *     Hitesh Soliwal (Synexia) - tests for viewport mode, shared style policy, visibility gate
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Suite for testing all of the widget test cases.
 */
@Suite
@SelectClasses({ //
		// Enhanced M3 API contracts; recorded differentials have a separate oracle run.
		Test_M3BulkUpdates.class, //
		Test_M3CheckState.class, //
		Test_M3Collation.class, //
		Test_M3ColumnFilter.class, //
		Test_M3ColumnSort.class, //
		Test_M3DiffApply.class, //
		Test_M3Emptiness.class, //
		Test_M3FilterAsync.class, //
		Test_M3ItemRange.class, //
		Test_M3ItemSelection.class, //
		Test_M3Model.class, //
		Test_M3Patch.class, //
		Test_M3Prefetch.class, //
		Test_M3ProxyModel.class, //
		Test_M3Residency.class, //
		Test_M3RowExtents.class, //
		Test_M3RowMath.class, //
		Test_M3ScrollBarModel.class, //
		Test_M3ScrollMemento.class, //
		Test_M3Scrolling.class, //
		Test_M3SortFilterWidgets.class, //
		Test_M3TextMatch.class, //
		Test_M3TextSegments.class, //
		Test_M3TokenMatch.class, //
		Test_M3TypeAhead.class, //
		Test_M3VisibilityObserver.class, //
		// Existing widget contracts
		Test_VirtualTreeCoordinateVisibility.class, //
		Test_org_eclipse_swt_custom_BidiSegmentListener.class, //
		Test_org_eclipse_swt_custom_BusyIndicator.class, //
		Test_org_eclipse_swt_custom_CCombo.class, //
		Test_org_eclipse_swt_custom_CLabel.class, //
		Test_org_eclipse_swt_custom_CTabFolder.class, //
		Test_org_eclipse_swt_custom_CTabItem.class, //
		Test_org_eclipse_swt_custom_ExtendedModifyListener.class, //
		Test_org_eclipse_swt_custom_LineBackgroundListener.class, //
		Test_org_eclipse_swt_custom_LineStyleListener.class, //
		Test_org_eclipse_swt_custom_StyleRange.class, //
		Test_org_eclipse_swt_custom_StyledText.class, //
		Test_org_eclipse_swt_custom_StyledTextContent.class, //
		Test_org_eclipse_swt_custom_StyledTextLineSpacingProvider.class, //
		Test_org_eclipse_swt_custom_StyledText_VariableLineHeight.class, //
		Test_org_eclipse_swt_custom_StyledText_multiCaretsSelections.class, //
		Test_org_eclipse_swt_custom_StyledText_PagedContent.class, //
		Test_org_eclipse_swt_custom_StyledText_ApiDifferential.class, //
		Test_org_eclipse_swt_custom_StyledText_ApiScale.class, //
		Test_org_eclipse_swt_custom_StyledText_SingleControl.class, //
		Test_org_eclipse_swt_custom_StyledText_VisibilityGate.class, //
		Test_org_eclipse_swt_custom_StyledText_VisibilityDifferential.class, //
		Test_org_eclipse_swt_custom_StyledText_RoundTrip.class, //
		Test_org_eclipse_swt_custom_TextChangeListener.class, //
		Test_org_eclipse_swt_custom_VerifyKeyListener.class, //
		Test_org_eclipse_swt_widgets_Button.class, //
		Test_org_eclipse_swt_widgets_Canvas.class, //
		Test_org_eclipse_swt_widgets_Caret.class, //
		Test_org_eclipse_swt_widgets_ColorDialog.class, //
		Test_org_eclipse_swt_widgets_Combo.class, //
		Test_org_eclipse_swt_widgets_Composite.class, //
		Test_org_eclipse_swt_widgets_CoolBar.class, //
		// Failing test: Test_org_eclipse_swt_widgets_CoolItem.class, //
		Test_org_eclipse_swt_widgets_DateTime_Style_CALENDAR.class, //
		Test_org_eclipse_swt_widgets_DateTime_Style_DATE.class, //
		Test_org_eclipse_swt_widgets_DateTime_Style_TIME.class, //
		Test_org_eclipse_swt_widgets_DirectoryDialog.class, //
		Test_org_eclipse_swt_widgets_Event.class, //
		Test_org_eclipse_swt_widgets_ExpandBar.class, //
		Test_org_eclipse_swt_widgets_ExpandItem.class, //
		Test_org_eclipse_swt_widgets_FileDialog.class, //
		Test_org_eclipse_swt_widgets_FontDialog.class, //
		Test_org_eclipse_swt_widgets_Group.class, //
		Test_org_eclipse_swt_widgets_Label.class, //
		Test_org_eclipse_swt_widgets_Link.class, //
		Test_org_eclipse_swt_widgets_List.class, //
		Test_org_eclipse_swt_widgets_Menu.class, //
		Test_org_eclipse_swt_widgets_MenuItem.class, //
		Test_org_eclipse_swt_widgets_MessageBox.class, //
		Test_org_eclipse_swt_widgets_Monitor.class, //
		Test_org_eclipse_swt_widgets_ProgressBar.class, //
		Test_org_eclipse_swt_widgets_Sash.class, //
		Test_org_eclipse_swt_widgets_Scale.class, //
		Test_org_eclipse_swt_widgets_StyleBits.class, //
		Test_org_eclipse_swt_widgets_ScrollBar.class, //
		Test_org_eclipse_swt_widgets_ScrollBar_CallbackDisposalGtk.class, //
		Test_org_eclipse_swt_widgets_ScrolledComposite.class, //
		Test_org_eclipse_swt_widgets_Shell.class, //
		Test_org_eclipse_swt_widgets_Slider.class, //
		Test_org_eclipse_swt_widgets_Spinner.class, //
		Test_org_eclipse_swt_widgets_Synchronizer.class, //
		Test_org_eclipse_swt_widgets_TabFolder.class, //
		Test_org_eclipse_swt_widgets_TabItem.class, //
		Test_org_eclipse_swt_widgets_Table.class, //
		Test_org_eclipse_swt_widgets_TableColumn.class, //
		Test_org_eclipse_swt_widgets_TableItem.class, //
		Test_org_eclipse_swt_widgets_Text.class, //
		Test_org_eclipse_swt_widgets_ToolBar.class, //
		Test_org_eclipse_swt_widgets_ToolItem.class, //
		Test_org_eclipse_swt_widgets_Tree.class, //
		Test_org_eclipse_swt_widgets_Tree_CoordinateVisibilityGtk.class, //
		Test_org_eclipse_swt_widgets_TreeColumn.class, //
		Test_org_eclipse_swt_widgets_TreeItem.class, //
		Test_org_eclipse_swt_widgets_ViewportPaintGraphAtomicGrowth.class, //
		Test_org_eclipse_swt_widgets_ViewportPaintGraphDirtyWordScan.class, //
		Test_org_eclipse_swt_widgets_VirtualPageSource.class, //
})
public class AllWidgetTests {
}
