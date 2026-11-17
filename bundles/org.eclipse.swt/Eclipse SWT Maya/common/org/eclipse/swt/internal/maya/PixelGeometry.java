// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum PixelGeometry {
    UNKNOWN,
    RGB_H,
    BGR_H,
    RGB_V,
    BGR_V;

    @ApiStatus.Internal public static final PixelGeometry[] _values = values();
}