// Changed for SWT (Apache-2.0 section 4b): unused lombok imports removed; package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

import org.jetbrains.annotations.*;

/**
 * @see org.eclipse.swt.internal.maya.FilterMipmap
 * @see org.eclipse.swt.internal.maya.CubicResampler
 */
public interface SamplingMode {
    public static final SamplingMode DEFAULT = new FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE);
    public static final SamplingMode LINEAR = new FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE);
    public static final SamplingMode MITCHELL = new CubicResampler(0.33333334f, 0.33333334f);
    public static final SamplingMode CATMULL_ROM = new CubicResampler(0, 0.5f);

    // 10 + 30-bit float + 32-bit float: CubicResampler
    // 01 + 30-bit zeros + 32-bit int:   SamplingModeAnisotropic
    // 00 + 30-bit int   + 32-bit int:   FilterMipmap
    @ApiStatus.Internal long _pack();
}