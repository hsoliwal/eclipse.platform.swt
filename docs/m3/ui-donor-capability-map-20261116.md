<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT native superset donor capability matrix — 2026-11-16

We absorb implementation algorithms and compatible API capabilities, **not wholesale
framework ownership**. SWT/GTK/Cocoa/Win32/Android/iOS controls retain native handles,
selection, event ordering, thread confinement, accessibility and lifecycle. Nebula
optional widgets remain separately packaged.

| Donor | Exact capability | Existing SWT/M3 target | Code-copy policy |
| --- | --- | --- | --- |
| Qt [QAbstractItemModel](https://doc.qt.io/qt-6/qabstractitemmodel.html) | persistent indexes, model update signals, data roles | SWT Tree/Table, JFace viewers, DOOP index lanes | Source LGPL/GPL/commercial per file; concept-only here |
| Flutter [sliver lists](https://api.flutter.dev/flutter/rendering/RenderSliverFixedExtentList-class.html) | fixed/variable extents and cache regions | VirtualScrollMetrics, VirtualRowExtents | BSD-3-Clause; no Dart source copied |
| JavaFX [VirtualFlow](https://github.com/openjdk/jfx/blob/master/modules/javafx.controls/src/main/java/javafx/scene/control/skin/VirtualFlow.java) | recycler cell reuse, variable geometry | SWT native item residency, VirtualRowWindowPlan | GPLv2 with Classpath Exception; independently implement |
| WinUI [ItemsRepeater](https://learn.microsoft.com/windows/apps/design/controls/items-repeater) | element prepared/clearing lifecycle, virtualizing layout and caching | DOOP viewport plans and native cells | Microsoft UI XAML MIT; no code copied here |
| Eclipse Nebula Grid | fixed columns, groups, rich paint and selection | Existing Nebula bundles, SWT shared geometry | EPL-2.0/dual; do not move optional widgets into core |
| SwingX / Apache NetBeans | JXTreeTable, Outline, sorting/filtering | SwingX and JFace original viewers | SwingX LGPL, NetBeans Apache-2.0; verify source by file |
| Android / iOS | native recycler, IME, touch and accessibility | SWT platform-native backends | preserve ownership and platform verification |

## First implemented absorption

`VirtualRowExtents.rowAt` now searches sorted measured-row ends by Fenwick
binary lifting in **O(log m)**, down from the previous O(log² m) binary search
with repeated prefix queries. It retains existing primitive row ID, extent,
and Fenwick arrays and exact zero-height and gap semantics.

New package-private `visibleRange(offset,viewport,beforePixels,afterPixels,out)`
projects an **asymmetric pixel-cache window** for directional prefetch. No
per-row object is created; the existing API remains unchanged. This is an
opt-in internal API, not yet a public SWT widget property.

No source from Qt, Flutter, JavaFX, WinUI, Nebula, or SwingX is copied by
this change. Further API and platform work must pass the same behavior,
provenance and native lifecycle gates.
