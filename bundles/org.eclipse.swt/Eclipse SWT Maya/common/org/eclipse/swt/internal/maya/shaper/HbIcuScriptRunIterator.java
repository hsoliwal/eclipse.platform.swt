// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.shaper;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.*;
import org.eclipse.swt.internal.maya.impl.*;

public class HbIcuScriptRunIterator extends ManagedRunIterator<ScriptRun> {
    static { Library.staticLoad(); }

    public HbIcuScriptRunIterator(ManagedString text, boolean manageText) {
        super(_nMake(Native.getPtr(text)), text, manageText);
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(text);
    }

    public HbIcuScriptRunIterator(String text) {
        this(new ManagedString(text), true);
    }

    @Override
    public ScriptRun next() {
        try {
            _nConsume(_ptr);
            return new ScriptRun(_getEndOfCurrentRun(), _nGetCurrentScriptTag(_ptr));
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal public static native long _nMake(long textPtr);
    @ApiStatus.Internal public static native int _nGetCurrentScriptTag(long ptr);
}