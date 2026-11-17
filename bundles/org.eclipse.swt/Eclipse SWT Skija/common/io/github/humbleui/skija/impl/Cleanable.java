// Changed for SWT (Apache-2.0 section 4b): Java 9 version (shared/java9/impl at tag 0.116.3).
package io.github.humbleui.skija.impl;

import java.lang.ref.Cleaner;

public final class Cleanable {
    private static final Cleaner cleaner = Cleaner.create();

    public static Cleanable register(Object obj, Runnable action) {
        return new Cleanable(cleaner.register(obj, action));
    }

    private final Cleaner.Cleanable cleanable;

    private Cleanable(Cleaner.Cleanable cleanable) {
        this.cleanable = cleanable;
    }

    public void clean() {
        this.cleanable.clean();
    }
}
