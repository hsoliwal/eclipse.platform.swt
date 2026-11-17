// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;

/**
 *  Data holds an immutable data buffer.
 */
public class Data extends Managed {
    static { Library.staticLoad(); }

    public long getSize() {
        try {
            Stats.onNativeCall();
            return _nSize(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    public byte[] getBytes() {
        return getBytes(0, getSize());
    }

    public byte[] getBytes(long offset, long length) {
        try {
            Stats.onNativeCall();
            return _nBytes(_ptr, offset, length);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     *  Returns true if these two objects have the same length and contents,
     *  effectively returning 0 == memcmp(...)
     */
    @ApiStatus.Internal @Override
    public boolean _nativeEquals(Native other) {
        try {
            Stats.onNativeCall();
            return _nEquals(_ptr, Native.getPtr(other));
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(other);
        }
    }

    

    





    

    



    @ApiStatus.Internal
    public Data(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    public static native long    _nGetFinalizer();
    public static native long    _nSize(long ptr);
    public static native byte[]  _nBytes(long ptr, long offset, long length);
    public static native boolean _nEquals(long ptr, long otherPtr);
    
    
    
    
    
}