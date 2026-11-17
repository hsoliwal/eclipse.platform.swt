// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import java.util.function.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

public class Font extends Managed {
    static { Library.staticLoad(); }

    @ApiStatus.Internal public FontMetrics _metrics = null;
    @ApiStatus.Internal public Float _spacing = null;

    @ApiStatus.Internal
    public Font(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    @ApiStatus.Internal
    public Font(long ptr, boolean managed) {
        super(ptr, _FinalizerHolder.PTR, managed);
    }

    /**
     * Returns Font initialized with default values
     */
    public Font() {
        this(_nMakeDefault());
        Stats.onNativeCall();
    }

    /**
     * Returns Font with Typeface and default size
     *
     * @param typeface  typeface and style used to draw and measure text. Pass null for default
     */
    public Font(@Nullable Typeface typeface) {
        this(_nMakeTypeface(Native.getPtr(typeface)));
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(typeface);
    }

    /**
     * @param typeface  typeface and style used to draw and measure text. Pass null for default
     * @param size      typographic size of the text
     */
    public Font(@Nullable Typeface typeface, float size) {
        this(_nMakeTypefaceSize(Native.getPtr(typeface), size));
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(typeface);
    }

    /**
     * Constructs Font with default values with Typeface and size in points,
     * horizontal scale, and horizontal skew. Horizontal scale emulates condensed
     * and expanded fonts. Horizontal skew emulates oblique fonts.
     *
     * @param typeface  typeface and style used to draw and measure text. Pass null for default
     * @param size      typographic size of the text
     * @param scaleX    text horizonral scale
     * @param skewX     additional shear on x-axis relative to y-axis
     */
    public Font(@Nullable Typeface typeface, float size, float scaleX, float skewX) {
        this(_nMakeTypefaceSizeScaleSkew(Native.getPtr(typeface), size, scaleX, skewX));
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(typeface);
    }

    /**
     * Compares fonts, and returns true if they are equivalent.
     * May return false if Typeface has identical contents but different pointers.
     */
    @ApiStatus.Internal @Override
    public boolean _nativeEquals(Native other) {
        try {
            return _nEquals(_ptr, Native.getPtr(other));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(other);
        }
    }

    @ApiStatus.Internal 
    public void _resetMetrics() {
        _metrics = null;
        _spacing = null;
    }

    @ApiStatus.Internal
    public static Font makeClone(long ptr) {
        Stats.onNativeCall();
        return new Font(_nMakeClone(ptr));
    }

    /**
     * Sets whether to always hint glyphs. If forceAutoHinting is set, instructs the font manager to always hint glyphs. Only affects platforms that use FreeType as the font manager.
     *
     * @param value  setting to always hint glyphs
     * @return       this
     */
    public Font setAutoHintingForced(boolean value) {
        _resetMetrics();
        Stats.onNativeCall();
        _nSetAutoHintingForced(_ptr, value);
        return this;
    }

    /**
     * Requests, but does not require, that glyphs respect sub-pixel positioning.
     *
     * @param value  setting for sub-pixel positioning 
     * @return       this
     */
    public Font setSubpixel(boolean value) {
        _resetMetrics();
        Stats.onNativeCall();
        _nSetSubpixel(_ptr, value);
        return this;
    }

    /**
     * Requests that baselines be snapped to pixels when the current transformation matrix is axis
     * aligned.
     * 
     * @param value  setting for baseline snapping to pixels
     * @return       this
     */
    public Font setBaselineSnapped(boolean value) {
        _resetMetrics();
        Stats.onNativeCall();
        _nSetBaselineSnapped(_ptr, value);
        return this;
    }

    /**
     * Whether edge pixels draw opaque or with partial transparency.
     */
    public FontEdging getEdging() {
        try {
            Stats.onNativeCall();
            return FontEdging._values[_nGetEdging(_ptr)];
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Requests, but does not require, that edge pixels draw opaque or with
     * partial transparency.
     */
    public Font setEdging(FontEdging value) {
        _resetMetrics();
        Stats.onNativeCall();
        _nSetEdging(_ptr, value.ordinal());
        return this;
    }

    /**
     * Sets level of glyph outline adjustment. Does not check for valid values of hintingLevel.
     */
    public Font setHinting(FontHinting value) {
        _resetMetrics();
        Stats.onNativeCall();
        _nSetHinting(_ptr, value.ordinal());
        return this;
    }

    /**
     * @return {@link Typeface} if set, or null
     */ 
    @Nullable
    public Typeface getTypeface() {
        try {
            Stats.onNativeCall();
            long ptr = _nGetTypeface(_ptr);
            return ptr == 0 ? null : new Typeface(ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @return  text size in points
     */
    public float getSize() {
        try {
            Stats.onNativeCall();
            return _nGetSize(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @return  text scale on x-axis. Default value is 1
     */
    public float getScaleX() {
        try {
            Stats.onNativeCall();
            return _nGetScaleX(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @return  text skew on x-axis. Default value is 0
     */
    public float getSkewX() {
        try {
            Stats.onNativeCall();
            return _nGetSkewX(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Sets text size in points. Has no effect if value is not greater than or equal to zero.
     */
    public Font setSize(float value) {
        _resetMetrics();
        Stats.onNativeCall();
        _nSetSize(_ptr, value);
        return this;
    }

    /**
     * Sets text scale on x-axis. Default value is 1.
     */
    public Font setScaleX(float value) {
        _resetMetrics();
        Stats.onNativeCall();
        _nSetScaleX(_ptr, value);
        return this;
    }

    /**
     *  Converts text into glyph indices.
     *
     *  @return  the corresponding glyph ids for each character.
     */
    public short[] getStringGlyphs(String s) {
        return getUTF32Glyphs(s.codePoints().toArray());
    }

    /**
     *  Given an array of UTF32 character codes, return their corresponding glyph IDs.
     *
     *  @return  the corresponding glyph IDs for each character.
     */
    public short[] getUTF32Glyphs(int[] uni) {
        try {
            Stats.onNativeCall();
            return _nGetUTF32Glyphs(_ptr, uni);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @return  the bounding box of text
     */
    public Rect measureText(String s) {
        return measureText(s, null);
    }

    /**
     * @param p  stroke width or PathEffect may modify the advance with
     * @return   the bounding box of text
     */
    public Rect measureText(String s, Paint p) {
        try {
            Stats.onNativeCall();
            return _nMeasureText(_ptr, s, Native.getPtr(p));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(p);
        }
    }

    public float measureTextWidth(String s) {
        Stats.onNativeCall();
        return measureTextWidth(s, null);
    }

    public float measureTextWidth(String s, Paint p) {
        try {
            Stats.onNativeCall();
            return _nMeasureTextWidth(_ptr, s, Native.getPtr(p));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(p);
        }
    }

    /**
     * Retrieves the bounds for each glyph
     */
    public Rect[] getBounds(short[] glyphs) {
        return getBounds(glyphs, null);
    }

    /**
     * Retrieves the bounds for each glyph
     */
    public Rect[] getBounds(short[] glyphs, Paint p) {
        try {
            Stats.onNativeCall();
            return _nGetBounds(_ptr, glyphs, Native.getPtr(p));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(p);
        }
    }

    /**
     * Returns FontMetrics associated with Typeface. Results are scaled by text size but does not take into account
     * dimensions required by text scale, text skew, fake bold, style stroke, and {@link PathEffect}.
     */
    @NotNull
    public FontMetrics getMetrics() {
        try {
            if (_metrics == null) {
                Stats.onNativeCall();
                _metrics = _nGetMetrics(_ptr);
            }
            return _metrics;
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @ApiStatus.Internal public static native long    _nGetFinalizer();
    @ApiStatus.Internal public static native long    _nMakeDefault();
    @ApiStatus.Internal public static native long    _nMakeTypeface(long typefacePtr);
    @ApiStatus.Internal public static native long    _nMakeTypefaceSize(long typefacePtr, float size);
    @ApiStatus.Internal public static native long    _nMakeTypefaceSizeScaleSkew(long typefacePtr, float size, float scaleX, float skewX);
    @ApiStatus.Internal public static native long    _nMakeClone(long ptr);
    @ApiStatus.Internal public static native boolean _nEquals(long ptr, long otherPtr);

    @ApiStatus.Internal public static native void    _nSetAutoHintingForced(long ptr, boolean value);

    @ApiStatus.Internal public static native void    _nSetSubpixel(long ptr, boolean value);

    @ApiStatus.Internal public static native void    _nSetBaselineSnapped(long ptr, boolean value);
    @ApiStatus.Internal public static native int     _nGetEdging(long ptr);
    @ApiStatus.Internal public static native void    _nSetEdging(long ptr, int value);

    @ApiStatus.Internal public static native void    _nSetHinting(long ptr, int value);
    @ApiStatus.Internal public static native long    _nGetTypeface(long ptr);

    @ApiStatus.Internal public static native float   _nGetSize(long ptr);
    @ApiStatus.Internal public static native float   _nGetScaleX(long ptr);
    @ApiStatus.Internal public static native float   _nGetSkewX(long ptr);

    @ApiStatus.Internal public static native void    _nSetSize(long ptr, float value);
    @ApiStatus.Internal public static native void    _nSetScaleX(long ptr, float value);

    @ApiStatus.Internal public static native short[] _nGetUTF32Glyphs(long ptr, int[] uni);

    @ApiStatus.Internal public static native Rect    _nMeasureText(long ptr, String str, long paintPtr);
    @ApiStatus.Internal public static native float   _nMeasureTextWidth(long ptr, String str, long paintPtr); 

    @ApiStatus.Internal public static native Rect[]  _nGetBounds(long ptr, short[] glyphs, long paintPtr);

    @ApiStatus.Internal public static native FontMetrics _nGetMetrics(long ptr);

}