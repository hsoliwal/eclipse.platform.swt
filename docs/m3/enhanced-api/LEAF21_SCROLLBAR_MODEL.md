# Leaf 21: the scroll bar model as an int lane (M3ScrollBarModel)

Map row M3-SCROLLBAR-GEOMETRY. The viewports own a logical scroll bar (`VirtualScrollMetrics`,
`ViewportScrollBarGeometry`, `ViewportScrollLayout`): a table of millions of rows has a logical maximum, and the
H/V visibility is solved to a fixed point (PR #31). Clients read it through six getters and two booleans per bar.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | meaning |
|---|---|
| `int [] get (Scrollable, int orientation)` | a new lane: `MINIMUM`, `MAXIMUM`, `THUMB`, `INCREMENT`, `PAGE_INCREMENT`, `SELECTION`, `VISIBLE` (0/1), `ENABLED` (0/1); `null` without such a bar |
| `boolean get (Scrollable, int orientation, int [] lane)` | fills a caller-owned lane (at least `SIZE` long), allocation-free; false (lane untouched) without such a bar |

## Contract (tested in `Test_M3ScrollBarModel`, `Test_M3ScrollBarModelDifferential`)

- The lane equals the scroll bar's getters for Table, virtual Tree, List, multi-line Text and ScrolledComposite, in
  every startup mode; `null` / false for a widget without the bar; a lane longer than `SIZE` keeps its tail.
- Against the official 3.135.0 jar: the vertical bars of a Table, List and Tree under the shared seeded operations equal
  the stock getters' answers (this checks the viewport's logical bars, through the lane, against stock).

## Not in this leaf

`setSelection` for the drawn (pixel) modes and a solver probe (H/V oscillation counter): the drawn modes' scrolling
belongs to render-modes, and the fixed point is ViewportScrollLayout's own contract (PR #31).

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` plus this leaf's class; win32, GTK and Cocoa source sets 0 errors |
| JUnit, hidden desktop | `Test_M3ScrollBarModel` 3/3 viewport, 3/3 original |
| differential against the official 3.135.0 jar | `Test_M3ScrollBarModelDifferential` (shared `M3OfficialScene`, 8 seeds x 120 steps, plain and virtual): original mode 0 mismatches. Viewport mode differs in hidden-bar values (VISIBLE 0 in both runs): a virtual Tree whose 13 rows fit reports maximum 12 and thumb 12 where stock reports 0 and 0 (virtual seed 0 step 10), and a List with one item reports maximum 100 where stock reports 0 (plain seed 0 step 4). The lane equals the getters by construction, so these are the viewports' logical bars differing from stock while hidden (owners: the Tree and List viewport scroll geometry); open |
| coverage (JaCoCo 0.8.15, both modes) | lines 19/19; branches 16/20, the other 4 are `SWT.error` probe artifacts: every reachable branch covered (after the disabled-bar check; 3/3 in both modes again) |

Open: the viewport hidden-bar differences above; GTK and Cocoa not run.

Update (2026-10-31): the viewport differences were the Tree and List viewports' hidden-bar emulation, fixed by PR #298 (TreeViewport: rows that fit read 0) and PR #299 (ListViewport: the default bar 0..100 only with SWT.H_SCROLL, measured on the official jar). With both, the official-jar differential of this leaf has 0 mismatches in viewport and original mode (plain and virtual).
