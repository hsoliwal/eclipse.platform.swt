<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT native viewport selection window — Qt/WinUI donor absorption (2026-11-16)

Owner: existing `org.eclipse.swt.internal.ViewportRuntime.Selection`, backed by
two primitive arrays of normalized half-open intervals. The Qt
QItemSelectionModel/QItemSelectionRange concept is applied without copying Qt code.
WinUI/JavaFX-style viewport demand motivates fetching only rows actually needed
for paint/selection rather than materializing a global selection.

New additive APIs:
`selectedCount(int fromInclusive,int toExclusive)`,
`toArray(int fromInclusive,int toExclusive)`, and
`copySelectedIndices(int fromInclusive,int toExclusive,int[] dest,int offset)`.
The caller-owned copy path makes **zero temporary per-row objects or arrays**
inside the implementation. It clips to a half-open viewport, binary-seeks
normalized selected/excluded ranges, supports complement mode and first
validates the destination capacity so failure does not partially write.
It does not allocate an array proportional to logical row count.

No changes to legacy `toArray()`, `selectedCount()`, selection mutation,
native widget handles, accessibility, event dispatch or thread confinement.
The standalone Java21 algorithm oracle checks 160,000 bounded queries and
an Integer.MAX_VALUE logical model, but is **not** receiving product CI.
The SWT original-source Java21 gate and registered JUnit regressions must
still run on this commit before qualification; platform native release
gates remain separate. No source from Qt, Flutter, JavaFX or WinUI copied.
