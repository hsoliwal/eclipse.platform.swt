// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum InversionMode {
    NO,
    BRIGHTNESS,
    LIGHTNESS;

    @ApiStatus.Internal public static final InversionMode[] _values = values();
}
