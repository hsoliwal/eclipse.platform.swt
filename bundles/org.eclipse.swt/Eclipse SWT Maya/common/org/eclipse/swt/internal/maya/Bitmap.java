// Changed for SWT (Apache-2.0 section 4b): unused lombok imports removed; methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import java.nio.*;
import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;
import org.eclipse.swt.internal.maya.types.*;

public class Bitmap extends Managed implements IHasImageInfo {
    static { Library.staticLoad(); }

    @ApiStatus.Internal
    public ImageInfo _imageInfo = null;

    @ApiStatus.Internal
    public Bitmap(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    /**
     * Creates an empty Bitmap without pixels, with {@link ColorType#UNKNOWN},
     * {@link ColorAlphaType#UNKNOWN}, and with a width and height of zero. 
     * PixelRef origin is set to (0, 0). Bitmap is not volatile.
     *
     * Use {@link #setImageInfo(ImageInfo, long)} to associate ColorType, ColorAlphaType, width, and height after Bitmap has been created.
     * 
     * @see <a href="https://fiddle.skia.org/c/@Bitmap_empty_constructor">https://fiddle.skia.org/c/@Bitmap_empty_constructor</a>
     */
    public Bitmap() {
        this(_nMake());
        Stats.onNativeCall();
    }

    /**
     * Copies settings from src to returned Bitmap. Shares pixels if src has pixels
     * allocated, so both bitmaps reference the same pixels.
     *
     * @return  copy of src
     *
     * @see <a href="https://fiddle.skia.org/c/@Bitmap_copy_const_SkBitmap">https://fiddle.skia.org/c/@Bitmap_copy_const_SkBitmap</a>
     */
    @NotNull @Contract("-> new")
    public Bitmap makeClone() {
        try {
            Stats.onNativeCall();
            return new Bitmap(_nMakeClone(_ptr));
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @NotNull @Contract("-> new")
    public static Bitmap makeFromImage(@NotNull Image image) {
        assert image != null : "Can’t makeFromImage with image == null";
        Bitmap bitmap = new Bitmap();
        bitmap.allocPixels(image.getImageInfo());
        if (image.readPixels(bitmap))
            return bitmap;
        else {
            bitmap.close();
            throw new RuntimeException("Failed to readPixels from " + image);
        }
    }

    @Override @NotNull
    public ImageInfo getImageInfo() {
        try {
            if (_imageInfo == null) {
                Stats.onNativeCall();
                _imageInfo = _nGetImageInfo(_ptr);
            }
            return _imageInfo;
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }        
    }

    /**
     * Returns row bytes, the interval from one pixel row to the next. Row bytes
     * is at least as large as: getWidth() * getBytesPerPixel().
     *
     * Returns zero if getColorType() is {@link ColorType#UNKNOWN}, or if row bytes
     * supplied to {@link #setImageInfo(ImageInfo)} is not large enough to hold a row of pixels.
     *
     * @return  byte length of pixel row
     */
    public long getRowBytes() {
        try {
            Stats.onNativeCall();
            return _nGetRowBytes(_ptr);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * Returns IRect { 0, 0, width(), height() }.
     *
     * @see <a href="https://fiddle.skia.org/c/@Bitmap_getBounds_2">https://fiddle.skia.org/c/@Bitmap_getBounds_2</a>
     */
    @NotNull
    public IRect getBounds() {
        return IRect.makeXYWH(0, 0, getWidth(), getHeight());
    }

   /** 
    * <p>Sets ImageInfo to info following the rules in setImageInfo() and allocates pixel
    * memory. rowBytes must equal or exceed info.width() times info.bytesPerPixel(),
    * or equal zero.</p>
    *
    * <p>Returns false and calls reset() if ImageInfo could not be set, or memory could
    * not be allocated.</p>
    *
    * <p>On most platforms, allocating pixel memory may succeed even though there is
    * not sufficient memory to hold pixels; allocation does not take place
    * until the pixels are written to. The actual behavior depends on the platform
    * implementation of malloc().</p>
    *
    * @param info      contains width, height, ColorAlphaType, ColorType, ColorSpace
    * @param rowBytes  size of pixel row or larger; may be zero
    * @return          true if pixel storage is allocated
    */
    public boolean allocPixels(@NotNull ImageInfo info, long rowBytes) {
        try {
            _imageInfo = null;
            Stats.onNativeCall();
            return _nAllocPixelsRowBytes(_ptr,
                info._width,
                info._height,
                info._colorInfo._colorType.ordinal(),
                info._colorInfo._alphaType.ordinal(),
                Native.getPtr(info._colorInfo._colorSpace),
                rowBytes);
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(info._colorInfo._colorSpace);
        }
    }

    /**
     * <p>Sets ImageInfo to info following the rules in {@link #setImageInfo(ImageInfo, long)} and allocates pixel
     * memory.</p>
     *
     * <p>Returns false and calls reset() if ImageInfo could not be set, or memory could
     * not be allocated.</p>
     *
     * <p>On most platforms, allocating pixel memory may succeed even though there is
     * not sufficient memory to hold pixels; allocation does not take place
     * until the pixels are written to. The actual behavior depends on the platform
     * implementation of malloc().</p>
     *
     * @param imageInfo  contains width, height, ColorAlphaType, ColorType, ColorSpace
     * @return           true if pixel storage is allocated
     * @see <a href="https://fiddle.skia.org/c/@Bitmap_allocPixels_2">https://fiddle.skia.org/c/@Bitmap_allocPixels_2</a>
     */
    public boolean allocPixels(@NotNull ImageInfo imageInfo) {
        return allocPixels(imageInfo, imageInfo.getMinRowBytes());
    }

    /**
     * Sets ImageInfo to width, height, and native color type; and allocates
     * pixel memory. Sets ImageInfo to {@link ColorAlphaType#PREMUL}.
     *
     * Calls reset() and returns false if width exceeds 29 bits or is negative,
     * or height is negative.
     *
     * Returns false if allocation fails.
     *
     * Use to create Bitmap that matches the native pixel arrangement on
     * the platform. Bitmap drawn to output device skips converting its pixel format.
     *
     * @param width   pixel column count; must be zero or greater
     * @param height  pixel row count; must be zero or greater
     * @return        true if pixel storage is allocated
     */
    public boolean allocN32Pixels(int width, int height) {
        return allocN32Pixels(width, height, false);
    }

    /**
     * Sets ImageInfo to width, height, and native color type; and allocates
     * pixel memory. If opaque is true, sets ImageInfo to {@link ColorAlphaType#OPAQUE};
     * otherwise, sets to {@link ColorAlphaType#PREMUL}.
     *
     * Calls reset() and returns false if width exceeds 29 bits or is negative,
     * or height is negative.
     *
     * Returns false if allocation fails.
     *
     * Use to create Bitmap that matches the native pixel arrangement on
     * the platform. Bitmap drawn to output device skips converting its pixel format.
     *
     * @param width   pixel column count; must be zero or greater
     * @param height  pixel row count; must be zero or greater
     * @param opaque  true if pixels do not have transparency
     * @return        true if pixel storage is allocated
     */
    public boolean allocN32Pixels(int width, int height, boolean opaque) {
        return allocPixels(ImageInfo.makeN32(width, height, opaque ? ColorAlphaType.OPAQUE : ColorAlphaType.PREMUL));
    }

    /** 
     * <p>Returns pixel at (x, y) as unpremultiplied color.
     * Returns black with alpha if ColorType is {@link ColorType#ALPHA_8}.</p>
     *
     * <p>Input is not validated: out of bounds values of x or y returns undefined values
     * or may crash if. Fails if ColorType is {@link ColorType#UNKNOWN} or
     * pixel address is nullptr.</p>
     *
     * <p>ColorSpace in ImageInfo is ignored. Some color precision may be lost in the
     * conversion to unpremultiplied color; original pixel data may have additional
     * precision.</p>
     *
     * @param x  column index, zero or greater, and less than getWidth()
     * @param y  row index, zero or greater, and less than getHeight()
     * @return   pixel converted to unpremultiplied color
     */
    public int getColor(int x, int y) {
        try {
            Stats.onNativeCall();
            return _nGetColor(_ptr, x, y);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    /**
     * <p>Look up the pixel at (x,y) and return its alpha component, normalized to [0..1].
     * This is roughly equivalent to GetColorA(getColor()), but can be more efficent
     * (and more precise if the pixels store more than 8 bits per component).</p>
     *
     * @param x  column index, zero or greater, and less than getWidth()
     * @param y  row index, zero or greater, and less than getHeight()
     * @return   alpha converted to normalized float
     */
    public float getAlphaf(int x, int y) {
        try {
            Stats.onNativeCall();
            return _nGetAlphaf(_ptr, x, y);
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @Nullable
    public byte[] readPixels() {
        return readPixels(getImageInfo(), getRowBytes(), 0, 0);
    }

    /** 
     * <p>Copies a rect of pixels from Bitmap. Copy starts at (srcX, srcY),
     * and does not exceed Bitmap (getWidth(), getHeight()).</p>
     *
     * <p>dstInfo specifies width, height, ColorType, AlphaType, and ColorSpace of
     * destination. dstRowBytes specifics the gap from one destination row to the next.
     * Returns true if pixels are copied. Returns false if:
     *
     * <ul>
     *   <li>dstRowBytes is less than dstInfo.getMinRowBytes()</li>
     *   <li>PixelRef is null</li>
     * </ul>
     *
     * <p>Pixels are copied only if pixel conversion is possible. If Bitmap getColorType() is
     * {@link ColorType#GRAY_8}, or {@link ColorType#ALPHA_8}; dstInfo.colorType() must match.
     * If Bitmap getClorType() is {@link ColorType#GRAY_8}, dstInfo.getColorSpace() must match.
     * If Bitmap getAlphaType() is {@link ColorAlphaType#OPAQUE}, dstInfo.getAlphaType() must
     * match. If Bitmap getColorSpace() is null, dstInfo.getColorSpace() must match. Returns
     * false if pixel conversion is not possible.</p>
     *
     * <p>srcX and srcY may be negative to copy only top or left of source. Returns
     * false if getWidth() or getHeight() is zero or negative.
     * Returns false if abs(srcX) &gt;= getWidth(), or if abs(srcY) &gt;= getHeight().</p>
     *
     * @param dstInfo      destination width, height, ColorType, AlphaType, ColorSpace
     * @param dstRowBytes  destination row length
     * @param srcX         column index whose absolute value is less than width()
     * @param srcY         row index whose absolute value is less than height()
     * @return             pixel data or null
     */
    @Nullable
    public byte[] readPixels(@NotNull ImageInfo dstInfo, long dstRowBytes, int srcX, int srcY) {
        try {
            Stats.onNativeCall();
            return _nReadPixels(_ptr,
                dstInfo._width,
                dstInfo._height,
                dstInfo._colorInfo._colorType.ordinal(),
                dstInfo._colorInfo._alphaType.ordinal(),
                Native.getPtr(dstInfo._colorInfo._colorSpace),
                dstRowBytes,
                srcX,
                srcY);
        } finally {
            ReferenceUtil.reachabilityFence(this);
            ReferenceUtil.reachabilityFence(dstInfo._colorInfo._colorSpace);
        }
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @ApiStatus.Internal public static native long    _nGetFinalizer();
    @ApiStatus.Internal public static native long    _nMake();
    @ApiStatus.Internal public static native long    _nMakeClone(long ptr);

    @ApiStatus.Internal public static native ImageInfo _nGetImageInfo(long ptr);

    @ApiStatus.Internal public static native long    _nGetRowBytes(long ptr);

    @ApiStatus.Internal public static native boolean _nAllocPixelsRowBytes(long ptr, int width, int height, int colorType, int alphaType, long colorSpacePtr, long rowBytes);

    @ApiStatus.Internal public static native int     _nGetColor(long ptr, int x, int y);

    @ApiStatus.Internal public static native float   _nGetAlphaf(long ptr, int x, int y);

    @ApiStatus.Internal public static native byte[]  _nReadPixels(long ptr, int width, int height, int colorType, int alphaType, long colorSpacePtr, long dstRowBytes, int srcX, int srcY);

}