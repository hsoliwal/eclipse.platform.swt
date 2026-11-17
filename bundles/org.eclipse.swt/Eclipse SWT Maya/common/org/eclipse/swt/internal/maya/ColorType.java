// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.impl.*;

/**
 * Describes how pixel bits encode color. A pixel may be an alpha mask, a
 * grayscale, RGB, or ARGB.
 */
public enum ColorType {
    /**
     * Uninitialized
     */
    UNKNOWN,      

    /**
     * Pixel with alpha in 8-bit byte
     */
    ALPHA_8,      

    /**
     * Pixel with 5 bits red, 6 bits green, 5 bits blue, in 16-bit word
     */
    RGB_565,      

    /**
     * Pixel with 4 bits for alpha, red, green, blue; in 16-bit word
     */
    ARGB_4444,    

    /**
     * Pixel with 8 bits for red, green, blue, alpha; in 32-bit word
     */
    RGBA_8888,    

    /**
     * Pixel with 8 bits each for red, green, blue; in 32-bit word
     */
    RGB_888X,     

    /**
     * Pixel with 8 bits for blue, green, red, alpha; in 32-bit word
     */
    BGRA_8888,    

    /**
     * 10 bits for red, green, blue; 2 bits for alpha; in 32-bit word
     */
    RGBA_1010102, 

    /**
     * 10 bits for blue, green, red; 2 bits for alpha; in 32-bit word
     */
    BGRA_1010102, 

    /**
     * Pixel with 10 bits each for red, green, blue; in 32-bit word
     */
    RGB_101010X,  

    /**
     * Pixel with 10 bits each for blue, green, red; in 32-bit word
     */
    BGR_101010X,

    /**
     * Pixel with 10 bits each for blue, green, red; in 32-bit word, extended range
     */
    BGR_101010X_XR,

    /**
     * Pixel with grayscale level in 8-bit byte
     */
    GRAY_8,       

    /**
     * Pixel with half floats in [0,1] for red, green, blue, alpha; in 64-bit word
     */
    RGBA_F16NORM, 

    /**
     * Pixel with half floats for red, green, blue, alpha; in 64-bit word
     */
    RGBA_F16,     

    /**
     * Pixel using C float for red, green, blue, alpha; in 128-bit word
     */
    RGBA_F32,     

    // The following 6 colortypes are just for reading from - not for rendering to

    /**
     * Pixel with a uint8_t for red and green
     */
    R8G8_UNORM,        

    /**
     * Pixel with a half float for alpha
     */
    A16_FLOAT,         

    /**
     * Pixel with a half float for red and green
     */
    R16G16_FLOAT,      

    /**
     * Pixel with a little endian uint16_t for alpha
     */
    A16_UNORM,         

    /**
     * Pixel with a little endian uint16_t for red and green
     */
    R16G16_UNORM,      

    /**
     * Pixel with a little endian uint16_t for red, green, blue, and alpha
     */
    R16G16B16A16_UNORM;

    static { Library.staticLoad(); }

    @ApiStatus.Internal public static final ColorType[] _values = values();

    /**
     * Native ARGB 32-bit encoding
     */
    public static ColorType N32 = BGRA_8888;

    /**
     * Returns the number of bytes required to store a pixel, including unused padding.
     * Returns zero for {@link #UNKNOWN}.
     *
     * @return  bytes per pixel
     */  
    public int getBytesPerPixel() {
        switch (this) {
            case UNKNOWN:            return 0;
            case ALPHA_8:            return 1;
            case RGB_565:            return 2;
            case ARGB_4444:          return 2;
            case RGBA_8888:          return 4;
            case BGRA_8888:          return 4;
            case RGB_888X:           return 4;
            case RGBA_1010102:       return 4;
            case RGB_101010X:        return 4;
            case BGRA_1010102:       return 4;
            case BGR_101010X:        return 4;
            case GRAY_8:             return 1;
            case RGBA_F16NORM:       return 8;
            case RGBA_F16:           return 8;
            case RGBA_F32:           return 16;
            case R8G8_UNORM:         return 2;
            case A16_UNORM:          return 2;
            case R16G16_UNORM:       return 4;
            case A16_FLOAT:          return 2;
            case R16G16_FLOAT:       return 4;
            case R16G16B16A16_UNORM: return 8;
        }
        throw new RuntimeException("Unreachable");
    }

    public float getB(byte color) {
        switch (this) {
            case GRAY_8:
                return Byte.toUnsignedInt(color) / 255f;
            default:
                throw new IllegalArgumentException("getB(byte) is not supported on ColorType." + this);
        }
    }

    public float getB(short color) {
        switch (this) {
            case RGB_565:
                return (color & 0b11111) / 31f;
            case ARGB_4444:
                return (color & 0xF) / 15f;
            default:
                throw new IllegalArgumentException("getB(short) is not supported on ColorType." + this);
        }
    }

    public float getB(int color) {
        switch (this) {
            case RGBA_8888:
                return ((color >> 8) & 0xFF) / 255f;
            case RGB_888X:
                return ((color >> 8) & 0xFF) / 255f;
            case BGRA_8888:
                return ((color >> 24) & 0xFF) / 255f;
            case RGBA_1010102:
                return ((color >> 2) & 0b1111111111) / 1023f;
            case RGB_101010X:
                return ((color >> 2) & 0b1111111111) / 1023f;
            case BGRA_1010102:
                return ((color >> 22) & 0b1111111111) / 1023f;
            case BGR_101010X:
                return ((color >> 22) & 0b1111111111) / 1023f;
            default:
                throw new IllegalArgumentException("getB(int) is not supported on ColorType." + this);
        }
    }

}