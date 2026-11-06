package org.eclipse.swt.internal.render.m3.gc;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.Region;
import io.github.humbleui.skija.TextBlob;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/**
 * Abstraction for Skija's Canvas, for future decoupling.
 */
public interface IM3Canvas {
	int getSaveCount();
	/** The current local-to-device matrix (leaf 6, TRANSFORM_STATE: the GC transform is drawn on the frame's matrix). */
	default Matrix33 getLocalToDevice() {
		return Matrix33.IDENTITY;
	}
	void restoreToCount(int saveCount);
	void drawLine(float x0, float y0, float x1, float y1, Paint paint);
	void drawArc(int left, int top, int right, int bottom, int startAngle, int sweepAngle, boolean useCenter, Paint paint);
	void drawRect(Rect rect, Paint paint);
	void drawOval(Rect rect, Paint paint);
	void drawPath(Path path, Paint paint);
	void drawPolygon(float[] points, Paint paint);
	void drawRRect(RRect rrect, Paint paint);
	void drawImage(IM3Image image, int x, int y);
	void drawImageRect(IM3Image image, Rect src, Rect dst, io.github.humbleui.skija.SamplingMode sampling, Paint paint, boolean strict);
	void setMatrix(Matrix33 matrix);
	void save();
	void clipRect(Rect rect);
	void clear(int color);
	void restore();
	void clipPath(Path m3Path, ClipMode intersect, boolean b);
	void drawString(String text, int x, int i, Font f, Paint fgp);
	void clipRegion(Region m3Region);

	/** L13 GLYPH_RUN_DRAW v1: one positioned text blob (doubles record or ignore it) */
	default void drawTextBlob(TextBlob blob, float x, float y, Paint paint) {
	}
}