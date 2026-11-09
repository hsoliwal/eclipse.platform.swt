<!-- SPDX-License-Identifier: EPL-2.0 -->
# M3 superset build 2026-11-09 (SWT win32 x86_64): release notes

Release candidate `m3-superset-20261109`. It is unsigned, published only as a draft release, and has no tag.

- Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261109.jar`, SHA-256 `@JAR_SHA@`
- Built from branch `m3/release-candidate-20261109` (PR #335) at product source commit `55ccfce4799725b322a9a72ef47bdfbf78d4e698`
- Base: master `4fb49875cc` (#334, which includes #327-#334). Forwarded on top: #325, which also carries #323.
- Re-landed: #183, #190 + #220, #221 and #239. These PRs were merged into other PR branches and never reached master. Each was a clean 3-way fold, and api_loss_audit reported 0 lost declarations. See `STACKED_NOT_ON_MASTER.tsv`.
- Release fixes:
  - Test_Display now starts on a thread with no Display.
  - The M3 differential tests release their Display.
  - The SVG fragment, M3 backend and Skija are on the gate classpath.
  - The test bundle declares its Skija imports.
  - The gpu.m3 module's Skija pins now match the version its sources compile against (0.116.3; the module did not build before).
  - Three stale tests are fixed, and the VirtualPageSource fixture is fixed.
  - Tree page-source rows stay facade-free when painted (win32).
  - All 330 test classes are reachable from AllTests.
- OSGi identity: `org.eclipse.swt.win32.win32.x86_64` `3.138.0.m3-superset-20261109`. The fragment host, BREE `JavaSE-21` and `Export-Package` are taken unchanged from the fork manifest.
- Natives: the fork's own `binaries/org.eclipse.swt.win32.win32.x86_64` (4975r19) and `WebView2Loader.dll`.
- `META-INF/m3/MAPPING.tsv` lists every shipped entry with its source path, git blob, origin and SHA-256. `META-INF/m3/LEDGER.tsv` has one row per PR merged since 2026-10-13, classified against the candidate.

## Gate summary

Full table: [CHECKLIST.md](CHECKLIST.md).

@GATES@

## Test results (Windows 11, JDK 21.0.9, `-Xcheck:jni`, strict checks, invisible desktop)

@TESTS@
