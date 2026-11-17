// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

public class Shader extends RefCnt {
    static { Library.staticLoad(); }
    
    

    // Linear

    

    public static Shader makeLinearGradient(float x0, float y0, float x1, float y1, int[] colors) {
        return makeLinearGradient(x0, y0, x1, y1, colors, null, GradientStyle.DEFAULT);
    }
    
    

    

    

    public static Shader makeLinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, GradientStyle style) {
        assert positions == null || colors.length == positions.length : "colors.length " + colors.length + "!= positions.length " + positions.length;
        Stats.onNativeCall();
        return new Shader(_nMakeLinearGradient(x0, y0, x1, y1, colors, positions, style.getTileMode().ordinal(), style._getFlags(), style._getMatrixArray()));
    }

    

    

    // Radial

    

    

    

    

    

    

    

    

    // Two-point Conical

    

    

    

    

    

    

    

    

    // Sweep

    

    

    

    

    

    

    

    

    

    

    //

    

    

    

    

    @ApiStatus.Internal
    public Shader(long ptr) {
        super(ptr);
    }

    
    public static native long _nMakeLinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, int tileType, int flags, float[] matrix);
    
    
    
    
    
    
    
    
    
    
    
}