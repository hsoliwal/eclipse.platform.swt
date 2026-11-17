// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

public enum EncodePNGFilterFlag {
    ZERO  (0x00),
    NONE  (0x08),
    SUB   (0x10),
    UP    (0x20),
    AVG   (0x40),
    PAETH (0x80),
    ALL   (0x08 | 0x10 | 0x20 | 0x40 | 0x80);

    @ApiStatus.Internal public final int _value;

    EncodePNGFilterFlag(int value) {
        this._value = value;
    }
};
