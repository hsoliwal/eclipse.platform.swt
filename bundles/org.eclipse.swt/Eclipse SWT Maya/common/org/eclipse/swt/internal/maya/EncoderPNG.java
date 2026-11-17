// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.eclipse.swt.internal.maya.impl.*;
import org.jetbrains.annotations.*;

public class EncoderPNG {
    /**
     * Encode the provided image and return the resulting bytes.
     *
     * @param image  image to encode
     * @return       nullptr if the pixels could not be read or encoding otherwise fails.
     */
    @Nullable
    public static Data encode(@NotNull Image image) {
        return encode(null, image, EncodePNGOptions.DEFAULT);
    }

    /**
     * Encode the provided image and return the resulting Data.
     *
     * @param ctx    If the image was created as a texture-backed image on a GPU context, 
     *               that ctx must be provided so the pixels can be read before being encoded.
     *               For raster-backed images, ctx can be null.
     * @param image  image to encode
     * @param opts   may be used to control the encoding behavior
     * @return       nullptr if the pixels could not be read or encoding otherwise fails.
     */
    @Nullable
    public static Data encode(@Nullable DirectContext ctx, @NotNull Image image, @NotNull EncodePNGOptions opts) {
        try {
            assert image != null : "Can’t EncoderPNG::encode with image == null";
            assert opts != null : "Can’t EncoderPNG::encode with opts == null";
            Stats.onNativeCall();
            long ptr = _nEncode(Native.getPtr(ctx), Native.getPtr(image), opts._flags, opts._zlibLevel);
            return ptr == 0 ? null : new Data(ptr);
        } finally {
            ReferenceUtil.reachabilityFence(ctx);
            ReferenceUtil.reachabilityFence(image);
        }
    }

    @ApiStatus.Internal public static native long _nEncode(long ctxPtr, long imagePtr, int flags, int zlibLevel);
}
