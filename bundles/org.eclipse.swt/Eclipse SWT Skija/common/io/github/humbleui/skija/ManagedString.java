// Changed for SWT (Apache-2.0 section 4b): unused lombok imports removed; methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;

public class ManagedString extends Managed {
    static { Library.staticLoad(); }
    
    @ApiStatus.Internal
    public ManagedString(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    public ManagedString(String s) {
        this(_nMake(s));
        Stats.onNativeCall();
    }

    @Override
    public String toString() {
        try {
            Stats.onNativeCall();
            return _nToString(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    

    

    

    

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @ApiStatus.Internal public static native long _nMake(String s);
    @ApiStatus.Internal public static native long _nGetFinalizer();
    @ApiStatus.Internal public static native String _nToString(long ptr);
    
    
    
    
}