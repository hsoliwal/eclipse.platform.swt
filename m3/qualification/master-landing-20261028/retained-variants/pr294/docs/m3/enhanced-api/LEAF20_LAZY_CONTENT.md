# Leaf 20: lazy content from a source of ids (M3LazyContent)

Map row M3-LAZY-CONTENT. A virtual Tree or Table needs a `SWT.SetData` listener that finds the row's data, sets its
texts and, in a tree, its child count; every application writes that listener again, and JFace's
`ILazyTreeContentProvider` does it at the viewer level with element objects. The upstream asks (swt#882, swt#904,
platform.ui#1093 and its family) come from code that asks child counts and items eagerly.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | meaning |
|---|---|
| `M3LazyContent.Source` | `childCount (long id)`, `childId (long parentId, int index)`, `text (long id, int column)`; `ROOT` (-1) is the invisible root |
| `install (Tree or Table, Source)` | binds the source to a widget created with `SWT.VIRTUAL`: the top-level count now, everything else on request; a second install replaces the first and empties the widget |
| `refresh (Tree, TreeItem or null)`, `refresh (Table)` | asks the structure below a row (or the top level) again: the count now, the children when shown |
| `idOf (TreeItem or TableItem)` | the id of a filled row, `ROOT` for a row no source filled |
| `uninstall (Control)` | removes the source; rows keep what they show |
| `stats (Control)` | `CHILD_COUNTS`, `CHILD_IDS`, `FILLS` |

## Contract (tested in `Test_M3LazyContent`)

- `install` asks one child count (the top level). A `SWT.SetData` request for a row asks its id, one text per column
  and, in a tree, its child count; nothing else.
- With 10^6 top-level rows (2 x 10^4 in the original mode, where the stock tree inserts every native row), the first
  paint fills fewer than 200 rows, and a jump to the middle fills the new window only (fewer than 600 fills in all).
- Ids and texts follow the source exactly (the oracle is the source); `refresh` re-asks the count below a row once and
  re-fills its children; a replaced or removed source is never asked again.
- The answers are the same in every startup mode.

## DOOP

The binding holds the source and three counters. A filled row keeps its id as item data (one `Long` per filled row,
that is per row the widget has shown); no row object is made for a row that was never shown, and the widget's own
virtual machinery (the tree viewport's lanes in viewport mode) keeps everything else.

## Donors and algorithm

`FOSS_REUSE_DECISION.tsv` rows `lazy-content-api` (x86core LazyTreeViewer and JFace ILazyTreeContentProvider,
RAP flat-index window, HugeJsonViewer flat parent table: patterns); `ALGORITHM_CATALOGUE.tsv` row
`lazy-evaluation+memo`.

## Not in this leaf

Images and per-cell colours from the source, and async loading with placeholder rows (row M3-SERVER-TREE).

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` plus the class; win32, GTK and Cocoa source sets 0 errors (`-Xlint:all` clean) |
| JUnit, hidden desktop | `Test_M3LazyContent` 5/5 viewport, 5/5 original |
| differential against the official 3.135.0 jar | `Test_M3LazyContentDifferential` (8 seeds x 120 steps): a virtual Tree and Table over a synthetic source; the official run uses the stock idiom (a hand-written `SWT.SetData` listener, ids as item data, `setItemCount` + `clearAll` on change), this SWT installs M3LazyContent and calls `refresh`; expand, collapse, scroll and source-change steps; texts of the rows on screen and counts: 0 mismatches in viewport and original |
| coverage (JaCoCo 0.8.15, both modes) | lines 48/48; every branch miss is an `SWT.error` probe artifact of a one-condition check with an `assertThrows` test: every reachable branch covered |
| example | `examples/.../snippets/m3/M3LazyContentExample.java` (10^6 folders of 50 files; `exit` argument for a scripted run) |
