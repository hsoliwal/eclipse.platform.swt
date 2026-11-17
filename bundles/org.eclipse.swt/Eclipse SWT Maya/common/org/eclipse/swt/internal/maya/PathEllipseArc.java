// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum PathEllipseArc {
    /** Smaller of arc pair. */
    SMALLER,

    /** Larger of arc pair. */
    LARGER;

    @ApiStatus.Internal public static final PathEllipseArc[] _values = values();
}
