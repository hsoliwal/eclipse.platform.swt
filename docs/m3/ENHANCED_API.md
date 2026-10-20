# Enhanced SWT API (M3): map and first leaves

Branch `m3/enhanced-api-20261017`, based on integration round 3 (`ff36ce3c51`). The map is
`docs/m3/ENHANCED_API_MAP.tsv`; the donor ledger of the shipped leaves is
`docs/m3/enhanced-api/FOSS_REUSE_DECISION.tsv`. The user's directive (2026-10-17): the donor links are
mined; expand them into an enhanced, additive API and include the first implementations; then "is there
any API you can add" from our own commit history, and from ecosystems beyond IntelliJ/SWT/Swing/SwingX.

## Binding rules

- **Additive and opt-in.** The stock SWT contract and behaviour do not change. Nothing here is reachable
  unless a client calls an `M3*` type. No stock file is modified by the first leaves.
- **M3 names.** Widget-bound API lives in `org.eclipse.swt.widgets.M3*` (public, M3-prefixed, so it has
  package access to the viewport lanes for fast paths); widget-free API (text, language) lives in
  `org.eclipse.swt.m3`. Never Skija/Skia names.
- **Mode-neutral contract.** Every M3 API answers identically in all five modes
  (`-Dswt.widget.mode=viewport|original|custom|m3|gpu`). The first implementation computes from public
  SWT state with integer arithmetic; a lane fast path may replace it only when the recorded answers stay
  equal (the leaf's JUnit test runs under `viewport` and `original`; that is the leaf's differential).
- **DOOP** (brief rule 18): results are ints, packed longs and int ranges; value objects
  (`M3ItemRange`, `M3ScrollInfo`, `M3TextSegments`) exist only because the caller asked and hold no
  state the lanes do not hold. No per-item allocation on any path that scales with the item count.
- **Java only** (rule 13); Synexia header on every new file (`SYNEXIA_HEADER.java.txt`); JUnit for every
  class with the 99% target (rule 15); no commit trailers.
- **Donor-first.** Every leaf has rows in `enhanced-api/FOSS_REUSE_DECISION.tsv` before code. GPLv2+CPE,
  LGPL and non-Java donors are patterns only; EPL/Apache/BSD rows may be adapted with the notice kept;
  first-party fork classes are reused as they are.

## Map columns (`ENHANCED_API_MAP.tsv`)

| column | meaning |
|---|---|
| `api_id` | stable id, `M3-<CAPABILITY>` |
| `family` | tree, table, list, grid, all, text, language, theme, perf |
| `source` | `first-party` (phase 1b: already in our viewport internals, never exposed by stock), `first-party-table` (candidates of the Table agent, `docs/m3/table-viewport/ENHANCED_API_CANDIDATES.tsv` on its branch at 55dd5b6c69; all four deduplicated into M3-BULK-UPDATES, M3-TYPE-AHEAD, M3-PREFETCH and M3-WIDTH-TRACK), `donor` (phase 1: `DONOR_CATALOGUE.tsv` keys), `cross-ecosystem` (phase 1c: non-Java patterns, rule 13) |
| `swt`, `nebula`, `rwt`, `intellij`, `swing_swingx`, `awt` | toolkit applicability (user 2026-10-18, "everything now has a viewport"): `Y` the same M3 surface over the toolkit's own widget (SWT Tree/Table/List; Nebula Grid/Gallery; RAP RWT Tree/Table; IntelliJ JBList/Tree/JBTable; Swing JList/JTree/JTable and SwingX JX*; AWT java.awt.List), `M` needs a mapping to a different widget kind, `-` no counterpart |
| `m3jdk_core` | `Y` when the lane logic is toolkit-neutral and moves into the shared M3JDK viewport core (the viewport-everywhere agent carries it); `-` for toolkit event wiring only |
| `donor_slugs` | catalogue keys, `HF:<hf_id>` rows of `HF_LANGUAGE.tsv`, or `CROSS:<ecosystem-feature>` |
| `donor_evidence` | the donor classes/methods or our classes that prove the mechanism |
| `proposed_m3_surface` | type and methods |
| `doop_data_plan` | lanes and the facade policy |
| `contract_tests` | what the JUnit test must assert |
| `priority` | P1..P4 by viewport relevance |
| `status` | `leaf-N-shipped` or `proposed` |
| `upstream_asks` | upstream issues and PRs the row answers, from the GitHub harvest (`docs/M3-SCALE/runs/20261017-ui-donor-atomize/GITHUB_PR_TOP30.md` on com.synexia `codex/m3-ui-donor-atomize-20261017`): `swt#624` SWT.VIRTUAL for Composite, `swt#882`/`swt#904` TreeItem.getItemCount cost, `swt#208` setItemCount freeze, `platform.ui#1093` family (viewer limits), `jdk#17165`/`jdk#27682` (Swing fixes); `-` when none is known |

Ranking: first-party rows outrank donor rows where they overlap (the mechanism exists and is measured);
the task's first three leaves are first-party exposures plus the language seed; the strongest
cross-ecosystem patterns (diffable snapshots, visibility observer, proxy models, scroll restoration) are
P2, directly behind them.

## Phase 1b: first-party capabilities (mined from our commit history)

`git log` of the Tree, Table, List, all-widgets, Grid and Swing branches and the classes under
`Eclipse SWT/common/org/eclipse/swt/widgets` and `internal/residency`:

| api_id | our mechanism | commit / class evidence |
|---|---|---|
| M3-RANGE-SELECT, M3-VISIBLE-RANGE | hybrid range/bitset selection, visible window | `VirtualSelectionModel`, `ViewportRuntime.Selection`, `VirtualScrollMetrics` |
| M3-SCROLL-INFO, M3-SCROLLBAR-GEOMETRY | logical scroll model owned by the viewport | `VirtualScrollMetrics`, `ViewportScrollBarGeometry`, `ViewportScrollLayout` |
| M3-ROW-MATH | O(log n) row/item mapping, subtree sizes, row extents | `VirtualTreeSiblingIndex`, `VirtualTreeTopology`, `VirtualTreeVisibleProjection`, `VirtualRowExtents`, PR #197 |
| M3-RESIDENCY, M3-OVERSCAN | resident window, materialized facades, pressure | `VirtualTreeResidentPlan`, `org.eclipse.swt.internal.residency.*`, `M3_ITEM_RESIDENCY.md` |
| M3-BULK-UPDATES | one diff-planned native pass; `setItemCount(10^6)` 4.9 ms | `ViewportBatch`, `BatchScope`, `VirtualRowWindowPlan`, commit 6359659c9a |
| M3-EMPTINESS | `SWT.EmptinessChanged` on n->0 and 0->n | commit 0904279df1, `TreeViewport` |
| M3-PREFETCH | SetData negotiated for the window only | `TreeViewport.checkData` calls, `LIST_MEDIATED_VIEWPORT.md` item 6 |
| M3-MODE-QUERY | startup mode switch | `WidgetMode` (commit cbc458de66) |
| M3-SNAPSHOT | recorded differential: state string + pixel CRC per step | `ViewportDifferential`, commit 8823cb2213 |
| M3-LOG | asynchronous structured logging | `SwtLog` |
| M3-THEME-HOOKS | look-and-feel seam LF1/LF2 | commit 6a1fe02b18, `LOOK_AND_FEEL.md` |
| M3-WIDTH-TRACK | width-defining row | `VirtualListWidth` |

## Phase 1c: cross-ecosystem patterns (non-Java; patterns only)

iOS UIKit (diffable data sources, prefetchDataSource, performBatchUpdates, swipe actions), Android
RecyclerView (RecycledViewPool, DiffUtil, ItemAnimator, SnapHelper, nested scrolling), web virtual
scrollers and DOM (overscan, scroll restoration, sticky rows, IntersectionObserver, scroll anchoring,
scroll snap), data grids (AG Grid pinning, grouping, server-side rows, flash, fill handle; the AG Grid
block cache is already in `docs/m3/swt-lazy-data-20261013-FOSS_REUSE_DECISION.tsv`), Flutter slivers,
Monaco/CodeMirror (decorations, view zones) and Qt (`QSortFilterProxyModel`). Rows: M3-DIFF-APPLY,
M3-VISIBILITY-OBSERVER, M3-PROXY-MODEL, M3-SCROLL-RESTORE, M3-STICKY-ROWS, M3-REUSE-POOL, M3-VIEW-ZONES,
M3-COLUMN-PIN, M3-ROW-GROUPING, M3-SNAP-SCROLL, M3-OVERSCAN, M3-ITEM-ANIMATOR, M3-ROW-ACTIONS,
M3-COLLAPSING-HEADER, M3-CELL-FLASH, M3-FILL-HANDLE, M3-NESTED-SCROLL, M3-SERVER-TREE.

## Leaves

| leaf | api_ids | types | design doc | status |
|---|---|---|---|---|
| 1 | M3-RANGE-SELECT, M3-VISIBLE-RANGE, M3-MODE-QUERY | `M3ItemRange`, `M3ItemSelection`, `M3Mode` | `enhanced-api/LEAF1_ITEM_RANGES.md` | green: Test_M3ItemRange 6/6, Test_M3ItemSelection 19/19, viewport and original |
| 2 | M3-SCROLL-INFO, M3-SCROLL-TO-ALIGNED | `M3ScrollInfo`, `M3Scrolling` | `enhanced-api/LEAF2_SCROLLING.md` | green: Test_M3Scrolling 11/11, viewport and original |
| 3 | M3-TEXT-SEGMENTS | `org.eclipse.swt.m3.M3TextSegments` | `enhanced-api/LEAF3_TEXT_SEGMENTS.md` | green: Test_M3TextSegments 8/8 (headless and on the hidden desktop) |
| 4 | M3-TOKEN-CURSOR | `org.eclipse.swt.m3.M3TokenVocabulary`, `M3TokenMatch`, `M3TextSegments.tokens` | `enhanced-api/LEAF4_TOKEN_CURSOR.md` | green: Test_M3TokenMatch 11/11 (headless and on the hidden desktop, viewport and original) |
| 5 | M3-EMPTINESS | `org.eclipse.swt.widgets.M3Emptiness`, win32 Tree/Table/List mutator seam | `enhanced-api/LEAF5_EMPTINESS.md` | code and Test_M3Emptiness committed; not built yet: builds parked 2026-10-19 (G: scratch volume dirty) |
| 6 | M3-ROW-MATH | `org.eclipse.swt.widgets.M3RowMath`, TreeViewport rank/select seam, gtk/cocoa stubs | `enhanced-api/LEAF6_ROW_MATH.md` | code and Test_M3RowMath committed; not built yet: builds parked 2026-10-19 (G: scratch volume dirty) |

Next leaves after these, in map order: M3-RESIDENCY,
M3-BULK-UPDATES, M3-PREFETCH, M3-DIFF-APPLY, M3-VISIBILITY-OBSERVER, M3-PROXY-MODEL,
M3-SCROLL-RESTORE, M3-LOCALE-COLLATION.

## Viewport events and part visibility (brief rule 25)

User, 2026-10-20: "would like to add viewport listener too?" and "you own the implementation, you are not a layer on
it". The viewport-change event and the visibility event are core SWT and M3JDK Swing API, fired by the widget
implementations themselves. They replace the enhanced-API add-ons that announced the same facts from outside
(leaves 9 and 11), so there is one mechanism.

| event | owner | API | when it fires |
|---|---|---|---|
| part visibility | all-widgets agent (`m3/allwidgets-viewport-20261016`, names published in `docs/m3/visibility/PART_VISIBLE.md`, 26ed0ce5cad) | `SWT.PartVisible` = 57, `SWT.PartHidden` = 58; `PartVisibilityListener` (`partVisible`, `partHidden`), `PartVisibilityAdapter`, `PartVisibilityEvent` (`cause`: `VISIBILITY`, `SIZE`, `MINIMIZE`, `REPARENT`, `CONTAINER`; `origin`); `Control.isPartVisible ()`, `setPartHidden`/`getPartHidden` | once per transition of the control's effective visibility: `setVisible` of it or an ancestor (tabs, stack pages, expand items), a size crossing zero, shell minimize/restore, `setParent`, a container's `setPartHidden`; pre-order, only to listening controls |
| viewport | all-widgets agent (event and `Control` plumbing); Tree, Table, List, Grid fire it from their viewports | `SWT.Viewport`, `ViewportListener.viewportChanged (ViewportEvent)`: first and last visible row, entered and left row ranges as int pairs, extents changed, cause mask `SCROLL`, `RESIZE`, `LAYOUT`, `MODEL_DELTA`, `VISIBILITY`, `EXPAND` | once per coalesced viewport change, never while hidden; the first event after a show comes with the `PartVisible` transition; no cost without a listener |
| Swing parallel | Swing agent (M3JDK `java.desktop`) | `javax.swing.event.ViewportListener` / `ViewportEvent` on `JComponent`, the same fields (names agreed with the all-widgets agent) | the same rules |

### The enhanced API on top of the events

| enhanced API | becomes | how |
|---|---|---|
| `M3VisibilityObserver` (leaf 11): `ENTERED`, `LEFT` ranges | `SWT.Viewport` | the entered and left ranges are the event's; the observer was never released and is withdrawn when the event lands (its latch, triggers and the "announced at the next trigger" gap for a programmatic `setTopIndex` go with it) |
| `M3VisibilityObserver` extents draft (`EXTENTS`, `HIDDEN`, `SHOWN`, `isViewportHidden`, `isViewportDirty`) | `ViewportEvent` extents bit, `SWT.PartHidden`/`PartVisible` | withdrawn on 2026-10-20 before release (6555e432494); the Swing draft too (M3jdk21 502e0cd08f6) |
| `M3Prefetch` (leaf 9): `PREFETCH`, `CANCEL` of the window widened by `setWindow (before, after)` | a listener of `SWT.Viewport` | keeps only the overscan arithmetic: the window's entered and left ranges are the event's ranges widened by the overscan and clamped to the rows (`M3Prefetch.difference`, shared with leaf 11) |
| `M3Residency` (leaf 7): resident range, facade counts | read-only views, plus release on `SWT.Viewport` left ranges | facades of rows that left the window and are not held are released by the widget's residency (parent-owned, lazy); the views keep answering for diagnostics |
| hidden-viewport deferral in `M3Model` (top row re-anchored at the show) | inside the widgets, on `PartHidden`/`PartVisible` | withdrawn from the enhanced API (6555e432494); the rule 24 gate (hide, 10^4 deltas, show: 0 native operations and 0 paints while hidden, one paint after) is the widget owners' |

The widget seams already sit where the event will be fired: `Table.setItemCount`, `Table.m3StructureChanged`,
`ListViewport` (window replan and scroll), `TableViewport.cacheHint`, `TreeViewport` window commit, and
`M3BulkUpdates.bulk`. Each calls `M3Prefetch.windowChanged` and `M3VisibilityObserver.changed` today; both calls
become one fire of `SWT.Viewport`.

### Examples (`org.eclipse.swt.snippets.m3`)

| example | pattern | the line that moves to `SWT.Viewport` |
|---|---|---|
| `M3LazyLoadingExample` | a `SWT.VIRTUAL` Table of 10^6 rows reads a slow store in pages of 100: entered rows request their pages, left rows withdraw the requests the store has not served, an arrived page clears only its visible rows | `M3VisibilityObserver.addListener (table, ...)` |
| `M3PrefetchReleaseExample` | a cache that holds the window and nothing else: blocks of 64 rows loaded on `PREFETCH`, released on `CANCEL`, overscan 256 rows each side; SetData never misses | `M3Prefetch.addListener (table, ...)` |
| `M3StickyGroupHeaderExample` | a pinned group header: 10^6 rows in groups of different sizes, the strip names the group of the first visible row (binary search over an `int` lane of group starts), redrawn only when the group changes | `M3VisibilityObserver.addListener (table, ...)` |
| `M3HiddenTabDeltasExample` | rule 24: 100,000 deltas into a table in a background tab, then one show; counts labels asked, paints and the top row | none: measured with stock means (a counting `SetData` source and `SWT.Paint` listener) |
| `M3DeltaStreamExample` | 100,000 one-row deltas into a visible table of 10^6 rows and a tree (leaf 19) | none |

Runs, 2026-10-20, hidden desktop, jar `ea20261019m`, both modes, scripted with `exit` (60 to 200 programmatic
`setTopIndex` steps): the three viewport examples received only the first announcement. Lazy loading: 1 page
requested and served, the visible rows of later steps never loaded. Prefetch: 0 blocks prefetched, 4,325 SetData
misses. Sticky header: 1 viewport change, the strip named another group than the first visible row's in 116 of 120
steps. Cause: a `SWT.VIRTUAL` Table has no `TableViewport` (`Table.java`: the viewport is made only when
`(style & SWT.VIRTUAL) == 0`), so nothing announces a programmatic scroll: `M3Prefetch` fires only through a
viewport, and the observer waits for the next scroll-bar, wheel, key or resize trigger. Interactive use (wheel, keys,
scroll bar) drives all three. This is the gap `SWT.Viewport` closes: it must fire for programmatic scrolls and for
`SWT.VIRTUAL` tables. The hidden-tab run with 100,000 deltas hit the 1,700 s cap without output; a 10,000-delta
run is queued. The Swing twins of the examples follow the Swing `ViewportListener`.

## Test method

Each leaf has one JUnit class under `tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/`.
Widget tests run on the hidden desktop (`ui_lock.sh` + `hidden_java.sh`) twice: once with the default
`viewport` mode and once with `-Dswt.widget.mode=original`; both must pass with the same assertions
(the mode differential of the leaf). The text leaf runs headless. Every assertion compares the M3 answer
with a public-API oracle (`getSelectionIndices`, `getBounds`, `getTopIndex`, `java.text.BreakIterator`).

## Runs

| date | jar | sources | run | result |
|---|---|---|---|---|
| 2026-10-18 | `ta20261017a` (C:/m3-superset/tva) | leaves 1-3 at c1b4e589e9 (before the round-6 merge; the merge touches none of the M3 files) | hidden desktop, `-Dswt.widget.mode=viewport`, the four classes | 40/40 |
| 2026-10-18 | same | same | hidden desktop, `-Dswt.widget.mode=original` | 40/40 |
| 2026-10-18 | same | same | headless, Test_M3TextSegments + Test_M3ItemRange | 14/14 |
| 2026-10-18 | `ta20261017a` + branch M3 classes ahead on the class path | coverage fixes (branch-light row arithmetic, walk counts rows when the target is absent) and 4 more tests | hidden desktop under JaCoCo, viewport and original | 44/44 and 44/44 |
| 2026-10-18 | `ta20261017a` rebuilt from 80d1091718 (round-6 merge included) | branch head | hidden desktop, viewport and original, no patch dir | 44/44 and 44/44 |
| 2026-10-18 | `ta20261017a` (80d1091718) + branch M3 classes ahead on the class path | leaf 4 and round-7 merge (no M3 file touched by the merge) | hidden desktop under JaCoCo, viewport and original, five classes | 55/55 and 55/55 |
| 2026-10-19 | none | leaves 5-6 (win32 Tree/Table/List/TreeViewport seams, M3Emptiness, M3RowMath) | not run: build output moved from C:/m3-superset/tva to G:/m3-scratch/enhanced-api as directed, then G: (exFAT) was found dirty; three full builds there failed on filesystem errors (no compile error was reported), and the coordinator parked all builds until a new output location | pending |

Coverage: `enhanced-api/coverage/COVERAGE.md` (lines 100%, branches 94.2% measured; the 19 missed branches are JaCoCo probe artifacts of SWT.error checks, each exercised by an assertThrows test).

The round-6 jar needed four javac attempts: three crashed with native out-of-memory during machine-wide memory
storms, and the logs are gzipped in C:/m3-superset/tva/logs.
