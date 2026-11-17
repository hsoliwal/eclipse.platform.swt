// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya;

public interface FourByteTag {
    static int fromString(String name) {
        assert name.length() == 4 : "Name must be exactly 4 symbols, got: '" + name + "'";
        return (name.charAt(0) & 0xFF) << 24
             | (name.charAt(1) & 0xFF) << 16
             | (name.charAt(2) & 0xFF) << 8
             | (name.charAt(3) & 0xFF);

    }

    static String toString(int tag) {
        return new String(new byte[] { (byte) (tag >> 24 & 0xFF),
                               (byte) (tag >> 16 & 0xFF),
                               (byte) (tag >> 8 & 0xFF),
                               (byte) (tag & 0xFF) });
    }
}