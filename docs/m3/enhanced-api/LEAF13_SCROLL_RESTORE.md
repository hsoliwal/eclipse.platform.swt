# Leaf 13: scroll memento (M3ScrollMemento)

Map row M3-SCROLL-RESTORE (history.scrollRestoration, VirtualFlow's (index, offset) anchor). After a reload or a
structural change the application wants the user to see the same rows again. Stock SWT offers
`getTopIndex`/`setTopIndex`, but the index goes stale as soon as rows are inserted or removed above it, and a Tree has
no row index at all.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| method | meaning |
|---|---|
| `long save(Table / List / Tree)` | packs the anchor (the top row; the visible row of `M3RowMath` for a Tree) into one long |
| `long remap(long memento, int index, int delta)` | moves the anchor through an edit: `delta > 0` rows inserted at `index`, `delta < 0` rows removed from `index` on |
| `boolean restore(Table / List / Tree, long memento)` | scrolls back to the anchor (clamped to the rows); false, with nothing changed, for a value that is not a memento |

## Contract

- `restore(save())` shows the same top row. The horizontal position is not part of the memento: the native controls scroll
  horizontally only through their own messages, which a scroll bar `setSelection` does not send.
- With `remap` applied for each edit, the same row is on top after inserts and removals above it.
- A removed anchor yields the row that took its place.
- Only `save` creates mementos. `restore` rejects any other long (a tag byte), including 0 and -1, and changes nothing.
- The answers are the same in every startup mode.

## DOOP

- One `long`: a 7-bit tag, 31 bits of row, and 24 reserved zero bits (bit 63 clear). A long whose tag or reserved bits differ
  is not a memento.
- No object and no listener: remapping is arithmetic on the caller's edit counts.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv`: bit packing of the anchor and an index shift under insert/remove.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` (sha256 `d3e26aff00a3...`); win32, GTK and Cocoa source sets 0 errors |
| JUnit, hidden desktop | `Test_M3ScrollMemento` 8/8 viewport, 8/8 original (one new test: a removal or insertion below the anchor keeps it) |
| differential against the official 3.135.0 jar | `Test_M3ScrollMementoDifferential` (shared `M3OfficialScene`, 8 seeds x 120 steps, plain and virtual): memento steps save the top row of the Table or List, insert or remove rows above or below it, scroll to the top and restore; the official run computes the anchor by arithmetic and calls `setTopIndex`, this SWT uses `save`, `remap`, `restore`; top index and public state: 0 mismatches in viewport and original |
| coverage (JaCoCo 0.8.15, both modes) | lines 40/40; branches 32/34, the other 2 are `SWT.error` probe artifacts: every reachable branch covered |

Open: GTK and Cocoa not run.
