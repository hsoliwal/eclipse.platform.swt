# Leaf 18: content widths (M3WidthTrack) over measured text widths (M3TextMeasure)

Map rows M3-WIDTH-TRACK and M3-SHAPING-MEASURE: one leaf, because both are the same mechanism (a width measured once
per text and font, then read from int lanes). An application that sizes a column or a side panel to its content calls
`TableColumn.pack` or measures every item with a `GC`. Both cost O(rows) GC measurements on every call, and they repeat
the work after every edit.

## Surface (public, `@since 3.138`)

`org.eclipse.swt.m3.M3TextMeasure` (widget-free; row M3-SHAPING-MEASURE):

| member | answer |
|---|---|
| `M3TextMeasure (Drawable [, capacity])` | a measure for a control or image, holding at most `capacity` entries (default 65,536) |
| `int width (String, Font)` | `GC.textExtent (text).x` with the font (`null`: the drawable's GC font), measured once per text and font |
| `int [] widths (String [], Font)` | the widths of many texts, one GC for all misses |
| `forget (String, Font)`, `invalidate (Font)` | forget one entry; forget a font's entries (`null`: all) |
| `release ()` | dispose the GC opened by the first miss |
| `long [] stats ()` | `MEASURED`, `HITS`, `ENTRIES` |

`org.eclipse.swt.widgets.M3WidthTrack` (row M3-WIDTH-TRACK):

| member | answer |
|---|---|
| `int contentWidth (List)` / `contentWidth (Table, int column)` | the widest item (cell) text width, each cell with `TableItem.getFont (column)` |
| `int widestRow (List)` / `widestRow (Table, int column)` | the first row with that width, -1 without rows |
| `invalidate (List or Table, M3ItemRange)` | forget the widths of the rows' current texts (owner-drawn content changed outside the item setters) |
| `long [] stats (Control)` | the widget's measuring counts |

## Contract (tested in `Test_M3WidthTrack`)

- `contentWidth` equals the maximum over all rows of `GC.textExtent` on a fresh GC with the row's font, in every
  startup mode; `widestRow` is the first row with that width; empty widgets answer 0 and -1.
- A second call without edits measures nothing. After an insert, only the new text is measured; narrowing the widest
  row costs one measurement and the next widest row defines the width.
- Table cells are measured with their own font (cell font, item font, table font); a virtual table is asked for every
  row (as by `getItems`).
- `M3TextMeasure`: widths equal `GC.textExtent`; a repeat (also of an equal but not identical string) is a hit;
  forgetting 700 random entries of 2,000 keeps every other entry reachable (backward-shift deletion); the table never
  holds more than its capacity.

## DOOP

One `M3TextMeasure` per widget, as keyed data, created by the first question. Its table is three parallel lanes
(`String`, `Font`, `int`) in open addressing at load at most 1/2: no entry objects, no boxing. The strings are the
widget's own (List items, cell texts); the measure holds references, never copies. A scan keeps the best row and width
in one packed `long`.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv` (rule 21): open addressing with backward-shift deletion for the width table, and a
running maximum over cached widths for the widest row. A segment tree or heap over row widths was rejected: it needs an
edit notification from every item setter on three platforms for a rare question (pack, layout).

## Donors

`FOSS_REUSE_DECISION.tsv` rows `width-track-api` (VirtualListWidth and TableViewport.widestRow first-party; IntelliJ
EditorSizeManager width-defining line as pattern) and `text-measure-api` (NetBeans TextLayoutCache pattern, Caffeine
TinyLFU rejected for the hot path, HarfBuzz pattern only: the platform GC shapes).

## Not in this leaf

- The fast path over the viewport text lanes (`TableViewport` cells, `VirtualListWidth`): the public scan asks the
  item API, so in viewport mode a Table makes a facade per row on the first question (thin, parent-owned).
- `M3WidthTrack.pack (TableColumn)`: `TableColumn.setWidth (contentWidth + insets)` is one line in the client; the
  native autosize insets differ per platform and theme, so the leaf does not guess them.
- Script-run keys and locale-specific shaping (HarfBuzz pattern): the GC shapes; widths are keyed by text and font.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` plus the two classes; win32, GTK and Cocoa source sets 0 errors (`-Xlint:all` clean) |
| JUnit, hidden desktop | `Test_M3WidthTrack` 9/9 viewport, 9/9 original |
| differential against the official 3.135.0 jar | `Test_M3WidthTrackDifferential` (shared `M3OfficialScene`, 8 seeds x 120 steps, plain and virtual): the official run records the widest row and width of the Table and List from a fresh `GC.textExtent` scan after every step; this SWT records M3WidthTrack, which reuses widths measured in earlier steps: 0 mismatches in viewport and original |
| coverage (JaCoCo 0.8.15, both modes) | M3TextMeasure lines 91/91, branches 44/50; M3WidthTrack lines 48/48, branches 27/28; every miss is an `SWT.error` probe artifact of a one-condition check with an `assertThrows` test: every reachable branch covered |
| example | `examples/.../snippets/m3/M3WidthTrackExample.java` (20,000 rows; `exit` argument for a scripted run) |
