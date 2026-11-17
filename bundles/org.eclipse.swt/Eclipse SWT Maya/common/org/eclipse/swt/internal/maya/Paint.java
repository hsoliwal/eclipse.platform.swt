// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;
import org.eclipse.swt.internal.maya.types.*;

public class Paint extends Managed {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @ApiStatus.Internal
    public Paint(long ptr, boolean managed) {
        super(ptr, _FinalizerHolder.PTR, managed);
    }

    /**
     * Constructs SkPaint with default values.
     *
     * @see <a href="https://fiddle.skia.org/c/@Paint_empty_constructor">https://fiddle.skia.org/c/@Paint_empty_constructor</a>
    */
    public Paint() {
        super(_nMake(), _FinalizerHolder.PTR);
        Stats.onNativeCall();
    }

    /**
     * <p>Makes a shallow copy of Paint. PathEffect, Shader,
     * MaskFilter, ColorFilter, and ImageFilter are shared
     * between the original paint and the copy.</p>
     *
     * <p>The referenced objects PathEffect, Shader, MaskFilter, ColorFilter,
     * and ImageFilter cannot be modified after they are created.</p>
     *
     * @return  shallow copy of paint
     *
     * @see <a href="https://fiddle.skia.org/c/@Paint_copy_const_SkPaint">https://fiddle.skia.org/c/@Paint_copy_const_SkPaint</a>
     */
    @NotNull @Contract("-> new")
    public Paint makeClone() {
        try {
            Stats.onNativeCall();
            return new Paint(_nMakeClone(_ptr), true);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal @Override
    public boolean _nativeEquals(Native other) {
        try {
            return _nEquals(_ptr, Native.getPtr(other));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(other);
        }
    }

    /** 
     * Returns true if pixels on the active edges of Path may be drawn with partial transparency.
     * 
     * @return  antialiasing state
     */
    public boolean isAntiAlias() {
        try {
            Stats.onNativeCall();
            return _nIsAntiAlias(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Requests, but does not require, that edge pixels draw opaque or with partial transparency.
     * 
     * @param value  setting for antialiasing
     */
    @NotNull @Contract("_ -> this")
    public Paint setAntiAlias(boolean value) {
        Stats.onNativeCall();
        _nSetAntiAlias(_ptr, value);
        return this;
    }

    /**
     * @return  true if color error may be distributed to smooth color transition.
     */
    public boolean isDither() {
        try {
            Stats.onNativeCall();
            return _nIsDither(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @return  whether the geometry is filled, stroked, or filled and stroked.
     */
    @NotNull
    public PaintMode getMode() {
        try {
            Stats.onNativeCall();
            return PaintMode._values[_nGetMode(_ptr)];
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Sets whether the geometry is filled, stroked, or filled and stroked.
     *
     * @see <a href="https://fiddle.skia.org/c/@Paint_setStyle">https://fiddle.skia.org/c/@Paint_setStyle</a>
     * @see <a href="https://fiddle.skia.org/c/@Stroke_Width">https://fiddle.skia.org/c/@Stroke_Width</a>
     */
    @NotNull @Contract("!null -> this; null -> fail")
    public Paint setMode(@NotNull PaintMode style) {
        assert style != null : "Paint::setMode expected style != null";
        Stats.onNativeCall();
        _nSetMode(_ptr, style.ordinal());
        return this;
    }

    /** 
     * Retrieves alpha and RGB, unpremultiplied, packed into 32 bits.
     * Use helpers {@link Color#getA(int)}, {@link Color#getR(int)}, {@link Color#getG(int)}, and {@link Color#getB(int)} to extract
     * a color component.
     *
     * @return  unpremultiplied ARGB
     */
    public int getColor() {
        try {
            Stats.onNativeCall();
            return _nGetColor(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Retrieves alpha and RGB, unpremultiplied, as four floating point values. RGB are
     * extended sRGB values (sRGB gamut, and encoded with the sRGB transfer function).
     *
     * @return  unpremultiplied RGBA
     */
    @NotNull
    public Color4f getColor4f() {
        try {
            Stats.onNativeCall();
            return _nGetColor4f(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Sets alpha and RGB used when stroking and filling. The color is a 32-bit value,
     * unpremultiplied, packing 8-bit components for alpha, red, blue, and green.
     *
     * @param color  unpremultiplied ARGB
     *
     * @see <a href="https://fiddle.skia.org/c/@Paint_setColor">https://fiddle.skia.org/c/@Paint_setColor</a>
     */
    @NotNull @Contract("_ -> this")
    public Paint setColor(int color) {
        Stats.onNativeCall();
        _nSetColor(_ptr, color);
        return this;
    }

    /**
     * Sets alpha and RGB used when stroking and filling. The color is four floating
     * point values, unpremultiplied. The color values are interpreted as being in sRGB.
     *
     * @param color       unpremultiplied RGBA
     * @return            this
     */
    @NotNull @Contract("!null -> this; null -> fail")
    public Paint setColor4f(@NotNull Color4f color) {
        return setColor4f(color, null);
    }

    /**
     * Sets alpha and RGB used when stroking and filling. The color is four floating
     * point values, unpremultiplied. The color values are interpreted as being in
     * the colorSpace. If colorSpace is nullptr, then color is assumed to be in the
     * sRGB color space.
     *
     * @param color       unpremultiplied RGBA
     * @param colorSpace  SkColorSpace describing the encoding of color
     * @return            this
     */
    @NotNull @Contract("!null, _ -> this; null, _ -> fail")
    public Paint setColor4f(@NotNull Color4f color, @Nullable ColorSpace colorSpace) {
        try {
            assert color != null : "Paint::setColor4f expected color != null";
            Stats.onNativeCall();
            _nSetColor4f(_ptr, color.getR(), color.getG(), color.getB(), color.getA(), Native.getPtr(colorSpace));
            return this;
        } finally {
            ReferenceUtil.reachabilityFence(colorSpace);
        }
    }

    /**
     * Retrieves alpha from the color used when stroking and filling.
     * 
     * @return  alpha ranging from 0f, fully transparent, to 1f, fully opaque
     */
    public float getAlphaf() {
        return getColor4f().getA();
    }

    /**
     * Retrieves alpha from the color used when stroking and filling.
     * 
     * @return  alpha ranging from 0, fully transparent, to 255, fully opaque
     */
    public int getAlpha() {
        return Math.round(getAlphaf() * 255f);
    }

    /**
     * <p>Replaces alpha, leaving RGB unchanged. An out of range value triggers
     * an assert in the debug build. a is a value from 0f to 1f.</p>
     * 
     * <p>a set to zero makes color fully transparent; a set to 1.0 makes color
     * fully opaque.</p>
     *
     * @param a  alpha component of color
     * @return   this
     */
    @NotNull @Contract("_ -> this")
    public Paint setAlphaf(float a) {
        return setColor4f(getColor4f().withA(a));
    }

    /**
     * <p>Replaces alpha, leaving RGB unchanged. An out of range value triggers
     * an assert in the debug build. a is a value from 0 to 255.</p>
     * 
     * <p>a set to zero makes color fully transparent; a set to 255 makes color
     * fully opaque.</p>
     *
     * @param a  alpha component of color
     * @return   this
     */
    @NotNull @Contract("_ -> this")
    public Paint setAlpha(int a) {
        return setAlphaf(a / 255f);
    }

    /** 
     * Returns the thickness of the pen used by Paint to outline the shape.
     *
     * @return  zero for hairline, greater than zero for pen thickness
     */
    public float getStrokeWidth() {
        try {
            Stats.onNativeCall();
            return _nGetStrokeWidth(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Sets the thickness of the pen used by the paint to outline the shape.
     * A stroke-width of zero is treated as "hairline" width. Hairlines are always exactly one
     * pixel wide in device space (their thickness does not change as the canvas is scaled).
     * Negative stroke-widths are invalid; setting a negative width will have no effect.
     *
     * @param width  zero thickness for hairline; greater than zero for pen thickness
     *
     * @see <a href="https://fiddle.skia.org/c/@Miter_Limit">https://fiddle.skia.org/c/@Miter_Limit</a>
     * @see <a href="https://fiddle.skia.org/c/@Paint_setStrokeWidth">https://fiddle.skia.org/c/@Paint_setStrokeWidth</a>
     */
    @NotNull @Contract("_ -> this")
    public Paint setStrokeWidth(float width) {
        Stats.onNativeCall();
        _nSetStrokeWidth(_ptr, width);
        return this;
    }

    /** 
     * Returns the limit at which a sharp corner is drawn beveled.
     *
     * @return  zero and greater miter limit
     */
    public float getStrokeMiter() {
        try {
            Stats.onNativeCall();
            return _nGetStrokeMiter(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Sets the limit at which a sharp corner is drawn beveled.
     * Valid values are zero and greater.
     * Has no effect if miter is less than zero.
     *
     * @param miter  zero and greater miter limit
     * @return       this
     *
     * @see <a href="https://fiddle.skia.org/c/@Paint_setStrokeMiter">https://fiddle.skia.org/c/@Paint_setStrokeMiter</a>
     */
    @NotNull @Contract("_ -> this")
    public Paint setStrokeMiter(float miter) {
        Stats.onNativeCall();
        _nSetStrokeMiter(_ptr, miter);
        return this;
    }

    /**
     * @return  the geometry drawn at the beginning and end of strokes.
     */
    @NotNull @Contract("-> this")
    public PaintStrokeCap getStrokeCap() {
        try {
            Stats.onNativeCall();
            return PaintStrokeCap._values[_nGetStrokeCap(_ptr)];
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /** 
     * Sets the geometry drawn at the beginning and end of strokes.
     * 
     * @return  this
     *
     * @see <a href="https://fiddle.skia.org/c/@Paint_setStrokeCap_a">https://fiddle.skia.org/c/@Paint_setStrokeCap_a</a>
     * @see <a href="https://fiddle.skia.org/c/@Paint_setStrokeCap_b">https://fiddle.skia.org/c/@Paint_setStrokeCap_b</a>
     */
    @NotNull @Contract("!null -> this; null -> fail")
    public Paint setStrokeCap(@NotNull PaintStrokeCap cap) {
        assert cap != null : "Paint::setStrokeCap expected cap != null";
        Stats.onNativeCall();
        _nSetStrokeCap(_ptr, cap.ordinal());
        return this;
    }

    /**
     * @return  the geometry drawn at the corners of strokes.
     */
    @NotNull @Contract("-> this")
    public PaintStrokeJoin getStrokeJoin() {
        try {
            Stats.onNativeCall();
            return PaintStrokeJoin._values[_nGetStrokeJoin(_ptr)];
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Sets the geometry drawn at the corners of strokes.
     *
     * @return  this
     * 
     * @see <a href="https://fiddle.skia.org/c/@Paint_setStrokeJoin">https://fiddle.skia.org/c/@Paint_setStrokeJoin</a>
     */
    @NotNull @Contract("!null -> this; null -> fail")
    public Paint setStrokeJoin(@NotNull PaintStrokeJoin join) {
        assert join != null : "Paint::setStrokeJoin expected join != null";
        Stats.onNativeCall();
        _nSetStrokeJoin(_ptr, join.ordinal());
        return this;
    }

    /**
     * @return  {@link Shader} or null
     * @see     <a href="https://fiddle.skia.org/c/@Paint_refShader">https://fiddle.skia.org/c/@Paint_refShader</a>
     */
    @Nullable
    public Shader getShader() {
        try {
            Stats.onNativeCall();
            long shaderPtr = _nGetShader(_ptr);
            return shaderPtr == 0 ? null : new Shader(shaderPtr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @param shader  how geometry is filled with color; if null, color is used instead
     *
     * @see <a href="https://fiddle.skia.org/c/@Color_Filter_Methods">https://fiddle.skia.org/c/@Color_Filter_Methods</a>
     * @see <a href="https://fiddle.skia.org/c/@Paint_setShader">https://fiddle.skia.org/c/@Paint_setShader</a>
     */
    @NotNull @Contract("_ -> this")
    public Paint setShader(@Nullable Shader shader) {
        try {
            Stats.onNativeCall();
            _nSetShader(_ptr, Native.getPtr(shader));
            return this;
        } finally {
            ReferenceUtil.reachabilityFence(shader);
        }
    }

    /**
     * @return  {@link ColorFilter} or null
     * @see     <a href="https://fiddle.skia.org/c/@Paint_refColorFilter">https://fiddle.skia.org/c/@Paint_refColorFilter</a>
     */
    @Nullable
    public ColorFilter getColorFilter() {
        try {
            Stats.onNativeCall();
            long colorFilterPtr = _nGetColorFilter(_ptr);
            return colorFilterPtr == 0 ? null : new ColorFilter(colorFilterPtr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Returns BlendMode. By default, returns {@link BlendMode#SRC_OVER}.
     *
     * @return  mode used to combine source color with destination color
     */
    @Nullable
    public BlendMode getBlendMode() {
        try {
            Stats.onNativeCall();
            return BlendMode._values[_nGetBlendMode(_ptr)];
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Sets SkBlendMode to mode. Does not check for valid input.
     *
     * @param mode  BlendMode used to combine source color and destination
     * @return      this
     */
    @NotNull @Contract("!null -> this; null -> fail")
    public Paint setBlendMode(@NotNull BlendMode mode) {
        assert mode != null : "Paint::setBlendMode expected mode != null";
        Stats.onNativeCall();
        _nSetBlendMode(_ptr, mode.ordinal());
        return this;
    }

    /**
     * @return  {@link PathEffect} or null
     * @see     <a href="https://fiddle.skia.org/c/@Paint_refPathEffect">https://fiddle.skia.org/c/@Paint_refPathEffect</a>
     */
    @Nullable
    public PathEffect getPathEffect() {
        try {
            Stats.onNativeCall();
            long pathEffectPtr = _nGetPathEffect(_ptr);
            return pathEffectPtr == 0 ? null : new PathEffect(pathEffectPtr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @param p  replace {@link Path} with a modification when drawn
     *
     * @see <a href="https://fiddle.skia.org/c/@Mask_Filter_Methods">https://fiddle.skia.org/c/@Mask_Filter_Methods</a>
     * @see <a href="https://fiddle.skia.org/c/@Paint_setPathEffect">https://fiddle.skia.org/c/@Paint_setPathEffect</a>
     */
    @NotNull @Contract("_ -> this")
    public Paint setPathEffect(@Nullable PathEffect p) {
        try {
            Stats.onNativeCall();
            _nSetPathEffect(_ptr, Native.getPtr(p));
            return this;
        } finally {
            ReferenceUtil.reachabilityFence(p);
        }
    }

    /**
     * @return  {@link MaskFilter} if previously set, null otherwise
     * @see     <a href="https://fiddle.skia.org/c/@Paint_refMaskFilter">https://fiddle.skia.org/c/@Paint_refMaskFilter</a>
     */
    @Nullable
    public MaskFilter getMaskFilter() {
        try {
            Stats.onNativeCall();
            long maskFilterPtr = _nGetMaskFilter(_ptr);
            return maskFilterPtr == 0 ? null : new MaskFilter(maskFilterPtr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @return  {@link ImageFilter} or null
     * @see     <a href="https://fiddle.skia.org/c/@Paint_refImageFilter">https://fiddle.skia.org/c/@Paint_refImageFilter</a>
     */
    @Nullable
    public ImageFilter getImageFilter() {
        try {
            Stats.onNativeCall();
            long imageFilterPtr = _nGetImageFilter(_ptr);
            return imageFilterPtr == 0 ? null : new ImageFilter(imageFilterPtr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public static native long  _nGetFinalizer();
    public static native long  _nMake();
    public static native long  _nMakeClone(long ptr);
    public static native boolean _nEquals(long ptr, long otherPtr);

    public static native boolean _nIsAntiAlias(long ptr);
    public static native void  _nSetAntiAlias(long ptr, boolean value);
    public static native boolean _nIsDither(long ptr);

    public static native int   _nGetMode(long ptr);
    public static native void  _nSetMode(long ptr, int value);
    public static native int   _nGetColor(long ptr);
    public static native Color4f _nGetColor4f(long ptr);
    public static native void  _nSetColor(long ptr, int argb);
    public static native void  _nSetColor4f(long ptr, float r, float g, float b, float a, long colorSpacePtr);
    public static native float _nGetStrokeWidth(long ptr);
    public static native void  _nSetStrokeWidth(long ptr, float value);
    public static native float _nGetStrokeMiter(long ptr);
    public static native void  _nSetStrokeMiter(long ptr, float value);
    public static native int   _nGetStrokeCap(long ptr);
    public static native void  _nSetStrokeCap(long ptr, int value);
    public static native int   _nGetStrokeJoin(long ptr);
    public static native void  _nSetStrokeJoin(long ptr, int value);

    public static native long  _nGetShader(long ptr);
    public static native void  _nSetShader(long ptr, long shaderPtr);
    public static native long  _nGetColorFilter(long ptr);

    public static native int   _nGetBlendMode(long ptr);
    public static native void  _nSetBlendMode(long ptr, int mode);
    public static native long  _nGetPathEffect(long ptr);
    public static native void  _nSetPathEffect(long ptr, long pathEffectPtr);
    public static native long  _nGetMaskFilter(long ptr);

    public static native long  _nGetImageFilter(long ptr);

}
