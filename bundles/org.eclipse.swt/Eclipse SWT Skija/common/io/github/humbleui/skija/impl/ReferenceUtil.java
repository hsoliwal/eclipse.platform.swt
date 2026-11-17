// Changed for SWT (Apache-2.0 section 4b): Java 9 version (shared/java9/impl at tag 0.116.3).
package io.github.humbleui.skija.impl;

public class ReferenceUtil {

    /**
     * @see java.lang.ref.Reference#reachabilityFence(Object)
     */
    public static void reachabilityFence(Object ref) {
        // Forward to Reference.reachabilityFence(Object) to ensure that this method
        // will continue to work even if the JVM behavior changes in the future.
        java.lang.ref.Reference.reachabilityFence(ref);
    }
}
