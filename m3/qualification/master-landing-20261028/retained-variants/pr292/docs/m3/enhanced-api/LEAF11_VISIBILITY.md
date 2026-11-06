# Leaf 11: visible-range observer (M3VisibilityObserver)

Map row M3-VISIBILITY-OBSERVER (IntersectionObserver, Nebula Grid `GridVisibleRangeSupport`, RecyclerView attach and
detach). M3Prefetch (leaf 9) reports the rows a widget holds. This leaf reports the rows the user can see, for
impressions, lazy decorations, and starting and stopping per-row animations.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | meaning |
|---|---|
| `M3VisibilityObserver.EVENT` (`0x4D330003`) | event type; `detail` is `ENTERED` (0) or `LEFT` (1); `start` and `end` are one range (inclusive; Tree: `M3RowMath` visible rows) |
| `addListener` / `removeListener(Table / List / Tree, Listener)` | the first listener hears the current visible range as `ENTERED` |

The visible range is `M3ItemSelection.getVisibleRange`: the rows that intersect the client area.

## Contract

- After each change the listeners have received `LEFT` for old minus new, then `ENTERED` for new minus old. Each is
  at most two ranges, and together they partition the change.
- No event is sent when the range is unchanged.
- Triggers:
  - scrolling (scroll bar, wheel, keyboard and `setTopIndex` / `setTopItem` in the viewport mode);
  - resize;
  - expand and collapse;
  - structural changes.
  Each settles to one pair of announcements. The check runs once at the end of the triggering event (coalesced with
  `asyncExec`), and immediately at the viewport window commits.
- Ranges are delivered, never items.
- In the original mode, programmatic `setTopIndex` without a following paint or event is announced at the next
  trigger. This is stated in the Javadoc.

## DOOP and seam

- One latch per listened widget (the last announced range plus a scheduled flag) in keyed data.
- Listeners are kept in the event table.
- The viewport window seams of leaf 9 (`TreeViewport.ensureWindow`, `ListViewport.layout` and `rebuild`,
  `TableViewport.cacheHint`, and the Table structural seam) also call the observer.
- Common listeners for Resize, Expand, Collapse and the vertical scroll bar Selection are installed only while a
  listener exists.

## Algorithm choices

The interval difference of leaf 9 is reused, so `ALGORITHM_CATALOGUE.tsv` gets no new row.

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398`, jar `master-d5f6048` plus this leaf's classes; win32, GTK and Cocoa source sets 0 errors |
| JUnit, hidden desktop | `Test_M3VisibilityObserver` 9/9 viewport, 9/9 original (three new tests: a list without a scroll bar and a second listener, coalesced triggers with a dispose before the check, a listener added without the API) |
| differential against the official 3.135.0 jar | `Test_M3VisibilityObserverDifferential` (shared `M3OfficialScene`, 8 seeds x 120 steps, plain and virtual): the official run records the rows of the Table and List whose bounds meet the client area; this SWT records the rows replayed from ENTERED/LEFT after the event loop settles. First run: 3 failures - a programmatic `setTopIndex` on a virtual Table (viewport) and on a List (original), and a List `add` (original), were never announced (the documented "announced at the next scroll" gap). Fixed: the latch also triggers on `SWT.Paint` (a change that shows paints), and `List.setTopIndex`, `Table.setTopIndex` and `Tree.setTopItem` check when they return on win32, GTK and Cocoa (text-lane recipe `tools/m3_observer_seams.sh`, `m3_seam_wrap.awk` KIND=observe; second run unchanged). After the fix: 0 mismatches in viewport and original |
| coverage (JaCoCo 0.8.15, both modes) | lines 62/62; branches 43/46: 1 `SWT.error` probe artifact and 2 `isDisposed ()` edges behind the latch's own listeners and `hooks (EVENT)` (a disposed widget has neither): every reachable branch covered |

Open: GTK and Cocoa not run.
