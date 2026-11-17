// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;

public class PathEffect extends RefCnt {
    static { Library.staticLoad(); }
    
    public enum Style {
        /** translate the shape to each position */
        TRANSLATE,
        /** rotate the shape about its center */
        ROTATE,
        /** transform each point, and turn lines into curves */
        MORPH;

        @ApiStatus.Internal public static final Style[] _values = values();
    }

    
    
    
    
    

    

    

    

    public static PathEffect makeDash(float[] intervals, float phase) {
        Stats.onNativeCall();
        return new PathEffect(_nMakeDash(intervals, phase));
    }

    

    @ApiStatus.Internal
    public PathEffect(long ptr) {
        super(ptr);
    }

    
    
    
    
    
    
    public static native long _nMakeDash(float[] intervals, float phase);
    
}