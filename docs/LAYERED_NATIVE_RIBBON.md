<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
# Native SWT layered ribbon and slide panels

This is **an adapter over the existing `LayeredViewport`**, not a renderer fork,
React runtime, SWT control replacement, or browser. The native `TabFolder`
provides ribbon tabs; the existing `Layer` provides pinned X/Y ownership,
stable Z identity, native Control placement and disposal. A general
`LayeredSlidePanel` supports top, bottom, left and right anchored drawers.

```java
LayeredViewport viewport = new LayeredViewport(shell, SWT.NONE);
LayeredRibbon ribbon = new LayeredRibbon(viewport, 36, 180);
Composite home = ribbon.addTab("Home");
new org.eclipse.swt.widgets.Button(home, SWT.PUSH).setText("Run");
ribbon.addTab("Insert");
ribbon.setExpanded(true, true);
ribbon.bringToFront(); // after adding scrolling content layers
```

The tab strip remains visible while a ribbon page expands. Its native tab
selection, keyboard focus and accessibility remain SWT-owned; geometry moves
by `Layer.setWorldBounds`, using `setPinned(SWT.HORIZONTAL|SWT.VERTICAL)`.
The stable layer is never removed/recreated during slide transitions.

For a floating native drawer, create a `Composite` **as a child of the
existing viewport** and pass it to `LayeredSlidePanel` using edge
`SWT.LEFT`, `RIGHT`, `TOP` or `BOTTOM`, plus collapsed/expanded extents.
Panels borrow their Control: closing a panel removes the Layer but leaves
that Control alive. `LayeredRibbon` creates and owns its `TabFolder` and
disposes that control on close.

Every animated frame is on SWT's UI thread. A generation check cancels old
frames on reversal, replacement, or disposal; interpolation uses bounded
integer extents, no native timers or per-row Control objects. No new JNI
entrypoint, GPU compositor, `Canvas`, backing-store owner or Web/JS library
is added. OS native child Controls still stack above all painted layers, even
when a painted layer has a higher logical Z index: arbitrary CSS stacking
between painted and native widgets is explicitly **not** supported.

The native-run evidence from a different pinned-image test (two original
tests alone and the pinned header test alone) does **not** prove a shared
three-test run; reproduce any grey screen using combined ordered runs and
deterministic screen pixel oracles, not 'no exception' as success.

Pending: run the newly registered SWT native JUnit methods on Windows,
GTK3/GTK4 and Cocoa; run native pixel snapshots during in-flight animation,
IME/keyboard accessibility, focus handoff on fully hidden drawers, mouse
capture, native handle/RSS disposal, and full recipe/Maven source seals.
No claims of successful native visual execution are made in this changeset.

## Pin/slide native pixel sequence regression (2026-11-28)

All three selected Maya native tests now **preserve their first exact RGB assertions**. Only
after a mismatch does it observe the same pixel for one bounded second. It
always fails on a wrong first capture, reporting `MAYA_PIXEL_LATE` when the
expected color later arrives, or `MAYA_PIXEL_PERSISTENT` if it does not.
The failure includes first/last RGB, sample count, elapsed time, pixel
coordinates and scroll origin. Its one-pixel SWT Image is disposed on error.

Run `tools/maya/run-native-layer-sequence.ps1` with the existing Windows Maya
test rig to preserve logs from the original two methods, the pinned method,
all three method selectors in one JVM, and the full current class. Repeat
the matrix; require nonzero test discovery, zero failed/aborted tests,
no JNI warnings, and a successful process exit. Command-line selector order
does **not** guarantee JUnit execution order. Inspect the actual JUnit tree.

```powershell
& ./tools/maya/run-native-layer-sequence.ps1 `
    -Runner '/f/m3-scratch/jni-lcd/run_maya.sh' `
    -Tree 'F:/m3-scratch/maya-dcomp/tree' `
    -ExtraClasspath 'F:/m3-scratch/layers/pin/natcls' `
    -OutRoot 'F:/m3-scratch/maya-dcomp/native-sequence' -Iterations 3
```

This test-only diagnostic introduces no production Java API, JNI symbol,
new native visual, widget owner or altered z-order policy.
