// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Stats {
    public static boolean enabled = false;
    public static long nativeCalls = 0;
    public static Map<String, Integer> allocated = new ConcurrentHashMap<>();

    public static void onNativeCall() {
        if (enabled)
            nativeCalls++;
    }

    public static void onAllocated(String className) {
        if (enabled)
            allocated.merge(className, 1, Integer::sum);
    }

    public static void onDeallocated(String className) {
        if (enabled)
            allocated.merge(className, -1, Integer::sum);
    }
}
