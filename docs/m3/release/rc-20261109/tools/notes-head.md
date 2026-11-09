<!-- SPDX-License-Identifier: EPL-2.0 -->
# M3 superset build 2026-11-09 (SWT win32 x86_64): release notes

Release candidate `m3-superset-20261109`. It is unsigned, published only as a draft release, and has no tag.

- Jar: `org.eclipse.swt.win32.win32.x86_64-m3-superset-20261109.jar`, SHA-256 `@JAR_SHA@`
- Built from branch `m3/release-candidate-20261109` (PR #335) at product source commit `4f82a7a9ab922e955d2a52c740d9956e0b6833c4`
- Base: master `4fb49875cc` (#334). Forwarded on top: #325, which also carries #323. Test-isolation fix: the two M3 differential classes now dispose their Display.
- OSGi identity: `org.eclipse.swt.win32.win32.x86_64` `3.138.0.m3-superset-20261109`. The fragment host, BREE `JavaSE-21` and `Export-Package` are taken unchanged from the fork manifest.
- Natives: the fork's own `binaries/org.eclipse.swt.win32.win32.x86_64` (4975r19) and `WebView2Loader.dll`. The 20261013.2 build used the official 3.135.0 natives instead.
- Resources: fork package resources, including the M3 SVG glyphs and skins, and the localized `SWTMessages*.properties` (renamed from `._properties`). Legal files come from `binaries/legal_files/win32.win32`.
- `META-INF/m3/MAPPING.tsv` lists every shipped entry with its source path, git blob, origin (FORK, FORK_RESOURCE or FORK_NATIVE) and SHA-256. It also lists the 9 official 3.135.0 classes that are not shipped, all private nested or internal.
- `META-INF/m3/LEDGER.tsv` has one row per PR merged since 2026-10-13, classified against the candidate, plus the candidate and #325 forward rows.

## Gate summary

Full table: [CHECKLIST.md](CHECKLIST.md).

@GATES@

## Test results (Windows 11, JDK 21.0.9, `-Xcheck:jni`, invisible desktop)

@TESTS@
