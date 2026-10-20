# Viewport agent brief (shared by every widget agent)

Goal (user, 2026-10-16): every SWT and Nebula widget becomes a viewport with the **same public contract and the
same visible behaviour**. We implement SWT/Nebula itself, not a layer on top. Everything is allowed, JNI
included, as long as the API contract and the effective behaviour do not change. "Viewport logic is king";
this is what will be offered to Eclipse, so write upstream-quality Eclipse code.

## Reference implementation: win32 Tree (read first)

- Worktree `C:/m3-superset/swt-tree`, branch `m3/tree-viewport-20261016`, PR hsoliwal/eclipse.platform.swt#196.
- Design: `docs/m3/TREE_MEDIATED_VIEWPORT.md`. Mediator: `bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/TreeViewport.java`.
- Common infra in `Eclipse SWT/common/org/eclipse/swt/widgets/`: `VirtualScrollMetrics`, `VirtualTreeResidentPlan`,
  `VirtualItemState`, `VirtualListModel`, `VirtualSelectionModel`, `ViewportScrollLayout`, `VirtualRowExtents`,
  `VirtualPageHost`; residency engine `org.eclipse.swt.internal.residency` (`docs/m3/M3_ITEM_RESIDENCY.md`).
- Pattern: the outer window (`hwndParent`) is the viewport and owns the logical scroll bar; the native control is a
  content plane holding only the resident window; public items are facades whose state lives in lanes
  (bit flags, packed ints); a facade's native handle is validated before use (handles are reused natively).
- Tests: acceptance (`Test_org_eclipse_swt_widgets_Tree_Viewport`) and the **recorded differential**
  (`Test_org_eclipse_swt_widgets_Tree_ViewportDifferential`): seeded random public-API scripts run against a
  plain widget and a viewport widget; every step records answer | scroll bar | selection | focus | events, and
  pixels per seed; any difference fails with the op log (that log is the reproduction). Also
  `docs/m3/tree-viewport/aspect_matrix.py`: every public member and behaviour aspect mapped to its test.
- Native behaviour that the mediator must reproduce is **measured with an oracle program first** (see
  `docs/m3/tree-viewport/evidence/EnsureVisibleOracle.java.txt`), never guessed.

## Rules

1. **Donor-first.** Before new architecture write `docs/m3/<widget>-viewport/FOSS_REUSE_DECISION.tsv`
   (columns: capability, candidate_repo, commit, source_paths, license, contract_fit, decision, rejected_reason,
   adapted_hashes, notice_path). Search first: this fork's PRs and history
   (`gh pr list --repo hsoliwal/eclipse.platform.swt --state all --search <widget> --limit 100`,
   `git log --all --oneline -S<symbol>`, `git log --grep`), hsoliwal/nebula, swt-initiative31 repos, JetBrains/skija,
   Eclipse TM, ECF, Sequoyah SWT VNC. **User 2026-10-17: anything on GitHub pertaining to Swing, SwingX, AWT, JavaFX, SWT,
   Draw2D, Nebula and GEF is a donor for the viewport variants** (e.g. OpenJFX VirtualFlow, Flowless, NatTable, NetBeans Outline, Glazed Lists,
   FlatLaf, SWTSwing). The shared catalogue is K:/m3-work/donors/catalogue/DONOR_CATALOGUE.tsv + TRICKS.md (donor-survey
   agent); check it before designing. Licence per row decides ADAPT/PORT vs pattern-only. Lean structures from hsoliwal/com.synexia and hsoliwal/M3jdk21 may be
   **inlined as copies** (never a dependency), with provenance noted.
2. **Docs precede code**: `docs/m3/<WIDGET>_MEDIATED_VIEWPORT.md` (design, measured native rules, status).
3. **Recipe-first hooks.** Hooks into existing SWT files are one-line statements inserted by the OpenRewrite
   recipe `com.synexia.openrewrite.viewport.M3InsertStatements` (positions AFTER, BEFORE, BLOCK_START, BLOCK_END,
   METHOD_START, METHOD_END; selectors `parameterCount`, `parameterTypes`). Crate (read-only for you):
   `K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes`; see
   `tools/gen_tree_viewport_yml.py` there and how `C:/m3-superset/tools/tree_viewport_cycle.sh` applies it with
   `M3PerFileApply` (classpath `$C/target/classes;$(cat $C/target/cp.txt)`). Put YOUR generator and YAML in your
   own worktree under `docs/m3/<widget>-viewport/recipe/` (YAML at `.../recipe/META-INF/rewrite/m3-swt-<widget>-viewport.yml`)
   and prepend that `recipe` dir to the classpath. Never write into the Synexia crate (the lead ports it later).
   New classes (the mediator, tests) are written directly. Apply twice: the second pass must be UNCHANGED.
4. **Viewport is the DEFAULT** (user decision 2026-10-16). Modes are chosen at startup by one SWT-global switch:
   `-Dswt.widget.mode=viewport|original|custom|m3|gpu` (global) and `-Dswt.widget.mode.<Widget>=...` (per widget class).
   `original` = stock SWT, `custom` = GC custom-drawn, `m3` = drawn by the M3 renderer, `gpu` = drawn on the graphics hardware
   (Direct2D on win32, OpenGL elsewhere; user decision 2026-10-17). **All five modes are optimized heavily**
   (user 2026-10-17): in `original` only where behaviour stays identical to stock (differential vs the official jar). Add the class
   `Eclipse SWT/common/org/eclipse/swt/internal/WidgetMode.java` to your branch **byte-identical** to
   `C:/m3-superset/tools/WidgetMode.java.txt` (so branches merge cleanly) and gate your widget with
   `static final boolean ENABLED = WidgetMode.of ("<Widget>", WidgetMode.VIEWPORT) == WidgetMode.VIEWPORT;`.
   Nebula reads the same property names (`swt.widget.mode`, `swt.widget.mode.Grid`) through its own copy of that
   class in a Nebula internal package. Upstream tests that are white-box tests of the replaced native internals skip
   (JUnit assumption) only when the viewport is active. Tests for the stock path run with `-Dswt.widget.mode=original`.
5. **CPU/GPU split**: never do O(n) backing-structure work on the UI thread; only O(window) native operations there.
6. **Logging** (user 2026-10-16): java.util.logging, asynchronous, through `org.eclipse.swt.internal.SwtLog`
   (copy `C:/m3-superset/tools/SwtLog.java.txt` byte-identical; Nebula: own package copy):
   `SwtLog.getLog ("org.eclipse.swt.widgets.<Widget>")`, `LOG.trace/debug/info (() -> ...)` only. Never System.out.
7. **Event objects** may be reused from a pressure-aware LRU once out of listeners (unless the user kept them).
8. **Code style**: Eclipse SWT style (tabs, `method (args)` spacing), Javadoc, no
   internal jargon in public docs. Methods small. LF line endings (Python writes need `newline=''`; check
   `git diff --cached --check` before every commit).
   **Attribution (user 2026-10-17): every new file we author carries the Synexia header**
   `C:/m3-superset/tools/SYNEXIA_HEADER.java.txt` (Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others; Apache-2.0, with EPL-2.0 as
   the alternative so Eclipse can take it; `SPDX-License-Identifier: Apache-2.0 OR EPL-2.0`); scripts use
   `SYNEXIA_HEADER.script.txt`. Upstream Eclipse/Nebula files we modify keep their licence and get a contributor line
   `Synexia - <what changed>`; donor-derived files keep the donor's licence and notice and add Synexia as contributor.
   Never relabel third-party code.

9. **One family, maximum reuse, minimum planner** (user 2026-10-16): "Table must be close to Tree and List must
   be close to Table." Shape every mediator like `TreeViewport` (same method names, same phases: capture → plan →
   retire/insert → commit → bind → restore focus; same `residentHandle` validation; same selection-neutral caret
   restore; same recorded-differential harness). Put shared logic in `Eclipse SWT/common/.../widgets` classes
   reused by all (a flat list is a Tree with depth 1; a Table is a List with columns). The planner per rebuild must
   be the minimum: diff old vs new window, touch only changed rows; no per-row allocation on the hot path.
10. **Optimize massively, arithmetic first.** Prefer integer arithmetic structures (Fenwick/BIT prefix sums for row
    heights and visible counts, order-statistic indexes, binary lifting for ancestor/row mapping, interval
    arithmetic for damage rectangles, bit-packed lanes, sorted int arrays + binary search) over object graphs.
    Changing JNI/native code is allowed when contract and behaviour are kept.
11. **More donors:** Eclipse GEF **Draw2d** (`org.eclipse.draw2d.Viewport`, `FreeformViewport`, `RangeModel`,
    `DefaultRangeModel`, `ScrollPane`, `DeferredUpdateManager` damage coalescing; EPL-2.0) and the Initiative 31 /
    Skija survey at `C:/m3-superset/tools/I31_SKIJA_TRICKS.md` (visible-range math, paint cull, SetData once,
    row-rect damage, scrollbar model, ListLikeModel anchor/current; `Canvas.scroll` ScrollWindowEx blit).
    Initiative 31 renderers belong to the `custom`/`m3` modes only; the `viewport` mode keeps native pixels.

12. **Upstream defect policy** (lead decision 2026-10-16, from "same behaviour" + "our release fixes errors"):
    - `original` mode stays behaviourally identical to stock (it is the compatibility reference); never fix stock
      defects there.
    - The default mode (`viewport`, also `custom`/`m3`/`gpu`) fixes genuine upstream defects: behaviour that
      contradicts the API's own Javadoc, crashes, data loss, or clipped/wrong rendering.
    - Every such fix gets a row in `docs/m3/<widget>-viewport/UPSTREAM_DEFECTS.tsv` (widget, API, stock behaviour, our
      behaviour, cause with stock file:line, Javadoc quote), and the stock differential accepts exactly the listed
      divergences (in the widget's test, not the shared harness). Behaviour changes that are improvements but not
      defect fixes stay out unless the user asks.

13. **Java only for new code** (user 2026-10-17, overrides the earlier "JNI allowed"): every line we add is Java.
    No new C/C++/JNI shims and no native builds; existing SWT natives stay as they are. Native capabilities are
    reached through what SWT's Java layer already offers (OS/COM VtblCall), through Java libraries that bring their
    own natives (the M3 renderer's third-party binding), or through the JDK's Java FFM API (java.lang.foreign; preview in JDK 21, final in 22+)
    where a plain export must be called. Non-Java projects (Dawn, Skia C++, abseil, folly, ...) are studied and
    ported to Java as patterns, never compiled in.

14. **M3 naming** (user 2026-10-17): nothing of ours is called Skija or Skia; the mode is `m3`
    (`WidgetMode.M3`, `-Dswt.widget.mode=...|m3|...`), and our classes, packages, docs and properties use M3* names
    (e.g. `M3GC`, `M3FontMetrics`, `M3PlaneBackend`, `M3Surface`). Renames are done by recipe (OpenRewrite
    ChangeType/ChangePackage, second pass UNCHANGED) and the result is atomized and patternized. Limits: a third-party
    library's own Java binding classes keep their package when its prebuilt native library binds to those names
    (renaming would break it and rule 13 forbids rebuilding natives), and they sit behind one internal M3 adapter;
    derived code keeps the original licence notice that names its source (Apache-2.0/EPL require it).

15. **99% JUnit coverage** (user 2026-10-17): every class we add or change is covered by JUnit tests to at least 99%
    of lines and branches, measured with JaCoCo (Java agent; report under `docs/m3/<widget>-viewport/coverage/`).
    UI tests still run only on hidden desktops. Uncoverable lines (platform guards, defensive dead code) are listed
    with a reason in the coverage report, never silently excluded.
16. **Themeability** (user 2026-10-17): look and feel is a pluggable, first-class API, not hard-coded paint:
    a theme supplies colours, fonts, metrics (row height, indents, insets, scroll bar geometry), glyphs/images and
    row/cell renderers. Drawn modes (`custom`, `m3`, `gpu`) take every visual from the theme (themes `native`,
    `swing`, `initiative31`, user-defined); the `viewport` and `original` modes stay native but expose the same
    theme hooks where SWT already allows them (colours, fonts, images, custom draw). Selected with
    `-Dswt.widget.theme=<name>` and at runtime through the theme API. Themes are differential-tested like modes.

17. **Enhanced SWT API: language support** (user 2026-10-17): language support is offered as enhanced SWT API,
    additive and opt-in, so the stock contract and behaviour are unchanged when unused. Language data comes from
    **Hugging Face** (datasets/models, each with its licence recorded; data imported as data with provenance, never
    as code). Themeability (rule 16) is enhanced API in the same sense. New public API is documented as an M3
    enhancement (Javadoc + docs/m3), covered to 99% (rule 15) and Java only (rule 13).

18. **DOOP: data-oriented OOP** (user 2026-10-17; the law behind the viewport). State and data live in
    structure-of-arrays lanes: packed ints/longs, bitsets and flag masks, dynamic arrays (Synexia swap-in/swap-out
    arrays), prefix sums / order-statistic indexes. Objects (TreeItem, TableItem, GridItem, Swing nodes, JavaFX cells)
    are thin, lazy, parent-owned facades created only when a caller's API asks for one, identified by an int id and
    released or recycled under pressure; they hold no state that the lanes do not hold. No per-row allocation on
    hot paths. Applies to SWT, Nebula, and in M3JDK to Swing, SwingX and JavaFX ("DOOP Swing/SwingX/JFX"). Each
    leaf states its lanes and facades in its design doc. Prior art: `VirtualItemState` / topology lanes, Grid
    `GridRowTree`, `VirtualTreeSiblingIndex`, Synexia packed collections.

19. **All SWT and Swing work from GitHub becomes atomized, patternized work** (user 2026-10-17). Every donor in
    `K:/m3-work/donors/catalogue/DONOR_CATALOGUE.tsv` (SWT, Swing, SwingX, AWT, JavaFX, Draw2D, Nebula, GEF, and the
    M3 renderer's third-party sources) is run through Synexia's atomize/patternize projection
    (`.m3/task-crates/java-corpus-inline-20261008/atomize_patternize.py`); outputs (atoms, exact and structural
    patterns, spectrum, provenance by repo/commit/path/blob) go to Synexia
    `docs/M3-SCALE/runs/20261017-ui-donor-atomize/`. Outputs are hashes and patterns, never copied source; a
    pattern becomes code only through a FOSS_REUSE_DECISION row (licence rules unchanged). Our own SWT/Nebula/M3JDK
    work is atomized the same way (attribution agent), so donor and own patterns land in one pattern space.

20. **Edit tools** (user 2026-10-17): change Java through OpenRewrite recipes (Synexia crates, second pass UNCHANGED);
    change text/docs/scripts with sed or awk; search with grep or rg. No ad-hoc Python patch scripts for edits.

21. **Catalogue-driven optimization** (user 2026-10-18: "we should use LeetCode and HackerRank for optimizing code"). Before
    optimizing any operation, name its problem category (bit manipulation, prefix sums, two pointers, sliding window,
    binary search on index, monotonic stack, union-find, Fenwick/segment tree, hashing/open addressing, ...), consult
    the algorithm catalogue (M3jdk21 `m3/collections/qualification/*/ALGORITHM_CATALOGUE.tsv` schema; Synexia
    `M3AlgorithmCatalog`), compare LeetCode/HackerRank/GeeksforGeeks candidates against the current code for
    correctness, complexity and measured speed, apply the winner, and append the evaluation + rationale as a row in
    your track's `docs/m3/<track>/ALGORITHM_CATALOGUE.tsv`. Example: LeetCode 136 / HackerRank Lonely Integer =
    XOR fold (x^x=0, O(n) time, O(1) space): the bit-manipulation category that viewport state masks, selection
    bitsets and popcount-based counts draw from. Problem statements are never copied; only licensed solutions.

22. **JUL logging** (user 2026-10-19: "use JUL logging"). All logging goes through java.util.logging: SWT via `org.eclipse.swt.internal.SwtLog` (JUL, asynchronous); Nebula, GEF, JFace and the custom widgets via a `java.util.logging.Logger` named after the package; M3JDK via `System.Logger` (which routes to JUL). Never System.out/err, SLF4J, Log4j or commons-logging in our code. Hot paths guard with `isLoggable`/supplier lambdas so a disabled level allocates nothing. Levels: FINEST/FINER (TRACE) for per-row detail, FINE (DEBUG) for plans and decisions, INFO for mode selection and one-shot facts.

23. **Optimization pass** (user 2026-10-20: "proceed with optimizing the whole"). Follow `C:/m3-superset/tools/OPTIMIZATION_PASS.md`: measure at 10^5 and 10^6 first, one model + deltas, allocation-free hot paths, catalogue algorithms, native work O(window); one ledger row per leaf in `docs/m3/<track>/OPTIMIZATION_LEDGER.tsv`; no number, no leaf.

24. **Delta-only views** (user 2026-10-20: "all delta based view"; "next target is the Problems view, then all the platform UI using the same viewport and DOOP"). On a model change a view applies only a delta (insert/remove/update/move to M3Patch): no root `refresh()`, no `setInput()`, no clear-and-re-add in a change handler. Gate per view: `DELTA_GATE.tsv` (rg count of rebuild calls reachable from listeners = 0, or each site justified with file:line) plus a runtime full-refresh counter = 0 in the differential.
    - **Viewport deltas (resize, layout)** (user 2026-10-20: "resize and layout change the port"). A resize, scroll, zoom/DPI change, font change, column resize/move/hide, row-height change, expand/collapse or layout pass changes the viewport, and that change is also a delta: compute the rows that enter and leave the resident window and materialize/release only those; update extents incrementally (Fenwick/prefix sums for row heights, column-offset lanes for widths); repaint only damaged rows/cells. Never re-plan, re-measure or re-create the whole window on a resize or layout. Gate: a resize sweep (for example 50 sizes, ±1 px steps and large jumps) and a column-resize sweep with counters for full re-plans = 0 and native create/destroy = rows entering/leaving only.
    - **Hidden viewports: dirty while hidden, render on becoming visible** (user 2026-10-20, said six times: "if the viewport becomes visible from hidden it should paint; while it is hidden the model may make changes and the viewport is dirty and renders on becoming visible"). While a viewport is hidden (not visible, zero size, minimized or inactive part, unselected tab or stack page, collapsed section, off-screen in a scrolled parent), model deltas still update the lanes but cause no native work, no item creation and no paint; the viewport only accumulates a dirty window (coalesced, O(1) per delta: a flag plus the union of touched ranges). On becoming visible (SWT.Show / setVisible(true) / part or tab activation / expand / non-zero resize), it applies ONE window patch against the current model and paints once. Gate: hide, apply 10^4 deltas, show: native ops and paints while hidden = 0; after show, visible rows equal stock rendering of the same model; exactly one paint pass.

25. **We own the implementation; we are not a layer on it** (user 2026-10-20, repeated: "you own the entire SWT .. you own the SWT scrollbar, part becoming visible, that is the invariant; it is not a layer, it is the implementation itself"; "you own the implementation, you are not a layer on it .. that is the invariant: INTRODUCE A NEW LISTENER PART VISIBLE"; "the viewport becoming [visible] should come from SWT or Swing"). Viewport behaviour, scrollbars and visibility live inside SWT (and M3JDK Swing) classes themselves, reached through recipes on the fork, never as wrappers, adapters or listeners bolted on from above. Visibility is an SWT event: new event types `SWT.PartVisible` / `SWT.PartHidden` (typed `PartVisibilityListener`, query `Control.isPartVisible()`), fired by SWT itself when a control's effective visibility changes: setVisible on the control or any ancestor, TabFolder/CTabFolder selection, StackLayout top control, Shell iconify/deiconify and minimize, ExpandItem/collapsible expand and collapse, size 0 to non-zero and back. Viewports consume it internally (hidden: lanes only, dirty window; visible: one patch, one paint). JFace viewers, e4 parts and IPartListener2 partVisible/partHidden are built on this SWT event, not the other way round. Swing in M3JDK gets the same in its implementation (JComponent, built on the showing state).
    - **Viewport listener** (user 2026-10-20: "would like to add viewport listener too?"). Next to PartVisible, SWT gets `SWT.Viewport` events with a typed `ViewportListener` (`viewportChanged(ViewportEvent)`), and M3JDK Swing gets a parallel `javax.swing` ViewportListener on JComponent. Each event carries the first/last visible index, the entered and left row ranges (int pairs, no per-row objects), whether the extents changed, and a cause bit mask (SCROLL, RESIZE, LAYOUT, MODEL_DELTA, VISIBILITY, EXPAND). It is fired by the widget implementation once per coalesced viewport change, never while hidden; the PartVisible transition carries the first event after show. JFace ILazyContentProvider/ILazyTreeContentProvider, prefetch/release and sticky headers are driven by it. Cost: zero when no listener is attached.

26. **Public model API** (user 2026-10-20: "do you want to expose the model through a new API, so clients can start using that too?" Yes). The lane model becomes public toolkit API, owned inside SWT and M3JDK Swing (rule 25), parallel names: `M3ListModel`, `M3TableModel`, `M3TreeModel` (row/column counts, parent-child topology as lanes, cell text through interned label ids or a `CharSequence` provider with no per-cell String, images, state bits for checked/expanded/enabled/custom flags, row heights) and `M3Patch` (insert/remove/update/move runs, applied in O(changed + log n)). SWT: `List/Table/Tree.setModel(...)`, `getModel()`, `model.apply(patch)`; TreeItem/TableItem stay as lazy facades for the stock API; the stock virtual path (`setItemCount` + `SWT.SetData`) maps onto an internal model so existing clients keep working. Swing: `M3TableModel implements TableModel`, `M3TreeModel implements TreeModel`, `M3ListModel implements ListModel`, set through the stock `setModel`, with the patch API on top. JFace delta viewers, data binding and the sort/filter proxy models (`M3ProxyModel`) all bind to these types, so a client holds one model and every layer sees the same lanes. The `ViewportListener`/`PartVisible` events complete the client contract. Document with examples; keep the stock API identical.

27. **Template-first views** (user 2026-10-20: "make it so clean that all the other views become just templated"). The Problems view is factored into reusable parts: lane schema, change-source adapters, scope/filter lanes, grouping/sort lanes, delta viewer, counts. Every other view is a spec file `<ViewClass>.m3view` plus an OpenRewrite conversion recipe that removes its rebuild paths and binds it to the template. Contract: integration docs/m3/view-template/VIEW_TEMPLATE.md. View-specific Java exists only for a change source no adapter covers yet, and that adapter then goes into the template for everyone.

28. **Thread architecture: CPU/GPU separation and a Swing-like SWT Synchronizer** (user 2026-10-20: "CPU vs GPU thread separation must exist", "the SWT synchronizer too", "just like Swing"). Three roles, never mixed: the UI thread (OS events, native ops of the resident window, applying one patch per frame); model threads (lanes, deltas, sort/filter/scope, adapters); and in the drawn modes a GPU render thread (consumes immutable generation-numbered snapshots, never touches the model). They exchange only immutable patches/snapshots tagged with a generation; the newest wins, stale ones are dropped. SWT's Synchronizer (common Synchronizer.java: a ConcurrentLinkedQueue of a new RunnableLock per asyncExec) becomes an EventQueue-like dispatcher inside SWT itself (rule 25): an allocation-free ring of runnable slots; keyed coalescing (`asyncExec(key, runnable)`: the latest per key wins, like Swing EventQueue.coalesceEvents and RepaintManager); priority classes (input > layout/paint > model patches > idle); model patches batched and applied once per frame (frame pacing); and deadlock-safe syncExec with timeouts reported via JUL. Stock asyncExec/syncExec semantics and ordering stay identical for unkeyed calls (differential-tested). M3JDK Swing gets the matching model-patch coalescing in EventQueue/RepaintManager. The 338 syncExec sites in correction-sites are reviewed against it (worker threads must not block on the UI).

29. **Behaviours catalogue** (user 2026-10-20: decorators with tooltips, autocomplete, validators, formatters, constrainers, numeric/string formats, enablers/disablers, bulk selection movers, expand/collapse to level, scroll to first/last selected; "what else is used so commonly"). Each behaviour is built once on the lane model with parallel SWT/Swing APIs, attaches to any widget, is declared per view in its `.m3view` spec, and is delta-only. Catalogue with usage evidence and owners: integration docs/m3/behaviours/BEHAVIOURS.md.

30. **Eclipse stays M3JDK21 compatible: Java 21 source and API** (user 2026-10-20, four times: "please change eclipse to M3JDK21 compatible"). Every bundle of every Eclipse fork (platform.ui, platform, jdt.ui, jdt.core, jdt.debug, SWT, Nebula, EMF) declares `Bundle-RequiredExecutionEnvironment: JavaSE-21` (or lower), and its `.classpath` container, `.settings/org.eclipse.jdt.core.prefs` and `jre.compilation.profile` say 21. No Java 22+ syntax: no unnamed variables `_` (write `unused`, `unused2`, ...), no `import module`, no statements before `super(...)`, no primitive patterns. No Java 22+ API. Upstream master moved 100 bundles to JavaSE-25 after 2026-09; branch `m3/jdk21-compat-20261020` in each fork holds the downstream delta. Re-apply the text-lane recipe `docs/m3/jdk21/apply_jdk21.sh` after every merge from upstream (fixed point), then run the gate `docs/m3/jdk21/compile_gate.sh`: errors of `--release 21` on the recipe output minus errors of `--release 25` on the original must be 0. Git Bash `sed`/`awk` drop CR bytes: use `sed -b` and `awk -v BINMODE=3`. Runtime stand-in until a Windows M3JDK21 image exists: Temurin 21.0.12.1 at `F:/jdks/temurin21/jdk-21.0.12.1+1`; the M3JDK21 Windows build (donor: adoptium/temurin-build) is the user's to run.

31. **Machine Oriented Programming (MOP): corrections are memory** (user 2026-10-20: "each time the LLM corrects a recipe, the recipe records that memory for the LLM or coding agent ... the invariant of the self-improving Machine Oriented Programming paradigm ... Atomize and Patternize, annotate and Document"; "a recipe compiler"). Every recipe crate carries an append-only `RECIPE_CORRECTIONS.tsv` (id, date, agent, symptom, cause, correction, files, test_added, lesson). A correction without a row is not finished; a testable correction lands with its test; rows are never edited, a later row supersedes; agents read the ledger before running or changing the recipe. The four verbs: atomize (one leaf, one purpose), patternize (catalogues such as TEMPLATES.tsv and CODE_MAPPING.tsv), annotate (multipass marks with permalinks, `//FLLDCIM:` anchors), document (README, ledgers, receipts). Next leaf: a **recipe compiler** that turns catalogue rows and ledger lessons into executable passes (text lane: awk/sed; Java lane: OpenRewrite) with generated tests. Canonical statement (paradigm by Hitesh Soliwal, Synexia): M3jdk21 `m3/docs/MACHINE_ORIENTED_PROGRAMMING.md`. Reference crate: com.synexia `recipe-crates/m3-eclipse-jdk21-compat-20261020` (14 corrections on day one).

32. **Four mechanical passes before the agent's pass, across all of Eclipse** (user 2026-10-20: "mark mechanical pass two", "pass three: enhance targets, template matching", "pass four: markers across entire Eclipse, code mapping possibilities", "learn code from interface and recipe"). Pass one: grep census of the sites. Pass two: mechanical marks per site (bundle, scope MAIN/TEST/EXAMPLE, type, enclosing method, construct, permalink). Pass three: template matching against `TEMPLATES.tsv` (40 listener and callback families learned from the interface each call registers, each mapped to an enhancement target and its rule). Pass four: code mapping against `CODE_MAPPING.tsv` (Java API usage mapped to M3 replacements: collection lanes, precompute, M3String, M3File streams, the public model API, delta views, JFace forwarding to widgets). Both sweeps stream `git grep` over the bare forks (no checkout); outputs in the integration branch `docs/m3/enhance-targets/` and `docs/m3/code-mapping/` (COUNTS.tsv plus per-repo site lists). The agent's final pass reads these tables; new patterns found there go back into the catalogues (rule 31). Widgets own all the API and JFace forwards to it; editor and text APIs are enhancement targets (T28 DOCUMENT_DELTA, T29 STYLEDTEXT_LANE, M28 TEXT_API_LANE). Every agent pass leaves a **trail** (every command it ran, in order) and **hints** (what the next pass must know: ambiguities, over- and under-matches, precedence), persisted next to its output (TRAIL.tsv, HINTS.md) and read before the next pass starts (user 2026-10-20: "leave trail and hints"). Pass six is the **multipass compiler**: the catalogues are compiled into OpenRewrite recipes (learn from the interface, adversarial verify, critic, deterministic compile) so the marks come from the AST and the same recipes later carry the transforms (crate m3-eclipse-enhance-marks-20261020). Culprit order for the transform lanes (user 2026-10-20): collections first, then StringBuilder/StringBuffer and concatenation, IO, regex, loops (banned: lanes and streams), method length (15 lines); an agent takes the highest-ranked culprit present in its file first; EMF/e4 model code is a target, not a dependency to keep.

33. **No second AST: the compiler's AST during the Maven build is the one AST** (user 2026-10-20: "static analyzers construct their own freaking AST, such a waste of CPU and RAM; the right place is the Maven AST"). Marks, census, metrics and checks run on the trees the compiler already built during `mvn compile` (javac `com.sun.source.util.Plugin` with a `TaskListener`, `Trees`, `Elements`, `Types`; ECJ's AST in the IDE and Tycho builds), never on a private AST of a static analyzer (OpenRewrite LST, PMD, SpotBugs, Checkstyle parsers). One parse, many projections (CPU MULTI-VIEW LAW). OpenRewrite is used only where a rewrite must print source back, and then it consumes the marks the compiler pass produced instead of re-deriving them. The M3 AST is this compiler AST, projected into lanes (parked until the UI is done). First leaf: `M3MarksPlugin` (javac plugin, crate m3-eclipse-enhance-marks-20261020) runs inside the JDK21 gate compile and writes the mark rows; the differential against the grep marks of passes three and four feeds the catalogues (MOP).

## Machine rules (hard)

- **Hidden desktop (user requirement 2026-10-16: never move the user's mouse, never steal focus):** every GUI
  program or UI test runs on the invisible desktop `WinSta0\m3-ui-tests`:
  `bash C:/m3-superset/tools/ui_lock.sh <agent> bash C:/m3-superset/tools/hidden_java.sh <seconds> <log-file> <java args...>`
  (launcher `C:/m3-superset/tools/run_hidden_desktop.ps1`; WM_PRINT captures, uxtheme, window messages work there;
  SendInput/Display.post/SetCursorPos do NOT — tests needing real input are separate, opt-in, and must ask the lead).
  ui_lock.sh hands out up to 3 slots (`M3_UI_SLOTS`), each its own invisible desktop `m3-ui-tests-<slot>`
  (exported as `M3_UI_DESKTOP`, read by hidden_java.sh), so three agents run UI tests at once. Memory probes
  (`tools/commit_free_kb.sh`) have hard timeouts.
  Record references on the hidden desktop too, so both sides render under the same conditions.
- **Reference = official stock SWT:** record the reference with the official jar
  `~/.m2/repository/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar`
  on the class path (harness `ViewportDifferential.isReference`), not only the fork's `original` mode (the fork's
  original paths can deviate from stock, e.g. Tree bar maximum 294 vs 299).
- (legacy) visible-desktop runs used
  `bash C:/m3-superset/tools/ui_lock.sh <agent> bash C:/m3-superset/tools/run_guarded.sh <seconds> <java ...>`.
  A hung UI test leaves a busy window on the user's desktop. Acceptance tests must skip (JUnit assumption),
  not block, when the viewport is inactive. Park the cursor outside the shell before pixel captures.
- **Builds:** every javac/jar build runs ONLY under `bash C:/m3-superset/tools/build_lock.sh <agent> <cmd>`.
  Use `C:/m3-superset/tools/compile_platform.sh <worktree> win32` (compile check) and
  `C:/m3-superset/tools/build_and_tests.sh <worktree> <your-out-dir> <stamp>` (jar + tests). Your out dir:
  `K:/m3-work/agents/<agent>/out`. JAVA_HOME `C:/Program Files/Java/jdk-21`; use `python`, never `python3`.
- **Memory is scarce.** Gates refuse below 4 GB (build) / 6 GB (UI) commit headroom; on refusal wait with a
  bounded retry loop inside one bash script (e.g. up to 30 x 20 s), then retry. Never stop or kill processes
  you did not start (other sessions' `git status` processes are the user's).
- **Disk:** C: ~5 GB, K: ~8 GB free. Worktrees go to `K:/m3-work/agents/<agent>/wt` (blobless repo exists at
  `C:/m3-superset/swt-tree`'s object store; use `git -C C:/m3-superset/swt-tree worktree add ...`).
- **Git:** branch `m3/<widget>-viewport-20261016` from `origin/master` (hsoliwal/eclipse.platform.swt) unless told
  otherwise; commit often with real, plain messages (no Co-Authored-By or "Generated with" lines: user rule, Synexia code is the user's property).
  push to origin; open a **draft** PR against
  `hsoliwal/eclipse.platform.swt` master (no "Generated with" footer).
  Never merge, never force-push, never `--admin`, never touch master directly. Do not change ACLs on
  `C:\m3-work-c` or `C:\m3-superset\i31-build`. Do not touch other agents' worktrees.
- **Truth:** report only what you ran; tests not run are "not run", with the reason.

## Report back (under 500 words)

Branch, commits, PR URL; what is mediated; measured native rules; tests and their exact results; open gaps;
next leaves.
