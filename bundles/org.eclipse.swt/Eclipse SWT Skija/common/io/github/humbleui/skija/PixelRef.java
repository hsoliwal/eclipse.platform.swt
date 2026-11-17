// Changed for SWT (Apache-2.0 section 4b): unused lombok imports removed; methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;

public class PixelRef extends RefCnt {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public PixelRef(long ptr) {
        super(ptr);
    }

    public int getWidth() {
        try {
            Stats.onNativeCall();
            return _nGetWidth(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public int getHeight() {
        try {
            Stats.onNativeCall();
            return _nGetHeight(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal public static native int  _nGetWidth(long ptr);
    @ApiStatus.Internal public static native int  _nGetHeight(long ptr);

}