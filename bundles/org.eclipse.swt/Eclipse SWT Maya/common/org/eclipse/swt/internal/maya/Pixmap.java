// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.types.IRect;
import org.eclipse.swt.internal.maya.ImageInfo;
import org.eclipse.swt.internal.maya.SamplingMode;
import org.eclipse.swt.internal.maya.impl.*;
import org.eclipse.swt.internal.maya.types.*;

public class Pixmap extends Managed {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public Pixmap(long ptr, boolean managed) {
        super(ptr, _FinalizerHolder.PTR, managed);
    }

    public Pixmap() {
        this(_nMakeNull(), true);
        Stats.onNativeCall();
    }

    public static Pixmap make(ImageInfo info, long addr, int rowBytes) {
        try {
            long ptr = _nMake(
                info._width, info._height,
                info._colorInfo._colorType.ordinal(),
                info._colorInfo._alphaType.ordinal(),
                Native.getPtr(info._colorInfo._colorSpace), addr, rowBytes);
            if (ptr == 0)
                throw new IllegalArgumentException("Failed to create Pixmap.");
            return new Pixmap(ptr, true);
        } finally {
            ReferenceUtil.reachabilityFence(info._colorInfo._colorSpace);
        }
    }

    public void reset(ImageInfo info, long addr, int rowBytes) {
        Stats.onNativeCall();
        _nResetWithInfo(_ptr,
            info._width, info._height,
            info._colorInfo._colorType.ordinal(),
            info._colorInfo._alphaType.ordinal(),
            Native.getPtr(info._colorInfo._colorSpace), addr, rowBytes);
        ReferenceUtil.reachabilityFence(this);
        ReferenceUtil.reachabilityFence(info._colorInfo._colorSpace);
    }

    public int getColor(int x, int y) {
        Stats.onNativeCall();
        try {
            return _nGetColor(_ptr, x, y);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    public static native long _nGetFinalizer();
    public static native long _nMakeNull();
    public static native long _nMake(int width, int height, int colorType, int alphaType, long colorSpacePtr, long pixelsPtr, int rowBytes);

    public static native void _nResetWithInfo(long ptr, int width, int height, int colorType, int alphaType, long colorSpacePtr, long pixelsPtr, int rowBytes);

    // TODO methods flattening ImageInfo not included yet - use GetInfo() instead.

    // TODO shiftPerPixel

    public static native int _nGetColor(long ptr, int x, int y);

    // methods related to C++ types(addr8/16/32/64, writable_addr8/16/32/64) not included - not needed

    // TODO float erase methods not included
}