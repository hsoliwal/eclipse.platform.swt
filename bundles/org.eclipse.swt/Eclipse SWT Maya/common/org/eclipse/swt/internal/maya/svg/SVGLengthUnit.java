// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.svg;

import org.jetbrains.annotations.*;

public enum SVGLengthUnit {
    UNKNOWN,
    NUMBER,
    PERCENTAGE,
    EMS,
    EXS,
    PX,
    CM,
    MM,
    IN,
    PT,
    PC;

    @ApiStatus.Internal public static final SVGLengthUnit[] _values = values();
}
