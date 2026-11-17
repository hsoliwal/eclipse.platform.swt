// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import java.io.*;
import org.eclipse.swt.internal.maya.impl.*;
import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.types.*;

/**
 * <p>Base class for objects that draw into Canvas.</p>
 *
 * <p>The object has a generation id, which is guaranteed to be unique across all drawables. To
 * allow for clients of the drawable that may want to cache the results, the drawable must
 * change its generation id whenever its internal state changes such that it will draw differently.</p>
 */
public abstract class Drawable extends RefCnt {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public Rect _bounds = null;

    public Drawable() {
        super(_nMake());
        Stats.onNativeCall();        
        Stats.onNativeCall();
        _nInit(_ptr);
    }

    /**
     * Draws into the specified content. The drawing sequence will be balanced upon return
     * (i.e. the saveLevel() on the canvas will match what it was when draw() was called,
     * and the current matrix and clip settings will not be changed.
     */
    @ApiStatus.NonExtendable
    public Drawable draw(Canvas canvas) {
        return draw(canvas, null);
    }

    /**
     * Draws into the specified content. The drawing sequence will be balanced upon return
     * (i.e. the saveLevel() on the canvas will match what it was when draw() was called,
     * and the current matrix and clip settings will not be changed.
     */
    @ApiStatus.NonExtendable
    public Drawable draw(Canvas canvas, float x, float y) {
        return draw(canvas, Matrix33.makeTranslate(x, y));
    }

    /**
     * Draws into the specified content. The drawing sequence will be balanced upon return
     * (i.e. the saveLevel() on the canvas will match what it was when draw() was called,
     * and the current matrix and clip settings will not be changed.
     */
    @ApiStatus.NonExtendable
    public Drawable draw(Canvas canvas, @Nullable Matrix33 matrix) {
        try {
            Stats.onNativeCall();
            _nDraw(_ptr, Native.getPtr(canvas), matrix == null ? null : matrix._mat);
            return this;
        } finally {
            ReferenceUtil.reachabilityFence(canvas);
        }
    }

    /**
     * Return the (conservative) bounds of what the drawable will draw. If the drawable can
     * change what it draws (e.g. animation or in response to some external change), then this
     * must return a bounds that is always valid for all possible states.
     */
    @ApiStatus.NonExtendable
    public @NotNull Rect getBounds() {
        if (_bounds == null)
            _bounds = onGetBounds();
        return _bounds;
    }

    @ApiStatus.OverrideOnly
    public abstract void onDraw(Canvas canvas);

    @ApiStatus.OverrideOnly
    public abstract @NotNull Rect onGetBounds();

    @ApiStatus.Internal
    public void _onDraw(long canvasPtr) {
        onDraw(new Canvas(canvasPtr, false, this));
    }

    @ApiStatus.Internal public static native long _nMake();
    @ApiStatus.Internal public        native void _nInit(long ptr);
    @ApiStatus.Internal public static native void _nDraw(long ptr, long canvasPtr, float[] matrix);

}
