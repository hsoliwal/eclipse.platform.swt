# Brief: every platform.ui surface on the viewport (beyond views)

User, 2026-10-20: "all platform ui", after "next target is the Problems view and then all the platform UI using same viewport and DOOPS", "all delta based view", "platform ui is like a downstream giving requirements". Read first:
- C:/m3-superset/tools/VIEWPORT_AGENT_BRIEF.md (rules 1-25: one model, delta-only views, resize/layout and hidden-viewport deltas, own the implementation, SWT.PartVisible);
- C:/m3-superset/tools/OPTIMIZATION_PASS.md;
- PROGRAMME_PLAN.md sections 00g, 00j, 00l, 00m;
- on the JFace branch m3/jface-viewport-20261019 of hsoliwal/eclipse.platform.ui: docs/m3/markers-view/REFERENCE_TREE_VIEW.md, TREE_VIEW_API_MATRIX.tsv and FALLBACKS_TO_DELTAS.md.

## Division of work

The JFace agent (SendMessage "ae5acc369949b2347") owns the JFace viewers, the Problems view (the reference tree view) and the workbench views. You own every other platform.ui surface that shows lists, trees or tables. Reuse the JFace agent's delta viewers and the reference parts: no second model and no private copies. Branch m3/platform-ui-surfaces-20261020 off m3/jface-viewport-20261019, in a worktree at S:/m3-scratch/worktrees/platform-ui-surfaces. Merge forward; never copy.

## Surfaces, in order of measured waste

1. FilteredItemsSelectionDialog and its users: Open Resource, Open Type (via JDT UI, fork hsoliwal/eclipse.jdt.ui), Open Plug-in. This is the classic 10^5-10^6 item list; the pattern filter and the history merge must run as the sort-filter superseding-generation pattern on lanes.
2. Quick Access (Ctrl+3) and the command/keys lists.
3. FilteredTree/PatternFilter users: the Preferences dialog tree, Show View, New wizard selection, Keys preference table.
4. Selection dialogs: ListSelectionDialog, ElementTreeSelectionDialog, CheckedTreeSelectionDialog, ResourceListSelectionDialog.
5. Wizard pages with viewers (import/export trees, working sets), the Properties dialog tree, and the Perspective and Window switchers.

For each surface:
- an inventory row (SURFACE_INVENTORY.tsv: class, viewer, change sources, full-rebuild sites with file:line, delta path);
- measure before (10^4/10^5/10^6 items: keystroke-to-first-visible-page latency, UI-thread time per keystroke, allocations, retained heap);
- convert;
- a stock-vs-ours differential (same rows, order, selection and filter results after the same input sequence) with zero mismatches and a full-rebuild counter of 0;
- a DELTA_GATE.tsv row and an OPTIMIZATION_LEDGER.tsv row;
- one example.
Re-run C:/m3-superset/tools/delta_census.sh after each surface and record the drop for platform.ui (baseline 433 sites in main code).

## Machine rules

- Never merge PRs, never force-push. Draft PRs only.
- No Co-Authored-By or "Generated with" trailers.
- Java only, JUL logging, hooks through OpenRewrite recipes (second pass UNCHANGED), Synexia header on new files, contributor line on modified upstream files.
- Scratch under S:/m3-scratch/platform-ui-surfaces. Never write to G:, D: or E:.
- Builds through C:/m3-superset/tools/build_lock.sh platform-ui-surfaces <cmd>; UI runs through C:/m3-superset/tools/ui_lock.sh platform-ui-surfaces <cmd>.
- Commit and push after every surface. A surface without numbers and a passing differential is not done.

## Marked sites to fix (2026-10-20)

The lead marked every full-rebuild, per-element-map and syncExec site across the Eclipse forks: integration branch m3/viewport-integration-20261016 (hsoliwal/eclipse.platform.swt), docs/m3/correction-sites/ (CORRECTION_SITES.tsv with permalinks; FINAL_PASS_MODEL_LISTENER.tsv with a verdict and owner per model-listener rebuild; re-run with recipe/correction_sites.sh). Filter the rows owned by your track (owner column, or by repo and path) and close each one: either fixed (record the commit) or JUSTIFIED in your DELTA_GATE.tsv with numbers.
