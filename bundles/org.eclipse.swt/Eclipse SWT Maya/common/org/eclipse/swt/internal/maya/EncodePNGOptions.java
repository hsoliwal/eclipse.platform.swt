// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public class EncodePNGOptions {
    public static final EncodePNGOptions DEFAULT = new EncodePNGOptions(EncodePNGFilterFlag.ALL._value, 6);

    @ApiStatus.Internal
    public final int _flags;

    @ApiStatus.Internal 
    public final int _zlibLevel;

    @ApiStatus.Internal 
    public EncodePNGOptions(int flags, int level) {
        _flags = flags;
        _zlibLevel = level;
    }

}
