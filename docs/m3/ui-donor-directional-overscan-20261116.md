<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT directional viewport overscan — Flutter/WinUI donor absorption

The original SWT `ViewportRuntime.RowWindow` owns model-only viewport projection.
Its existing `setOverscanRows(int)` remains symmetric and unchanged for clients
that do not opt into directional overscan. Additional
`setDirectionalOverscanRows(beforeRows, afterRows)` and read accessors allow
forward/backward-biased cache budgets while retaining logical anchors,
collapsed viewport behavior, native item identities and generation semantics.

The existing `VirtualViewportPlanner` delegates the optional operation; no
new widget, native handle, per-row object, parallel state authority or mandatory
dependency is introduced.

**Donors:** Flutter `ScrollCacheExtent` (see its new API replacing deprecated
cacheExtent/cacheExtentStyle), WinUI ItemsRepeater vertical cache-length and
realization behavior, Qt model/view and JavaFX VirtualFlow ownership/lifecycle
concepts. All are behavior/API donors here; **no donor source copied**.
See official Flutter/WinUI documentation and keep platform code under original
license/source ownership.

**Tests:** independent Java21 RED/GREEN proof with 120,000 randomized
row-window equivalence cases, exact-source product CI contract and original
JUnit suite. Native GTK/Cocoa/Win32/Android/iOS lifecycle still requires
platform-specific acceptance before any release claim.
