# Leaf 18 (designed, not started): content width tracking (M3WidthTrack)

Map row M3-WIDTH-TRACK. An application that sizes a column or a side panel to its content calls `TableColumn.pack`
or measures every item with a `GC`. Both cost O(rows) on every call, and they repeat the work after every edit. The
viewports already keep the answer incrementally:

- The List viewport keeps `VirtualListWidth` inside `VirtualListModel`. Widths are measured lazily, once per text or
  font change, and the maximum is maintained; it becomes stale and is rescanned only when the widest row narrows. The
  List viewport writes it to `LB_SETHORIZONTALEXTENT`.
- The Table viewport keeps a second `VirtualListWidth` for the column-less scroll width (`TableViewport.widths`), and
  `widestRow(column, count)` for `TableColumn.pack`.

## Proposed surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| method | answer |
|---|---|
| `int contentWidth(List)` | the widest item's text width in pixels (the horizontal extent before insets) |
| `int contentWidth(Table, int column)` | the widest cell text width of the column, with the cell's font |
| `int widestRow(List)` / `widestRow(Table, int column)` | the row that defines it, -1 without rows |
| `void invalidate(Table, M3ItemRange)` | forget the measured widths of the rows (owner-drawn content changed outside the item setters) |

## Contract (to be tested)

- `contentWidth` equals the maximum over all rows of a `GC.textExtent` measurement with the item font, in every startup
  mode. Without the viewport it is computed by that scan.
- A second call without edits measures nothing. Narrowing the widest row triggers exactly one rescan of the width lane
  (counted through a package-private probe).
- `widestRow` names a row whose width equals `contentWidth`.

## Seams and owners

- The seams are owned by the list and table viewport agents: `VirtualListWidth` would gain an argmax lane (the index of
  the max, kept with `maxCount`).
- `ListViewport` and `TableViewport` would answer `m3ContentWidth` / `m3WidestRow`.
- This leaf adds only the public class, the stock scans and the tests. It starts once those owners agree on the
  argmax lane.

## Algorithm

A running maximum with a stale flag and one rescan on narrowing (amortized O(1) per edit), already in
`VirtualListWidth`. A max-heap or segment tree over the widths was rejected: it adds O(log n) work to every edit for a
query that is rare (pack, layout).
