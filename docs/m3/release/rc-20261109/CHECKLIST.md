<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT release candidate 2026-11-09: gate checklist

Candidate `m3-superset-20261109` is built from branch `m3/release-candidate-20261109` (PR #335).
Product source commit: `4f82a7a9ab92` (master `4fb49875cc` + #325 forward + test-isolation fix).
Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261109.jar`, SHA-256 `b9a3d521d176865c5edcf917ba3f0d0881498ff6066b790c5ef5919664903b7b`.

All gates were run locally on Windows 11 x86_64 with Oracle JDK 21.0.9.
GitHub Actions are disabled on this fork, so there is no CI result.
Evidence paths are relative to this directory; `SHA256SUMS` covers every evidence file.

PASS means executed, with the result shown. FAIL means executed and not met. PENDING means not executed here. HOLD means the user must decide.

| # | Gate | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| 1 | #325 forwarded onto current master | PASS | 13 conflicts resolved per hunk, with lineage proofs. #323 is carried. #327-#334 are included through master. | `FORWARD_325.tsv` |
| 2 | No public API lost in the fold | PASS | The only bundle Java delta compared with master is `M3ItemRange`, where `+ 1` becomes `+ 1L` (no signature change). Official 3.135.0 classes not shipped: 9, all private nested or internal (`StyledTextRenderer$*`, `PngDeflater*`, `IPropertyBag`). | `META-INF/m3/MAPPING.tsv` (`OFFICIAL_3.135.0_ONLY_NOT_SHIPPED`) |
| 3 | javac `--release 21` win32 | PASS | 893 sources, 1,373 classes, 0 errors | `gates/javac-win32.*` |
| 4 | javac `--release 21` GTK (linux x86_64 source set) | PASS | 682 sources, 1,020 classes, 0 errors | `gates/javac-gtk.*` |
| 5 | javac `--release 21` Cocoa (macOS aarch64 source set) | PASS | 821 sources, 1,129 classes, 0 errors | `gates/javac-cocoa.*` |
| 6 | JUnit sources compile (`--release 21`) | PASS | 383 sources, 0 errors. 42 excluded: 9 `performance/` sources need org.eclipse.test.performance; 33 `m3gc/` sources depend on `io.github.humbleui`, which the test bundle does not declare (gate 22). | `gates/javac-tests.summary.tsv`, `gates/tests-excluded-sources.txt` |
| 7 | Headless JUnit, win32, final jar | PASS | 190 found, 187 passed, 0 failed, 3 aborted. The aborts are `Test_VirtualNativeTableModel`, which assumes GTK. | `gates/junit-headless.*`, `gates/TEST-junit-*-headless.xml` |
| 8 | Display JUnit, win32, fork natives 4975r19, final jar: no failure introduced by the candidate | PASS | Candidate: 5,135 found, 4,951 passed, 82 failed, 82 aborted, 3 skipped. Master `4fb49875cc` in the same harness: 5,109 found, 4,935 passed, 83 failed. Per-test comparison: the candidate fixes `Test_M3ItemRange.test_duplicateAtMaximumIntCoalescesOnce` (the #325 repair). The only other difference is one clipboard-contention failure, which hit a different test in each run. | `gates/junit-display.*`, `gates/DISPLAY_FAILURES.tsv`, `gates/master-baseline/` |
| 8b | Display JUnit: zero failures | FAIL | All 82 failures are classified: 61 HARNESS_ORDER (Test_Display alone passes 64/65), 8 ENV_SVG_FRAGMENT (121/121 with the SVG fragment), 5 ENV_INPUT_HIDDEN_DESKTOP, 1 ENV_CLIPBOARD, and 7 PRE_EXISTING_MASTER defects (`VirtualPageSource` 4, `StyledText_PagedContent` 1, `Java2DCanvas` Region retention 1, `M3SortFilterWidgets` 1). The suite-level leak detector also reports "1 leaks found" on both master and the candidate. | `gates/DISPLAY_FAILURES.tsv`, `gates/master-baseline/MASTER_DISPLAY_FAILURES.tsv` |
| 9 | `-Xcheck:jni` diagnostics in every JUnit JVM | PASS | 0 JNI checker diagnostics in the headless, Display, rerun and SVG runs | `gates/junit-*.summary.tsv` (`jni_check_diagnostics`) |
| 10 | JNI glue consistent with Java natives (all platforms) | PASS | `mvn -N -Pjni-generator exec:exec@run-jni-generator` then `git diff --exit-code`: no diff. JDK 21 cannot run the generator, because it is a multi-file source launch (JEP 458 needs Java 22+), so JDK 25 was used. | `gates/jni-generator-jdk25.log`, `gates/jni-generator-diff.txt` |
| 11 | #325 release tool self-tests (Linux, as in CI) | PASS | `tools/release`: 79/79. `verify_m3_test_reports`: 31/31. On Windows, 8 `check_dependencies.sh` tests need a Linux `/bin/bash` (environment, not product). | `gates/release-tools-unittest-linux.log`, `gates/verify-m3-test-reports-unittest-linux.log`, `gates/release-tools-unittest.log` |
| 12 | Release surface (naming, module name, mandatory registrations) | PASS | `check_release_surface.py --strict` passed | `gates/release-surface.json` |
| 13 | Default-suite non-regression | PASS | `audit_suite_inventory.py --strict`: 328 candidates, 192 reachable, 0 new omissions, 0 missing references | `gates/suite-inventory.json` |
| 14 | Default-suite completeness (`--release`) | FAIL | 136 test classes are not reachable from `AllTests` (baseline 170). The #325 docs say this is expected to hold until the suite is closed. | `gates/suite-inventory-release.log` |
| 15 | Mandatory sparse-state classes executed | PASS | `check_junit_coverage.py --strict`: ItemBitSetDifferential 3/3, ItemBitSetMaximumPosition 4/4 | `gates/junit-coverage-headless.json` |
| 16 | Java 21 preflight | PASS | 27 BREE headers, all JavaSE-21. Numeric POM release/target values are <= 21. All 1,373 jar classes are major 65. | `gates/java21-preflight.tsv` |
| 17 | #325 headless Java 21 contract | PASS | `M3_SWT_HEADLESS_JAVA21_PASS checks=2321 seed=20261107` | `gates/headless-java21/` |
| 18 | Superset jar + MAPPING/LEDGER + SHA-256 | PASS | 1,373 classes, 65 fork resources and 6 fork natives. Each shipped entry is hashed in MAPPING.tsv. LEDGER has 213 rows. The tested jar and the final jar have identical bytes for every shipped entry. | `../../../../` release assets, `LEDGER.tsv` |
| 19 | M3 exact report gate (`REPORT_GATE.md`: 245 viewport + 244 original) | PENDING | Needs a qualified official-SWT recording directory and two fresh-JVM `AllTests` runs with per-class Surefire XML | `../release/REPORT_GATE.md` |
| 20 | Instrumented line/branch coverage (90% per changed atom) | PENDING | JaCoCo was not run for this candidate | `../RELEASE.md` |
| 21 | Native builds and JUnit on GTK3/GTK4/Wayland and macOS | PENDING | No Linux display or macOS host is available here, and hosted CI is disabled | `../release/INTEGRATION.md` |
| 22 | Test bundle builds `m3gc/` tests | FAIL | 33 `m3gc/` sources were excluded; 26 of them import `io.github.humbleui` (Skija), which the test MANIFEST does not require. The Tycho test build cannot compile them. | `gates/tests-excluded-sources.txt` |
| 23 | OSGi visibility of `org.eclipse.swt.m3` and new internal packages | HOLD | The host and fragment manifests do not export `org.eclipse.swt.m3` (public), `internal.render`, `internal.gpu`, `internal.canvasext` or `internal.m3.collections`. This was already recorded as HOLD in INTEGRATION.md. | `../release/INTEGRATION.md`, `gates/win32-packages.txt` |
| 24 | Merged PRs whose content never reached master | HOLD | 13 PRs were merged into other PR branches. 6 non-I31 PRs have their own lines missing from master: #183, #190, #191, #220 (59% present), #221 (18% present), #239 | `STACKED_NOT_ON_MASTER.tsv`, `LEDGER.tsv` |
| 25 | GitHub Actions CI on the candidate | PENDING | Workflows do not run on this fork until the user clicks "Enable workflows" | - |
| 26 | AllBrowserTests (Edge/WebView2) | PENDING | Not run | - |
