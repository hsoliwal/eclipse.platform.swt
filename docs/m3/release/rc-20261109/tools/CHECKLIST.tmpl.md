<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT release candidate 2026-11-09: gate checklist

Candidate `m3-superset-20261109` is built from branch `m3/release-candidate-20261109` (PR #335).
Product source commit: `@SRC@`. It is master `4fb49875cc` plus the #325 forward, the re-landed PRs and the release fixes.
Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261109.jar`, SHA-256 `@JAR_SHA@`.

All gates were run locally on Windows 11 x86_64 with Oracle JDK 21.0.9, `-Xcheck:jni` and `-Dorg.eclipse.swt.internal.enableStrictChecks=true`, as in the tests' Surefire configuration.
GitHub Actions are disabled on this fork, so there is no CI result. Evidence paths are relative to this directory; `SHA256SUMS` covers every evidence file.

PASS means executed, with the result shown. FAIL means executed and not met. PENDING means not executed here. HOLD means the user must decide.

| # | Gate | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| 1 | #325 forwarded onto current master | PASS | 13 conflicts resolved per hunk, with lineage proofs. #323 is carried; #327-#334 are included through master. | `FORWARD_325.tsv` |
| 2 | Merged PRs that never reached master | PASS | Re-landed: #183, #190 + #220, #221 and #239, with own-line presence now 100%. #191 and 6 Initiative 31 PRs are NOT_APPLICABLE, with proof. #323 is CARRIED. | `STACKED_NOT_ON_MASTER.tsv`, `relands/` |
| 3 | No public API lost (every fold and fix) | PASS | api_loss_audit lost=0 for each re-land and for the Tree fix. The only official 3.135.0 classes not shipped are 9 private nested or internal classes. | `relands/api-loss-*.tsv`, `META-INF/m3/MAPPING.tsv` |
| 4 | javac `--release 21` win32 / GTK / Cocoa | PASS | 893 / 682 / 821 sources, 0 errors | `gates/javac-{win32,gtk,cocoa}.*` |
| 5 | M3 render/GPU backend compiles (Skija 0.116.3) and the gpu.m3 module builds | PASS | 34 sources, 0 errors. `mvn package` BUILD SUCCESS after aligning the pins; before, the module failed with 3 errors against its pinned 0.143.18. | `gates/javac-m3-backend.*`, `gates/gpu-m3-mvn-0.116.3.log`, `gates/javac-gpu-m3-0.143.18.log` |
| 6 | JUnit sources compile (`--release 21`), including m3gc | PASS | 418 sources, 0 errors. The only exclusion is the 9 `performance/` sources, which need org.eclipse.test.performance. | `gates/javac-tests.summary.tsv` |
| 7 | Headless JUnit | PASS | @HEADLESS@ | `gates/junit-headless.*` |
| 8 | m3gc (M3 GC over Skija) | PASS | @M3GC@ | `gates/junit-m3gc.*` |
| 9 | Display JUnit: default suite (AllTests without AllBrowserTests), fork natives 4975r19 | @DISPLAY_STATUS@ | @DISPLAY@ | `gates/junit-display.*`, `gates/DISPLAY_FAILURES.tsv` |
| 10 | Display failures: none caused by the environment or the harness | @ENV_STATUS@ | @ENV@ | `gates/DISPLAY_FAILURES.tsv` |
| 11 | `-Xcheck:jni` diagnostics | PASS | @JNI@ | `gates/junit-*.summary.tsv` |
| 12 | JNI glue consistent with the Java natives (all platforms) | PASS | Regenerated with JDK 25 (a multi-file source launch needs Java 22+); `git diff --exit-code` shows no change | `gates/jni-generator-jdk25.log`, `gates/jni-generator-diff.txt` |
| 13 | #325 release tool self-tests | PASS | Linux: 79/79 and 31/31. Windows: 71/79; the other 8 need a Linux `/bin/bash` (`check_dependencies.sh`). | `gates/release-tools-unittest*.log`, `gates/verify-m3-test-reports-unittest-linux.log` |
| 14 | Release surface (naming, module name, mandatory registrations) | PASS | `check_release_surface.py --strict` passed | `gates/release-surface.json` |
| 15 | Default-suite completeness (`audit_suite_inventory --release`) | PASS | 330/330 classes reachable from AllTests (was 194). The omission baseline is empty, so `--strict` rejects any class that drops out again. | `gates/suite-inventory.json`, `gates/suite-inventory-release.log` |
| 16 | Mandatory sparse-state classes executed | PASS | `check_junit_coverage.py --strict`: ItemBitSetDifferential 3/3, ItemBitSetMaximumPosition 4/4 | `gates/junit-coverage-headless.json` |
| 17 | Java 21 preflight (no Java 22+ API) | PASS | 27 BREE headers, all JavaSE-21. Numeric POM release values are <= 21. All jar classes are major 65. javac `--release 21` is gate 4. | `gates/java21-preflight.tsv` |
| 18 | #325 headless Java 21 contract (re-landed ItemBitSet) | PASS | `M3_SWT_HEADLESS_JAVA21_PASS checks=2321` | `gates/headless-java21/` |
| 19 | Superset jar + MAPPING/LEDGER + SHA-256 | PASS | 1,373 classes, 65 fork resources and 6 fork natives, each hashed in MAPPING. LEDGER classifies 211 PRs. The released jar and the tested jar differ only in `META-INF/m3/LEDGER.tsv`. | release assets, `LEDGER.tsv` |
| 20 | M3 exact two-mode report gate (`REPORT_GATE.md`) | PENDING | Needs a qualified official-SWT recording directory and per-class Surefire XML; the 11 recorded-oracle methods abort without one | `../release/REPORT_GATE.md` |
| 21 | Instrumented line/branch coverage (90% per changed atom) | PENDING | JaCoCo was not run | `../RELEASE.md` |
| 22 | Native builds and JUnit on GTK3/GTK4/Wayland and macOS | PENDING | No Linux display or macOS host is available here, and hosted CI is disabled | `../release/INTEGRATION.md` |
| 23 | AllBrowserTests (Edge/WebView2) | PENDING | Not run | - |
| 24 | OSGi Export-Package of `org.eclipse.swt.m3` and the new internal packages | HOLD (user decision) | Not exported by the host or fragment manifests. INTEGRATION.md already records it as HOLD. | `../release/INTEGRATION.md`, `gates/win32-packages.txt` |
| 25 | GitHub Actions CI | HOLD (user: "Enable workflows") | Workflows do not run on this fork | - |
| 26 | Merge | HOLD (user: merge #335) | #335 is a draft; it supersedes #325 | - |
