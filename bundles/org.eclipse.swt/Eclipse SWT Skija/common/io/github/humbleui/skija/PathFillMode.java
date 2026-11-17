// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune).
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;

public enum PathFillMode {
    /** Specifies that "inside" is computed by a non-zero sum of signed edge crossings. */
    WINDING,

    /** Specifies that "inside" is computed by an odd number of edge crossings. */
    EVEN_ODD,

    /** Same as {@link #WINDING}, but draws outside of the path, rather than inside. */
    INVERSE_WINDING,

    /** Same as {@link #EVEN_ODD}, but draws outside of the path, rather than inside. */
    INVERSE_EVEN_ODD;

    @ApiStatus.Internal public static final PathFillMode[] _values = values();

}
