<!-- SPDX-License-Identifier: EPL-2.0 -->
# M3 superset build 2026-11-09 (SWT win32 x86_64): release notes

Release candidate `m3-superset-20261109`. It is unsigned, published only as a draft release, and has no tag.

- Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261109.jar`, SHA-256 `6073f2dad2752363972335fe0e36efa5643ed07d124a5ebad8b8e672916d5795`
- Built from branch `m3/release-candidate-20261109` (PR #335) at product source commit `55ccfce4799725b322a9a72ef47bdfbf78d4e698`
- Base: master `4fb49875cc` (#334, which includes #327-#334). Forwarded on top: #325, which also carries #323.
- Re-landed: #183, #190 + #220, #221 and #239. These PRs were merged into other PR branches and never reached master. Each was a clean 3-way fold, and api_loss_audit reported 0 lost declarations. See `STACKED_NOT_ON_MASTER.tsv`.
- Release fixes:
  - Test_Display now starts on a thread with no Display.
  - The M3 differential tests release their Display.
  - The SVG fragment, M3 backend and Skija are on the gate classpath.
  - The test bundle declares its Skija imports.
  - The gpu.m3 module's Skija pins now match the version its sources compile against (0.116.3; the module did not build before).
  - Three stale tests are fixed, and the VirtualPageSource fixture is fixed.
  - Tree page-source rows stay facade-free when painted (win32).
  - All 330 test classes are reachable from AllTests.
- OSGi identity: `org.eclipse.swt.win32.win32.x86_64` `3.138.0.m3-superset-20261109`. The fragment host, BREE `JavaSE-21` and `Export-Package` are taken unchanged from the fork manifest.
- Natives: the fork's own `binaries/org.eclipse.swt.win32.win32.x86_64` (4975r19) and `WebView2Loader.dll`.
- `META-INF/m3/MAPPING.tsv` lists every shipped entry with its source path, git blob, origin and SHA-256. `META-INF/m3/LEDGER.tsv` has one row per PR merged since 2026-10-13, classified against the candidate.

## Gate summary

Full table: [CHECKLIST.md](CHECKLIST.md).

| # | Gate | Status |
| --- | --- | --- |
| 1 | #325 forwarded onto current master | PASS |
| 2 | Merged PRs that never reached master | PASS |
| 3 | No public API lost (every fold and fix) | PASS |
| 4 | javac `--release 21` win32 / GTK / Cocoa | PASS |
| 5 | M3 render/GPU backend compiles (Skija 0.116.3) and the gpu.m3 module builds | PASS |
| 6 | JUnit sources compile (`--release 21`), including m3gc | PASS |
| 7 | Headless JUnit | PASS |
| 8 | m3gc (M3 GC over Skija) | PASS |
| 9 | Display JUnit: default suite (AllTests without AllBrowserTests), fork natives 4975r19 | FAIL (19 real failures, all pre-existing) |
| 10 | Display failures: none caused by the environment or the harness | FAIL (9, environment of the invisible desktop) |
| 11 | `-Xcheck:jni` diagnostics | PASS |
| 12 | JNI glue consistent with the Java natives (all platforms) | PASS |
| 13 | #325 release tool self-tests | PASS |
| 14 | Release surface (naming, module name, mandatory registrations) | PASS |
| 15 | Default-suite completeness (`audit_suite_inventory --release`) | PASS |
| 16 | Mandatory sparse-state classes executed | PASS |
| 17 | Java 21 preflight (no Java 22+ API) | PASS |
| 18 | #325 headless Java 21 contract (re-landed ItemBitSet) | PASS |
| 19 | Superset jar + MAPPING/LEDGER + SHA-256 | PASS |
| 20 | M3 exact two-mode report gate (`REPORT_GATE.md`) | PENDING |
| 21 | Instrumented line/branch coverage (90% per changed atom) | PENDING |
| 22 | Native builds and JUnit on GTK3/GTK4/Wayland and macOS | PENDING |
| 23 | AllBrowserTests (Edge/WebView2) | PENDING |
| 24 | OSGi Export-Package of `org.eclipse.swt.m3` and the new internal packages | HOLD (user decision) |
| 25 | GitHub Actions CI | HOLD (user: "Enable workflows") |
| 26 | Merge | HOLD (user: merge #335) |

## Test results (Windows 11, JDK 21.0.9, `-Xcheck:jni`, strict checks, invisible desktop)

| Run | Found | Passed | Failed | Aborted (assumption) | Skipped (`@Disabled`) | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| Headless (22 classes) | 190 | 187 | 0 | 3 (GTK-only) | 0 | `gates/junit-headless.*` |
| m3gc (AllM3GcTests, M3 backend + Skija 0.116.3) | 97 | 97 | 0 | 0 | 0 | `gates/junit-m3gc.*` |
| Display: AllTests without AllBrowserTests (330 classes reachable) | 6,264 | 6,040 | 28 | 176 | 3 | `gates/junit-display.*`, `gates/DISPLAY_FAILURES.tsv` |

The 28 Display failures are all classified, and none is introduced by the candidate:

| Class | Count | Reason and evidence |
| --- | --- | --- |
| PRE_EXISTING_MASTER | 16 | Same tests and messages on master `4fb49875cc` in the same harness: viewport acceptance and visibility tests (Tree/Table/List/Combo viewport, AllWidgets_HiddenViewport) among the newly wired classes, and the VirtualPageSource bulk batch. Root cause and next step in `PRE_EXISTING_DEFECTS.tsv`. |
| UPSTREAM_KNOWN_FAILING | 3 | CoolItem. Upstream AllWidgetTests lists it as `// Failing test` (0ef6051a20). |
| ENV_INPUT_HIDDEN_DESKTOP | 7 | Display.post, focus traversal and shell activation do not reach the invisible test desktop. |
| ENV_CLIPBOARD | 2 | Another process held the shared OS clipboard; both tests passed in earlier runs. |

The `AllNonBrowserTests` suite-level leak detector reports "1 leaks found", on master as well. The JNI checker reported 0 diagnostics in every JVM.

Fixed in this candidate, compared with master in the same harness:
- 61 HARNESS_ORDER failures: Test_Display now releases the thread's Display.
- 8 ENV_SVG failures: the SVG fragment and JSVG are on the gate classpath.
- 9 Tracker failures: the test now releases the Tracker.
- 6 of 7 earlier pre-existing failures: StyledText_PagedContent, Java2DCanvas, M3SortFilterWidgets, and three VirtualPageSource tests (Tree page-source facades and the swap fixture).
- The M3ItemRange MAX_VALUE test (#325).

## Included pull requests since 2026-10-13 (204)

MERGED: the merge commit is on the candidate and changes the tree. MERGED_NOOP: the merge is on the candidate and its content was already there. CONTAINED: the PR head is an ancestor of the candidate. RE-LANDED: the PR was merged into another PR's branch, never reached master, and was folded into this candidate. CARRIED: its content arrives through #325. Source: `LEDGER.tsv` (also in the jar as `META-INF/m3/LEDGER.tsv`).

| PR | merged | class | effect | title |
| --- | --- | --- | --- | --- |
| #27 | 2026-11-06 | MERGED | 30 files +52080/-0 | [SUPERSEDED by #26] Align SWT Maven version with 3.136 bundle API |
| #112 | 2026-10-13 | MERGED | 10 files +1685/-15 | Restore SWT Java2DCanvas with bounded viewport rendering |
| #113 | 2026-10-13 | MERGED | 7 files +695/-4 | fix(viewport): suppress empty, zero-width and hidden GTK row windows |
| #114 | 2026-10-13 | MERGED_NOOP | content already on first parent | GTK: project logical Table viewport and harden model ownership |
| #115 | 2026-10-13 | MERGED | 40 files +17403/-22 | StyledText: viewport architecture for very long text (applies Synexia #10808 postimages) |
| #116 | 2026-10-13 | MERGED | 3 files +682/-0 | Add platform-neutral VirtualListModel and VirtualListWidth (List viewport atoms L1, L2) |
| #117 | 2026-10-13 | MERGED | 11 files +948/-3 | Add VirtualRowExtents (variable row extents) and a GWT compile gate for the shared viewpor |
| #118 | 2026-10-13 | MERGED | 37 files +26460/-82 | StyledText: every API under the viewport |
| #119 | 2026-10-13 | MERGED | 65 files +12942/-130 | m3: lazy data source, lazy tree children, lazy tab content (design first) |
| #120 | 2026-10-13 | MERGED | 20 files +15742/-56 | StyledText: no measurement, layout, timer or redraw work while not visible (applies Synexi |
| #121 | 2026-10-13 | MERGED | 3 files +1121/-0 | Add ViewportDamageRegion and ViewportDamageRecorder: pixel-exact damage model fed by RFB r |
| #122 | 2026-10-13 | MERGED | 14 files +1722/-1231 | m3: toolkit-neutral ItemResidency engine (proxy contract, sparse/pattern lanes, ported Tie |
| #123 | 2026-10-13 | MERGED | 26 files +16074/-199 | StyledText: scroll by blit, no full repaints for caret and selection, packed selection (le |
| #124 | 2026-10-13 | MERGED | 33 files +2794/-4 | m3: win32 visibility gate — no paint/layout work for widgets that cannot be seen |
| #125 | 2026-10-13 | MERGED | 53 files +6294/-0 | examples: Superset Viewport Showcase and Viewport Test Matrix runner |
| #126 | 2026-10-13 | MERGED_NOOP | content already on first parent | Retained GPU planes beneath win32 GC (packed-int caches, RFB damage, Skija backend |
| #127 | 2026-10-13 | MERGED_NOOP | content already on first parent | m3: leaf 2 planes, box model, damage hook, debounce (win32) |
| #128 | 2026-10-13 | MERGED_NOOP | content already on first parent | m3: leaf 3 layout and resize (no-op bounds skip, ResizeBench) |
| #129 | 2026-10-13 | MERGED_NOOP | content already on first parent | List/Combo/CCombo: native owner-data viewport for SWT.VIRTUAL (win32 LBS_NODATA) |
| #130 | 2026-10-13 | MERGED | 14 files +438/-1 | ci: cut fork Actions load, guard secret-less publishers, record CI errors |
| #131 | 2026-10-13 | MERGED | 2 files +2/-2 | Version bump for org.eclipse.swt.examples.ole.win32 (3.110.200) |
| #132 | 2026-10-13 | MERGED | 2 files +34/-5 | [GTK] Release the WebKit cookie list as GList in Browser.getCookie() |
| #133 | 2026-10-13 | MERGED | 2 files +36/-4 | [GTK] Do not lose Display.wake() calls in Display.sleep() |
| #134 | 2026-10-13 | MERGED_NOOP | content already on first parent | Integration build 2026-10-13 (do not merge before its parts) |
| #136 | 2026-10-14 | MERGED | 2 files +166/-0 | Skip empty viewport replay before native preparation |
| #137 | 2026-10-14 | MERGED | 197 files +17657/-898 | M3 superset consolidation 2026-10-13: restore ancestry-only merges and merge all live bran |
| #140 | 2026-10-14 | MERGED | 6 files +336/-0 | build: package existing retained Skija owners through canonical metadata |
| #141 | 2026-10-14 | MERGED | 28 files +654/-372 | M3 superset follow-up: apply upstream Eclipse sync from #138 to master |
| #142 | 2026-10-14 | MERGED | 23 files +4659/-231 | M3 superset follow-up: land GTK logical Table viewport and Win32 Tree frontier |
| #143 | 2026-10-14 | MERGED | 6 files +168/-5 | Replay affine GC state through retained GPU OpStream |
| #144 | 2026-10-14 | MERGED | 7 files +269/-1 | Own expanded TreeItem state in SWT Tree |
| #145 | 2026-10-14 | MERGED | 7 files +280/-17 | feat(swt): widget-owned viewport pressure, parent flag epochs and exact native scrollbars |
| #146 | 2026-10-14 | MERGED | 2 files +114/-23 | SWT.VIRTUAL List: widget-owned pressure-aware overscan and event-safe prefetch |
| #147 | 2026-10-14 | MERGED | 3 files +54/-22 | SWT.VIRTUAL List: bounded overscan and parent-epoch-safe SetData |
| #148 | 2026-10-14 | MERGED | 1 files +365/-12 | feat(swt): integrate viewport and native scrollbars inside Java2DCanvas |
| #149 | 2026-10-14 | MERGED | 8 files +347/-13 | Consolidate Skija packaging and shared List growth with current viewport master |
| #150 | 2026-10-14 | MERGED | 2 files +122/-1 | fix(swt): internalize Tree overscan pressure in actual scroll/geometry owner |
| #151 | 2026-10-14 | MERGED | 2 files +42/-25 | Initiative 31 SWT List: implement viewport paint and scrollbar events inside widget |
| #152 | 2026-10-15 | MERGED | 2 files +127/-18 | fix(swt/cocoa): improve Tree's own pressure-aware paint window and O(depth) residency clea |
| #153 | 2026-10-14 | MERGED | 1 files +1/-1 | Fix Tree helper collection type resolution inside SWT widgets |
| #154 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | Initiative31 SWT List: primitive selection and O(1) select-all inside widget |
| #155 | 2026-10-15 | MERGED | 3 files +154/-8 | Improve viewport affine internals: primitive corners and translation fast path |
| #156 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | SWT List: O(1) first selected index and no intermediate selected-index array |
| #157 | 2026-10-15 | MERGED | 2 files +73/-1 | perf(swt): direct Initiative31 List incremental scrollbar metrics |
| #158 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | perf(initiative31): reuse one native GC within SWT List width measurement |
| #159 | 2026-10-14 | MERGED | 4 files +100/-50 | Avoid virtual item materialization during DPI callbacks |
| #160 | 2026-10-15 | MERGED | 1 files +14/-4 | fix(swt): preserve existing virtual item state through array growth failure |
| #161 | 2026-10-15 | MERGED | 1 files +30/-4 | fix(swt/win32): keep virtual Table DPI inside its resident viewport |
| #162 | 2026-10-15 | MERGED | 2 files +55/-8 | fix(swt): stage packed viewport mask growth before row insertion |
| #163 | 2026-10-14 | MERGED | 3 files +33/-4 | Preserve Win32 Tree item identity and recover SetData callback context |
| #164 | 2026-10-15 | MERGED | 7 files +1327/-0 | docs: map SWT package contracts and explicit coverage |
| #165 | 2026-10-15 | MERGED | 2 files +157/-0 | docs: add SWT source tour and development cheat sheet |
| #166 | 2026-10-15 | MERGED | 2 files +190/-30 | perf(swt): shift parent-owned selection ranges without rebuilding temporary models |
| #167 | 2026-10-15 | MERGED | 3 files +68/-5 | feat(swt): consolidate intrinsic viewport state, resident DPI and recipe-owned backing fix |
| #168 | 2026-10-16 | MERGED | 2 files +113/-0 | fix(swt): failure-safe ItemBitSet directory growth, preserving sparse state |
| #169 | 2026-10-15 | MERGED | 3 files +247/-82 | perf(swt): sparse variable-row extents and lazy width backing |
| #170 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | fix(swt): preserve shared Initiative31 drawing resource ownership |
| #171 | 2026-10-15 | MERGED | 3 files +202/-11 | perf(swt): skip preceding patterns in viewport count and successor queries |
| #172 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | WIP: intrinsic Tree expansion, topology reuse and page navigation |
| #173 | 2026-10-15 | MERGED | 1 files +28/-4 | fix(swt): compact viewport cache recency ranks before frame-clock rollover |
| #174 | 2026-10-15 | MERGED | 3 files +165/-0 | perf(swt): guarded cyclic word arithmetic in existing ItemLanes |
| #175 | 2026-10-15 | MERGED | 1 files +10/-3 | Avoid cold native expansion when clearing virtual TreeItem children |
| #176 | 2026-10-15 | MERGED | 7 files +549/-0 | test(swt): launch original nonvirtual Tree with mediated SetData and bounded rows |
| #177 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | fix(project31): intrinsic Table/Tree viewport extents, bounded hover and callback cleanup |
| #178 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | fix(tree): honor native viewport endpoints and horizontal geometry on Initiative31 |
| #179 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | fix(project31): intrinsic Table/Tree header viewport geometry and paint cleanup |
| #180 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | perf(list): avoid full-list snapshots in original stock renderer |
| #181 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | fix(project31): prevent stale selection and hover state in original Table/Tree renderers |
| #183 | 2026-10-16 | RE-LANDED | merged into m3/sparse-state-safety-20261014 | perf(swt): binary RUN lookup in original ItemBitSet, preserving sparse-state safety |
| #184 | 2026-10-16 | MERGED | 1 files +32/-21 | Fix TreeItem removeAll reentry without per-item storage |
| #185 | 2026-10-16 | MERGED | 4 files +338/-3 | perf(swt): adaptive live dirty-word traversal in original paint graph |
| #186 | 2026-10-16 | MERGED | 1 files +120/-0 | docs: expand SWT homepage with concepts and practical entry points |
| #190 | 2026-10-16 | RE-LANDED | merged into m3/original-viewport-paint-dirty-word-scan-20261015 | fix(swt): failure-atomic node/edge backing in original ViewportPaintGraph |
| #192 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | fix(swt/tree): admit generic Selection only for actual row mouse presses |
| #193 | 2026-10-16 | MERGED | 2 files +19/-11 | StyledText tests: keep measured shells unoccluded (visibility gate, API differential) |
| #194 | 2026-10-16 | CONTAINED | PR head is an ancestor of the candidate | Initiative31 integration: merge #154-#181 onto prototype-skija (viewport-first resolutions |
| #195 | 2026-10-16 | MERGED | 3 files +99/-0 | gtk, cocoa: wire SWT.VIRTUAL TabFolder lazy content (recipe-generated) |
| #196 | 2026-10-16 | MERGED | 22 files +3314/-0 | Tree as a mediated viewport (win32), default mode: TreeViewport, WidgetMode switch, record |
| #197 | 2026-10-16 | MERGED | 3 files +49/-0 | Tree viewport: order-statistic sibling index for row mapping |
| #198 | 2026-10-16 | MERGED | 5 files +466/-0 | All widgets as viewports: plan, measured native limits, scrollable-Composite plane geometr |
| #199 | 2026-10-16 | MERGED | 35 files +5104/-23 | List/Combo viewport (win32): resident list box, logical bar |
| #200 | 2026-10-16 | MERGED | 18 files +2724/-1 | Table viewport (win32): owner-data ListView as the native viewport, shaped like the Tree v |
| #201 | 2026-10-17 | MERGED_NOOP | content already on first parent | Viewport programme integration: Tree, Table, List, scrollables, StyledText mode, harness ( |
| #202 | 2026-10-17 | MERGED | 11 files +970/-129 | Render modes: drawn planes (custom, skija, gpu) for Tree, Table, List + cross-mode benchma |
| #203 | 2026-10-17 | MERGED_NOOP | content already on first parent | Viewport programme, round 2: Tree stock rules, GPU mode, drawn modes, agent leaves |
| #204 | 2026-10-17 | MERGED_NOOP | content already on first parent | Synexia attribution: ledger-driven headers and contributor lines (recipe-applied) |
| #205 | 2026-10-17 | MERGED_NOOP | content already on first parent | WIP checkpoint: SWT Tree viewport working source |
| #206 | 2026-10-17 | MERGED_NOOP | content already on first parent | WIP checkpoint: SWT Table viewport working source |
| #207 | 2026-10-17 | MERGED_NOOP | content already on first parent | WIP checkpoint: SWT attribution and naming working source |
| #208 | 2026-10-17 | MERGED_NOOP | content already on first parent | WIP checkpoint: SWT render tools working source |
| #209 | 2026-10-17 | MERGED | 4567 files +1235630/-0 | merge: additive table viewport checkpoint |
| #210 | 2026-10-17 | MERGED_NOOP | content already on first parent | merge: additive tree viewport checkpoint |
| #211 | 2026-10-17 | MERGED_NOOP | content already on first parent | merge render tools checkpoint |
| #212 | 2026-10-17 | MERGED_NOOP | content already on first parent | M3 naming: Initiative 31 Skija names become M3 names (recipe-applied) |
| #214 | 2026-10-17 | MERGED | 2 files +248/-0 | [M3][Draft] Catalogue 213 SWT PRs and guard viewport source-history reconciliation |
| #215 | 2026-10-17 | MERGED | 4 files +131/-1 | fix(viewport): invalidate child native handles when a Tree ancestor is replaced |
| #216 | 2026-10-17 | MERGED | 5 files +709/-0 | docs(viewport): inventory 451 historical SWT/Nebula PRs and preserve reconciliation eviden |
| #217 | 2026-10-17 | MERGED | 2 files +158/-0 | test(viewport): prove native row-window overflow rejection without changing SWT sources |
| #218 | 2026-10-17 | MERGED | 2 files +136/-0 | [M3][Draft][Test] Guard SWT Tree logical selectAll across cold viewport rows |
| #219 | 2026-10-17 | MERGED | 61 files +7243/-2 | Fix hidden Tree accessibility coordinates and preserve complete PR history |
| #220 | 2026-10-17 | RE-LANDED | merged into m3/viewport-refinement-20261017 | Recover failure-atomic paint-graph growth without replacing viewport history |
| #221 | 2026-10-17 | RE-LANDED | merged into m3/viewport-paintgraph-recovery-20261017 | Guard GTK scrollbar visibility notifications after callback disposal |
| #222 | 2026-10-17 | MERGED | 2 files +66/-0 | [M3][Draft] Pin Synexia recipe custody and public-fork receiving inventory |
| #223 | 2026-10-18 | MERGED | 3 files +140/-0 | test(swt): execute canonical Synexia bootstrap inventory through OpenRewrite SDK |
| #224 | 2026-10-19 | MERGED | 3 files +66/-1 | [M3] Restore bootstrap runtime and pin canonical recipe ownership |
| #225 | 2026-10-19 | MERGED | 26 files +1355/-0 | Advance recipe source custody to com.synexia |
| #226 | 2026-10-19 | MERGED | 662 files +37277/-2412 | Synexia attribution and M3 naming re-applied after integration round 6 |
| #227 | 2026-10-19 | MERGED | 76 files +10164/-6 | Enhanced M3 API: map of 46 capabilities |
| #228 | 2026-10-19 | MERGED | 17 files +3060/-0 | Tree viewport: JUnit coverage toward 99% (headless + hidden-desktop UI tests, COVERAGE_GAP |
| #229 | 2026-10-19 | MERGED | 3 files +111/-2 | fix(viewport): preserve resident Table/List window bounds on overflowing edits |
| #230 | 2026-10-19 | MERGED | 3 files +111/-2 | converge(swt): preserve native Table/List viewport bounds after overflowing edits |
| #232 | 2026-10-19 | MERGED | 25 files +1241/-38 | M3 column sort and column filters (enhanced API, leaf 1) |
| #233 | 2026-10-19 | MERGED | 120 files +1560/-380 | merge(upstream): sync eclipse-platform/eclipse.platform.swt master 3af6685769 (47 commits) |
| #234 | 2026-10-19 | MERGED | 262 files +31968/-793 | Preserve SWT viewport integration round 10 checkpoint |
| #235 | 2026-10-21 | MERGED | 31 files +4554/-0 | M3 label interning, short-label immediates and tree path ids (internal collections, Java o |
| #236 | 2026-10-21 | MERGED | 14 files +1513/-0 | [Draft] Fix full-range scrollbar scaling and hit geometry through canonical recipe |
| #237 | 2026-10-21 | MERGED | 2 files +49/-6 | fix(swt): receive exact full-range scrollbar geometry with 5.7M Java21 checks |
| #239 | 2026-10-21 | RE-LANDED | merged into m3/swt-interning-20261019 | chore(swt): preserve interning widget benchmark source |
| #240 | 2026-10-21 | MERGED | 12 files +516/-64 | perf(SWT): rewrite original EventTable with M3 indexed dynamic-array high-water slots |
| #241 | 2026-10-21 | MERGED | 1 files +1/-0 | Restore the existing full-range scrollbar proof runtime closure |
| #242 | 2026-10-22 | MERGED | 5 files +136/-0 | M3AST: apply source-sealed original compiler Maven receiver |
| #243 | 2026-10-22 | MERGED | 1 files +38/-9 | Fix CCombo selection ordering and callback safety |
| #244 | 2026-10-22 | MERGED | 2 files +54/-1 | Guard themed row text bounds after image forwarding |
| #245 | 2026-10-22 | MERGED | 15 files +5125/-0 | [Draft] Reconcile upstream GTK4 List MouseDown and DefaultSelection on fork |
| #246 | 2026-10-23 | MERGED | 11 files +68/-10 | fix(swt): align 3.138 host, eight fragments and optional GPU dependency |
| #247 | 2026-10-23 | MERGED | 3 files +37/-8 | Repair GTK4 list selection and spinner UTF-8/UTF-16 insertion offset |
| #248 | 2026-10-23 | MERGED | 4 files +33/-3 | Fix three retained GPU dangling Javadoc warnings without suppressing javac |
| #249 | 2026-10-23 | MERGED | 5 files +25/-4 | Fix SWT superset examples Javadoc visibility and factory link errors |
| #250 | 2026-10-23 | MERGED | 511 files +143773/-654 | M3 viewport integration superset (rounds 10-13, docs/m3 marks, Java 21 docs) |
| #251 | 2026-10-28 | MERGED | 47 files +3060/-226 | M3 render modes (custom, m3, gpu) superset |
| #252 | 2026-10-24 | MERGED | 2 files +139/-0 | fix(swt): restore missing ThreadRole source with headless role-guard test |
| #253 | 2026-10-24 | MERGED | 2 files +2/-0 | tests: restore the two closing braces lost by the 2026-10-22 merge (JUnit sources did not |
| #254 | 2026-10-24 | MERGED | 2 files +31/-2 | EffectiveVisibility: clip a node against its top handle (the gate parked every virtual Tre |
| #255 | 2026-10-27 | MERGED | 1 files +23/-1 | fix(swt/tree): preserve committed native handles on invalid viewport window |
| #256 | 2026-10-28 | MERGED | 351 files +43846/-47 | Viewport programme, round 3: Tree custom draw identical to stock, all-widget gate with sto |
| #257 | 2026-10-29 | MERGED | 7 files +458/-7 | Fix bounded GTK Table scrolling, provider disposal and Text lifetime |
| #258 | 2026-10-29 | MERGED | 2 files +133/-3 | test(swt): fix hidden-child coordinate ranks in linear reference oracle |
| #259 | 2026-10-31 | MERGED | 68 files +8971/-92 | SYnc |
| #260 | 2026-10-29 | MERGED | 5 files +487/-25 | Fix GTK Tree clear ranges and Combo native-handle lifecycle |
| #261 | 2026-10-29 | MERGED | 4 files +360/-9 | Add opt-in retained-pixel reuse for Java2DCanvas scrolling |
| #262 | 2026-10-29 | MERGED | 4 files +297/-1 | Fix frame snapshot growth and release inactive row payloads |
| #263 | 2026-10-29 | MERGED | 5 files +308/-2 | Preserve damage subset semantics across accumulated bounds |
| #264 | 2026-10-29 | MERGED | 2 files +78/-0 | Register Java2DCanvas scroll reuse in the normal SWT test suite |
| #265 | 2026-10-30 | MERGED | 1 files +76/-18 | Record source-built SWT scroll-reuse qualification |
| #266 | 2026-10-30 | MERGED | 217 files +80949/-267 | Superset: additively land open PRs #259, #265 |
| #267 | 2026-10-30 | MERGED | 46 files +2734/-7 | GC extension seam: adopt Initiative 31's sealed GC, GCExtension and canvasext (leaf 1, no |
| #269 | 2026-10-30 | MERGED | 109 files +10942/-7 | Superset: additively land open PRs #267, #268 |
| #270 | 2026-10-30 | MERGED | 21 files +1168/-35 | Drawn planes: owner-draw protocol with a real GC (GCExtension over M3ExternalGC in m3/gpu, |
| #271 | 2026-10-31 | MERGED | 18 files +889/-32 | GC seam leaf 4: GPU M3 tree compiles against the bound Skija, SWT.M3 Canvas activation beh |
| #272 | 2026-10-31 | MERGED | 18 files +1776/-9 | Feature switches: M3Features registry (packed bitset, property precedence, discovery) and |
| #273 | 2026-11-06 | MERGED_NOOP | content already on first parent | Superset 2026-10-30b: all open PRs of eclipse.platform.swt merged additively (2 merged, 1 |
| #275 | 2026-10-31 | MERGED | 50 files +6064/-0 | Superset 2026-10-30c: all open PRs of eclipse.platform.swt merged additively (3 merged, 1 |
| #276 | 2026-10-31 | MERGED | 67 files +8540/-0 | M3 example gallery (all kinds) and combinatorial test matrix (IPOG, all modes) |
| #278 | 2026-10-31 | MERGED | 4 files +235/-1 | Enhanced API leaf 6 (M3-ROW-MATH): built, tested, official-jar differential |
| #279 | 2026-10-31 | MERGED | 5 files +329/-1 | Enhanced API leaf 10 (M3-DIFF-APPLY): built, tested, official-jar differential |
| #280 | 2026-10-31 | MERGED | 4 files +137/-1 | Enhanced API leaf 13 (M3-SCROLL-RESTORE): built, tested, official-jar differential |
| #281 | 2026-10-31 | MERGED | 4 files +149/-1 | Enhanced API leaf 14 (M3-PROXY-MODEL): built, tested, official-jar differential |
| #282 | 2026-10-31 | MERGED | 2 files +12/-1 | Enhanced API leaf 16 (M3-ESTIMATED-HEIGHTS): built and tested |
| #283 | 2026-11-06 | MERGED | 3 files +104/-1 | Enhanced API leaf 8 (M3-BULK-UPDATES): built, tested, official-jar differential (original- |
| #285 | 2026-10-31 | MERGED | 226 files +25929/-531 | Draft: chronological additive SWT superset (#259–#284), renderer and enhanced API composit |
| #286 | 2026-10-31 | MERGED | 46 files +6614/-9 | Theme and language switchers: M3Theme packed slots + runtime switch, M3Language relabel/RT |
| #287 | 2026-11-06 | MERGED | 8 files +425/-1 | Enhanced API M3-SCROLLBAR-GEOMETRY (leaf 21): M3ScrollBarModel, tests, official-jar differ |
| #288 | 2026-11-06 | MERGED_NOOP | content already on first parent | Superset 2026-10-31d: all open PRs of eclipse.platform.swt merged additively (10 merged, 2 |
| #289 | 2026-11-06 | MERGED | 17 files +33425/-0 | Enhanced API leaf 5 (M3-EMPTINESS): built, tested, GTK/Cocoa seams, re-entrancy fix, offic |
| #290 | 2026-11-06 | MERGED | 6 files +1648/-0 | Enhanced API leaf 7 (M3-RESIDENCY): built, tested, resident-range fix, official-jar differ |
| #291 | 2026-11-06 | MERGED | 7 files +1952/-0 | Enhanced API leaf 9 (M3-PREFETCH): built, tested, dispose-safe cache hint, official-jar di |
| #292 | 2026-11-06 | MERGED | 18 files +50601/-0 | Enhanced API leaf 11 (M3-VISIBILITY-OBSERVER): built, tested, paint/scroll triggers, offic |
| #293 | 2026-11-06 | MERGED | 11 files +1369/-0 | Enhanced API M3-WIDTH-TRACK + M3-SHAPING-MEASURE (leaf 18): M3WidthTrack over M3TextMeasur |
| #294 | 2026-11-06 | MERGED | 10 files +1132/-0 | Enhanced API M3-LAZY-CONTENT (leaf 20): M3LazyContent |
| #295 | 2026-10-31 | MERGED | 6 files +410/-0 | swt/m3: public viewport core API (resident window, logical extent, planes, damage) |
| #296 | 2026-11-06 | MERGED | 10 files +932/-0 | Enhanced API M3-SNAPSHOT (leaf 22): M3Snapshot |
| #297 | 2026-11-06 | MERGED | 2 files +86/-0 | Drawn plane: a paint inside a frame waits for the next frame (NPE in RetainedRows.paint) |
| #298 | 2026-11-06 | MERGED | 2 files +81/-0 | Tree viewport: a virtual tree whose rows fit reads 0 on every vertical bar value, as stock |
| #300 | 2026-11-06 | MERGED | 2 files +133/-3 | Tree viewport: SWT.EmptinessChanged follows stock item ids (M3-EMPTINESS parity) |
| #302 | 2026-10-31 | MERGED | 38 files +2060/-102 | Leaf 18: ship M3 in the product - one M3 fragment, sealed API filters, notice, artifact + |
| #303 | 2026-11-06 | MERGED | 5 files +949/-0 | M3FilterRow follows moves of the row and the control |
| #304 | 2026-10-31 | MERGED | 45 files +1704/-66 | Real-app fidelity: background parity, drawn-row images/grid/truncation/offset, RTL M3 canv |
| #305 | 2026-11-06 | MERGED | 2 files +31/-8 | M3TreeWalk asks each parent for its child count once (M3RowMath linear on plain trees, mat |
| #306 | 2026-11-06 | MERGED | 8 files +591/-0 | Enhanced API M3-LOG (leaf 24): M3Log structured access to SWT's asynchronous log |
| #307 | 2026-11-06 | MERGED | 605 files +245133/-7074 | Superset round 2026-10-31e: every open PR merged additively (15 merged, 11 retained) |
| #308 | 2026-10-31 | MERGED | 20 files +779/-7 | L12 prep: one Skija (0.143.18 + types 0.2.0) as a re-runnable forward delta package (tools |
| #309 | 2026-10-31 | MERGED | 20 files +779/-7 | M3 text leaf (L13/L14): TextLayout and GC text through M3, IDE editor 32,840 -> 4,352 px v |
| #310 | 2026-10-31 | MERGED | 61 files +3359/-178 | M3 L12 one Skija applied: 0.143.18 + types 0.2.0 (stacked on #309) |
| #311 | 2026-10-31 | MERGED | 83 files +954/-851 | SWT M3 on jdk.m3.graphics: Skija names -> M3 owner (69 files, compiles with no Skija jar; |
| #312 | 2026-10-31 | MERGED | 25 files +240/-177 | SWT superset: GitHub fork network consolidated additively (10 merged, 3 contained, 24 conf |
| #313 | 2026-10-31 | MERGED | 23 files +273/-62 | jdk.m3.graphics prose: the #311 review lows (stale Skija prose, exception text, describe l |
| #314 | 2026-10-31 | MERGED | 20 files +1875/-24 | Array loops to System.arraycopy (intrinsic) and Arrays.fill (readability) by recipe |
| #315 | 2026-11-04 | MERGED | 746 files +1445880/-0 | history(swt): preserve all 742 exact historical lost Git blobs for audited restoration [HO |
| #316 | 2026-11-06 | MERGED | 750 files +1446020/-0 | DRAFT/HOLD: additive SWT 746-file historical source archive with exact two-parent merge |
| #317 | 2026-11-06 | MERGED | 2 files +82/-0 | DRAFT/HOLD: exact 26-open-PR SWT ancestry superset, zero live source changes |
| #318 | 2026-11-03 | MERGED | 2 files +82/-0 | docs: integrate verified SWT history receipts into the existing feature branch |
| #320 | 2026-11-06 | MERGED | 122 files +3774/-993 | Sync upstream eclipse-platform master (supersedes #319) |
| #321 | 2026-11-09 | MERGED | 6 files +618/-2 | Release hardening: SWT naming, JUnit integrity and instrumented coverage intake |
| #322 | 2026-11-09 | MERGED | 17 files +1251/-3 | SWT release preparation: preserve naming and require executed M3 coverage |
| #323 | 2026-11-09 | CARRIED | merged into release/prep-naming-docs-coverage-20261107 | Prepare SWT release packaging, readiness and source-bound evidence |
| #324 | 2026-11-09 | MERGED | 15 files +1492/-17 | Prepare SWT M3 release naming, docs, and test coverage |
| #326 | 2026-11-09 | MERGED | 1 files +1/-0 | [Win32] Restore OS.LVM_GETITEMSTATE: master does not compile on win32 |
| #327 | 2026-11-09 | MERGED | 2 files +20/-6 | [Win32] Visibility gate: catch up a control with deferred work when its geometry changes |
| #328 | 2026-11-09 | MERGED | 1 files +6/-3 | Control part visibility test: expectations to the PartVisibilityEvent.origin contract |
| #329 | 2026-11-09 | MERGED | 2 files +81/-13 | AnimatedProgress: park the animation timer while the control cannot be seen (every-widget |
| #330 | 2026-11-09 | MERGED | 2 files +56/-0 | [Win32] ProgressBar: stop the marquee timer while nobody can see the bar (every-widget H) |
| #332 | 2026-11-09 | MERGED | 2 files +48/-0 | [Win32] List: VIRTUAL SetData prefetch parks while nobody can see the list (every-widget H |
| #333 | 2026-11-09 | MERGED | 3 files +82/-0 | [Win32] Occlusion tracker: park while no shell of the Display is visible (every-widget H) |
| #334 | 2026-11-09 | MERGED | 2 files +48/-0 | CTabFolder: no posted tab update while the folder cannot be seen (every-widget H) |

## Merged pull requests not applicable to master (7)

| PR | outcome | proof |
| --- | --- | --- |
| #182 | NOT_APPLICABLE (initiative31 line) | SkijaGC.java change on the I31 Skija branch; master renamed the Skija owner (#212/#311) |
| #187 | NOT_APPLICABLE (initiative31 line) | Drawing.java on the I31 Skija branch |
| #188 | NOT_APPLICABLE (initiative31 line) | CustomComposite.java on the I31 branch |
| #189 | NOT_APPLICABLE (initiative31 line) | DefaultListRenderer.java on the I31 branch |
| #191 | NOT_APPLICABLE (owner absent) | its 3 files are the Initiative 31 drawn Tree/TreeItem under Eclipse SWT/common/widgets plus a RECEIVER note; master has no common Tree.java (git ls-tree HEAD: 0 matches); base m3/initiative31-tree-demand-mediation-20261015 belongs to the initiative31 prototype line |
| #231 | NOT_APPLICABLE (initiative31 line) | merged into the I31 SWT-on-Swing prototype branch |
| #238 | NOT_APPLICABLE (initiative31 line) | Eclipse SWT/swing sources of the I31 SWT-on-Swing prototype |

Re-landed and carried PRs, with their own-line presence before and after, are in `STACKED_NOT_ON_MASTER.tsv`.
