// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;
import org.eclipse.swt.internal.maya.types.*;

public class TextBlob extends Managed {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public TextBlob(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    /** 
     * Returns conservative bounding box. Uses Paint associated with each glyph to
     * determine glyph bounds, and unions all bounds. Returned bounds may be
     * larger than the bounds of all glyphs in runs.
     *
     * @return  conservative bounding box
     */
    public Rect getBounds() {
        try {
            Stats.onNativeCall();
            return _nBounds(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Returns a TextBlob built from a single run of text with x-positions and a single y value.
     * Returns null if glyphs is empty.
     *
     * @param glyphs  glyphs drawn
     * @param xpos    array of x-positions, must contain values for all of the glyphs.
     * @param ypos    shared y-position for each glyph, to be paired with each xpos.
     * @param font    Font used for this run
     * @return        new TextBlob or null
     */
    public static TextBlob makeFromPosH(short[] glyphs, float[] xpos, float ypos, Font font) {
        try {
            assert glyphs.length == xpos.length : "glyphs.length " + glyphs.length + " != xpos.length " + xpos.length;
            Stats.onNativeCall();
            long ptr = _nMakeFromPosH(glyphs, xpos, ypos, Native.getPtr(font));
            return ptr == 0 ? null : new TextBlob(ptr);
        } finally {
            ReferenceUtil.reachabilityFence(font);
        }
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @ApiStatus.Internal public static native long _nGetFinalizer();
    @ApiStatus.Internal public static native Rect _nBounds(long ptr);

    @ApiStatus.Internal public static native long _nMakeFromPosH(short[] glyphs, float[] xpos, float ypos, long fontPtr);

}