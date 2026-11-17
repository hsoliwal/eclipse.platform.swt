package org.eclipse.swt.tests.junit.maya;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({ Test_org_eclipse_swt_maya_MayaLibrary.class, //
		org.eclipse.swt.internal.gpu.Test_org_eclipse_swt_maya_M3Canvas.class, //
		org.eclipse.swt.internal.canvasext.raster.Test_org_eclipse_swt_maya_TextLayoutDraw.class, //
		Test_org_eclipse_swt_maya_RectangleConverter.class, //
		Test_org_eclipse_swt_maya_RGBAEncoder.class, //
		Test_org_eclipse_swt_maya_MayaToSwtImageConvert.class, //
		Test_org_eclipse_swt_maya_MayaToSwtImageConverter.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawLine.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawArc.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawOval.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawPath.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawPoint.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawPolygon.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawPolyline.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawRectangle.class, //
		org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_drawRoundRectangle.class, //
		Test_org_eclipse_swt_maya_drawImageCache.class, //
		Test_org_eclipse_swt_maya_FontSmoothing.class, //
		Test_org_eclipse_swt_maya_drawString.class, //
		Test_org_eclipse_swt_maya_drawText.class, //
		Test_org_eclipse_swt_maya_drawText_NoCache.class, //
		Test_org_eclipse_swt_maya_DrawnCustomDraw.class, //
		Test_org_eclipse_swt_maya_CanvasFactory.class, //
		Test_org_eclipse_swt_maya_CanvasCoverage.class, //
		Test_org_eclipse_swt_maya_CanvasDamage.class, //
		Test_org_eclipse_swt_maya_StrokeState.class, //
		Test_org_eclipse_swt_maya_CompositeCoverage.class, //

})
public class AllMayaGcTests {

}