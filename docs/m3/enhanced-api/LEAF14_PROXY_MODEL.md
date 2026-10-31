# Leaf 14: proxy model (M3ProxyModel)

Map row M3-PROXY-MODEL. Prior art: Qt `QSortFilterProxyModel`, Glazed Lists `FilteredList(SortedList(source))`, and
SwingX `RowFilter`/`RowSorter`.

A virtual widget shows a sorted or filtered view of the application's rows when every SetData request is answered
through a mapping from view rows to source rows. Applications now rebuild arrays of objects for this. The proxy keeps
the mapping as int lanes.

## Surface (package `org.eclipse.swt.m3`, public, `@since 3.138`)

| member | meaning |
|---|---|
| `M3ProxyModel(int sourceSize)` | a model over source rows `0 .. sourceSize-1` with no stage (identity) |
| `addFilter(IntPredicate keep)` | appends a filter stage; `addSort(RowComparator)` and `addSort(M3SortKeys)` append a stable sort stage |
| `clearStages()`, `setSourceSize(int)` | edit the model; nothing is recomputed until `refresh()` |
| `refresh()` | recomputes the stages in order |
| `size()` | the view rows |
| `map(int row) -> sourceRow` | view row to source row |
| `unmap(int sourceRow) -> row` | source row to view row, -1 when filtered out |
| `bind(Table, RowFill<TableItem>)` / `bind(List, IntFunction<String>)` | drives a virtual Table (SetData) or a List (items) from the model; call again after `refresh()` |

## Contract

- `map(unmap(s)) == s` for every kept source row, and `unmap(map(r)) == r` for every view row.
- `size()` equals the number of source rows that pass every filter.
- Sort stages are stable. A chain of stages equals their composition: filter then sort equals the stable sort of the
  filtered rows.
- After `bind`, SetData on view row r fills the item from source row `map(r)`. Items are never asked for rows the
  filters removed.
- The core is headless.

## DOOP

- One `int[]` permutation lane (view to source), one `int[]` inverse lane (source to view or -1), and the stage list.
- Sorting is a merge sort of int lanes with no boxing. Filters write the permutation in place.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv`: a permutation lane plus inverse lane against object lists.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` (sha256 `d3e26aff00a3...`); win32, GTK and Cocoa source sets 0 errors |
| JUnit, hidden desktop | `Test_M3ProxyModel` 8/8 viewport, 8/8 original (one new test: a stale `SWT.SetData` request beyond the view is ignored) |
| differential against the official 3.135.0 jar | `Test_M3ProxyModelDifferential` (8 seeds x 120 steps): a virtual Table shows a filtered and sorted view of a source; the official run computes the view with boxed streams and drives the table with `setItemCount`, `clearAll` and its own `SWT.SetData` listener, this SWT binds an M3ProxyModel; view size, top index and the first rows' texts: 0 mismatches in viewport and original |
| coverage (JaCoCo 0.8.15, both modes) | lines 75/75; branches 57/62, the other 5 are `SWT.error` probe artifacts: every reachable branch covered |

Open: GTK and Cocoa not run.
