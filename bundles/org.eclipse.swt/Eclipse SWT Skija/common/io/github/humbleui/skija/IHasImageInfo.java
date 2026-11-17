// Changed for SWT (Apache-2.0 section 4b): unused lombok imports removed; methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;
import io.github.humbleui.skija.impl.*;

public interface IHasImageInfo {
    ImageInfo getImageInfo();

        /**
     * Returns pixel count in each row. Should be equal or less than
     * getRowBytes() / getImageInfo().getBytesPerPixel().
     *
     * May be less than getPixelRef().getWidth(). Will not exceed getPixelRef().getWidth() less
     *
     * @return  pixel width in ImageInfo
     */
    default int getWidth() {
        return getImageInfo()._width;
    }

    /**
     * Returns pixel row count.
     *
     * Maybe be less than getPixelRef().getHeight(). Will not exceed getPixelRef().getHeight()
     *
     * @return  pixel height in ImageInfo
    */
    default int getHeight() {
        return getImageInfo()._height;
    }

    @NotNull
    default ColorInfo getColorInfo() {
        return getImageInfo()._colorInfo;
    }    

    @NotNull
    default ColorType getColorType() {
        return getImageInfo()._colorInfo._colorType;
    }    

    @NotNull
    default ColorAlphaType getAlphaType() {
        return getImageInfo()._colorInfo._alphaType;
    }

    @Nullable
    default ColorSpace getColorSpace() {
        return getImageInfo()._colorInfo._colorSpace;
    }

}
