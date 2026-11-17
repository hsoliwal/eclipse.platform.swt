// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum PaintMode {
    FILL,
    STROKE,
    STROKE_AND_FILL;

    @ApiStatus.Internal public static final PaintMode[] _values = values();
}
