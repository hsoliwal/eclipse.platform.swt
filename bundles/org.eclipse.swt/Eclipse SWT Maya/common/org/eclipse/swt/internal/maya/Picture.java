// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import java.util.function.*;
import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;
import org.eclipse.swt.internal.maya.types.*;

public class Picture extends RefCnt {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public Picture(long ptr) {
        super(ptr);
    }

}