// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;

public class BackendRenderTarget extends Managed {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public BackendRenderTarget(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @ApiStatus.Internal public static native long _nGetFinalizer();

}