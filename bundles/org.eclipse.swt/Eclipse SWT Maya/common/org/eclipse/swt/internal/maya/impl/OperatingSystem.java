// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.impl;

import java.util.Locale;

public enum OperatingSystem {
    WINDOWS,
    LINUX,
    MACOS,
    UNKNOWN;

    public static final OperatingSystem CURRENT;

    static {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            CURRENT = MACOS;
        } else if (os.contains("windows")) {
            CURRENT = WINDOWS;
        } else if (os.contains("nux") || os.contains("nix")) {
            CURRENT = LINUX;
        } else {
            CURRENT = UNKNOWN;
        }
    }
}
