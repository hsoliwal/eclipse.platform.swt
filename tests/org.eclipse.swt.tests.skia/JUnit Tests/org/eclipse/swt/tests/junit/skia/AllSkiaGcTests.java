package org.eclipse.swt.tests.junit.skia;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({ Test_org_eclipse_swt_skia_RectangleConverter.class, //
		Test_org_eclipse_swt_skia_RGBAEncoder.class, //
		Test_org_eclipse_swt_skia_M3ToSwtImageConvert.class, //
		Test_org_eclipse_swt_skia_M3ToSwtImageConverter.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawLine.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawArc.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawOval.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawPath.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawPoint.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawPolygon.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawPolyline.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawRectangle.class, //
		org.eclipse.swt.tests.junit.skia.Test_org_eclipse_swt_skia_drawRoundRectangle.class, //
		Test_org_eclipse_swt_skia_drawImageCache.class, //
		Test_org_eclipse_swt_skia_FontSmoothing.class, //
		Test_org_eclipse_swt_skia_drawString.class, //
		Test_org_eclipse_swt_skia_drawText.class, //
		Test_org_eclipse_swt_skia_drawText_NoCache.class, //
		Test_org_eclipse_swt_skia_DrawnCustomDraw.class, //
		Test_org_eclipse_swt_skia_M3CanvasFactory.class, //
		Test_org_eclipse_swt_skia_CanvasCoverage.class, //
		Test_org_eclipse_swt_skia_CanvasDamage.class, //
		Test_org_eclipse_swt_skia_StrokeState.class, //
		Test_org_eclipse_swt_skia_CompositeCoverage.class, //

})
public class AllSkiaGcTests {

}