# Leaf 7: residency views (M3Residency)

Map row M3-RESIDENCY. With the viewports, a Table, List or Tree keeps only a bounded window of rows ready to paint, and
makes Java item objects (facades) only on demand: a List keeps its native rows in a window (`VirtualRowWindowPlan`), a
Tree keeps its paint window with memory-adaptive overscan (`VirtualTreeViewport`), and a Table holds facades strongly in
a ring of recent rows and weakly after that (`TableViewport.hold/release`). None of this is observable through stock
API, so applications and tests cannot check that a 10^6-row widget really stays at O(window).

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | answer |
|---|---|
| `M3ItemRange residentRange(Table / List / Tree)` | rows held ready to paint: the List's native row window, the Tree's paint window (visible-row coordinates, as `M3RowMath`), the rows in view joined with the last native cache hint of a Table without `SWT.VIRTUAL` (empty before the first paint; a virtual Table keeps the stock owner-data path); every row without a viewport |
| `boolean isMaterialized(Table, int index)` | whether a `TableItem` exists for the row now; never makes one |
| `long[] stats(Table / List / Tree)` | `[RESIDENT_ROWS, FACADES, HELD_FACADES]`: rows of `residentRange`, item objects alive (strongly or weakly reachable), and the strongly held ones; a List has no item objects (0) |

## Contract

- `residentRange` contains the visible range (`M3ItemSelection.getVisibleRange`, `M3RowMath` rows for a Tree) once the
  widget has painted.
- With the viewport, its size is bounded by the window (visible rows plus overscan), not by the item count. Without the
  viewport it is every row.
- `isMaterialized` is false for a row of a virtual viewport table that was never asked for, and true after `getItem`.
  It never makes a facade, so asking does not change the answer.
- `FACADES >= HELD_FACADES >= 0`. After scrolling through a 10^5-row virtual table the held facades stay bounded by the
  recent ring plus the application's own references (DOOP: facades are thin and parent-owned).
- The stats are read in one pass over the facade arrays, O(rows), and are meant for diagnostics and tests, not for paint
  paths.
- Platforms: the win32 widgets answer. On GTK and Cocoa `residentRange` is every row and the facade counts are -1
  (unknown), stated in the Javadoc.

## Not in this leaf

The map row also names a pressure listener and evicted/bytes counters. No widget adopts the residency engine
(`org.eclipse.swt.internal.residency`, see `M3_ITEM_RESIDENCY.md`) yet, so there is no pressure source or eviction
count to expose. These wait for that adoption. They are recorded as the open part of the row, not faked.

## DOOP and seam

- Read-only views over existing lanes; the only new state is the Table's last cache-hint range (two ints).
- The platform seam is the package-private `m3ResidentRange()` (null for every row) and `m3Facades(boolean held)` on
  Table, List and Tree, plus `Table.m3Materialized(int)`. On GTK and Cocoa these are stubs.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv`: an interval view of an existing window plan, plus a linear census of the facade arrays.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` plus the TableViewport change below; win32, GTK and Cocoa source sets 0 errors |
| JUnit, hidden desktop | `Test_M3Residency` 9/9 viewport, 9/9 original |
| differential against the official 3.135.0 jar | `Test_M3ResidencyDifferential` (shared `M3OfficialScene`, 8 seeds x 120 steps, plain and virtual): the official run records the rows of the Table and List whose bounds meet the client area; this SWT records the same rows when the resident range covers them, the stats are ordered and `isMaterialized` answers the same twice. First run: viewport plain failed at seed 0 step 1, resident `[10..14]` against visible `0..14` after 28 rows were added: the Table viewport answered the last `LVN_ODCACHEHINT`, which names only the rows a paint exposed. Fixed: `TableViewport.residentRange` joins the rows in view (top row and one page) with the hint; it stays empty before the first paint and without rows (a hidden table announces nothing, `Test_M3Prefetch.test_aHiddenTableAnnouncesNothing`). After the fix: 0 mismatches in viewport and original |
| coverage (JaCoCo 0.8.15, both modes) | lines 22/22; branches 14/16, the other 2 are `SWT.error` probe artifacts: every reachable branch covered |

Open: the map row's pressure listener and evicted/bytes counters wait for a widget to adopt `ItemResidency` (see "Not in this leaf"); GTK and Cocoa answer every row (stubs), not run.
