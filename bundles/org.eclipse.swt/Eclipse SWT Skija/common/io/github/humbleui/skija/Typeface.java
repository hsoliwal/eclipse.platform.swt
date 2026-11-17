// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

public class Typeface extends RefCnt {
    static { Library.staticLoad(); }
    
    /**
     * @return  the typeface’s intrinsic style attributes
     */
    public FontStyle getFontStyle() {
        try {
            Stats.onNativeCall();
            return new FontStyle(_nGetFontStyle(_ptr));
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }













    /**
     * @return  true if the two typefaces reference the same underlying font, treating null as the default font
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













    /**
     * Return a new typeface based on this typeface but parameterized as specified in the
     * variation. If the variation does not supply an argument for a parameter
     * in the font then the value from this typeface will be used as the value for that argument.
     * @return  same typeface if variation already matches, new typeface otherwise
     * @throws IllegalArgumentException  on failure
     */
    public Typeface makeClone(FontVariation variation) {
        return makeClone(new FontVariation[] { variation }, 0);
    }

    /**
     * Return a new typeface based on this typeface but parameterized as specified in the
     * variations. If the variations does not supply an argument for a parameter
     * in the font then the value from this typeface will be used as the value for that argument.
     * @return  same typeface if all variation already match, new typeface otherwise
     * @throws IllegalArgumentException  on failure
     */
    public Typeface makeClone(FontVariation[] variations) {
        return makeClone(variations, 0);
    }

    /**
     * Return a new typeface based on this typeface but parameterized as specified in the
     * variations. If the variations does not supply an argument for a parameter
     * in the font then the value from this typeface will be used as the value for that argument.
     * @return  same typeface if all variation already match, new typeface otherwise
     * @throws IllegalArgumentException  on failure
     */
    public Typeface makeClone(FontVariation[] variations, int collectionIndex) {
        try {
            if (variations.length == 0)
                return this;
            Stats.onNativeCall();
            long ptr = _nMakeClone(_ptr, variations, collectionIndex);
            if (ptr == 0)
                throw new IllegalArgumentException("Failed to clone Typeface " + this + " with " + Arrays.toString(variations));
            return new Typeface(ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }























    /**
     * @return  the family name for this typeface. The language of the name is whatever the host platform chooses
     */
    public String getFamilyName() {
        try {
            Stats.onNativeCall();
            return _nGetFamilyName(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Return a rectangle (scaled to 1-pt) that represents the union of the bounds of all
     * of the glyphs, but each one positioned at (0,). This may be conservatively large, and
     * will not take into account any hinting or other size-specific adjustments.
     */
    public Rect getBounds() {
        try {
            Stats.onNativeCall();
            return _nGetBounds(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal
    public Typeface(long ptr) {
        super(ptr);
    }

    @ApiStatus.Internal public static native int      _nGetFontStyle(long ptr);
    
    
    
    
    @ApiStatus.Internal public static native boolean  _nEquals(long ptr, long otherPtr);
    
    
    
    
    @ApiStatus.Internal public static native long     _nMakeClone(long ptr, FontVariation[] variations, int collectionIndex);
    
    
    
    
    
    
    
    
    
    
    @ApiStatus.Internal public static native String   _nGetFamilyName(long ptr);
    @ApiStatus.Internal public static native Rect     _nGetBounds(long ptr);
}