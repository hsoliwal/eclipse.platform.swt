// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.skottie;

import org.jetbrains.annotations.*;

public enum LogLevel {
    WARNING,
    ERROR;

    @ApiStatus.Internal public static final LogLevel[] _values = values();
}