// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import java.nio.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;

public class Image extends RefCnt implements IHasImageInfo {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public ImageInfo _imageInfo = null;

    @ApiStatus.Internal
    public Image(long ptr) {
        super(ptr);
    }



    /**
     * <p>Creates Image from pixels.</p>
     *
     * <p>Image is returned if pixels are valid. Valid Pixmap parameters include:</p>
     * <ul>
     * <li>dimensions are greater than zero;</li>
     * <li>each dimension fits in 29 bits;</li>
     * <li>ColorType and AlphaType are valid, and ColorType is not ColorType.UNKNOWN;</li>
     * <li>row bytes are large enough to hold one row of pixels;</li>
     * <li>pixel address is not null.</li>
     * </ul>
     *
     * @param imageInfo  ImageInfo
     * @param bytes      pixels array
     * @param rowBytes   how many bytes in a row
     * @return           Image
     *
     * @see <a href="https://fiddle.skia.org/c/@Image_MakeRasterCopy">https://fiddle.skia.org/c/@Image_MakeRasterCopy</a>
     */
    public static Image makeRasterFromBytes(ImageInfo imageInfo, byte[] bytes, long rowBytes) {
        try {
            Stats.onNativeCall();
            long ptr = _nMakeRasterFromBytes(imageInfo._width,
                                             imageInfo._height,
                                             imageInfo._colorInfo._colorType.ordinal(),
                                             imageInfo._colorInfo._alphaType.ordinal(),
                                             Native.getPtr(imageInfo._colorInfo._colorSpace),
                                             bytes,
                                             rowBytes);
            if (ptr == 0)
                throw new RuntimeException("Failed to makeRaster " + imageInfo + " " + bytes + " " + rowBytes);
            return new Image(ptr);
        } finally {
            ReferenceUtil.reachabilityFence(imageInfo._colorInfo._colorSpace);
        }
    }











    



    

    /**
     * Returns a ImageInfo describing the width, height, color type, alpha type, and color space
     * of the Image.
     *
     * @return  image info of Image.
     */
    @Override @NotNull
    public ImageInfo getImageInfo() {
        try {
            if (_imageInfo == null) {
                synchronized(this) {
                    if (_imageInfo == null) {
                        Stats.onNativeCall();
                        _imageInfo = _nGetImageInfo(_ptr);
                    }
                }
            }
            return _imageInfo;
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }







    

    

    @NotNull
    public Shader makeShader(@NotNull FilterTileMode tm) {
        return makeShader(tm, tm, SamplingMode.DEFAULT, null);
    }

    

    

    @NotNull
    public Shader makeShader(@NotNull FilterTileMode tmx,
                             @NotNull FilterTileMode tmy,
                             @NotNull SamplingMode sampling,
                             @Nullable Matrix33 localMatrix) {
        try {
            assert tmx != null : "Can’t Bitmap.makeShader with tmx == null";
            assert tmy != null : "Can’t Bitmap.makeShader with tmy == null";
            assert sampling != null : "Can’t Bitmap.makeShader with sampling == null";
            Stats.onNativeCall();
            return new Shader(_nMakeShader(_ptr, tmx.ordinal(), tmy.ordinal(), sampling._pack(), localMatrix == null ? null : localMatrix._mat));
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }



    

    public boolean readPixels(@NotNull Bitmap dst) {
        return readPixels(null, dst, 0, 0, false);
    }

    public boolean readPixels(@NotNull Bitmap dst, int srcX, int srcY) {
        return readPixels(null, dst, srcX, srcY, false);
    }

    /**
     * <p>Copies Rect of pixels from Image to Bitmap. Copy starts at offset (srcX, srcY),
     * and does not exceed Image (getWidth(), getHeight()).</p>
     *
     * <p>dst specifies width, height, ColorType, AlphaType, and ColorSpace of destination.</p>
     *
     * <p>Returns true if pixels are copied. Returns false if:</p>
     * <ul>
     * <li>dst has no pixels allocated.</li>
     * </ul>
     *
     * <p>Pixels are copied only if pixel conversion is possible. If Image ColorType is
     * ColorType.GRAY_8, or ColorType.ALPHA_8; dst.getColorType() must match.
     * If Image ColorType is ColorType.GRAY_8, dst.getColorSpace() must match.
     * If Image AlphaType is AlphaType.OPAQUE, dst.getAlphaType() must
     * match. If Image ColorSpace is null, dst.getColorSpace() must match. Returns
     * false if pixel conversion is not possible.</p>
     *
     * <p>srcX and srcY may be negative to copy only top or left of source. Returns
     * false if getWidth() or getHeight() is zero or negative.</p>
     *
     * <p>Returns false if abs(srcX) &gt;= Image.getWidth(), or if abs(srcY) &gt;= Image.getHeight().</p>
     *
     * <p>If cache is true, pixels may be retained locally, otherwise pixels are not added to the local cache.</p>
     *
     * @param context the DirectContext in play, if it exists
     * @param dst     destination bitmap
     * @param srcX    column index whose absolute value is less than getWidth()
     * @param srcY    row index whose absolute value is less than getHeight()
     * @param cache   whether the pixels should be cached locally
     * @return        true if pixels are copied to dstPixels
     */
    public boolean readPixels(@Nullable DirectContext context, @NotNull Bitmap dst, int srcX, int srcY, boolean cache) {
        try {
            assert dst != null : "Can’t readPixels with dst == null";
            return _nReadPixelsBitmap(_ptr, Native.getPtr(context), Native.getPtr(dst), srcX, srcY, cache);
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(context);
            ReferenceUtil.reachabilityFence(dst);
        }
    }

    public boolean readPixels(@NotNull Pixmap dst, int srcX, int srcY, boolean cache) {
        try {
            assert dst != null : "Can’t readPixels with dst == null";
            return _nReadPixelsPixmap(_ptr, Native.getPtr(dst), srcX, srcY, cache);
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(dst);
        }
    }

    

    @ApiStatus.Internal public static native long _nMakeRasterFromBytes(int width, int height, int colorType, int alphaType, long colorSpacePtr, byte[] pixels, long rowBytes);
    
    
    
    
    @ApiStatus.Internal public static native ImageInfo _nGetImageInfo(long ptr);
    @ApiStatus.Internal public static native long    _nMakeShader(long ptr, int tmx, int tmy, long samplingMode, float[] localMatrix);
    
    
    
    @ApiStatus.Internal public static native boolean _nReadPixelsBitmap(long ptr, long contextPtr, long bitmapPtr, int srcX, int srcY, boolean cache);
    @ApiStatus.Internal public static native boolean _nReadPixelsPixmap(long ptr, long pixmapPtr, int srcX, int srcY, boolean cache);
}