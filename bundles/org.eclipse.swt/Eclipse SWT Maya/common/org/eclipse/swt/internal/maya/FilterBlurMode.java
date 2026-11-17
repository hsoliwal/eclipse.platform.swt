// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum FilterBlurMode {
    /** fuzzy inside and outside */
    NORMAL,
    /** solid inside, fuzzy outside */
    SOLID,
    /** nothing inside, fuzzy outside */
    OUTER,
    /** fuzzy inside, nothing outside */
    INNER;

    @ApiStatus.Internal public static final FilterBlurMode[] _values = values();
}
