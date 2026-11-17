// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;

public class FontMgr extends RefCnt {
    static { Library.staticLoad(); }

    public int getFamiliesCount() {
        try {
            Stats.onNativeCall();
            return _nGetFamiliesCount(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public String getFamilyName(int index) {
        try {
            Stats.onNativeCall();
            return _nGetFamilyName(_ptr, index);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Find the closest matching typeface to the specified familyName and style
     * and return a ref to it. The caller must call {@link #close()} on the returned
     * object. Will return null if no 'good' match is found.
     *
     * Passing null as the parameter for `familyName` will return the
     * default system font.
     *
     * It is possible that this will return a style set not accessible from
     * {@link #makeStyleSet(int)} or {@link #matchFamily(String)} due to hidden or
     * auto-activated fonts.
     */
    @Nullable
    public Typeface matchFamilyStyle(String familyName, FontStyle style) {
        try {
            Stats.onNativeCall();
            long ptr = _nMatchFamilyStyle(_ptr, familyName, style._value);
            return ptr == 0 ? null : new Typeface(ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public static class _DefaultHolder {
        static { Stats.onNativeCall(); }
        public static final FontMgr INSTANCE = new FontMgr(_nDefault(), false);
    }

    /**
     * Return the default fontmgr.
     */
    public static FontMgr getDefault() {
        return _DefaultHolder.INSTANCE;
    }

    @ApiStatus.Internal
    public FontMgr(long ptr) {
        super(ptr);
    }

    @ApiStatus.Internal
    public FontMgr(long ptr, boolean allowClose) {
        super(ptr, allowClose);
    }

    public static native int _nGetFamiliesCount(long ptr);
    public static native String _nGetFamilyName(long ptr, int index);

    public static native long _nMatchFamilyStyle(long ptr, String familyName, int fontStyle);

    public static native long _nDefault();
}