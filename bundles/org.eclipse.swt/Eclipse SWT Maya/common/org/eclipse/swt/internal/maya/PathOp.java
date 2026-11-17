// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

/**
 *  The logical operations that can be performed when combining two paths.
 */
public enum PathOp {
    /** subtract the op path from the first path */
    DIFFERENCE,
    /** intersect the two paths */
    INTERSECT,
    /** union (inclusive-or) the two paths */
    UNION,
    /** exclusive-or the two paths */
    XOR,
    /** subtract the first path from the op path */
    REVERSE_DIFFERENCE;

    @ApiStatus.Internal public static final PathOp[] _values = values();
}
