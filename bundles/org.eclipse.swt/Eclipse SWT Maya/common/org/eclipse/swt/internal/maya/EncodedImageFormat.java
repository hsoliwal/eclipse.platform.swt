// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

/**
 * @deprecated - use {@link EncoderJPEG}, {@link EncoderPNG} or {@link EncoderWEBP} directly
 */
@Deprecated
public enum EncodedImageFormat {
    BMP,
    GIF,
    ICO,
    JPEG,
    PNG,
    WBMP,
    WEBP,
    PKM,
    KTX,
    ASTC,
    DNG,
    HEIF;

    @ApiStatus.Internal public static final EncodedImageFormat[] _values = values();
}