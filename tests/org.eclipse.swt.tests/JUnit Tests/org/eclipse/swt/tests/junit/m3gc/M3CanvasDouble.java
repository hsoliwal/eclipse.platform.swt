package org.eclipse.swt.tests.junit.m3gc;


import java.util.List;

import org.eclipse.swt.internal.render.m3.gc.IM3Canvas;
import org.eclipse.swt.internal.render.m3.gc.IM3Image;

import jdk.m3.graphics.M3ClipMode;
import jdk.m3.graphics.M3Font;
import jdk.m3.graphics.M3Matrix33;
import jdk.m3.graphics.M3Paint;
import jdk.m3.graphics.M3Path;
import jdk.m3.graphics.M3Region;
import jdk.m3.graphics.M3SamplingMode;
import jdk.m3.graphics.M3RRect;
import jdk.m3.graphics.M3Rect;

public class M3CanvasDouble implements IM3Canvas {

	int saveCount;
	String name;
	List<MethodCall> calls;

	public M3CanvasDouble(int width, int height, String string, List<MethodCall> calls) {
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
	public void drawLine(float x0, float y0, float x1, float y1, M3Paint paint) {
		add(logCall("drawLine", x0, y0, x1, y1, paint));
	}

	@Override
	public void drawArc(int left, int top, int right, int bottom, int startAngle, int sweepAngle, boolean useCenter,
			M3Paint paint) {
		add(logCall("drawArc", left, top, right, bottom, startAngle, sweepAngle, useCenter, paint));
	}

	@Override
	public void drawRect(M3Rect rect, M3Paint paint) {
		add(logCall("drawRect", rect, paint));
	}

	@Override
	public void drawOval(M3Rect rect, M3Paint paint) {
		add(logCall("drawOval", rect, paint));
	}

	@Override
	public void drawPath(M3Path path, M3Paint paint) {
		add(logCall("drawPath", path, paint));
	}

	@Override
	public void drawPolygon(float[] points, M3Paint paint) {
		add(logCall("drawPolygon", points, paint));
	}

	@Override
	public void drawRRect(M3RRect rrect, M3Paint paint) {
		add(logCall("drawRRect", rrect, paint));
	}

	@Override
	public void drawImage(IM3Image image, int x, int y) {
		add(logCall("drawImage", image, x, y));
	}

	@Override
	public void drawImageRect(IM3Image image, M3Rect src, M3Rect dst, M3SamplingMode sampling, M3Paint paint, boolean strict) {
		add(logCall("drawImageRect", image, src, dst, sampling, paint, strict));
	}

	@Override
	public void setMatrix(M3Matrix33 matrix) {
		add(logCall("setMatrix", matrix));
	}

	@Override
	public void save() {
		add(logCall("save"));
		saveCount++;
	}

	@Override
	public void clipRect(M3Rect rect) {
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
	public void clipPath(M3Path m3Path, M3ClipMode intersect, boolean b) {
		add(logCall("clipPath", m3Path, intersect, b));
	}

	@Override
	public void drawString(String text, int x, int i, M3Font f, M3Paint fgp) {
		add(logCall("drawString", text, x, i, f, fgp));
	}

	@Override
	public void clipRegion(M3Region m3Region) {
		add(logCall("clipRegion", m3Region));
	}

	private void add(MethodCall logCall) {
		calls.add(logCall);
		
	}

	private MethodCall logCall(String methodName, Object... args) {
		return new MethodCall(name, methodName, args);
	}

}