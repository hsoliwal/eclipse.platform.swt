// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

/**
 * Level of glyph outline adjustment
 */
public enum FontHinting {
    /**
     * glyph outlines unchanged
     */
    NONE,

    /**
     * minimal modification to improve constrast
     */
    SLIGHT,

    /**
     * glyph outlines modified to improve constrast
     */
    NORMAL,

    /**
     * modifies glyph outlines for maximum constrast
     */
    FULL;

    @ApiStatus.Internal public static final FontHinting[] _values = values();
}