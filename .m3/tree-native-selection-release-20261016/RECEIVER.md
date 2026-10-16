<!-- SPDX-License-Identifier: EPL-2.0 -->
# Original Initiative31 SWT Tree native release Selection candidate

**DRAFT — native contract parity remains RED/NOT_ACCEPTED.**

## Exact two-pass source provenance

This receiving branch extends SWT
[#172](https://github.com/hsoliwal/eclipse.platform.swt/pull/172),
source parent commit `5536d1195386c2927562637a39a0ed97d3ce6472`,
**without rebasing or force-pushing**.

1. Reuse the **existing** current-eight-owner TreeItem source image from
   [Synexia #11692](https://github.com/hsoliwal/com.synexia/pull/11692):
   original `TreeItem.java` blob
   `0da23fe0480597cdf70a7542092ed99ae7d87358`,
   exact previously generated image
   `efea1008ebca08ec22aeeb32801f937b8efe80d0`.
   The earlier recipe sends `SWT.Expand/Collapse` while listeners can still
   observe old expansion state. **Do not implement another copy of that atom.**
2. Apply **one new original Tree.java FILE-local source-sealed pass** owned by
   Synexia branch `m3/tree-native-selection-release-20261016`:
   source Git blob `ab68ac590865251816c7a47c3d6aa1dbbc37c6fb`,
   SHA-256 `9ec368e1480f0aed7b4151327609d81a8a1bb11cf465a2324b614d70d310d8fe`;
   output Git blob `3056dc5c20af686cd90b4f203973884451e192f2`,
   SHA-256 `8efa9490f06ce5c816df4912a70be3ec604f14c3b8690ea7d7b9c6ede95c894b`.
   Receiver source matches canonical recipe output exactly.

## Scope: only extra itemless generic Selection

Existing `Tree.onMouseUp` unconditionally emits generic
`SWT.Selection` on left-button release, including when the same mouse press
has already invoked a TreeItem disclosure or CHECK action. The native
comparison in Synexia #11692 observed an **extra generic itemless Selection**
in all four original/native disclosure and checkbox scenarios.

One private parent-owned Boolean records whether the **left press** was
handled by arrow expansion or checkbox toggling. It is armed before the
callback (reentrant MouseUp can run there), reset on the next left press and
consumed on release. Nonvirtual and virtual methods share the same owner
flag. Unmatched/ordinary row presses retain the existing generic Selection.
No alternate native control, Event object model, per-row proxy, JNI production
symbol, Skija painter, GC owner or public/protected/package signature changed.

The current candidate **does NOT correct**:
- custom Tree's registered internal MouseDown/MouseUp listener counts
  (stock native Tree has zero under the no-app-mouse-hooks profile);
- delayed callbacks after OS Down/Up return or focus differences;
- all dispatcher/resource/accessibility/pixel and cross-platform semantics.

## Bounded evidence and residual gates

Canonical [Synexia source seal and JDK21 proof](https://github.com/hsoliwal/com.synexia/tree/m3/tree-native-selection-release-20261016/synexia-openrewrite-recipes/recipe-crates/tree-native-selection-release-20261016)
includes the exact original/after Tree images, one-file Maven/OpenRewrite recipe,
before/after SHA-256 manifest, authored SDK fixed-point/drift-refusal cases,
Java21 JDK AST source/API audit, and bounded event-state model. A related
independent Java21 state harness executed locally with **25,038 assertions
passed**, including 25,000 seeded cases. The exact committed source-level
AST audit and Maven SDK tests are authored but have **not** run in real SWT.

The **actual Windows native JNI comparator** and four prior stock/common
native traces belong to [Synexia #11692](https://github.com/hsoliwal/com.synexia/pull/11692)
and are **RED** on the older packaged common runtime `fe3b552a...`.
Those results cannot be transferred to this untested composed branch.
Current candidate requires actual native rebuild of the combined TreeItem/
Tree.java recipient, same stock HWND/zoom/input harness, all four trace
comparisons, pointer reentrancy, item identity, event ordering and focus,
Win32/GTK/Cocoa, Unicode/IME, accessibility and pixel/Skija checks.

**Maven/OpenRewrite SDK, complete 680-source/Tycho product builds, native
SWT/JNI and 360-widget parity are NOT_RUN/HOLD**. Do not merge on the
strength of a Java model or a matching source hash.

## Mixed-button source recipe correction

The current exact recipe prevents a right MouseUp while `SWT.BUTTON1` is
held from consuming the pending **left** disclosure/CHECK action. The
one-bit suppression applies and clears only on `event.button == 1`.
This follows a recorded multi-button counterexample and is committed as
an additive correction to the canonical recipe **before** reapplying its
exact postimage. Stock/native SWT acceptance is still NOT_RUN/RED.
