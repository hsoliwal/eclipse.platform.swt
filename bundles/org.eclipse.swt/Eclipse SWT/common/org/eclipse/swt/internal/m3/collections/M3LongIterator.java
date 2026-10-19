/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3LongIterator (same author; the SWT copy of the viewport collections).
 * Licensed under the Apache License, Version 2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

import java.util.Objects;
import java.util.function.LongConsumer;

/** Primitive iterator contract: no boxed Long is created per element. */
public interface M3LongIterator {
    boolean hasNext();

    long nextLong();

    default void forEachRemaining(LongConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        while (hasNext()) {
            consumer.accept(nextLong());
        }
    }
}
