// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.shaper;

import java.util.*;
import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.*;
import org.eclipse.swt.internal.maya.impl.*;

public class IcuBidiRunIterator extends ManagedRunIterator<BidiRun> {
    static { Library.staticLoad(); }

    public IcuBidiRunIterator(ManagedString text, boolean manageText, int bidiLevel) {
        super(_nMake(Native.getPtr(text), bidiLevel), text, manageText);
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(text);
    }

    public IcuBidiRunIterator(String text, int bidiLevel) {
        this(new ManagedString(text), true, bidiLevel);
    }

    @Override
    public BidiRun next() {
        try {
            _nConsume(_ptr);
            return new BidiRun(_getEndOfCurrentRun(), _nGetCurrentLevel(_ptr));
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal public static native long _nMake(long textPtr, int bidiLevel);
    @ApiStatus.Internal public static native int _nGetCurrentLevel(long ptr);
}