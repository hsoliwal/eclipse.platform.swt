# Consolidation 2026-10-23: pending work compared, supersets built, PRs opened

User ask: "look at the PRs and compare the pending work: the viewports, the M3JDK; let us consolidate: superset-merge
all PRs, atomize/patternize all repos, consolidate." Everything below is mechanical where it could be (REST dumps,
CPU ordering, server-side merges, javac gates); the only hand decisions are the five JFace conflict resolutions,
each recorded in the superset commit messages.

## 1. What was pending (state at 01:00 UTC)

| Where | Open PRs | Our branches vs master |
| --- | --- | --- |
| UI forks (SWT, Nebula, GEF, PDE, platform, JDT, equinox, EMF, aggregator) | 0 (the owner merged 78 SWT, 71 Nebula, 10 GEF, 4 PDE PRs in the week) | 15 programme branches, all behind master; SWT viewport integration 304 ahead / 44 behind |
| eclipse.platform.ui | 3 agent PRs (#27, #35, #37), all conflicting with master | jface-viewport (6 ahead, conflicting), jdk21-compat, marks-tags (conflicting) |
| M3jdk21 | 162 (158 after the dump): 84 dirty against master, 63 blocked, 11 unknown; 1,293 shared paths | jbs scaffold 1,086 behind |
| com.synexia | 281 (246 after the dump) | two recipe-crate branches |

The requirements themselves (user, 2026-10-23: "you have all the requirements in your session logs", "the PR history and
comments speak of requirements"): `requirements/REQUIREMENTS_ALL.tsv`, rendered as `requirements/REQUIREMENTS.md`: 974 rows,
759 unique directives from the Claude (241) and Codex (518) session logs of 2026-10-07 to 23, and the PR history of all
nine forks (12,600 Synexia PRs, 1,862 M3jdk21 PRs, the Eclipse forks) reduced to what speaks for the user: the owner's
own typed comments (lowercase, short, no code spans) and the sentences of agent-written bodies and comments that quote
the user ("the user asked", "per the user", "(user, ...)"). Agent receipts are not requirements. `REQUIREMENTS_COVERAGE.tsv`
maps each track to the PR or branch that carries it. The pending work above is what those requirements produced that
is not yet on a master branch.

## 2. What was done

| Step | Result |
| --- | --- |
| Forward-merge master into every programme branch (server side) | 13 of 15 merged; 2 platform.ui branches conflicting |
| SWT compile gates on the merged integration head 4c997f14 | win32 860 sources / 0 errors, gtk 651 / 0, cocoa 793 / 0 |
| Superset PRs opened for the owner | SWT #250 (viewport integration) #251 (render modes), Nebula #273, platform #18 #19, platform.ui #44 (Java 21) #45 (JFace superset), jdt.ui #4, jdt.core #5, jdt.debug #3, equinox #1, aggregator #13, EMF #2, GEF #18, PDE #5, M3jdk21 superset PR |
| platform.ui JFace superset `m3/jface-superset-20261023` | jface-viewport + PR #27 + PR #37 (contains #35) merged on master; 5 conflict files resolved on master's lineage; gate 0 errors; supersedes #27 #35 #37 |
| M3jdk21 superset `m3/superset-20261023` | 38 of 158 PR heads merged server side, 120 conflicting listed in the ledger; PR #1861 |
| Synexia superset `codex/superset-20261023` | 72 of 246 PR heads merged server side, 174 conflicting listed in the ledger; PR opened after the API limit reset |
| JFace superset anchored | PR #47 on top of #45: 11,276 compiler-AST marks of org.eclipse.ui.ide, 9,844 anchored lines in 987 files |
| Atomize/patternize receiver | rolled out to the 6 branches that lacked it; present on all programme branches now |
| Workflows (agents) | still no model turns (agent limit): the JFace merge was done by hand, the M3jdk21 conflicts wait for agents |

M3jdk21 superset by track (merged / conflicting): m3/collections 18 / 54, m3/tooling 1 / 15, m3/fs 6 / 8,
m3/qualification 0 / 10, m3/file 1 / 6, m3/runtime-integration 1 / 6, test 1 / 5, src/java.base 0 / 4,
m3/compatibility 1 / 3, m3/ports 3 / 0, the rest 7 / 9.

## 3. Pending work by track after this pass

| Track | Where it lives now | Next |
| --- | --- | --- |
| Viewports (SWT, Nebula, render modes) | PRs #250 #251 #273, gates green | owner merges; then the jdk21 branches and marks on top |
| JFace / Problems view / platform UI | PR #45 (superset), #44 (Java 21), #18 (platform views), PDE #5 | owner merges #45; M3SparseExtents leaf from M3jdk21; anchors regenerated on the superset |
| Java 21 for M3JDK21 | 9 PRs (one per fork), all gates green: the platform FFM residue closed by porting Win32Handler back to JNA with the four later upstream fixes (3c1ea6c) | owner merges |
| M3JDK (collections, fs, tooling, qualification) | superset PR with 38 merged, 120 conflicting by track (m3/collections 72 PRs, m3/tooling 16, m3/fs 14, m3/qualification 10) | conflict pass by agents, PR by PR, module Maven gate |
| Synexia recipes | superset PR with 72 merged, 174 conflicting (recipe-crates 44, src 39, crates 12 of the dumped tracks) | conflict pass by agents, crate Maven gate and RECIPE_MEMORY per crate |
| Atomize/patternize | receiver on every branch | bootstrap (inventory binding) on each final superset, then run.sh. First try on the SWT integration worktree failed: `sha256sum` cannot read the long paths under `.superset/conflicts/pr-209-table-viewport-20261017-0725/...` (leftover conflict copies from the 2026-10-17 superset); deleting that directory needs the owner's approval, then the bootstrap runs |

## 4. Method, reusable

`dump_prs.py` (REST list, mergeable state, files), `build_order.py` (order, overlaps, tracks), `superset_merge.sh`
(server-side merges, conflict ledger), `resolve_hunks.py` (ours/theirs/union/per-hunk resolution, byte-preserving),
the forward-merge loop and `api_commit_dir.sh`. All under S:/m3-scratch/consolidate; copies go to the integration
docs and the recipe crate.
