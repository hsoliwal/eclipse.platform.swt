// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

public enum EncodeJPEGDownsampleMode {
    /**
     * Reduction by a factor of two in both the horizontal and vertical directions.
     */
    DS_420,

    /**
     * Reduction by a factor of two in the horizontal direction.
     */
    DS_422,

    /**
     * No downsampling.
     */
    DS_444
}
