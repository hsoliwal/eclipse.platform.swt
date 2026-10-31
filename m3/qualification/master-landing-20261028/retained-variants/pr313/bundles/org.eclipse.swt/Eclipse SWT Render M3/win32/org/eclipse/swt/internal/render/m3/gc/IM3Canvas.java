package org.eclipse.swt.internal.render.m3.gc;

import jdk.m3.graphics.M3ClipMode;
import jdk.m3.graphics.M3Font;
import jdk.m3.graphics.M3Matrix33;
import jdk.m3.graphics.M3Paint;
import jdk.m3.graphics.M3Path;
import jdk.m3.graphics.M3Region;
import jdk.m3.graphics.M3TextBlob;
import jdk.m3.graphics.M3RRect;
import jdk.m3.graphics.M3Rect;

/**
 * Abstraction for the jdk.m3.graphics canvas (<code>M3Canvas</code>), for future decoupling.
 */
public interface IM3Canvas {
	int getSaveCount();
	/** The current local-to-device matrix (leaf 6, TRANSFORM_STATE: the GC transform is drawn on the frame's matrix). */
	default M3Matrix33 getLocalToDevice() {
		return M3Matrix33.IDENTITY;
	}
	void restoreToCount(int saveCount);
	void drawLine(float x0, float y0, float x1, float y1, M3Paint paint);
	void drawArc(int left, int top, int right, int bottom, int startAngle, int sweepAngle, boolean useCenter, M3Paint paint);
	void drawRect(M3Rect rect, M3Paint paint);
	void drawOval(M3Rect rect, M3Paint paint);
	void drawPath(M3Path path, M3Paint paint);
	void drawPolygon(float[] points, M3Paint paint);
	void drawRRect(M3RRect rrect, M3Paint paint);
	void drawImage(IM3Image image, int x, int y);
	void drawImageRect(IM3Image image, M3Rect src, M3Rect dst, jdk.m3.graphics.M3SamplingMode sampling, M3Paint paint, boolean strict);
	void setMatrix(M3Matrix33 matrix);
	void save();
	void clipRect(M3Rect rect);
	void clear(int color);
	void restore();
	void clipPath(M3Path m3Path, M3ClipMode intersect, boolean b);
	void drawString(String text, int x, int i, M3Font f, M3Paint fgp);
	void clipRegion(M3Region m3Region);

	/** L13 GLYPH_RUN_DRAW v1: one positioned text blob (doubles record or ignore it) */
	default void drawTextBlob(M3TextBlob blob, float x, float y, M3Paint paint) {
	}
}