# Leaf 6: visible-row arithmetic of a Tree (M3RowMath)

Map row M3-ROW-MATH. Stock SWT has no row coordinates for a Tree. `getItem(Point)`, `getTopItem` and `showItem` work
in pixels or items, and an application that needs "row 12,345 of the expanded tree" walks `getItems()` recursively. The
viewport already owns the lanes that answer this. `VirtualTreeTopology` holds the per-parent visible extra-row sums,
and `VirtualTreeVisibleProjection` provides `rowAt` (select) and `visibleIndexOf` (rank) in O(depth * log k).

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| method | answer |
|---|---|
| `long visibleRowCount(Tree)` | rows the tree shows when every row is on screen: roots, plus the children of expanded items, recursively |
| `long rowOf(TreeItem)` | the item's visible row, or -1 when an ancestor is collapsed |
| `TreeItem itemAt(Tree, long row)` | the item at a visible row; ERROR_INVALID_RANGE outside `[0, visibleRowCount)` |
| `long subtreeRowCount(TreeItem)` | rows the item and its visible descendants take: 1 when collapsed |
| `long rowExtent(Tree, long row)` | pixel offset of the row's top from the top of row 0 (`row * getItemHeight()`; row may equal the count) |
| `long rowAtExtent(Tree, long y)` | the row at a pixel offset, or -1 outside the rows |

Rows are `long`, like the projection: a virtual tree's logical cardinality is not bounded by one level's int count.

## Contract

- `rowOf(itemAt(r)) == r` for every visible row.
- `itemAt(rowOf(item)) == item` for every visible item.
- `visibleRowCount`, `rowOf` and `subtreeRowCount` agree with a recursive public-API walk (`getItems`, `getExpanded`)
  after expand, collapse, insert, dispose, `setItemCount` and `removeAll`.
- `rowExtent` and `rowAtExtent` are inverse on row tops, and agree with `TreeItem.getBounds().y` relative to the top
  item for every row on screen.
- The answers are the same in every startup mode.
- Cost: with the tree viewport (SWT.VIRTUAL in viewport mode) the answers come from the lanes in O(depth * log k), with
  no walk and no facade per row; `itemAt` makes or reuses exactly one facade and pins it like `getItem`. Without the
  viewport, one preorder walk over the public API (M3TreeWalk), O(rows above the target).

## DOOP and seam

- No new state.
- The win32 `TreeViewport` gets four read-only methods over its existing lanes: `visibleRowOf`, `visibleItem`,
  `subtreeRows`, plus `virtualVisibleRowCount` on Tree.
- Each platform's `Tree` gets package-private `m3RowOf`, `m3ItemAt`, `m3VisibleRowCount` and `m3SubtreeRowCount`. On
  win32 they delegate to the viewport and answer `M3RowMath.UNKNOWN` without one. On GTK and Cocoa they are stubs that
  always answer UNKNOWN, so the common walk serves those platforms.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv` (rule 21): rank/select over per-parent visible-row prefix sums (the order-statistic
lanes the viewport keeps), with an iterative preorder walk as the fallback.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` (sha256 `d3e26aff00a3...`); win32 888, GTK 678, Cocoa 817 sources, 0 errors |
| JUnit, hidden desktop | `Test_M3RowMath` 7/7 (viewport); 6/7 and 1 skipped (original: `test_millionRowVirtualTreeAnswersFromTheLanes` assumes the lanes, which exist only with the viewport) |
| differential against the official 3.135.0 jar | `Test_M3RowMathDifferential`: seeded scripts (add, expand, collapse, dispose, `setItemCount` on the tree and on rows, `removeAll`, `clearAll`) on a plain and a virtual Tree, 8 seeds x 120 steps each; the official run records a preorder walk over the public API (visible row count, items at four rows, row and subtree size of the first six roots and of each one's first child, extent arithmetic); viewport and original: 0 mismatches (the virtual tree in viewport mode answers from the lanes) |
| coverage (JaCoCo 0.8.15, viewport and original merged) | lines 38/38; branches 33/38: 4 are `SWT.error` probe artifacts (one-condition checks, each exercised by an `assertThrows` test), 1 is the walk's "item not found" return in `rowOf`, unreachable once every ancestor is expanded (the walk then visits the item): effective 100% of reachable branches |

The differential found one oracle error of its own on the first run (Java integer division answered `rowAtExtent (-1)`
as row 0 in the walk oracle); M3RowMath was right (-1), and the oracle was corrected.

Open: GTK and Cocoa answer through the walk (no lanes there; their virtual trees have a topology on GTK that a later
seam can read); not run on GTK or Cocoa.
