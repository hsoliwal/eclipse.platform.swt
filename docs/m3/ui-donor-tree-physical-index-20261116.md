<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT original Tree Residency — physical and identity lookup indexes (2026-11-16)

Source owner: `VirtualTreeVisibleProjection.Residency`. No SWT native widget or
public List/Table/Tree method has been changed. Existing materialized IDs,
logical coordinates, physical parent/child indices, facade bindings, hint
entries, selection, accessibility, event order and disposal remain authoritative.

The original `physicalChild(parentEntry, index)` walked sibling links
O(index). It now queries a **lazy parent-owned primitive child directory**:
two `int[]` lanes are indexed by physical parent and sibling index,
rebuilt once after source mutation in O(resident window size), followed
by O(1) child lookup. Native snapshots are unchanged.

The original `entryForMaterializedId` and `entryForCoordinate` scanned the
window O(m). They now use lazy derived indexes with the existing
`VirtualLongIntTable` (parallel primitive arrays, no entry objects),
with first-match semantics preserved for duplicate facade IDs.
`reset`, `appendEntry`, and `bindFacade` invalidate the appropriate
derived indexes. Expander hints are included in coordinate/child lookups;
they are never assigned an ordinary materialized ID. All indexes are
bounded by *resident rows*, not logical Tree item count.

Donor capability: Qt's QModelIndex/QAbstractItemModel parent/child
indexing pattern, with SWT topology IDs as stable identities. **No Qt
source copied**, no Qt dependency or replacement of SWT's native Tree.
The independent Java21 algorithm probe passed 160,000 differential child
lookups. One diagnostic synthetic 20,000-query, 10,000-root sample
measured 155 ms linked traversal versus 1.1 ms indexed lookup.
These are **not** SWT GUI throughput measurements.

Qualification: original SWT JUnit adds nested/root coordinate,
facade rebind, expander hint, reset and high-water capacity cases.
A new Java21 headless gate compiles six real SWT source owners and
runs its own nested/10k-root/differential test; full native GTK/Cocoa/
Win32/Android/iOS and Maven/OpenRewrite acceptance remain separate.
