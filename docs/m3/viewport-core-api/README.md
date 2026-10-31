<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
# Public M3 viewport core API (enhanced API, additive)

Four thin public facades in `org.eclipse.swt.widgets` (binding rule: widget-bound M3 API lives there for
package access to the viewport lanes) over the package-private core every SWT viewport already uses:

| facade | core | answers |
|---|---|---|
| `M3ResidentWindow` | `VirtualViewportPlanner` (RowWindow) | half-open visible and resident ranges into a caller `int[]` |
| `M3LogicalExtent` | `VirtualScrollMetrics` | `long` content extent, visible rows, max top row, scroll bar projection |
| `M3Planes` | `ViewportLayerState` (`ViewportRuntime` planes) | dirty plane mask per scroll (vertical never dirties HEADER, horizontal never FROZEN) |
| `M3DamageTracker` | `ViewportDamageRegion` | damaged rectangles and blits, bounding box take-and-clear |

Additive and opt-in: no stock file changes; nothing runs unless a client calls an `M3*` type. Mode-neutral:
the facades hold no widget. DOOP: results are ints/longs written into caller arrays.

First client: the Nebula `m3web` widgets (hsoliwal/nebula PR #308), whose per-side viewport core binds to these
facades on SWT and to `com.m3.swing.viewport.M3ViewportIndexWindow` on the M3JDK Swing port, so both
toolkits share one window contract (the Nebula/Swing model differential compares them).

Test: `tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3viewport/Test_org_eclipse_swt_widgets_M3ViewportCore.java`:
the resident window equals the reference arithmetic of the Swing port's window contract over 20,000 random
geometries, logical extents beyond the int range, plane masks, damage union. Run 2026-10-31 with javac --release 21
-Werror and the JUnit console against the branch's core sources and against the m3 SWT jar tc20261017a: 5/5 both.
