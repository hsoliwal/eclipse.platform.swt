/*******************************************************************************
 * Test-only native symbol shim. Not compiled into the Maya production bundle.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.maya;
import java.nio.ByteBuffer;
public final class MayaComposition {
    public static native int _nFramePacketVersion();
    public static native int _nApplyPacked(long composition, ByteBuffer data, int length);
}
