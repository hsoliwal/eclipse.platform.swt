// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.skottie;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.*;
import org.eclipse.swt.internal.maya.impl.*;

/**
 * <p>A Logger subclass can be used to receive
 * {@link org.eclipse.swt.internal.maya.skottie.AnimationBuilder} parsing errors and warnings.</p>
 */
public abstract class Logger extends RefCnt {
    static { Library.staticLoad(); }

    public Logger() {
        super(_nMake());
        Stats.onNativeCall();
        Stats.onNativeCall();
        _nInit(_ptr);
    }

    @ApiStatus.OverrideOnly
    public abstract void log(LogLevel level, String message, @Nullable String json);

    @ApiStatus.Internal public static native long _nMake();
    @ApiStatus.Internal public        native void _nInit(long ptr);
}