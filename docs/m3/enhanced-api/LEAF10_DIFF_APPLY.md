# Leaf 10: identity diff (M3DiffApply)

Map row M3-DIFF-APPLY (cross-ecosystem: UIKit diffable data source, RecyclerView DiffUtil, Glazed Lists Tree4Deltas).
Applications that reload a model into a List, Table or Tree today call `removeAll` and add everything again. Every
row then loses its item object, selection, check state and (Tree) expansion, and the native control redoes all of
it. With stable row identities the widget can keep every row that is still in place and touch only the rest.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| method | meaning |
|---|---|
| `int [] apply(List, long[] oldIds, long[] newIds, LongFunction<String> text)` | reorders the list from `oldIds` to `newIds` |
| `int [] apply(Table, long[] oldIds, long[] newIds, ObjLongConsumer<TableItem> fill)` | the same for a table; `fill` fills each new or moved row (for `SWT.VIRTUAL` it may be null: rows fill through SetData) |
| `int [] applyChildren(Tree, TreeItem parent, long[] oldIds, long[] newIds, ObjLongConsumer<TreeItem> fill)` | the same for the children of `parent` (the roots when null) |

- `oldIds` must describe the current rows (length = item count); ids are unique within each array.
- The result is `{kept, removed, inserted}`: rows left in place, rows removed (dropped or moved), and rows inserted
  (new or moved).

## Contract

- After `apply`, the rows are in `newIds` order, and every row whose identity is in both arrays and whose relative order
  is kept is untouched: the same item object, selection, checked state and expansion.
- The kept rows are a longest increasing subsequence of the old positions taken in new order, so the number of
  remove/insert operations, `n_old - kept + n_new - kept`, is minimal for a widget whose only edits are remove and
  insert.
- Removals run in one `remove(int[])` call. Inserts run in ascending new index. The whole edit runs inside
  `M3BulkUpdates` (one repaint, at most one emptiness event).
- 10^5 rows reorder in well under a second; the diff itself is O(n log n).

## DOOP

- The id arrays are the only per-row input: a `long[]` index map, an `int[]` sequence, the LIS predecessor and tail
  lanes, and a kept bitset.
- No item object is made for a kept row.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv`: LIS by patience sorting (binary search over tails) against Myers diff and against
remove-all-and-reload.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` (sha256 `d3e26aff00a3...`); win32, GTK and Cocoa source sets 0 errors |
| JUnit, hidden desktop | `Test_M3DiffApply` 8/8 viewport, 8/8 original (one new test: a Table diff with insertions only) |
| differential against the official 3.135.0 jar | `Test_M3DiffApplyDifferential` (shared scene `M3OfficialScene`: a Table, List and Tree under seeded public operations, 8 seeds x 120 steps, plain and virtual): a diff step derives a new id order (drops, insertions, moves); the official run rebuilds with `setItems` / `removeAll` and new items, this SWT applies M3DiffApply; item texts and public state: 0 mismatches in viewport and original |
| coverage (JaCoCo 0.8.15, viewport and original merged) | lines 95/95; branches 74/80, the other 6 are `SWT.error` probe artifacts (one-condition checks with `assertThrows` tests): every reachable branch covered |

Open: GTK and Cocoa not run (no display host).
