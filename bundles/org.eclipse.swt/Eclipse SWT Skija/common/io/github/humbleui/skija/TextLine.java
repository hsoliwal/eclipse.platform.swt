// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.skija.shaper.*;

public class TextLine extends Managed {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public TextLine(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @NotNull @Contract("_, _ -> new")
    public static TextLine make(String text, Font font) {
        return make(text, font, ShapingOptions.DEFAULT);
    }

    @NotNull @Contract("_, _, _, _ -> new")
    public static TextLine make(String text, Font font, ShapingOptions opts) {
        try (Shaper shaper = Shaper.makeShapeDontWrapOrReorder();) {
            return shaper.shapeLine(text, font, opts);
        }
    }

    public float getAscent() {
        Stats.onNativeCall();
        try {
            return _nGetAscent(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public float getCapHeight() {
        Stats.onNativeCall();
        try {
            return _nGetCapHeight(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public float getXHeight() {
        Stats.onNativeCall();
        try {
            return _nGetXHeight(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public float getDescent() {
        Stats.onNativeCall();
        try {
            return _nGetDescent(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public float getWidth() {
        Stats.onNativeCall();
        try {
            return _nGetWidth(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public float getHeight() {
        Stats.onNativeCall();
        try {
            return _nGetHeight(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @Nullable
    public TextBlob getTextBlob() {
        Stats.onNativeCall();
        try {
            long res = _nGetTextBlob(_ptr);
            return res == 0 ? null : new TextBlob(res);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * @param offset  UTF-16 character offset
     * @return        glyph coordinate
     */
    public float getCoordAtOffset(int offset) {
        try {
            Stats.onNativeCall();
            return _nGetCoordAtOffset(_ptr, offset);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal public static native long  _nGetFinalizer();
    @ApiStatus.Internal public static native float _nGetAscent(long ptr);
    @ApiStatus.Internal public static native float _nGetCapHeight(long ptr);
    @ApiStatus.Internal public static native float _nGetXHeight(long ptr);
    @ApiStatus.Internal public static native float _nGetDescent(long ptr);

    @ApiStatus.Internal public static native float _nGetWidth(long ptr);
    @ApiStatus.Internal public static native float _nGetHeight(long ptr);
    @ApiStatus.Internal public static native long  _nGetTextBlob(long ptr);

    @ApiStatus.Internal public static native float _nGetCoordAtOffset(long ptr, int offset);
}