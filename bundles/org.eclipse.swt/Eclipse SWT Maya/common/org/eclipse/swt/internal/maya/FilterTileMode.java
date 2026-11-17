// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum FilterTileMode {
    /** Replicate the edge color if the shader draws outside of its original bounds. */
    CLAMP,

    /** Repeat the shader's image horizontally and vertically. */
    REPEAT,

    /** Repeat the shader's image horizontally and vertically, alternating mirror images so that adjacent images always seam. */
    MIRROR,

    /** Only draw within the original domain, return transparent-black everywhere else. */
    DECAL;

    @ApiStatus.Internal public static final FilterTileMode[] _values = values();
}