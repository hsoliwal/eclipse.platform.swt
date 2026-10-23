# Problems / Markers view: handover (resume here)

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "for markers view, document what you did, so Codex can resume if you run out of tokens, or you can
remember after a token reset". Read this file first, then the files in "Read next", in order.

## Where

- Repo: hsoliwal/eclipse.platform.ui (fork of eclipse-platform/eclipse.platform.ui).
- Branch: `m3/jface-viewport-20261019`. Local worktree: `S:/m3-scratch/worktrees/platform-ui`. Commits are pushed after
  every leaf; a 20-minute sweep (K:/m3-work/logs/commit_sweep.sh) checkpoints everything.
- Package: `bundles/org.eclipse.ui.ide/src/org/eclipse/ui/internal/views/markers/`.
- Owner so far: the JFace agent of the lead's session (Claude). Codex or a later session continues from here.

## What exists (code)

| File | Role |
|---|---|
| `M3MarkerLanes.java` (new, ~800 lines) | the one lane model of the view: marker id, marker, severity, interned message/location/resource/path ids, category, counts per severity and category |
| `M3MarkerDeltas.java` | the delta path: IMarkerDelta/IResourceDelta merged per marker id into lanes, debounced job with a superseding generation, per-category merge, window patch; flat presentation and resource-scoped filters go through the lanes too |
| `M3MarkerKeys.java`, `M3MarkerCategory.java`, `M3SlotOrder.java` (new) | sort keys, categories and the slot permutation lane |
| `Markers.java`, `MarkerContentGenerator.java`, `MarkersChangeListener.java`, `MarkerViewerContentProvider.java`, `MarkersTreeViewer.java` | small hooks into stock classes (recipe-applied, see recipe/) |
| `ProblemsView.m3view` | the view template spec (rule 27) |
| `M3PartVisibility` (org.eclipse.ui.workbench) | IPartListener2 partHidden/partVisible, the stand-in until SWT.PartVisible lands (all-widgets track) |
| `M3ViewSpec` | the `.m3view` parser (view template) |

Switch: `-Dorg.eclipse.ui.ide.markers.mode=viewport` turns the delta path on; without it the stock path runs unchanged.

## What exists (docs, docs/m3/markers-view and docs/m3/jface-viewport)

- `DESIGN.md`: stock behaviour, target, equality rules, measurements plan, differential, next.
- `docs/m3/jface-viewport/PROBLEMS_VIEW.md`: the lane model, the delta path, what is still a clean rebuild, proof.
- `FALLBACKS_TO_DELTAS.md`: how each clean-rebuild fallback becomes a delta, including Bug 296695 (scope set-diff).
- `REFERENCE_TREE_VIEW.md` + `TREE_VIEW_API_MATRIX.tsv`: the Problems view as the reference tree view; 305 viewer API
  methods with lane/delta status (mechanical pass: 61 DELTA, 114 FACADE, ...); the adoption recipe for other views.
- `docs/m3/jface-viewport/DELTA_GATE.tsv`: every rebuild site with JUSTIFIED/FAIL.
- `docs/m3/jface-viewport/OPTIMIZATION_LEDGER.tsv`: before/after numbers.
- `INVENTORY.tsv`, `UPSTREAM_DEFECTS.tsv`, `recipe/` (OpenRewrite hooks), `bench-bundle/` (in-IDE bench).
- Integration branch (hsoliwal/eclipse.platform.swt, `m3/viewport-integration-20261016`): docs/m3/correction-sites
  (marked rebuild sites across 12 forks), docs/m3/ui-thread-sites, docs/m3/view-template/VIEW_TEMPLATE.md,
  docs/m3/behaviours/BEHAVIOURS.md, docs/m3/VIEWPORT_AGENT_BRIEF.md (rules 1-29), docs/m3/VIEWPORT_PROGRAMME_PLAN.md.

## Tools (docs/m3/markers-view/tools)

- `build_overlay.sh`: rebuilds the markers classes with the M3 hooks into an overlay of the official Eclipse 2026-09 bundles.
- `apply_recipe.sh`: applies the OpenRewrite hooks (the second pass must report UNCHANGED).
- `run_markers.sh <side> <size> <seed> ...`: one bench or differential run in an isolated Equinox configuration on
  S:/m3-scratch/jface/eclipse (never the installed IDE). UI runs go through C:/m3-superset/tools/ui_lock.sh.
- `markers_round.sh`: a full round (stock and ours, sizes, seeds).
- `run_headless_tests.sh`: the headless unit tests. `COVER=1` adds JaCoCo over the M3 classes.
- `ledger_rows.sh`: bench results to OPTIMIZATION_LEDGER rows. `matrix_status.awk`: fills TREE_VIEW_API_MATRIX statuses.

## State (2026-10-20 13:40) and what is open

Done: lane model, delta path for marker deltas, resource-scoped filters through lanes, flat presentation, the Bug 296695
scope diff against lanes (commit d13d2522a5), the view-template parser and spec, the PartVisible stand-in, bench and
differential harness, API matrix mechanical pass.

Open, in order:
1. **Regression to fix first:** OPTIMIZATION_LEDGER rows "JV-R3 jface viewer mode original->viewport": `table.filter50`
   at 10^5 times out (>60 s) in viewport mode, against 28 s stock. `table.add1pct.sorted` at 10^4 is 99 -> 130 ms.
   The `refresh.sel10.hash` row improved (17,389 -> 4,750 ms). Find the filter path that is not on lanes.
2. DELTA_GATE FAIL row: `CachedMarkerBuilder.java:597,611` (working-set listener: `group.refresh()` then a full
   update). Make it the scope set-diff.
3. Deferred `MarkerEntry` facades: no entry object for rows outside the window; replace `Markers.markerToEntryMap`
   (HashMap) with the open-addressing id table.
4. Differential at 10^4/10^5/10^6 with zero mismatches, and the full-refresh counter = 0. Record the run ids here.
5. TREE_VIEW_API_MATRIX: no TODO; every row has a differential status.
6. Switch the PartVisible stand-in to SWT.PartVisible/PartHidden once the all-widgets track lands it (rule 25).
7. Draft PR to hsoliwal/eclipse.platform.ui once the differential passes (the user merges).
8. Then the template: generate a PDE form section and the Java Outline from specs (rule 27), and the footer,
   header-2, view bar and behaviours from docs/m3/behaviours.

## Rules that apply (brief rules 1-29; the ones that bite here)

Delta-only views, resize/layout and hidden viewports (24); own the implementation, PartVisible and Viewport listeners
(25); public model API (26); template-first views (27); threads (28); behaviours (29). Also: Java only, JUL logging,
recipe hooks, Synexia header on new files, no Co-Authored-By trailers, never merge PRs or force-push, scratch only under
S:/m3-scratch, never write to G:, D: or E:.

## Resume checklist (Codex or a fresh session)

1. `git -C S:/m3-scratch/worktrees/platform-ui fetch origin && git -C S:/m3-scratch/worktrees/platform-ui status`
   (or clone hsoliwal/eclipse.platform.ui and check out `m3/jface-viewport-20261019`).
2. Read: this file, PROBLEMS_VIEW.md, DESIGN.md, FALLBACKS_TO_DELTAS.md, DELTA_GATE.tsv, OPTIMIZATION_LEDGER.tsv.
3. Run `tools/run_headless_tests.sh`; then one `run_markers.sh` round for stock and ours at 10^4 to confirm the setup.
4. Take "Open" item 1 and work down the list. Append each result to this file under "Log".

## Log

- 2026-10-20 13:40: handover written by the lead (Claude) from branch head af302a4438.
