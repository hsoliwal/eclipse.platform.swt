<!-- SPDX-License-Identifier: EPL-2.0 -->
# M3 superset build 2026-11-10 (SWT win32 x86_64): release notes

Build `m3-superset-20261110`. It is unsigned, published only as a draft release, and has no tag.

- Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261110.jar`, SHA-256 `ce9ac688acab883ff116f0c330261d0eac2b6fc53725f0b5e216b08fc7ef64d7`
- Built from branch `m3/swt-known-issues-20261110` (PR #340) at product source commit `1cab89c6a3f2874bb10adec6f03e7775ef7bb7ad`
- Base: master `4eb4f5c8e0`: the 20261109 release candidate (#335), known issues 1 and 2 (#338) and #325 are merged.
- OSGi identity: `org.eclipse.swt.win32.win32.x86_64` `3.138.0.m3-superset-20261110`; fragment host, BREE `JavaSE-21` and `Export-Package` unchanged from the fork manifest.
- Natives: the fork's own `binaries/org.eclipse.swt.win32.win32.x86_64` (4975r19) and `WebView2Loader.dll`.
- `META-INF/m3/MAPPING.tsv` lists every shipped entry with its source path, git blob, origin and SHA-256; `META-INF/m3/LEDGER.tsv` classifies the PRs (#340 candidate; #335, #338, #325 merged; the 20261109 rows unchanged).

## What changed since m3-superset-20261109

The 6 known issues of 20261109 (the 7 real Display failures that also failed on master) are fixed. The viewport changes how the work is done; what a caller observes follows official SWT 3.135.0.

| # | Issue | Fix | Commit |
| --- | --- | --- | --- |
| 1 | VirtualPageSource bulk batch | Page-source rows are numbered logically from the window plan in viewport mode | cdc76672f6 (#338) |
| 2 | Combo logical bar after an edit-text match | A drop-down shown with SWP_SHOWWINDOW gets no WM_SHOWWINDOW; the logical bar follows when the list window shows with rows | b54196d1d8 (#338) |
| 3 | List inside-window splice: 56 native row operations, bound 7 | The -3 +2 splice left the window one row short of the need range and the layout moved it to the full overscan window (51 rows). While a model change applies, the window grows only to the need range: 6 operations | ebbb9ea074 |
| 4 | Table hidden show: 2 paint passes | Both passes were nested in the first WM_PAINT: the column-less widen's LVM_SETCOLUMNWIDTH draws right away. In WM_PAINT the viewport sets the width with the redraw off and invalidates; the paint in progress draws it once | edadc7534f |
| 5 | Tree_ViewportDeltas: 279 TreeView messages while parked, bound 172 | All came from the parking delta (39 rows x 7). A resident-state capture stays valid until a message can change a resident row, so the park does not capture twice; the caret clear skips rows the plan retires: 161 messages | 1cab89c6a3 |
| 6 | Table hidden top (M3Model on a hidden Table) | Official matrix first (291 rows, official 3.135.0 and the fork in both modes): a hidden ListView keeps its top through row edits, its bar is frozen, LVM_SCROLL moves by clamp (nPos + dy, 0, nMax - nPage + 1) - nPos, and show clamps the top to count - page. The dirty viewport answers that rule | fef5f00da1 |

Evidence per issue: `docs/m3/release/known-issues-20261109/` (probes with before/after logs, the oracle matrix, and the suite runs after each commit). api_loss_audit: lost=0 for every commit.

## Gate summary

Full table: [CHECKLIST.md](CHECKLIST.md).

| # | Gate | Status |
| --- | --- | --- |
| 1 | Known issues fixed (6 of 6) | PASS |
| 2 | No public API lost (each commit) | PASS |
| 3 | Official-SWT oracle for the hidden Table top | PASS |
| 4 | Tree/Table/List/Combo suites in both widget modes after each commit | PASS |
| 5 | javac `--release 21` win32 / GTK / Cocoa | PASS |
| 6 | M3 render backend compiles (Skija 0.116.3) | PASS |
| 7 | JUnit sources compile, including m3gc | PASS |
| 8 | Headless JUnit | PASS |
| 9 | m3gc | PASS |
| 10 | Display JUnit (AllTests without AllBrowserTests), fork natives 4975r19 | PASS (0 real failures in 3 runs) |
| 11 | Display failures caused by the environment | FAIL (invisible desktop: input, shared clipboard) |
| 12 | `-Xcheck:jni` diagnostics | PASS |
| 13 | Java 21 preflight | PASS |
| 14 | Superset jar + MAPPING/LEDGER + SHA-256 | PASS |
| 15 | Two-mode report gate, coverage, GTK/macOS, AllBrowserTests | PENDING |
| 16 | OSGi export of `org.eclipse.swt.m3`, Actions, merge of #340 | HOLD (user) |

## Test results (Windows 11, JDK 21.0.9, `-Xcheck:jni`, strict checks, invisible desktop)

| Run | Found | Passed | Failed | Aborted (assumption) | Skipped (`@Disabled`) | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| Headless (22 classes) | 190 | 187 | 0 | 3 (GTK-only) | 0 | `gates/junit-headless.*` |
| m3gc (AllM3GcTests) | 97 | 97 | 0 | 0 | 0 | `gates/junit-m3gc.*` |
| Display run 1 (alongside the suite gates) | 6,265 | 6,058 | 8 | 176 | 6 | `gates/junit-display-run1.*` |
| Display run 2 | 6,265 | 6,053 | 12 | 177 | 6 | `gates/junit-display-run2.*` |
| Display run 3 | 6,265 | 6,057 | 8 | 177 | 6 | `gates/junit-display-run3.*` |

Every Display failure is classified (`gates/DISPLAY_FAILURES-run{1,2,3}.tsv`); none is a real failure:

| Class | Per run | Reason and evidence |
| --- | --- | --- |
| ENV_INPUT_HIDDEN_DESKTOP | 6, 6, 6 | Display.post, focus traversal and shell activation do not reach the invisible test desktop. |
| ENV_CLIPBOARD / ENV_CLIPBOARD_DND | 1, 3, 1 | The dnd tests share the OS clipboard with other processes; a different test fails in each run, and the isolated reruns fail a different one again. |
| SUITE_LEAK_CHECK | 1, 1, 1 | The AllNonBrowserTests leak detector reports "1 leaks found", as on master. |
| FLAKY_NOT_REPRODUCED | 1, 3, 1 | Five tests, each failing in one run only and passing in the other two and in two isolated reruns (`gates/junit-display-flaky{1..4}.*`, `gates/junit-display-rerun.*`): Test_M3Model virtual-table pixels and VirtualPageSource check marks (both SWT.VIRTUAL tables, which have no TableViewport), M3ThemeImport file watcher, ItemResidencyPrint (Canvas), Control_batch busy cursor (a 250 ms timer against a 400 ms sleep). None runs code this build changed. |

The 7 known-issue tests pass in all three runs. The JNI checker reported 0 diagnostics.

## Still open

- Not run here: the M3 exact two-mode report gate, instrumented coverage, GTK/macOS builds and runs, AllBrowserTests.
- User decisions: the OSGi export of `org.eclipse.swt.m3`, "Enable workflows", and merging #340.
