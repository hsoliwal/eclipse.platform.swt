# Leaf 19: one model, deltas end to end (M3Model, M3Patch)

User, 2026-10-19 (memory `feedback-one-model-delta-processing`): one model across SWT, JFace and data binding, and
deltas end to end. The map row M3-BULK-UPDATES grows from "one repaint per bulk edit" (leaf 8) into the **SWT-side
model seam**: the widget's lanes are the model, JFace and data binding are thin views over it, and every change
arrives as a delta.

```
source delta (IResourceDelta, marker delta, model event)
   -> viewer delta: M3Patch, runs over ids, per parent
   -> widget: M3Model.apply(patch)
        inside the resident window: those rows are updated natively, O(changed)
        outside it:                 only the extents and the scroll bar change; zero native row operations
```

## Shapes

### `org.eclipse.swt.m3.M3Patch` (toolkit-neutral value, no SWT types)

The names and run order are those of M3JDK `com.m3.collections.M3Patch` (M3jdk21 #1403, produced by `M3ListDiff`),
so one patch can travel from a collections view to a widget unchanged. This leaf adds **update runs**, the **ids** of
the inserted rows, and the **parent scope**.

| member | meaning |
|---|---|
| `parent()` | id of the parent whose child list the patch edits; `M3Model.ROOT` (-1) for the roots or a flat list |
| `removeRuns()`, `removeAt(i)`, `removeCount(i)` | runs in old indices, ordered from the highest index down |
| `insertRuns()`, `insertAt(i)`, `insertCount(i)` | runs in new indices, ordered from the lowest index up |
| `insertedId(i, k)` | id of the k-th row of insert run i (one `long[]` lane for all runs) |
| `updateRuns()`, `updateAt(i)`, `updateCount(i)` | rows whose label changed, in new indices (applied last) |
| `moveCount()`, `moveFrom(i)`, `moveTo(i)` | pairs of a removed and an inserted index that carry the same id; the widget moves the row's state (texts, images, checked, selection) instead of asking for it again |
| `removedCount()`, `insertedCount()`, `updatedCount()`, `isEmpty()` | totals |
| `applyTo(Target)` | removes, then inserts, then updates, in the order above (`Target`: `remove(index, count)`, `insert(index, count)`, `update(index, count)`) |
| `M3Patch.builder(parent)` | `remove(at, count)`, `insert(at, long... ids)`, `update(at, count)`, `move(from, to)`, `build()`; checks the run order |
| `M3Patch.diff(parent, long[] oldIds, long[] newIds)` | the minimal patch (LIS kept rows, the leaf-10 `M3DiffApply` plan); moves are paired by id |

Packed lanes: `int[]` per run field plus one `long[]` of inserted ids. No entry objects.

### `org.eclipse.swt.widgets.M3Model` (the widget's model view)

`M3Model.of(Table | List | Tree)` returns the one model of the widget. It is kept in the widget, never copied.

| member | meaning |
|---|---|
| `ROOT` | the parent id of the roots (and of every row of a List or Table) |
| `size(parent)` | children of a parent: the row count for List/Table, the root or child count for Tree |
| `id(parent, index)`, `indexOf(id)`, `parentOf(id)` | the id lane; ids are positional (id = index) until the first patch or `setIds` gives them |
| `setIds(parent, long[] ids)` | attaches ids to the existing rows (no row work) |
| `text(id, column)`, `image(id, column)` | the label lanes, read without creating items |
| `apply(M3Patch patch, Labels labels)` | applies the patch; `Labels.fill(id, M3LabelSink sink)` is asked **only for rows inside the resident window**; the others are filled on demand, as `SWT.SetData` would |

Contract of `apply`:
1. The result equals the same edits made through the stock item API (`new TableItem(table, style, index)`,
   `remove`, `setText`, `TreeItem` equivalents). That covers:
   - item counts, texts, images and checked state;
   - the selection (which follows the rows' ids) and the focus row;
   - the events (`SWT.SetData`, `M3Emptiness`, `M3Prefetch`, `M3VisibilityObserver`);
   - the painted pixels.
2. The top row stays the same row (anchored by id). A patch entirely outside the resident window therefore changes
   only the scroll bar and performs **zero native row operations**. With the viewports the native row operations are
   counted (`M3Residency.stats(widget)[NATIVE_ROW_OPS]`, a new index):
   - List: `LB_INSERTSTRING`, `LB_DELETESTRING`, `LB_SETITEMDATA`;
   - Table: `LVM_REDRAWITEMS`, row `InvalidateRect`, `LVM_SETITEMCOUNT`;
   - Tree: `TVM_INSERTITEM`, `TVM_DELETEITEM`, `TVM_SETITEM`.
   The differential asserts zero for such a patch.
3. Inside the window the native work is O(changed rows).
4. One `apply` is one update (`M3BulkUpdates`): one repaint and at most one emptiness event.

### Consumers (the JFace agent first)

- `M3TableViewerModel` / `M3TreeViewerModel` (platform.ui fork, branch `m3/jface-viewport-20261019`, design
  section 5) keep `Object[] elements`, and an element's id is its index there.
  - Their `visible` and `rowOf` lanes become `M3Model.id` / `M3Model.indexOf`: the row-to-element store exists once,
    in the widget.
  - A refresh, filter or sort result is sent as `M3Patch.diff(ROOT, oldVisible, newVisible)`, or built directly from
    the worker's ENTERED/LEFT/ORDER changes (sort-filter design section 1).
  - The label provider is the `Labels` callback, asked for window rows only.
- Data binding observables (`IObservableList` diffs) map one to one:
  - `ListDiffEntry` add → insert run;
  - remove → remove run;
  - a replace pair → update.
- Workspace deltas (Markers view):
  - `IResourceDelta.ADDED` / `REMOVED` → insert / remove runs under the parent's id;
  - `CHANGED` with marker deltas → update runs.
  No full rebuild.

## Swing (M3JDK track, leaf S8)

Swing models already send deltas, so the seam routes them instead of rebuilding.
- `javax.swing.M3Patch` is the same value (same members).
- `AbstractTableModel.firePatch(M3Patch)` fires one `TableModelEvent` per run: DELETE (highest first), INSERT (lowest
  first), UPDATE. `fireTableDataChanged` is not used.
- `DefaultTableModel.apply(M3Patch, IntFunction<Object[]>)`, `DefaultListModel.apply(M3Patch, LongFunction<E>)` and
  `DefaultTreeModel.apply(M3Patch, ...)` edit their stores and fire those events. `nodesWereInserted` /
  `nodesWereRemoved` carry the child indices of each run.
- JTable, JList and JTree process INSERT/DELETE in O(changed). With the option `M3Patch.ANCHOR`, the viewport keeps
  the top row's content, so a delta outside the visible rect changes only the scroll bar.

## Steps

1. `M3Patch` value, builder and `diff`. Headless tests against a list oracle. `M3DiffApply` rebuilt on it.
2. `M3Model` for List, Table and Tree over the stock item API, inside `M3BulkUpdates`, with top-row anchoring and the
   native-row-operation counters. The differential compares against stock edits.
3. Viewport fast paths: lane shifts without facades. Then the zero-native-operations assertion for patches outside
   the window, and O(changed) inside it.
4. Swing S8.
5. Examples: the same scenario as the other examples (a delta stream on the million-row catalogue), in SWT and Swing.

## Status (2026-10-20)

| step | state |
|---|---|
| 1. `M3Patch` | shipped. `Test_M3Patch` passes 3/3 (headless): minimal diff checked against a quadratic LIS, replay against a list oracle, argument checks. |
| 2. `M3Model` for List and Table | pushed and compile-clean. `Test_M3Model` (differential against stock edits: rows, labels, selection, top row, emptiness events, pixels) is queued on the hidden desktop. |
| 2b. `M3Model` for Tree | pushed and compile-clean. Ids are kept per parent in `M3TreeModel` over `M3LongIndex` (growable long->int, backward-shift removal). The tree differential is in `Test_M3Model`. |
| 3. viewport fast paths and native-operation counters | specified below; needs the seams of the Table, List and Tree viewport owners |
| 4. the model side of a patch | done (code in the interval-sweep commits 0566ed57f93 and 8c13c0d1b9e), with one cost left. An update-only patch costs O(changed): it keeps the lane. A patch with inserts or removes still costs O(n) in the model: `M3Patch.apply (long [])` builds the new lane in one pass and one allocation (8 bytes per row, 8 MB at 10^6 rows; correction 2026-10-20, the first version of this row said O(changed) for every patch). The median below is an update delta; the public model API (`MODEL_API.md`: lanes by slot, order index per parent) removes the lane copy. `M3Patch.apply (long [])` builds the new lane (two-pointer merge of the kept rows and the insert runs) instead of one array per run. `M3Model` keeps its id-to-row index (`M3LongIndex`) across patches: removed ids leave, inserted ids come in with their rows, shifted rows keep a stale row as a hint that `indexOf` checks against the lane and rebuilds only when a stale hint is asked for; an update-only patch keeps the lane; the inserted-id check covers the changed rows only; the top row is followed through the runs in O(runs) (`follow`), and looked up by id only when the patch removed it. `Test_M3Model`, `Test_M3DiffApply`, `Test_M3Patch`, `Test_M3BulkUpdates`: 25/25 in viewport mode and 25/25 in original mode (hidden desktop, jar ea20261019m with the patched classes ahead). The widget side of an insert or remove is still one `createItem` / `destroyItem` per row, O(n) lane shifts in the viewports: step 3. Table agent, 2026-10-20: the Table side is done on `m3/table-viewport-20261016` (4d38d24437b, `Table.m3Splice`, one `LVM_SETITEMCOUNT` per run, 0 native row operations for 100 rows inserted above the window, 10^4 rows in 1.1 s instead of 18.2 s); open: `M3BulkUpdates.bulk` ends the scroll-bar plane with a whole-client invalidate (`ViewportBatch.leave`, `RDW_FRAME | RDW_INVALIDATE`), so a delta outside the window still repaints the 21 visible rows. |
| 5. examples | SWT: `M3DeltaStreamExample` (100,000 one-row deltas, 70% updates, 15% inserts, 15% removes, into a `SWT.VIRTUAL` Table of 10^6 rows and a Tree of 1,000 categories of 999 products, events pumped every 100 deltas, no refresh). Table, before step 4: 5,745 deltas in the 300 s cap, median 44,293 us per delta (the lane copy and the id index rebuilt per patch); after: 100,000 deltas in 200 s, median 15 us, p99 13,987 us (the p99 is the native row insert or remove of step 3); 31 labels asked for the whole stream (only rows on screen); the top row never moved for a delta outside the window. Tree: median 1,190 us before, 1,058 us after (its lanes are per parent, 999 rows; the cost is the item API). `M3HiddenTabDeltasExample`: the same stream into a table in a background tab, then one show (brief rules 24 and 25). Swing twin: pending (the Swing `apply` methods of S8 take the same patches). |
| Swing S8 | done on M3jdk21 `m3/swing-enhanced-api-20261019`: `M3Patch` d7284fdd1f9, `firePatch` and the three `apply` methods with their tests 8cc0a4d2b29, verification record cced93e804b (22 tests, new code 100% lines / 97.2% branches, behaviour differential equal). |

## Step 3: the splice seam (for the viewport owners)

Today `M3Model.apply` edits rows through the item API. Each inserted or removed row is one `createItem` /
`destroyItem`. With the viewports that is a lane shift plus one native count change per row, and the visible
content shifts before the top row is re-anchored. The zero-native-operations contract needs one call per run, in
each viewport:

| seam (package-private) | effect |
|---|---|
| `Table.m3Splice(int at, int removed, int inserted)` | `TableViewport.remove`/`insert` on the lanes and the item array once. Then one `LVM_SETITEMCOUNT` with `LVSICF_NOINVALIDATEALL | LVSICF_NOSCROLL`, the top index moved by the rows inserted or removed above it, and an invalidation of the visible rows whose content changed (none when the run is outside the window). |
| `List.m3Splice(int at, int removed, String[] inserted)` | `ListViewport` shifts its window plan. Native rows are touched only when the run intersects the window. |
| `Tree.m3Splice(TreeItem parent, int at, int removed, int inserted)` | `VirtualTreeTopology` and `TreeViewport.ensureWindow`. Native items are inserted or deleted only inside the paint window. |
| `long m3NativeRowOps()` on each widget | count of the native row operations (List: `LB_INSERTSTRING`/`LB_DELETESTRING`/`LB_RESETCONTENT`; Table: `LVM_SETITEMCOUNT` without the no-invalidate flags, `LVM_REDRAWITEMS`, row invalidations; Tree: `TVM_INSERTITEM`/`TVM_DELETEITEM`/`TVM_SETITEM`). Exposed as `M3Residency.stats(widget)[NATIVE_ROW_OPS]`. |

`M3Model.apply` calls the seam when the widget has a viewport and keeps the item-API path otherwise. The
differential stays as it is. A new test asserts `NATIVE_ROW_OPS` unchanged and no `EraseItem`/`SetData` for a patch
outside the window, and O(changed) inside it.
