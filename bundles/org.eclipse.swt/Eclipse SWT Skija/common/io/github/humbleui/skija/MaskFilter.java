// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;

public class MaskFilter extends RefCnt {
    static { Library.staticLoad(); }

    // public MaskFilter makeEmboss(float blurSigma, float lightDirectionX, float lightDirectionY, float lightDirectionZ, int lightPad, int lightAmbient, int lightSpecular) {
    //     Native.onNativeCall();
    //     return new MaskFilter(_nMakeEmboss(blurSigma, lightDirectionX, lightDirectionY, lightDirectionZ, lightPad, lightAmbient, lightSpecular));
    // }

    @ApiStatus.Internal
    public MaskFilter(long ptr) {
        super(ptr);
    }

    // public static native long _nMakeEmboss(float sigma, float x, float y, float z, int pad, int ambient, int specular);

}