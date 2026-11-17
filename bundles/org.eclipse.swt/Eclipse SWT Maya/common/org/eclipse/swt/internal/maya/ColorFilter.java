// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;

public class ColorFilter extends RefCnt {
    static { Library.staticLoad(); }

    public static class _LinearToSRGBGammaHolder {
        static { Stats.onNativeCall(); }
        public static final ColorFilter INSTANCE = new ColorFilter(_nGetLinearToSRGBGamma(), false);
    }

    public static class _SRGBToLinearGammaHolder {
        static { Stats.onNativeCall(); }
        public static final ColorFilter INSTANCE = new ColorFilter(_nGetSRGBToLinearGamma(), false);
    }

    public static class _LumaHolder {
        static { Stats.onNativeCall(); }
        public static final ColorFilter INSTANCE = new ColorFilter(_nGetLuma(), false);
    }

    @ApiStatus.Internal
    public ColorFilter(long ptr) {
        super(ptr);
    }

    @ApiStatus.Internal
    public ColorFilter(long ptr, boolean allowClose) {
        super(ptr, allowClose);
    }

    public static native long _nGetLinearToSRGBGamma();
    public static native long _nGetSRGBToLinearGamma();

    public static native long _nGetLuma();
}