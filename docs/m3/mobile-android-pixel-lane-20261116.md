<!-- SPDX-License-Identifier: EPL-2.0 -->
# Android SWT retained pixel movement: bounded Java fallback lane

`AndroidPixels` is the existing Android SWT backend for `Canvas.scroll`'s
retained bitmap copy. Its JNI/NDK `swtpixels` path is unchanged. The Java
fallback previously allocated a `width`-pixel lane when width was greater
than 65,536, violating its own per-step memory budget. This pass partitions
very wide source rectangles into horizontal tiles while retaining the
previous vertical-band fast path for ordinary widths.

The algorithm reads a tile fully before writing it. When pixels move right
(`dx > 0`), tiles progress right-to-left; otherwise they progress left-to-right.
When pixels move down (`dy > 0`), bands progress bottom-up; otherwise top-down.
Thus intersecting source and destination preserve memmove semantics for both
positive and negative offsets, with no new per-pixel object graph and a
reusable scratch buffer of at most 65,536 `int` pixels.

The Java21 headless contract compiles the **real product AndroidPixels.java**
using a clearly separated test-only `android.graphics.Bitmap` shim; it compares
3,008 source-to-destination moves with an independent snapshot oracle, including
90,000/130,000-wide, diagonal, reversed and random cases. It checks the
largest scratch lane never exceeds 65,536 ints. The original source baseline
failed this cap check (90,000 ints), and the patched source passed it.

This is not Android/iOS native runtime qualification: AndroidBitmap NDK JNI
remains unchanged and must be checked on-device. `Canvas`, SWT public APIs,
peer ownership, event/paint rules and the iOS backend are unchanged.
