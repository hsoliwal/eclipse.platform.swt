<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT build 2026-11-10: gate checklist

Candidate `m3-superset-20261110` is built from branch `m3/swt-known-issues-20261110b` (PR #342), product source commit `c96deadbca55` (manifest `M3-Source-Commit`).
It is master `f29eac6a4d` (#335, #338, #325, #339 and #340 merged) plus the fixes for known issues 4 and 5, cherry-picked from #340's branch after #340 was merged at fef5f00da1.
Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261110.jar`, SHA-256 `4848df52f4c94582400d4fa8cc0b258f8d45411497d712e48573b1011844a4ca`.

All gates ran locally on Windows 11 x86_64 with Oracle JDK 21.0.9, `-Xcheck:jni` and `-Dorg.eclipse.swt.internal.enableStrictChecks=true`.
GitHub Actions are disabled on this fork, so there is no CI result. Evidence paths are relative to this directory; `SHA256SUMS` covers every evidence file.
`gates-build2/` holds the gates of this jar. `gates/` holds the same gates for the first build of the fixes (at 1cab89c6a3, before #339 and #340 merged; jar SHA-256 `ce9ac688acab883ff116f0c330261d0eac2b6fc53725f0b5e216b08fc7ef64d7`), including three Display runs. The product source of the two builds differs only by #339 (CTabFolder.java and its new test).
Gates not re-run here keep their 2026-11-09 result in `../rc-20261109/CHECKLIST.md` (no native, JNI, manifest or release-tool change since).

| # | Gate | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Known issues fixed (6 of 6 listed for #338/#340) | PASS | 1 cdc76672f6, 2 b54196d1d8 (#338); 3 ebbb9ea074, 6 fef5f00da1 (#340); 4 9f466c0895, 5 a706059ff5 (#342; first committed as edadc7534f and 1cab89c6a3). Root causes, fixes and evidence per issue in `PRE_EXISTING_DEFECTS.tsv`. | `PRE_EXISTING_DEFECTS.tsv`, `../known-issues-20261109/` |
| 2 | No public API lost (each commit) | PASS | api_loss_audit lost=0 for ebbb9ea074, fef5f00da1, edadc7534f, 1cab89c6a3 and the cherry-picks 9f466c0895, a706059ff5 | `gates/api-loss-*.tsv`, `gates-build2/api-loss-*.tsv` |
| 3 | Official-SWT oracle for the hidden Table top | PASS | Official 3.135.0 matrix (291 rows); the fork's item API equals official on every row in both widget modes; the M3Model path now answers the same rule | `../known-issues-20261109/table-hidden-top/` |
| 4 | Tree/Table/List/Combo suites in both widget modes after each commit | PASS | Viewport: after 1cab89c6a3, 1,075 found, 1 failed (Combo test_consistency_Segments, clipboard). Original: the same 17 Tree failures as the issue-3 baseline (Tree tests of viewport behaviour run against the stock Tree), plus that clipboard test. Original-mode runs exclude Tree_UpstreamIssues (swt#882: linear in the stock Tree, 1,147 s) and Tree_ViewportEdges.aListenerDisposingTheTreeAbortsShowItem (the stock Tree crashes in COMCTL32 when a listener disposes it inside showItem). | `../known-issues-20261109/gates/` |
| 5 | javac `--release 21` win32 / GTK / Cocoa | PASS | 893 / 682 / 821 sources, 0 errors | `gates-build2/javac-{win32,gtk,cocoa}.*` |
| 6 | M3 render backend compiles (Skija 0.116.3) | PASS | 34 sources, 0 errors | `gates-build2/javac-m3-backend.*` |
| 7 | JUnit sources compile (`--release 21`), including m3gc | PASS | 420 sources, 0 errors (9 `performance/` sources excluded) | `gates-build2/javac-tests.summary.tsv` |
| 8 | Headless JUnit | PASS | 190 found, 187 passed, 0 failed, 3 aborted (GTK-only) | `gates-build2/junit-headless.*` |
| 9 | m3gc (M3 GC over Skija) | PASS | AllM3GcTests 97/97 | `gates-build2/junit-m3gc.*` |
| 10 | Display JUnit: default suite (AllTests without AllBrowserTests), fork natives 4975r19 | PASS (0 real failures) | This jar: 6,265 found, 6,057 passed, 8 failed, 177 aborted by platform or opt-in assumptions, 6 skipped. First build, three runs: 8 / 12 / 8 failed, 0 real. The 7 known-issue tests pass in all four runs. | `gates-build2/junit-display.*`, `gates-build2/DISPLAY_FAILURES.tsv`, `gates/junit-display-run{1,2,3}.*` |
| 11 | Display failures caused by the environment | FAIL (environment of the invisible desktop) | This jar: 6 ENV_INPUT_HIDDEN_DESKTOP (Display.post, focus traversal), 1 ENV_CLIPBOARD_CUT (CCombo.test_cut), 1 SUITE_LEAK_CHECK (as on master), 1 FLAKY_NOT_REPRODUCED (M3ThemeImport file watcher; passed in 3 isolated reruns). Across the five full runs, the timing/pixel tests that failed once each passed in the other runs and in isolated reruns. Rules: `classify_display_ki.py`. | `gates-build2/DISPLAY_FAILURES.tsv`, `gates-build2/junit-display-rerun{1,2,3}.*`, `gates/DISPLAY_FAILURES-run{1,2,3}.tsv`, `gates/junit-display-flaky{1..4}.*` |
| 12 | `-Xcheck:jni` diagnostics | PASS | 0 in the headless, m3gc and Display JVMs | `gates-build2/junit-*.summary.tsv` |
| 13 | Java 21 preflight (no Java 22+ API) | PASS | 39 rows, 0 failures; all 1,373 jar classes are major 65 | `gates-build2/java21-preflight.tsv` |
| 14 | Superset jar + MAPPING/LEDGER + SHA-256 | PASS | 1,373 classes and the fork's 6 natives, each hashed in MAPPING. LEDGER (= the jar's META-INF/m3/LEDGER.tsv): #342 candidate; #340, #339, #338, #335, #325 merged. The candidate row names `HEAD`; the manifest pins it (`M3-Source-Commit`). | release assets, `LEDGER.tsv` |
| 15 | Default-suite completeness, M3 two-mode report gate, instrumented coverage, GTK/macOS runs, AllBrowserTests | PENDING | Not re-run here. #339 adds Test_org_eclipse_swt_custom_CTabFolder_SameColour, which the Display suite did not run (not referenced from AllTests). | `../rc-20261109/CHECKLIST.md` |
| 16 | OSGi Export-Package of `org.eclipse.swt.m3`, GitHub Actions, merge of #342 | HOLD (user) | user decisions | - |
