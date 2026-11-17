// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum ContentChangeMode {
    /** Discards surface on change. */
    DISCARD,

    /** Preserves surface on change. */
    RETAIN;

    @ApiStatus.Internal public static final ContentChangeMode[] _values = values();
}