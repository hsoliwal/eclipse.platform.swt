// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;
import org.eclipse.swt.internal.maya.types.*;

public class Region extends Managed {
    static { Library.staticLoad(); }

    public enum Op {
        DIFFERENCE,
        INTERSECT,
        UNION,
        XOR,
        REVERSE_DIFFERENCE,
        REPLACE;

        @ApiStatus.Internal public static final Op[] _values = values();
    }

    public Region() {
        super(_nMake(), _FinalizerHolder.PTR);
        Stats.onNativeCall();
    }

    public IRect getBounds() {
        try {
            Stats.onNativeCall();
            return _nGetBounds(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public boolean setRect(IRect rect) {
        try {
            Stats.onNativeCall();
            return _nSetRect(_ptr, rect._left, rect._top, rect._right, rect._bottom);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public boolean setRects(IRect[] rects) {
        try {
            int[] arr = new int[rects.length * 4];
            for (int i = 0; i < rects.length; ++i) {
                arr[i * 4]     = rects[i]._left;
                arr[i * 4 + 1] = rects[i]._top;
                arr[i * 4 + 2] = rects[i]._right;
                arr[i * 4 + 3] = rects[i]._bottom;
            }
            Stats.onNativeCall();
            return _nSetRects(_ptr, arr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public boolean setPath(Path path, Region clip) {
        try {
            Stats.onNativeCall();
            return _nSetPath(_ptr, Native.getPtr(path), Native.getPtr(clip));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(path);
            ReferenceUtil.reachabilityFence(clip);
        }
    }

    public void translate(int dx, int dy) {
        try {
            Stats.onNativeCall();
            _nTranslate(_ptr, dx, dy);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public boolean op(Region r, Op op) {
        try {
            Stats.onNativeCall();
            return _nOpRegion(_ptr, Native.getPtr(r), op.ordinal());
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(r);
        }
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    public static native long    _nMake();
    public static native long    _nGetFinalizer();

    public static native IRect   _nGetBounds(long ptr);

    public static native boolean _nSetRect(long ptr, int left, int top, int right, int bottom);
    public static native boolean _nSetRects(long ptr, int[] rects);

    public static native boolean _nSetPath(long ptr, long pathPtr, long regionPtr);

    public static native void    _nTranslate(long ptr, int dx, int dy);

    public static native boolean _nOpRegion(long ptr, long regionPtr, int op);

}