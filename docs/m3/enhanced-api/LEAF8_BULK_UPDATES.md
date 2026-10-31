# Leaf 8: bulk updates (M3BulkUpdates)

Map row M3-BULK-UPDATES (with the Table agent's append-burst candidate).

## What already exists

The viewports already plan their native work instead of mirroring every edit:

- The List rebuilds its native window only when the edited rows leave the window (`ListViewport.ensureWindow`).
- The Tree answers `setItemCount(10^6)` in O(window).
- The Table defers an append burst into one `LVM_SETITEMCOUNT` (`TableViewport.defer` / `flush`).
- Every control can freeze its paint and scrollbar planes with `Control.beginBatch` / `endBatch` (public since 3.137).

What an application lacks is one call that says "these edits are one update": no paint, no scrollbar churn and no
emptiness flicker in between, and exception-safe.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | meaning |
|---|---|
| `M3BulkUpdates.run(Tree / Table / List, Runnable edits)` | runs the edits as one update: the viewport and scrollbar planes are frozen, M3Emptiness is held, and at the end the widget repaints only what changed, once, and announces at most one emptiness crossing |

## Contract

- The resulting items, texts, selection and top index equal those of the same edits without `run`.
- No paint reaches the widget while the edits run, even if the edits pump events. After `run` the damaged area is
  painted once.
- At most one `M3Emptiness` event, carrying the final count.
- Nesting is counted. An exception from the edits leaves no plane frozen and no hold open, and it propagates
  unchanged.
- In viewport mode the native work of a Table append burst is one count change (the `TableViewport` flush), and the
  List and Tree native work stays bounded by the window. In original mode the stock per-item native calls remain; the
  batch only coalesces their painting.

## DOOP

No new state: the plane depths live in the display's batch entry (`ViewportBatch`) and the hold lives in the M3Emptiness
latch. The class is common code over public and package API, so it works on every platform where `beginBatch` exists.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv`: scoped deferral (begin/end with a depth counter, try/finally) over per-edit flushing.

## Known issue (outside this leaf)

If the edits dispose the widget, `M3BulkUpdates` cannot end its batch: `endBatch` checks the widget. Win32
`ViewportBatch` has no dispose path, so the display keeps the disposed control's entry and its frozen-plane counters
(`batchViewports`, `batchScrollbars`) until the display itself is released. Behaviour stays correct: other controls
miss the entry lookup and paint normally. The cost is one leaked control reference and an entry lookup per paint.

The fix belongs to the viewport track, which owns `ViewportBatch`: `Control.releaseWidget` should drop the control's
batch entry and decrement the counters of its frozen planes without flushing. `Test_M3BulkUpdates.test_theEditsMayDisposeTheWidget`
exercises this path.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` (sha256 `d3e26aff00a3...`); win32, GTK and Cocoa source sets 0 errors |
| JUnit, hidden desktop | `Test_M3BulkUpdates` 9/9 viewport, 9/9 original |
| differential against the official 3.135.0 jar | `Test_M3BulkUpdatesDifferential` (shared `M3OfficialScene`, 8 seeds x 120 steps): burst steps run three public operations on one widget, unbatched with the official jar and inside `M3BulkUpdates.run` with this SWT; visible rows, selection counts and public state after each burst. Viewport: 0 mismatches, plain and virtual. Original: plain 0 mismatches; virtual fails in seed 0 with `ArrayIndexOutOfBoundsException: Index 79 out of bounds for length 79` at `TableStockItems.checkGrow (TableStockItems.java:79)` from `Table.CDDS_SUBITEMPREPAINT` (a paint after a batched burst on the virtual Table). The same operations unbatched pass in the original mode (the other leaves' differentials), so the batch (`Control.beginBatch` / `ViewportBatch`) leaves the stock sparse item keys (the R4_38 `_checkGrow` port) with an index beyond the native item count. Open defect |
| coverage (JaCoCo 0.8.15, both modes) | lines 14/14, branches 6/6 |

Open: the original-mode defect above (owner: the batch planes and `TableStockItems`); GTK and Cocoa not run.

Update (2026-10-31): the original-mode defect is fixed by PR #301. Seed 1 step 3 reduces to `setItemCount (57)`, 26
created items and `remove (0, 7)` in one burst: the R4_38 store shifts the keys after a removed range by the removed
items; unbatched, the ListView paints the rows in view inside the first `LVM_DELETEITEM` (the removed rows get items, so
the shift is right), the batch defers that paint. The official 3.135.0 jar throws the same exception for the same edits
with redraw off (TableColdRemoveOracle). PR #301 shifts by the removed rows while the viewport plane is frozen. With it
`Test_M3BulkUpdates` passes 10/10 in both modes (new test: cold rows removed in a burst) and the official-jar
differential has 0 mismatches in viewport and original mode, plain and virtual (run `diff-bulk-keys`). Open: GTK and
Cocoa not run.
