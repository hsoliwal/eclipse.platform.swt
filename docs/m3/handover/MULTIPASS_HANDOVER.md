# Multipass handover: Eclipse at Java 21, marks across all of Eclipse, the compiler pass (2026-10-20 to 23)

Resume here. Everything below is committed and pushed; nothing lives only in a chat.

## Branches and heads

| Repo | Branch | Head | What |
| --- | --- | --- | --- |
| hsoliwal/eclipse.platform.ui | `m3/jdk21-compat-20261020` | 4ccfad9125 | 43 bundles at Java 21, gate green, docs/m3/jdk21 |
| hsoliwal/eclipse.platform | `m3/jdk21-compat-20261020` | ad2a72dec6 | 27 bundles, residue 255 = FFM in org.eclipse.core.filesystem (FILL_ITEMS.tsv) |
| hsoliwal/eclipse.jdt.ui | `m3/jdk21-compat-20261020` | d23c6c5770 | 12 bundles, gate green |
| hsoliwal/eclipse.jdt.core | `m3/jdk21-compat-20261020` | 15d3911d64 | 13 bundles, gate green |
| hsoliwal/eclipse.jdt.debug | `m3/jdk21-compat-20261020` | 9fe98db0d8 | 5 bundles, gate green |
| hsoliwal/equinox, eclipse.platform.releng.aggregator, org.eclipse.emf, gef-classic | `m3/jdk21-compat-20261020` | a879dc0a75, 2089d848e8, f38fc409e4, e47f6bbcc4 | metadata and pom/product pins only |
| hsoliwal/eclipse.platform.ui | `m3/marks-tags-20261020` | 263d025 | org.eclipse.ui.ide with 3,162 CNPCDA anchors at every compiler-AST site, docs/m3/marks-ast |
| hsoliwal/eclipse.platform.swt | `m3/viewport-integration-20261016` | 6b114507b381 | docs/m3: enhance-targets, code-mapping, plan, culprits, marks-ast, brief, plan |
| hsoliwal/com.synexia | `codex/m3-eclipse-jdk21-compat-20261020` | cec9ad5a90e0 | crate m3-eclipse-jdk21-compat-20261020: text-lane recipe, gate, passes 2 to 5, culprits, RECIPE_MEMORY |
| hsoliwal/com.synexia | `codex/m3-eclipse-enhance-marks-20261020` | 2b536b43c4e8 | crate m3-eclipse-enhance-marks-20261020: M3MarksPlugin (javac), compiled mark recipes, RewriteTest gate |
| hsoliwal/M3jdk21 | `m3/jbs-enhancements-scaffold-20261020` | c262f27220 | m3/backports/jbs: 5,299 fixed JBS enhancements of 22 to 27, 733 already in jdk21u |
| hsoliwal/M3jdk21 | `m3/mop-invariant-20261020` | 2d817f3bbe | redundant with PR #1592 (m3/mop/SELF_IMPROVING_RECIPES.md); delete or ignore |

## The passes

| Pass | Tool (crate m3-eclipse-jdk21-compat unless noted) | Output |
| --- | --- | --- |
| 1 census | `census/census_all.sh`, `census/census_lines.sh` | JDK21_CENSUS_ALL.tsv, CENSUS.tsv per fork |
| recipe | `recipe/apply_jdk21.sh`, `recipe/unnamed_to_named.awk`, `recipe/apply_jdk21_pins.sh`, `recipe/fix_collisions.sh` | bundles at Java 21; `test/test_recipe.sh` 27 cases |
| gate | `gate/compile_gate.sh`, `gate/regate.sh` | errors(--release 21) minus errors(--release 25), BOM files excluded |
| 2 site marks | `passes/jdk21_pass2.sh` | PASS2_MARKS.tsv per fork (bundle, scope, type, enclosing method, construct, permalink) |
| 3 enhance targets | `passes/sweep.sh` with `catalogues/TEMPLATES.tsv` (40) | docs/m3/enhance-targets (43,933 main sites) |
| 4 code mapping | `passes/sweep.sh` with `catalogues/CODE_MAPPING.tsv` (33) | docs/m3/code-mapping |
| 5 plan | `passes/pass5_plan.sh`, `passes/LANES.tsv` | docs/m3/plan/PLAN.tsv: 13,919 files with their ordered approach |
| culprits | `passes/culprits.sh`, `passes/method_length_stream.sh` | docs/m3/culprits: collections 27,358; StringBuilder/concat 90,948; IO 7,481; regex 3,005; loops 60,073; methods over 15 lines 56,417 |
| 6 compile | crate m3-eclipse-enhance-marks: `compiler/learn_cpu.py`, `compiler/compile_marks.py` | 407 method patterns, 58 listener types, 55 mark recipes + composite; `mvn test` green |
| 7 compiler AST | `M3MarksPlugin` (javac plugin in the crate jar) | docs/m3/marks-ast/ui.ide: 4,319 marks; differential BOTH 1,993 / AST 1,184 / grep 3,673 |
| annotate | `compiler/tag_sites.awk` | anchors `//FLLDCIM: MARK — <template> <target> <kind>` on `m3/marks-tags-20261020` |

Run the compiler pass on a bundle: `javac -processorpath <crate jar> -Xplugin:"M3Marks <crate-dir> <out.tsv>"
--release 21 -implicit:none -XDshould-stop.ifError=FLOW @<gate args> @<sources>` (the gate args carry the p2 pool
class path; `S:/m3-scratch/jdk21/pass7/` holds the ui.ide run). Quote the -Xplugin line inside an @argfile.

## Rules that came out of this work (VIEWPORT_AGENT_BRIEF.md)

30 Eclipse stays Java 21 for M3JDK21. 31 MOP: every recipe correction is a RECIPE_MEMORY.tsv row. 32 four
mechanical passes before the agent's pass; trail and hints; culprit order collections > StringBuilder > IO >
regex > loops > method length. 33 no second AST: marks come from the compiler's AST during the build.

## Next leaves (after the token reset)

1. `eclipse.platform` FFM residue: restore the JNA Win32Handler from upstream 9dc9a9359b (FFM came in 31b3f6abb)
   and port the four later fixes; gate to 0 (FILL_ITEMS.tsv on the branch).
2. Run the compiler pass on every Java-25 bundle of the five forks (the gate args exist per fork) and tag them on
   `m3/marks-tags-20261020` branches; publish per-bundle differentials; feed grep-only rows back into the
   catalogues (MOP), AST-only rows into the regexes.
3. Maven plugin wrapper (user: "save this multipass approach as recipe or Maven plugin"): a mojo that runs javac
   with M3Marks on the project's sources and class path, the tag pass, and the method-length/culprit tables.
4. Transform lanes in culprit order, as recipes consuming the anchors: collections (M3 lanes), m3string, m3file,
   precompute, loops to lanes, method split. Each lane: a crate with RECIPE_MEMORY.tsv, gate, differential.
5. Learn-from-interfaces workflow (`multipass-compiler-learn-interfaces`, run wf_4c2d0557-a90) when agents are
   available: it refines the CPU-learned patterns (declaring supertypes, accessibility) and leaves trail and hints.
6. JBS rows to classify (4,566); jdt.core test class path for a sharper gate; merge the JDK21 branches into the
   working branches and move the markers harness to Temurin 21.

Pitfalls paid for are the RECIPE_MEMORY.tsv rows of both crates (22 so far): read them before touching the tools.

## Consolidation 2026-10-23 (same line, later the same day)

`docs/m3/consolidation/README.md` is the report. Supersets: platform.ui PR #45 (JFace superset) + #47 (anchors),
M3jdk21 PR #1861 (38 merged / 120 conflicting), com.synexia codex/superset-20261023 (72 merged / 174 conflicting),
13 forward-merged programme branches with PRs (#250 #251 #273 #18 #19 #44 #4 #5 #3 #1 #13 #2 #18 #5). Requirements
from the session logs and the PR history: `docs/m3/consolidation/requirements/REQUIREMENTS_ALL.tsv` (1,371 rows).
Next: the conflict passes (120 + 174 PRs, one PR at a time, agents), the atomize bootstrap once `.superset/conflicts`
may be deleted, then the transform lanes in culprit order on the merged supersets.

## Consolidation 2026-10-23, afternoon (session a6de83fe continued after compaction)

- Compiler-AST marks + anchors on every Java-25 bundle of the other four forks (javac plugin M3Marks inside the JDK21
  gate compile, `pass7/marks_fork.sh`): jdt.debug 18,707 marks / PR #4; platform 14,702 / PR #20; jdt.core 72,737 / PR #6;
  jdt.ui 36,142 / PR #5, each `m3/marks-tags-20261020` stacked on the fork's Java 21 PR, line counts unchanged.
- Maven form of the marks pass (user: "save as recipe or Maven plugin"): no new plugin, a maven-compiler-plugin profile
  (`recipe-crates/m3-eclipse-enhance-marks-20261020/maven/sample`), proof 5 marks on a module with no Eclipse class path.
- M3jdk21 superset (`m3/superset-20261023`, PR #1861): the batches were wrong until fixed (grep -P skip test dead in this
  Git Bash, python got POSIX paths under MSYS_NO_PATHCONV, rename detection fetching every blob, zdiff3 base sections,
  gate build output staged, orphaned batch processes after a stop, drifting conflict kinds): 8 RECIPE_MEMORY rows and the
  tools in `recipe-crates/m3-superset-merge-20261023` (72a9eacc06e0). State: 35 PRs resolved (hand + compiler-arbitrated),
  58 refused for C/JNI or workflow conflicts; their reading packets (conflict-marked files, SUMMARY.tsv, HUNKS.tsv with a
  class per hunk) are on the superset branch under `m3/docs/superset/reading-20261023`; gate residue removed
  (ab83dbe678a8); the workflow-only refusals re-run under WORKFLOW_POLICY=ours (batch_resolve4.sh).
- Synexia superset (`codex/superset-20261023`, PR #13187): the conflict table held three runs' rows for 175 PRs; one row
  per PR now, re-classified against the current head (`reclassify_pending.sh`). `api_merge_ledger.sh` merges a ledger-only
  PR in 20 s without worktree or push (merge-tree, unions, blobs API, tree rebuilt along the resolved paths, two-parent
  commit, fast-forward ref): 3 PRs merged by it (12863, 12534, 12860 already contained). The loop over the ~90 remaining
  ledger PRs (`run_api_loop.sh`) is blocked by the Claude Code auto-mode classifier ("Merge Without Review"): the owner runs
  `./run_api_loop.sh K:/m3-work/com.synexia hsoliwal/com.synexia codex/superset-20261023 CONFLICTS.com.synexia.tsv
  resolved.com.synexia.tsv` from S:/m3-scratch/consolidate, or adds a permission rule for it. The javac-gated batch
  (batch_resolve3.sh, fallback class path + sibling sources) runs over the 65 source-conflict PRs.
- Workflow agents: unavailable all day (the probe stalled on 6 attempts); everything ran as CPU scripts and one model.
- Owner decisions still open: merge the open PRs; delete `.superset/conflicts` (SWT integration branch) for the
  atomize bootstrap; delete the redundant M3jdk21 branch m3/mop-invariant-20261020.

## Later the same afternoon (2026-10-23, after 05:30 UTC)

- M3jdk21 superset: the batches run on S: with timeouts and a dedicated loop descriptor (Maven had eaten the loop's
  stdin); candidates per PR are superset:ours, superset:theirs, ours, theirs (hunk classes from diff3 markers: add/add
  unions, modify/modify from one side), every module above a conflicted file gated; three JSON-ledger PRs merged through
  the API path with the recorded scalar policy (JSON_SCALAR_POLICY=ours: superset value kept, PR value in the row).
  Reading packets now carry base sections and HUNKS_DIFF3.tsv (846 hunks: 493 modify/modify, 339 add/add; C: 114 vs 21).
  C stays with the owner: no C toolchain here, and the auto-mode classifier refused an ungated C union.
- Synexia superset: conflict table rebuilt (one row per PR, kinds against the current head: 94 LEDGER, 5 CLEAN_NOW,
  21 JAVA, 36 MIXED, 5 already contained); the javac-gated batch runs over the source conflicts; the ledger loop
  (run_api_loop.sh, 20 s per PR) still needs the owner (classifier).
- JBS scaffold: reading passes committed on m3/jbs-enhancements-scaffold-20261020 (m3/backports/reading/): 41
  collections-lane rows (16 confirmed, 14 docs, 8 preview holds, 3 API holds) and 195 java.lang/java.util rows (61
  adapted, 30 string-lane for the STRING owners, 33 docs, 18 API holds, 9 preview holds, runtime/native/data holds).
- Collections lane scaffold (culprit one): recipe-crates/m3-eclipse-collections-lane-20261023 (0281f8f75ab4): donor
  inventory (FOSS_REUSE_DECISION.tsv), TYPE_MAP.tsv (15 rows with the BCP condition), SITES.tsv (compiler-attributed
  sites per fork, main vs test), report-mode FindTypes recipe, gates, leaf order L1..L5; packaging gap: m3-collections
  as a JavaSE-21 bundle for the forks' target platform.
- M3MarksPlugin marks M10 (boxed-primitive generics on fields, locals, parameters); the four forks' marks tables are
  being recomputed with it (pass7/marks_only.sh -> marks2.tsv); the anchors are not re-tagged until tag_sites.awk is
  proven idempotent on already-tagged lines.
- Marks noise: jdt.core's 40k javac errors sit in tests.model ASTConverter tests and org.eclipse.test.* deps; main
  bundles attribute cleanly (13,427 marks in org.eclipse.jdt.core).

## Evening 2026-10-23 (after 06:30 UTC)

- Strings lane scaffold (culprit two): recipe-crates/m3-eclipse-strings-lane-20261023 (cf020d31ab39): SHAPE_MAP.tsv
  (builder chains, StringBuffer, interned comparisons, intern, constant formats), donors (M3jdk21 M3String runtime,
  Synexia string recipes), SITES.tsv (M13/M14/M33 per fork main vs test), report recipe, leaf order L1..L4. Lesson:
  plain + is already an M3 tuple on M3JDK21; the lane rewrites what the runtime cannot see.
- M10 anchors pushed on the four marks branches (jdt.debug 4f96d3b, platform ef30611, jdt.ui 7662d044, jdt.core
  34f7d8b); marks2.tsv on each branch; the lane crate's SITES.tsv carries the M10 counts (jdt.ui 138 main).
- JBS scaffold repaired twice (an empty table, then a truncated one) and finalised at 9783cc811602: 5,299 rows, the
  classifier writes UTF-8 and re-reads its own output; reading-pass rules folded in (HotSpot components hold-runtime,
  jpackage/jshell/jdeps tooling).
- Superset batches: retries now get a fresh worktree directory and a time-stamped side branch (a stale remote side
  branch made a push and a server merge fail); infrastructure refusals were separated from compiler verdicts and
  three PRs found already contained; M3jdk21 at 62 resolved / 66 refused (58 C/JNI, the rest compiler verdicts);
  Synexia source batch stops early (trace run in progress), 23 resolved; the baseline compile state of the M3jdk21
  modules on the superset head is being measured because every candidate of some PRs fails with one error.

## Superset head repair and the classifier boundary (2026-10-23, 07:00 UTC)

- The M3jdk21 superset head itself did not compile (m3/collections 56 errors, m3/file 88): clean server-side merges
  appended the same method block twice and whole-file side choices dropped methods other merged files call. Two
  mechanical repairs (dedupe_methods.py, donate_methods.py; compiler-arbitrated) took m3/collections to 12 and landed
  (41fb35c259db); m3/file needs a whole-file donor of M3File.java from PR 1619 or 1755 (REPAIR_20261023.md on the
  branch). Every "neither side compiles" verdict on PRs touching those modules is provisional until the head compiles.
- The Claude Code auto-mode classifier refused: the bulk ledger loop (run_api_loop.sh), the script landing gated
  merges through the API, an ungated C union, and the m3/file donor run. Single API merges, the push-based batches and
  docs commits were allowed. Owner options: add permission rules for S:/m3-scratch/consolidate/run_api_loop.sh and
  the repair tools, or run them: `./run_api_loop.sh K:/m3-work/com.synexia hsoliwal/com.synexia codex/superset-20261023
  CONFLICTS.com.synexia.tsv resolved.com.synexia.tsv` (94 ledger + 5 clean PRs, 20 s each) and
  `python -I donate_methods.py <mvn log> <worktree> <clone> <heads>` on m3/file.
- The Synexia source batch (batch5, traced) runs at about nine minutes per PR over 47 PRs; its worktrees under
  S:/m3-scratch/worktrees must not be touched while the todo file exists.

## 2026-10-23, 07:30 UTC: the whole-file resolutions dropped superset work

- `audit_theirs.py` over the 23 M3jdk21 PRs resolved as "theirs": 68 of 69 conflicted source files lost superset lines,
  3,703 lines (M3ASTPC.java 628, m3/file 530, parser 455, java.util.concurrent progress monitors 266, collections 319).
  The superset head's own compile errors made every superset-side candidate fail, so the gate took the only candidate
  that compiled. Audit and plan: `m3/docs/superset/AUDIT_THEIRS.md` on the branch.
- Fixes written, both under adversarial review by workflow agents before they run: `batch_resolve5.sh` (baseline-aware
  gate: only errors the head does not already have count; whole Maven modules, plain source roots derived from the
  package declaration, JDK modules, jtreg parse, python/xml/sh checks; empty units and crashed javac fail; no whole-file
  candidate) and `repair_theirs.sh` (hunk-level re-merge of each damaged file, restoration replayed on the current head,
  same gate, side branch + server merge). `gatelib.sh` holds the shared gate functions.
- Workflow agents work again after the model switch (probe 9 min); the JBS reading pass of the 1,997 remaining
  candidate rows runs as 19 chunk agents with a skeptic per chunk.
- C conflicts: all 73 distinct hunks have proposals (`C_PROPOSALS.tsv`, `C_PER_PR.tsv`): 23 PRs' C side equals the
  superset, 4 need one port (1513's span-bounded read), 22 need the owner (H1/H68: PR 1619's M3IndexMaskWindow.c is the
  fuller file; H24 M3StateRelation compose body).

## 2026-10-23, 08:00 UTC: session restart

The Claude Code session restarted after the audit; the crate commit (ad087d85b86b), the ledgers, the PR bodies and the
docs had landed. The three workflows (reviews of batch_resolve5.sh and repair_theirs.sh, the JBS reading pass) were
resumed from their run ids with cached agent results. Next in order: apply the review findings, run batch_resolve5.sh
over the 11 pending M3jdk21 PRs and the 47 pending Synexia source-conflict PRs, run repair_theirs.sh over the 23
whole-file resolutions, merge the JBS chunk readings into one table and commit it, publish the snapshot.
- 08:15 UTC: DRY_RUN=1 added to batch_resolve5.sh and repair_theirs.sh; the dry runs over the 11 pending M3jdk21 PRs, the 23 whole-file repairs and the 47 pending Synexia PRs run while the reviews finish. Next: C_OURS_PRS (23 PRs whose C side the reading pass found equal to the superset) through the batch with the C files kept as the superset has them; JBS chunk merge.
- 08:05 UTC: lanes three to six scaffolded as crates on the recipe branch (io 764652740406, regex cfe4cb8ad36b, loops 5c78b9f56bfd, method-length 847146af2710): site inventories from both passes, donors, shape maps, gates, leaf order. The marks plugin gets 48 method patterns (String regex methods, Pattern/Matcher, java.io.File, Files, stream constructors, ZipFile); the four forks' marks are recomputed with them. Dry runs: M3jdk21 PR 1406 accepted by the baseline-aware gate (m3 module 0 new over 460 baseline errors; java.base 0 new).
- 08:55 UTC: platform.ui PR #49 (m3/sparse-extents-20261023 -> m3/jface-viewport-20261019): M3SparseExtents + M3HeapGrowth inlined into bundles/org.eclipse.ui.ide under com.m3.collections (x-internal export), M3SparseExtentsTest under org.eclipse.ui.tests; javac --release 21 gate 0 errors, test compiles against junit-jupiter 6.1.3. Marks: M12/M24/M25/M31 anchors on the four m3/marks-tags-20261020 branches (jdt.debug e3bb0c60 308 lines/102 files, platform 48ab29f7 727/183, jdt.ui c5e7c5fb 612/209, jdt.core 5ca3f1ba 1297/192); io and regex lane SITES.tsv recounted from the extended plugin tables (d27df33c8208, 2e6ebf3c1f19 on codex/m3-eclipse-enhance-marks-20261020; M12 main jdt.ui 277, jdt.debug 133, jdt.core 126, platform 79; M31 main platform 276, jdt.ui 239, jdt.debug 137, jdt.core 112). JBS reading pass: 18 of 19 chunks read, 2,123 rows merged (JBS_READING_PASS_ALL.tsv) and 47 skeptic disagreements tabled (SKEPTIC_DISAGREEMENTS.tsv), commit a0994e2c2475 on m3/jbs-enhancements-scaffold-20261020; chunk01 (110 rows) stalled and the subagent session limit (resets 11:00 UTC) stopped two skeptics: re-run `Workflow(scriptPath jbs-reading-pass-wf_1feb4367-fb4.js, resumeFromRunId wf_1feb4367-fb4)` after the reset. Dry runs so far: M3jdk21 batch5 1406 DRY (ledger-only row, suspicious: a hand start_pr_merge shows 10 conflicted files; add per-PR merge logging after the runs finish), 1608/1614/1733 DRY-REFUSED (no candidate compiles over the head), 1766 running; Synexia dry on 12416 since 07:53 (the gate compiles, subprocesses alive). Reviews still running (batch 3 verdicts: finish_pr_merge.sh returns the worktree-remove status; repair 3). Synexia checkpoint caf7c1a72dd4 (2,822 files, codex/m3-java-corpus-extend-20261009) is behind the remote by three 2026-10-17 commits: forward merge under test, pushed to a preserve branch if it conflicts.
- 09:25 UTC: the two review workflows (batch_resolve5 / repair_theirs) confirmed 20+ defects; fixed as new files next to the running v1 scripts: batch_resolve6.sh, gatelib2.sh, repair_theirs2.sh, start_pr_merge2.sh, finish_pr_merge2.sh, resolve_hunks2.py (crate 64eafde4e55e, 9 RECIPE_MEMORY rows). Key rules: crash sentinels are verdicts (GATE_FAIL), never keys the baseline cancels; trimmed unit lists (the " " plain root refused every Maven-only PR: 1614/1766/1846/1847 rows said "plain[] 1 new over 0 baseline"); start_pr_merge logs per PR + MERGE_HEAD proof (PR 1406 false clean); dry rows only in .dry, "transient:" refusals retried; class path per pom hash; diff3 separator + prefix/suffix union; repair replays head-first, counts lost head lines (rejects > 0), add/add never whole-file, lands by compare-and-swap ref update. Smoke (dry): 1614 DRY-RESOLVED superset:theirs (0 new over 49), 1766 DRY-RESOLVED ours (0 new over 164). Full v6 dry runs started 09:25 (batch6.M3jdk21.dry.log, repair2.dry.log); the v1 Synexia dry run on PR 12416 cannot be stopped (classifier: Interfere With Workloads) and keeps running from the v1 files. platform.ui PR #49: M3SlotOrder (the existing consumer of M3SparseExtents) compiles with the port, 0 errors (comment on the PR).
- 09:50 UTC: S: ran out of space (951 GB used, 2.5 GB free: about 120 track directories from the 19th and 20th, donor fork clones and worktrees) while the v6 dry runs executed; rows written after 09:30 are invalid (cp: No space left on device in repair.dry.log). TaskStop stops only the harness wrapper: batch_resolve6 (PID 36360), repair_theirs v1 (24844) and the stalled Synexia batch_resolve5 (39004) run on as orphans and the classifier refuses to kill process trees; they write only .dry rows. Audits running: consolidate/SCRATCH_SIZES.tsv (du per track), SCRATCH_REPO_AUDIT.tsv (dirty, untracked, unpushed per repo), WORKTREE_AUDIT.tsv. Rule for the cleanup: only clean, fully pushed, regenerable clones are proposed; nothing with unpushed branches; live worktrees never. The v6 dry runs restart after the cleanup with a separate ledger copy (OUT=resolved.M3jdk21.v6.tsv) and WT_BASE M3jdk21v7, so the orphans and the rerun never share files. First batch review workflow finished: all confirmed findings are in the v6 files (its JAVAC_FAILED and partial-log findings match gatelib2 javac_status); poisoned v5 baseline cache mvn_80b335bcdfdb (javac ABNORMAL crash accepted as 2,845 baseline keys) removed. Regex lane: L1 inventory committed (124 Pattern.compile sites, 38 L1 candidates; 9cb5cafe30a6), donor Error Prone ConstantPatternCompile ADAPT; the L1 transform goes in as an OpenRewrite recipe (rule 20).
- 11:15 UTC: regex lane L1 is real: OpenRewrite recipe M3HoistConstantPatternCompileRecipe (lane crate m3-eclipse-regex-lane-20261023, 5 RewriteTest cases green, Error Prone ConstantPatternCompile adapted, NLS tag groups carried, bland names replaced by the enclosing method, same arguments share one field, static fields skipped), runner M3RegexLaneRunner (no fork class path needed), baseline-aware fork gate (apply_fork.sh / apply_leaf.sh, quoted argfiles). Landed: eclipse.jdt.debug PR #5 (2 files, gate 0 new errors). platform (4 files), jdt.ui (3), jdt.core (1) gate green over their pre-existing class-path errors and land after the disk cleanup. L2 inventory (306 String regex sites; plain replaceAll 27, split 114 = JDK fast path for one char) with the existing Synexia recipe M3RegexLiteralCallRecipe as the transform. Disk: S: hit 0 bytes at 10:50 UTC (the scratch drive also holds cpullm, SYSL-*, codex-* trees that are not ours); the orphan dry runs ended, their rows after 09:30 are invalid; removed: finished-track worktrees verified clean and identical to origin (eclipse.jdt.ui, nebula-widgets+rwt, egit, gef-classic, m3jdk-swing-api, m3jdk-ui-interning, swt-interning, swt-sync, marks-platform.ui, swt-collections) and the baseline worktrees. Preserved: platform-ui WIP merge -> preserve/jface-viewport-wip-20261023 (bfe9344666), synexia-jdk21-crate checkpoint -> preserve/synexia-jdk21-crate-checkpoint-20261023, Synexia checkpoint caf7c1a72dd4 -> preserve/m3-java-corpus-extend-checkpoint-20261023 (163 conflicting paths against the remote), swt-collections pushed. Consolidation: v6 dry runs must be re-run once S: has 20+ GB (separate ledger copy and WT_BASE); the Synexia v5 orphan (PID 39004) still runs from the v1 files.
- 10:45 UTC: regex lane L1 and L2 landed on all four forks as PRs for the owner: jdt.debug #5 (L1, 2 files) and #6 (L2, 3 files); platform #21 (L1, 4 files) and #22 (L2); jdt.ui #6 (L1, 3 files; L2 found no plain replaceAll/matches in main bundles); jdt.core #7 (L1) and #8 (L2). Every gate: javac --release 21 over the changed files, 0 new errors over the baseline of the same files (quoted argfiles, root-independent keys). Worktrees back on m3/jdk21-compat-20261020, clean. Scratch cleanup done (5.1 GB free on S:); the consolidation dry runs still wait for 20+ GB. Next leaves: multi-character plain split -> hoisted Pattern.split; then the IO lane L1 (java.io.File attribute chains) with the same runner/gate pattern.
- 10:50 UTC: L2 re-run with typed parsing (runner --classpath-args, the fork -cp jars): jdt.ui gained PR #7 (2 files); the other forks had no further site. Full PR list of the regex lane: jdt.debug #5 #6, platform #21 #22, jdt.ui #6 #7, jdt.core #7 #8. Crate at the latest API commit on codex/m3-eclipse-enhance-marks-20261020.
