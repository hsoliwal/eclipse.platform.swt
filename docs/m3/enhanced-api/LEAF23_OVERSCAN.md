# Leaf 23: overscan (M3Overscan)

Map row M3-OVERSCAN. With the mode-neutral residency (LEAF7, 2026-10-31) the resident rows are the rows in view in
every mode. `M3Overscan` lets an application ask for rows beyond them: the resident range becomes the rows in view
widened by the overscan and clamped to the rows, and `M3Prefetch` announces that window (widened again by its own
margins). It ties M3-RESIDENCY and M3-PREFETCH together, as the map row says.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | answer |
|---|---|
| `void set(Table / List / Tree, int rowsBefore, int rowsAfter)` | the overscan (a Tree in visible rows, as `M3RowMath`); checks the prefetch window at once |
| `int[] get(Table / List / Tree)` | `{rowsBefore, rowsAfter}`; `{0, 0}` by default |

## Contract

- `M3Residency.residentRange` == the rows in view widened by the overscan, clamped to `[0, rows)`; empty when nothing
  is in view or the widget is not visible. The same in every widget mode (computed from public state).
- Setting the overscan announces the rows that entered (PREFETCH) or left (CANCEL) the window.

## DOOP, donors, algorithm

One packed `long` per widget (widget data; none when both counts are 0). Donors (`FOSS_REUSE_DECISION.tsv`): TanStack
Virtual's `defaultRangeExtractor` (`max (start - overscan, 0)` .. `min (end + overscan, count - 1)`, MIT, TypeScript:
pattern only) and the Tree viewport's resident-plan margins (first-party). The arithmetic is an interval widen and clamp.

## Not in this leaf

Feeding the overscan into the viewports' own planners (so a viewport keeps exactly these rows natively) is a fast path
the contract does not need; it stays open. `M3ResidentWindow` (PR #295, superset branch) projects the same window and
can replace the arithmetic once it reaches master.
