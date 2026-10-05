# SWT Tree Bounded Residency Implementation Plan

> **For agentic workers:** Execute inline with `superpowers:executing-plans`. Track each task below; preserve the canonical recipe custody and the existing public SWT contracts.

**Goal:** Derive and reconcile GTK virtual Tree native rows from the logical visible-plus-overscan window, without native sibling-prefix growth or rebinding exposed `TreeItem` identities.

**Architecture:** Reuse `VirtualTreeTopology` for semantic coordinates and packed state, `VirtualTreeVisibleProjection` for visible preorder, and `VirtualTreeViewport` / `ViewportRuntime.RowWindow` for viewport geometry. Add bounded projection metadata to those owners, then make the GTK model consume the resident projection rather than advertise the full logical count to `GtkTreeView`.

**Tech stack:** Java 21, SWT GTK3, the existing native `SwtVirtualTreeModel`, Maven/OpenRewrite 8.17.1, and the unchanged canonical `M3HashPinnedJavaSnapshotRecipe`.

**Spec:** [Normative UI invariants, Synexia #9164](https://github.com/hsoliwal/com.synexia/pull/9164), [shared viewport owner, SWT #72](https://github.com/hsoliwal/eclipse.platform.swt/pull/72), [Java presentation authority, SWT #75](https://github.com/hsoliwal/eclipse.platform.swt/pull/75), and [native residency evidence, SWT #76](https://github.com/hsoliwal/eclipse.platform.swt/pull/76).

## Active goal and baseline

The active goal tracker contains this bounded-residency milestone. Worktree:
`swt-residency`, branch `codex/gtk-tree-bounded-residency-20261005`.
Baseline is the published [SWT draft #77](https://github.com/hsoliwal/eclipse.platform.swt/pull/77),
commit `96b4447aef074ddf4831ca939212f5cbd1d3e073`.

The first planner correction is already delivered separately: [SWT #77](https://github.com/hsoliwal/eclipse.platform.swt/pull/77) and [canonical recipe #9194](https://github.com/hsoliwal/com.synexia/pull/9194).
It preserves viewport capacity and insertion atomicity. Java 21 compilation and
source replay passed; behavioral/native qualification remains pending.

## Global constraints

- Preserve public signatures, event order, `SWT.SetData`, selection/check/gray/expand behavior, accessibility behavior and exposed Item identity.
- Java topology, state and presentation data remain authoritative; native residency is rebuildable.
- No production row, facade, or native sibling-prefix allocation proportional to the logical count.
- Public `getItems()` may materialize its explicitly requested result; viewport/navigation may not manufacture a prefix.
- Preserve real SWT GC and the established body/header/editor/feedback plane ownership.
- Author source changes in exact recipe payloads; apply through OpenRewrite and refuse source drift.
- Do not manually edit generated JNI files or commit compiled native binaries.
- Use additive branches and commits; leave both existing draft PRs intact.
- Do not claim runtime correctness, performance, strict admission or merge readiness from compilation alone.
- Tests are not added or run unless requested; record those qualification gates as pending.

## Review focus

1. A 500,000-root jump to the middle must not allocate 250,000 native rows; residency follows viewport demand.
2. Cold rows must retain `(parentId, childIndex)` coordinates without creating Java facades during planning.
3. Expanded ancestors and rows near a deep branch boundary must produce correct physical paths, indentation and hit translation.
4. Selection, focus, accessibility, DND and editors must resolve logical identities after a resident window moves.
5. Collapse, insertion, removal, resize and disposal must invalidate projection metadata without recycling pinned Items or exposing stale native iterators.

## Task 1: Inventory and seal the residency cohort

**Files:**

- Read `bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/{VirtualTreeTopology,VirtualTreeVisibleProjection,VirtualTreeViewport}.java`.
- Read `bundles/org.eclipse.swt/Eclipse SWT/gtk/org/eclipse/swt/widgets/{Tree,TreeItem}.java`.
- Read `bundles/org.eclipse.swt/Eclipse SWT PI/gtk/library/os_custom.c` and the corresponding `OS.java` bridge.
- Create a bounded task crate, source manifests, reuse ledgers and progress record under the existing canonical recipe owner.

**Interfaces:** Consume `visibleRowCount()`, `rowAt(long)`, `window(long,int)`, `firstPaintRow()`, `paintRowCount()`, and `nativeModelSnapshot()`; retain their contracts.

- [ ] Trace every native-path consumer from logical coordinates through GtkTreeIter / GtkTreePath and back to a `TreeItem`.
- [ ] Separate API-required materialization from ordinary viewport demand.
- [x] Record the current prefix-growth and GtkTreeView logical-count coupling sites.
- [x] Bind exact target revisions, hashes, licenses and candidate output paths before production mutation.

## Task 2: Add bounded primitive residency planning

**Files:** Recipe payloads for `VirtualTreeVisibleProjection.java` and `VirtualTreeViewport.java`; reuse `VirtualTreeTopology.java` as needed.

**Interfaces:** Produce a caller-owned bounded window of logical row coordinates and native ancestor requirements. Planning must be read-only with respect to facade/topology materialization. Fix the exact API in the task crate after Task 1's native consumer inventory; do not create a parallel topology or window engine.

- [x] Distill the current `rowAt` / `window` algorithm into reusable primitive output lanes for the admitted native cohort.
- [x] Derive row demand from `firstPaintRow()` and `paintRowCount()`.
- [x] Deduplicate required ancestors and retain deterministic parent/child order.
- [x] Bind invalidation to topology and viewport changes, including a window that keeps the same top row while geometry changes.
- [x] Apply the sealed candidate through OpenRewrite and compile the eight shared sources with Java 21 and `-Xlint:all -Werror`.
- [x] Compile all 493 production GTK Java sources with `-Xlint:all`; candidate and baseline both produce the identical 148-warning inventory. Full-owner `-Werror` qualification remains open.
- [x] Record review cases 1–3 as pending behavioral qualification.

Implemented interfaces: `VirtualTreeVisibleProjection.Residency`,
`residencyWindow(long,int,Residency)`, `VirtualTreeViewport.paintResidency(Residency)`
and topology/viewport `generation()`. The caller-owned buffer exposes logical
coordinates, dense physical parents/sibling indices, paint-row entry mapping,
physical path translation and an exact four-lane native packet. Ancestors are
included before the first paint row; the contiguous preorder window reuses its
current ancestor path and introduces each later row once. Planning creates no
`Row` objects, topology slots or SWT Items.

## Task 3: Reconcile a bounded GTK projection

**Files:** Exact recipe payloads for GTK `Tree.java`, `TreeItem.java`, and hand-maintained `os_custom.c`; change `OS.java` only if the current bridge cannot express the admitted snapshot.

**Interfaces:** Consume Task 2's primitive resident coordinates. Native child counts and GtkTreePaths describe the physical projection; public SWT indices and identities describe the logical topology.

- [ ] Keep GtkTreeView's advertised row count bounded; merely switching to the existing full logical model does not satisfy the goal.
- [ ] Implement explicit logical/physical coordinate translation at demand, rendering, hit testing and native callback boundaries.
- [ ] Reconcile the bounded native window under redraw suppression with `finally` restoration.
- [ ] Use Java presentation/state for off-window mutations and preserve pinned facade identities.
- [ ] Remove prefix growth only from paths covered by the complete translation contract.
- [x] Compile the current Java and hand-maintained GTK3 native candidate. Full live adapter reconciliation is still pending.
- [x] Record review cases 4–5 and all unexecuted native gates explicitly.

Completed preparation: the existing `SwtVirtualTreeModel` accepts `swt-residency`
packets, validates dense physical counts and maps entry IDs to Java facade IDs.
Virtual GTK Items retain their semantic IDs in Java; reserved topology slots
cannot be recycled while awaiting a facade. Logical `indexOf` and known parent
lookups use the topology. The attached view still uses the original GtkTreeStore;
its prefix-growth paths remain live until the complete translation is wired.

## Task 4: Publish source custody and qualification state

**Files:** Canonical recipe manifest/payloads and receipts; SWT cohort receipt; reconciled Java/native atom packets when that lane is available.

- [ ] Require an unchanged second recipe application and compare actual published Git trees with the reviewed local trees.
- [ ] Inspect the complete changed-file set and `git diff --check` output.
- [ ] Publish paired draft PRs for the bounded cohort, with exact source and engine identities.
- [ ] Keep behavioral/refusal tests, GUI/native/screenshots, full reactors, atom-packet reconciliation and strict M3 admission pending until executed.
- [ ] Re-read canonical branch source after any merge; draft publication is not merge qualification.

## Current progress

- [x] Read the relevant viewport/planner PR direction and the native-residency limitation in #76.
- [x] Deliver the first shared planner correction in SWT #77 / Synexia #9194.
- [x] Set the bounded-residency goal active and create its isolated worktree.
- [ ] Complete Task 1's callback and lifetime inventory.
- [x] Implement and compile Task 2's primitive planner cohort.
- [ ] Finish Task 3's live GTK residency reconciliation and Task 4's final source readback.
