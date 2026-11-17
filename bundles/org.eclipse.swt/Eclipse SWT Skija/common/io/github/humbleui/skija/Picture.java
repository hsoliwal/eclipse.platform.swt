// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import java.util.function.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;

public class Picture extends RefCnt {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public Picture(long ptr) {
        super(ptr);
    }

}