// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

import java.util.Arrays;

public class ImageFilter extends RefCnt {
    static { Library.staticLoad(); }
    
    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    // public static ImageFilter makePicture(Picture picture, Rect target) {
    //     Native.onNativeCall();
    //     return new ImageFilter(_nMakePicture(Native.pointer(picture), target.left, target.top, target.right, target.bottom));
    // }

    

    

    

    

    

    

    

    

    

    @ApiStatus.Internal
    public ImageFilter(long ptr) {
        super(ptr);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
}