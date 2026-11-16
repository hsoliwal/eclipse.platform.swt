<!-- SPDX-License-Identifier: EPL-2.0 -->
# Android SWT JNI retained-pixel move: lock cleanup and stride validation

This fixes two defensive branches inside the original Android swt_pixels.c
owner, preserving the existing bottom-up/top-down memmove traversal for
overlapping Canvas.scroll copies and the separate Java bitmap fallback.
There is no change to SWT Java/Android/iOS public APIs or native peer
ownership, and no Qt, Flutter, WinUI or JavaFX source is copied.

The original source could retain a successful AndroidBitmap_lockPixels
operation if the returned pixel pointer was unexpectedly null. It now
balances that successful lock with an unlock before refusing the native
operation. RGBA_8888 metadata with stride smaller than advertised width*4
is rejected before the pointer is locked.

RED/GREEN evidence: exact source baseline rejected by the host C harness
for unbalanced null-pointer-success locking. Revised product C code compiled
with gcc -std=c11 -Wall -Wextra -Werror -Wpedantic and passed 6,023 source
bitmap-copy/refusal cases and 9,043,045 pixel comparisons, both normally
and under AddressSanitizer and UndefinedBehaviorSanitizer on Linux.
Native AndroidBitmap NDK and device acceptance remain independent gates.
