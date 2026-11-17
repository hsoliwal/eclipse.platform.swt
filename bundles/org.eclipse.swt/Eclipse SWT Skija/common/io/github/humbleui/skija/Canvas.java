// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

public class Canvas extends Managed {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public final Object _owner;

    @ApiStatus.Internal
    public Canvas(long ptr, boolean managed, Object owner) {
        super(ptr, _FinalizerHolder.PTR, managed);
        this._owner = owner;
    }

    /**
     * <p>Constructs a canvas that draws into bitmap.
     * Sets default pixel geometry in constructed Surface.</p>
     *
     * <p>Bitmap is copied so that subsequently editing bitmap will not affect
     * constructed Canvas.</p>
     *
     * <p>May be deprecated in the future.</p>
     *
     * @param bitmap  width, height, ColorType, ColorAlphaType, and pixel
     *                storage of raster surface
     *
     * @see <a href="https://fiddle.skia.org/c/@Canvas_copy_const_SkBitmap">https://fiddle.skia.org/c/@Canvas_copy_const_SkBitmap</a>
     */
    public Canvas(@NotNull Bitmap bitmap) {
        this(bitmap, new SurfaceProps());
    }

    /**
     * <p>Constructs a canvas that draws into bitmap.
     * Use props to match the device characteristics, like LCD striping.</p>
     *
     * <p>Bitmap is copied so that subsequently editing bitmap will not affect
     * constructed Canvas.</p>
     *
     * @param bitmap  width, height, ColorType, ColorAlphaType, and pixel
     *                storage of raster surface
     * @param surfaceProps   order and orientation of RGB striping; and whether to use
     *                       device independent fonts
     *
     * @see <a href="https://fiddle.skia.org/c/@Canvas_const_SkBitmap_const_SkSurfaceProps">https://fiddle.skia.org/c/@Canvas_const_SkBitmap_const_SkSurfaceProps</a>
     */
    public Canvas(@NotNull Bitmap bitmap, @NotNull SurfaceProps surfaceProps) {
        this(_nMakeFromBitmap(bitmap._ptr, surfaceProps._getFlags(), surfaceProps._pixelGeometry.ordinal()), true, bitmap);
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(bitmap);
    }

    /** 
     * <p>Draws pts using clip, Matrix and Paint paint.</p>
     *
     * <p>Each adjacent pair of points draws a line segment.
     * count minus one lines are drawn; the first and last point are used once.</p>
     *
     * <p>Each line segment respects paint PaintStrokeCap and Paint stroke width.
     * PaintMode is ignored, as if were set to {@link PaintMode#STROKE}.</p>
     *
     * <p>Always draws each element one at a time; is not affected by
     * PaintStrokeJoin, and unlike drawPath(), does not create a mask from all points
     * and lines before drawing.</p>
     *
     * @param coords array of points to draw
     * @param paint  stroke, blend, color, and so on, used to draw
     *
     * @see <a href="https://fiddle.skia.org/c/@Canvas_drawPoints">https://fiddle.skia.org/c/@Canvas_drawPoints</a>
     */
    @NotNull @Contract("_, _ -> this")
    public Canvas drawPolygon(@NotNull float[] coords, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert coords != null : "Can’t drawPolygon with coords == null";
        assert paint != null : "Can’t drawPolygon with paint == null";
        Stats.onNativeCall();
        _nDrawPoints(_ptr, 2 /* SkCanvas::PointMode::kPolygon_PointMode */, coords, Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _, _, _, _ -> this")
    public Canvas drawLine(float x0, float y0, float x1, float y1, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert paint != null : "Can’t drawLine with paint == null";
        Stats.onNativeCall();
        _nDrawLine(_ptr, x0, y0, x1, y1, Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _, _, _, _, _, _, _ -> this")
    public Canvas drawArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean includeCenter, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert paint != null : "Can’t drawArc with paint == null";
        Stats.onNativeCall();
        _nDrawArc(_ptr, left, top, right, bottom, startAngle, sweepAngle, includeCenter, Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _ -> this")
    public Canvas drawRect(@NotNull Rect r, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert r != null : "Can’t drawRect with r == null";
        assert paint != null : "Can’t drawRect with paint == null";
        Stats.onNativeCall();
        _nDrawRect(_ptr, r._left, r._top, r._right, r._bottom, Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _ -> this")
    public Canvas drawOval(@NotNull Rect r, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert r != null : "Can’t drawOval with r == null";
        assert paint != null : "Can’t drawOval with paint == null";
        Stats.onNativeCall();
        _nDrawOval(_ptr, r._left, r._top, r._right, r._bottom, Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _ -> this")
    public Canvas drawRRect(@NotNull RRect r, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert r != null : "Can’t drawRRect with r == null";
        assert paint != null : "Can’t drawRRect with paint == null";
        Stats.onNativeCall();
        _nDrawRRect(_ptr, r._left, r._top, r._right, r._bottom, r._radii, Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _ -> this")
    public Canvas drawPath(@NotNull Path path, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert path != null : "Can’t drawPath with path == null";
        assert paint != null : "Can’t drawPath with paint == null";
        Stats.onNativeCall();
        _nDrawPath(_ptr, Native.getPtr(path), Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(path);
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _, _ -> this")
    public Canvas drawImage(@NotNull Image image, float left, float top) {
        return drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), Rect.makeXYWH(left, top, image.getWidth(), image.getHeight()), SamplingMode.DEFAULT, null, true);
    }

    @NotNull @Contract("_, _, _, _ -> this")
    public Canvas drawImage(@NotNull Image image, float left, float top, @Nullable Paint paint) {
        return drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), Rect.makeXYWH(left, top, image.getWidth(), image.getHeight()), SamplingMode.DEFAULT, paint, true);
    }

    @NotNull @Contract("_, _, _, _, _ -> this")
    public Canvas drawImageRect(@NotNull Image image, @NotNull Rect src, @NotNull Rect dst, @NotNull SamplingMode samplingMode, @Nullable Paint paint, boolean strict) {
        assert _ptr != 0 : "Canvas is closed";
        assert image != null : "Can’t drawImageRect with image == null";
        assert src != null : "Can’t drawImageRect with src == null";
        assert dst != null : "Can’t drawImageRect with dst == null";
        assert samplingMode != null : "Can’t drawImageRect with samplingMode == null";
        Stats.onNativeCall();
        _nDrawImageRect(_ptr, Native.getPtr(image), src._left, src._top, src._right, src._bottom, dst._left, dst._top, dst._right, dst._bottom, samplingMode._pack(), Native.getPtr(paint), strict);
        ReferenceUtil.reachabilityFence(image);
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_, _, _, _, _ -> this")
    public Canvas drawString(@NotNull String s, float x, float y, Font font, @NotNull Paint paint) {
        assert _ptr != 0 : "Canvas is closed";
        assert s != null : "Can’t drawString with s == null";
        assert paint != null : "Can’t drawString with paint == null";
        Stats.onNativeCall();
        _nDrawString(_ptr, s, x, y, Native.getPtr(font), Native.getPtr(paint));
        ReferenceUtil.reachabilityFence(font);
        ReferenceUtil.reachabilityFence(paint);
        return this;
    }

    @NotNull @Contract("_ -> this")
    public Canvas clear(int color) {
        assert _ptr != 0 : "Canvas is closed";
        Stats.onNativeCall();
        _nClear(_ptr, color);
        return this;
    }

    /**
     * Replaces Matrix with matrix.
     * Unlike concat(), any prior matrix state is overwritten.
     *
     * @param matrix  matrix to copy, replacing existing Matrix
     *
     * @see <a href="https://fiddle.skia.org/c/@Canvas_setMatrix">https://fiddle.skia.org/c/@Canvas_setMatrix</a>
     */
    @NotNull @Contract("_ -> this")
    public Canvas setMatrix(@NotNull Matrix33 matrix) {
        assert _ptr != 0 : "Canvas is closed";
        assert matrix != null : "Can’t setMatrix with matrix == null";
        Stats.onNativeCall();
        _nSetMatrix(_ptr, matrix._mat);
        return this;
    }

    @NotNull @Contract("_, _, _ -> this")
    public Canvas clipRect(@NotNull Rect r, @NotNull ClipMode mode, boolean antiAlias) {
        assert _ptr != 0 : "Canvas is closed";
        assert r != null : "Can’t clipRect with r == null";
        assert mode != null : "Can’t clipRect with mode == null";
        Stats.onNativeCall();
        _nClipRect(_ptr, r._left, r._top, r._right, r._bottom, mode.ordinal(), antiAlias);
        return this;
    }

    @NotNull @Contract("_ -> this")
    public Canvas clipRect(@NotNull Rect r) {
        return clipRect(r, ClipMode.INTERSECT, false);
    }

    @NotNull @Contract("_, _, _ -> this")
    public Canvas clipPath(@NotNull Path p, @NotNull ClipMode mode, boolean antiAlias) {
        assert _ptr != 0 : "Canvas is closed";
        assert p != null : "Can’t clipPath with p == null";
        assert mode != null : "Can’t clipPath with mode == null";
        Stats.onNativeCall();
        _nClipPath(_ptr, Native.getPtr(p), mode.ordinal(), antiAlias);
        ReferenceUtil.reachabilityFence(p);
        return this;
    }

    @NotNull @Contract("_, _ -> this")
    public Canvas clipRegion(@NotNull Region r, @NotNull ClipMode mode) {
        assert _ptr != 0 : "Canvas is closed";
        assert r != null : "Can’t clipRegion with r == null";
        assert mode != null : "Can’t clipRegion with mode == null";
        Stats.onNativeCall();
        _nClipRegion(_ptr, Native.getPtr(r), mode.ordinal());
        ReferenceUtil.reachabilityFence(r);
        return this;
    }

    @NotNull @Contract("_ -> this")
    public Canvas clipRegion(@NotNull Region r) {
        return clipRegion(r, ClipMode.INTERSECT);
    }

    public int save() {
        try {
            assert _ptr != 0 : "Canvas is closed";
            Stats.onNativeCall();
            return _nSave(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public int getSaveCount() {
        try {
            assert _ptr != 0 : "Canvas is closed";
            Stats.onNativeCall();
            return _nGetSaveCount(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @NotNull @Contract("-> this")
    public Canvas restore() {
        assert _ptr != 0 : "Canvas is closed";
        Stats.onNativeCall();
        _nRestore(_ptr);
        return this;
    }

    @NotNull @Contract("_ -> this")
    public Canvas restoreToCount(int saveCount) {
        assert _ptr != 0 : "Canvas is closed";
        Stats.onNativeCall();
        _nRestoreToCount(_ptr, saveCount);
        return this;
    }

    @ApiStatus.Internal
    public void invalidate() {
        _ptr = 0;
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    public static native long _nGetFinalizer();
    public static native long _nMakeFromBitmap(long bitmapPtr, int flags, int pixelGeometry);

    public static native void _nDrawPoints(long ptr, int mode, float[] coords, long paintPtr);
    public static native void _nDrawLine(long ptr, float x0, float y0, float x1, float y1, long paintPtr);
    public static native void _nDrawArc(long ptr, float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean includeCenter, long paintPtr);
    public static native void _nDrawRect(long ptr, float left, float top, float right, float bottom, long paintPtr);
    public static native void _nDrawOval(long ptr, float left, float top, float right, float bottom, long paint);
    public static native void _nDrawRRect(long ptr, float left, float top, float right, float bottom, float[] radii, long paintPtr);

    public static native void _nDrawPath(long ptr, long nativePath, long paintPtr);
    public static native void _nDrawImageRect(long ptr, long nativeImage, float sl, float st, float sr, float sb, float dl, float dt, float dr, float db, long samplingMode, long paintPtr, boolean strict);

    public static native void _nDrawString(long ptr, String string, float x, float y, long font, long paint);
    public static native void _nDrawTextBlob(long ptr, long blob, float x, float y, long paint);

    public static native void _nDrawColor(long ptr, int color, int blendMode);

    public static native void _nClear(long ptr, int color);

    public static native void _nSetMatrix(long ptr, float[] matrix);

    public static native void _nClipRect(long ptr, float left, float top, float right, float bottom, int mode, boolean antiAlias);

    public static native void _nClipPath(long ptr, long nativePath, int mode, boolean antiAlias);
    public static native void _nClipRegion(long ptr, long nativeRegion, int mode);

    public static native int  _nSave(long ptr);

    public static native int  _nGetSaveCount(long ptr);
    public static native void _nRestore(long ptr);
    public static native void _nRestoreToCount(long ptr, int saveCount);
}
