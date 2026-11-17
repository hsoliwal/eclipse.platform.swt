// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

/**
 * Cap draws at the beginning and end of an open path contour.
 */
public enum PaintStrokeCap {
    /**
     * No stroke extension
     */
    BUTT,

    /**
     * adds circle
     */
    ROUND,

    /**
     * adds square
     */
    SQUARE;

    @ApiStatus.Internal public static final PaintStrokeCap[] _values = values();
}
