package org.eclipse.swt.internal.render.m3.gc;

import jdk.m3.graphics.M3Canvas;
import jdk.m3.graphics.M3Font;
import jdk.m3.graphics.M3Matrix33;
import jdk.m3.graphics.M3Paint;
import jdk.m3.graphics.M3Path;
import jdk.m3.graphics.M3Region;
import jdk.m3.graphics.M3SamplingMode;
import jdk.m3.graphics.M3TextBlob;
import jdk.m3.graphics.M3RRect;
import jdk.m3.graphics.M3Rect;

/**
 * Adapter to wrap Skija's Canvas as IM3Canvas. (Stub for now, can be extended
 * as needed.)
 */
class M3CanvasAdapter implements IM3Canvas {
	private final M3Canvas delegate;

	public M3CanvasAdapter(M3Canvas canvas) {
		this.delegate = canvas;
	}

	@Override
	public int getSaveCount() {
		return delegate.getSaveCount();
	}

	@Override
	public M3Matrix33 getLocalToDevice() {
		return delegate.getLocalToDeviceAsMatrix33();
	}

	@Override
	public void restoreToCount(int saveCount) {
		delegate.restoreToCount(saveCount);
	}

	@Override
	public void drawLine(float x0, float y0, float x1, float y1, M3Paint paint) {
		delegate.drawLine(x0, y0, x1, y1, paint);
	}

	@Override
	public void drawArc(int left, int top, int right, int bottom, int startAngle, int sweepAngle, boolean useCenter,
			M3Paint paint) {
		delegate.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint);
	}

	@Override
	public void drawRect(M3Rect rect, M3Paint paint) {
		delegate.drawRect(rect, paint);
	}

	@Override
	public void drawOval(M3Rect rect, M3Paint paint) {
		delegate.drawOval(rect, paint);
	}

	@Override
	public void drawPath(M3Path path, M3Paint paint) {
		delegate.drawPath(path, paint);
	}

	@Override
	public void drawPolygon(float[] points, M3Paint paint) {
		delegate.drawPolygon(points, paint);
	}

	@Override
	public void drawRRect(M3RRect rrect, M3Paint paint) {
		delegate.drawRRect(rrect, paint);
	}

	@Override
	public void drawImage(IM3Image image, int x, int y) {
		delegate.drawImage(((M3ImageAdapter)image).getM3Image(), x, y);
	}

	@Override
	public void drawImageRect(IM3Image image, M3Rect src, M3Rect dst, M3SamplingMode sampling, M3Paint paint, boolean strict) {
		delegate.drawImageRect(((M3ImageAdapter)image).getM3Image(), src, dst, sampling, paint, strict);
	}

	@Override
	public void setMatrix(M3Matrix33 matrix) {
		delegate.setMatrix(matrix);
	}

	@Override
	public void save() {
		delegate.save();
	}

	@Override
	public void clipRect(M3Rect rect) {
		delegate.clipRect(rect);
	}

	@Override
	public void clear(int color) {
		delegate.clear(color);
	}

	@Override
	public void restore() {
		delegate.restore();
	}

	@Override
	public void clipPath(jdk.m3.graphics.M3Path path, jdk.m3.graphics.M3ClipMode mode,
			boolean doAntiAlias) {
		delegate.clipPath(path, mode, doAntiAlias);
	}

	@Override
	public void drawString(String text, int x, int i, M3Font f, M3Paint fgp) {
		delegate.drawString(text, x, i, f, fgp);

	}

	@Override
	public void clipRegion(M3Region m3Region) {
		delegate.clipRegion(m3Region);
	}

	@Override
	public void drawTextBlob(M3TextBlob blob, float x, float y, M3Paint paint) {
		delegate.drawTextBlob(blob, x, y, paint);
	}
}