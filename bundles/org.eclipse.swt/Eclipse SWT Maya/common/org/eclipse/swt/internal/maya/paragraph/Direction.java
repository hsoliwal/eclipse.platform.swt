// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.paragraph;

import org.jetbrains.annotations.*;

public enum Direction {
    RTL,
    LTR;

    @ApiStatus.Internal public static final Direction[] _values = values();
}