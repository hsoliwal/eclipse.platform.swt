package org.eclipse.swt.tests.junit.maya;


import java.util.List;

import org.eclipse.swt.internal.canvasext.raster.IRasterCanvas;
import org.eclipse.swt.internal.canvasext.raster.IRasterImage;
import org.eclipse.swt.internal.maya.ClipMode;
import org.eclipse.swt.internal.maya.Font;
import org.eclipse.swt.internal.maya.Matrix33;
import org.eclipse.swt.internal.maya.Paint;
import org.eclipse.swt.internal.maya.Path;
import org.eclipse.swt.internal.maya.Region;
import org.eclipse.swt.internal.maya.SamplingMode;
import org.eclipse.swt.internal.maya.types.RRect;
import org.eclipse.swt.internal.maya.types.Rect;

public class MayaCanvasDouble implements IRasterCanvas {

	int saveCount;
	String name;
	List<MethodCall> calls;

	public MayaCanvasDouble(int width, int height, String string, List<MethodCall> calls) {
		this.name = string;
		this.calls = calls;
	}
	
	@Override
	public int getSaveCount() {
		return saveCount;
	}

	@Override
	public void restoreToCount(int saveCount) {
		add(logCall("restoreToCount", saveCount));
		this.saveCount = saveCount;

	}

	@Override
	public void drawLine(float x0, float y0, float x1, float y1, Paint paint) {
		add(logCall("drawLine", x0, y0, x1, y1, paint));
	}

	@Override
	public void drawArc(int left, int top, int right, int bottom, int startAngle, int sweepAngle, boolean useCenter,
			Paint paint) {
		add(logCall("drawArc", left, top, right, bottom, startAngle, sweepAngle, useCenter, paint));
	}

	@Override
	public void drawRect(Rect rect, Paint paint) {
		add(logCall("drawRect", rect, paint));
	}

	@Override
	public void drawOval(Rect rect, Paint paint) {
		add(logCall("drawOval", rect, paint));
	}

	@Override
	public void drawPath(Path path, Paint paint) {
		add(logCall("drawPath", path, paint));
	}

	@Override
	public void drawPolygon(float[] points, Paint paint) {
		add(logCall("drawPolygon", points, paint));
	}

	@Override
	public void drawRRect(RRect rrect, Paint paint) {
		add(logCall("drawRRect", rrect, paint));
	}

	@Override
	public void drawImage(IRasterImage image, int x, int y) {
		add(logCall("drawImage", image, x, y));
	}

	@Override
	public void drawImageRect(IRasterImage image, Rect src, Rect dst, SamplingMode sampling, Paint paint, boolean strict) {
		add(logCall("drawImageRect", image, src, dst, sampling, paint, strict));
	}

	@Override
	public void setMatrix(Matrix33 matrix) {
		add(logCall("setMatrix", matrix));
	}

	@Override
	public void save() {
		add(logCall("save"));
		saveCount++;
	}

	@Override
	public void clipRect(Rect rect) {
		add(logCall("clipRect", rect));
	}

	@Override
	public void clear(int color) {
		add(logCall("clear", color));
	}

	@Override
	public void restore() {
		add(logCall("restore"));
	}

	@Override
	public void clipPath(Path mayaPath, ClipMode intersect, boolean b) {
		add(logCall("clipPath", mayaPath, intersect, b));
	}

	@Override
	public void drawString(String text, int x, int i, Font f, Paint fgp) {
		add(logCall("drawString", text, x, i, f, fgp));
	}

	@Override
	public void clipRegion(Region mayaRegion) {
		add(logCall("clipRegion", mayaRegion));
	}

	private void add(MethodCall logCall) {
		calls.add(logCall);
		
	}

	private MethodCall logCall(String methodName, Object... args) {
		return new MethodCall(name, methodName, args);
	}

}