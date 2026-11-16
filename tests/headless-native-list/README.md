<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
# SWT List source-method headless regressions

The verify.py runner extracts the exact public void setItemCount (int count)
method body from both original Cocoa and GTK List.java sources. It uses
brace- and Java-comment/string-aware parsing to reject missing/duplicate or
unterminated method declarations. It then compiles those original bodies
with --release 17 -Xlint:all -Werror and runs Java behavioral peers in a
temporary directory on JDK 17 and JDK 21.

Cocoa checks 4..10,000 successive row-count increases, bounded spare
references, preserved item text, late edits, native-update call counts and
shrinking. GTK checks the native append/write call count, iterator
allocation/free exactly once per insertion batch, single UTF-8 conversion
per batch, unchanged-count no-ops and shrink behavior.

These peers are test-only and never form part of a shipping SWT bundle.
No additional runtime Control, viewer, row facade, backend or wrapper is
introduced. These checks are NOT a replacement for actual macOS Cocoa,
GTK3/GTK4, SWT PDE/JUnit, JNI, selection, screen-reader, paint, or ABI
qualification, all of which remain required before declaring release
readiness.

Run: python3 tests/headless-native-list/verify.py .
