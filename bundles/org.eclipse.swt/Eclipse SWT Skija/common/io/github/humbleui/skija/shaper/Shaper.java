// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija.shaper;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

/**
 * Shapes text using HarfBuzz and places the shaped text into a
 * client-managed buffer.
 */
public class Shaper extends Managed {
    static { Library.staticLoad(); }
    
    

    

    

    

    

    @NotNull @Contract("-> new")
    public static Shaper makeShapeDontWrapOrReorder() {
        return makeShapeDontWrapOrReorder(null);
    }

    @NotNull @Contract("_ -> new")
    public static Shaper makeShapeDontWrapOrReorder(@Nullable FontMgr fontMgr) {
        try {
            Stats.onNativeCall();
            return new Shaper(_nMakeShapeDontWrapOrReorder(Native.getPtr(fontMgr)));
        } finally {
            ReferenceUtil.reachabilityFence(fontMgr);
        }
    }



    

    

    

    

    

    

    @NotNull @Contract("_, _, _ -> new")
    public TextLine shapeLine(String text, @NotNull Font font, @NotNull ShapingOptions opts) {
        try {
            assert opts != null : "Can’t Shaper::shapeLine with opts == null";
            assert font != null : "Can’t Shaper::shapeLine with font == null";
            Stats.onNativeCall();
            return new TextLine(_nShapeLine(_ptr, text, Native.getPtr(font), opts));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(font);
        }
    }

    

    

    

    

    @ApiStatus.Internal
    public Shaper(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    public static native long _nGetFinalizer();
    
    
    
    public static native long _nMakeShapeDontWrapOrReorder(long fontMgrPtr);
    
    public static native long _nMake(long fontMgrPtr);
    
    public static native long _nShapeLine(long ptr, String text, long fontPtr, ShapingOptions opts);
    
}
