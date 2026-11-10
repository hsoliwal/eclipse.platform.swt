<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT build 2026-11-10: gate checklist

Candidate `m3-superset-20261110` is built from branch `m3/swt-known-issues-20261110` (PR #340), product source commit `1cab89c6a3f2` (manifest `M3-Source-Commit`).
It is master `4eb4f5c8e0` (#335, #338 and #325 merged) plus the fixes for the 4 remaining known issues.
Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261110.jar`, SHA-256 `ce9ac688acab883ff116f0c330261d0eac2b6fc53725f0b5e216b08fc7ef64d7`.

All gates ran locally on Windows 11 x86_64 with Oracle JDK 21.0.9, `-Xcheck:jni` and `-Dorg.eclipse.swt.internal.enableStrictChecks=true`.
GitHub Actions are disabled on this fork, so there is no CI result. Evidence paths are relative to this directory; `SHA256SUMS` covers every evidence file.
Gates not re-run here (unchanged since `m3-superset-20261109`: no native, JNI, manifest, test-suite or release-tool change on this branch) keep their 2026-11-09 result in `../rc-20261109/CHECKLIST.md`.

| # | Gate | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Known issues fixed (6 of 6 listed for #338/#340) | PASS | 1 cdc76672f6 and 2 b54196d1d8 (#338, merged). 3 ebbb9ea074, 6 fef5f00da1, 4 edadc7534f, 5 1cab89c6a3 (#340). Root causes, fixes and evidence per issue in `PRE_EXISTING_DEFECTS.tsv`. | `PRE_EXISTING_DEFECTS.tsv`, `../known-issues-20261109/` |
| 2 | No public API lost (each commit) | PASS | api_loss_audit lost=0 for ebbb9ea074, fef5f00da1, edadc7534f, 1cab89c6a3 | `gates/api-loss-*.tsv` |
| 3 | Official-SWT oracle for the hidden Table top | PASS | Official 3.135.0 matrix (291 rows); the fork's item API equals official on every row in both widget modes; the M3Model path now answers the same rule | `../known-issues-20261109/table-hidden-top/` |
| 4 | Tree/Table/List/Combo suites in both widget modes after each commit | PASS | Viewport: after 1cab89c6a3, 1,075 found, 1 failed (Combo test_consistency_Segments, clipboard). Original: the same 17 Tree failures as the issue-3 baseline (Tree tests of viewport behaviour run against the stock Tree), plus that clipboard test. Original-mode runs exclude Tree_UpstreamIssues (swt#882: linear in the stock Tree, 1,147 s) and Tree_ViewportEdges.aListenerDisposingTheTreeAbortsShowItem (the stock Tree crashes in COMCTL32 when a listener disposes it inside showItem). | `../known-issues-20261109/gates/` |
| 5 | javac `--release 21` win32 / GTK / Cocoa | PASS | 893 / 682 / 821 sources, 0 errors | `gates/javac-{win32,gtk,cocoa}.*` |
| 6 | M3 render backend compiles (Skija 0.116.3) | PASS | 34 sources, 0 errors | `gates/javac-m3-backend.*` |
| 7 | JUnit sources compile (`--release 21`), including m3gc | PASS | 419 sources, 0 errors (9 `performance/` sources excluded) | `gates/javac-tests.summary.tsv` |
| 8 | Headless JUnit | PASS | 190 found, 187 passed, 0 failed, 3 aborted (GTK-only) | `gates/junit-headless.*` |
| 9 | m3gc (M3 GC over Skija) | PASS | AllM3GcTests 97/97 | `gates/junit-m3gc.*` |
| 10 | Display JUnit: default suite (AllTests without AllBrowserTests), fork natives 4975r19 | PASS (0 real failures in 3 runs) | 6,265 found in each run. Run 1 (alongside the suite gates): 6,058 passed, 8 failed. Run 2: 6,053 passed, 12 failed. Run 3: 6,057 passed, 8 failed. 176-177 aborted by platform or opt-in assumptions, 6 skipped. Every failure is ENV (input on the invisible desktop, the shared clipboard), the AllNonBrowserTests leak check (also on master), or a timing/pixel test that failed in one run only and passed in the other two and in two isolated reruns. All 7 known issues pass. | `gates/junit-display-run{1,2,3}.*`, `gates/DISPLAY_FAILURES-run{1,2,3}.tsv` |
| 11 | Display failures caused by the environment | FAIL (environment of the invisible desktop) | In each run: 6 ENV_INPUT_HIDDEN_DESKTOP (Display.post, focus traversal), 1-3 clipboard (a different dnd test each run: the OS clipboard is shared), 1 SUITE_LEAK_CHECK. FLAKY_NOT_REPRODUCED (5 tests, one run each): M3Model virtual-table pixels, VirtualPageSource check marks (both SWT.VIRTUAL, no TableViewport), M3ThemeImport file watcher, ItemResidencyPrint (Canvas), Control_batch busy cursor (250 ms timer). Classifier: `classify_display_ki.py` rules in `gates/`. | `gates/DISPLAY_FAILURES-run{1,2,3}.tsv`, `gates/junit-display-flaky{1..4}.*`, `gates/junit-display-rerun.*` |
| 12 | `-Xcheck:jni` diagnostics | PASS | 0 in the headless, m3gc and Display JVMs | `gates/junit-*.summary.tsv` |
| 13 | Java 21 preflight (no Java 22+ API) | PASS | 39 rows, 0 failures; all 1,373 jar classes are major 65 | `gates/java21-preflight.tsv` |
| 14 | Superset jar + MAPPING/LEDGER + SHA-256 | PASS | 1,373 classes and the fork's 6 natives, each hashed in MAPPING. LEDGER: #340 candidate, #335/#338/#325 merged. The candidate row names `HEAD`; the manifest pins it (`M3-Source-Commit`). The SVG fragment on the test classpath was not rebuilt (its jar was open in a concurrent run); its sources are unchanged since 20261109. | release assets, `LEDGER.tsv` |
| 15 | M3 exact two-mode report gate, instrumented coverage, GTK/macOS runs, AllBrowserTests | PENDING | Not run here (as for 20261109) | `../rc-20261109/CHECKLIST.md` |
| 16 | OSGi Export-Package of `org.eclipse.swt.m3`, GitHub Actions, merge of #340 | HOLD (user) | user decisions | - |
