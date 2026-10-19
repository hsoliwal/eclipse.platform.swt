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

## Status (2026-10-19)

| step | state |
|---|---|
| 1. `M3Patch` | shipped. `Test_M3Patch` passes 3/3 (headless): minimal diff checked against a quadratic LIS, replay against a list oracle, argument checks. |
| 2. `M3Model` for List and Table | pushed and compile-clean. `Test_M3Model` (differential against stock edits: rows, labels, selection, top row, emptiness events, pixels) is queued on the hidden desktop. |
| 2b. `M3Model` for Tree | pushed and compile-clean. Ids are kept per parent in `M3TreeModel` over `M3LongIndex` (growable long->int, backward-shift removal). The tree differential is in `Test_M3Model`. |
| 3. viewport fast paths and native-operation counters | specified below; needs the seams of the Table, List and Tree viewport owners |
| Swing S8 | done on M3jdk21 `m3/swing-enhanced-api-20261019` (22 tests, new code 100% lines, behaviour differential equal); the commit waits for disk space on K: |

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
