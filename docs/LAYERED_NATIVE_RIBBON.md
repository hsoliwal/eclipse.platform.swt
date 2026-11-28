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

## Shared opacity animation on native and painted image layers

`LayeredTransitions.fadeTo(layer, opacity, millis, easing, onComplete)`
animates 0..255 opacity through the existing one-timer SWT controller.
Two image layers can cross-fade concurrently without another widget,
backing store, texture owner or JNI symbol. Native DirectComposition image
layers retain their visuals and publish effect-opacity deltas; painted
layers invalidate their usual retained regions.

A layer has one active animation: a new move cancels its fade and vice
versa, preserving the last published value. A native child SWT Control
has no per-pixel alpha in this layer model; opacity animation rejects
such Controls instead of misrepresenting the visual result.

Frames still originate on the SWT UI thread: this is not native OS-offloaded
timeline execution. Win32/GTK/Cocoa pixel, accessibility, lifetime and
performance gates remain prerequisites to release.
