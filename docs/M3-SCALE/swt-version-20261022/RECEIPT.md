# SWT 3.138 host/fragment alignment — receiver receipt

Date: 22 October 2026

This PR is a **ten-file metadata-only correction** in the existing Eclipse SWT
owner. It does not replace SWT widgets, APIs, Java, JNI, platform natives,
or the inherited Eclipse OSGi fragments.

## Source and transformation authority

- Receiver baseline: `hsoliwal/eclipse.platform.swt` `master` commit
  `2f3d417c664d3e64a65abd81f6749284d7dba0dc`.
- Canonical Synexia OpenRewrite recipe:
  `synexia-openrewrite-recipes/recipe-crates/m3-eclipse-swt-version-20261022`
  in `hsoliwal/com.synexia`, commit
  `9b3e961d34276d7f74857baecc13faba17e5d213`.
- Named recipe: `com.synexia.eclipse.SwtBundleVersion20261022`.
- Input/output SHA-256 for all ten source paths:
  `resources/com/synexia/rewrite/hash-pinned-text/m3-eclipse-swt-version-20261022/manifest.tsv`
  in that canonical crate. Every baseline Git blob was compared against
  the receiver's actual `master` contents before submission.
- The only changes are the Maven host version
  `3.137.0-SNAPSHOT -> 3.138.0-SNAPSHOT` and eight native fragments'
  `Bundle-Version: 3.137.0.qualifier -> 3.138.0.qualifier`.
  The unchanged host manifest already declared `3.138.0.qualifier`.
- The optional `org.eclipse.swt.gpu.m3` POM has its **provided Win32 SWT
  dependency** aligned from `3.137.0-SNAPSHOT` to `3.138.0-SNAPSHOT`.
  Its own `3.137.0-SNAPSHOT` module version, OSGi host range, Java classes,
  native assets, and packaging contract do not change. This tenth path was
  admitted from SWT receiver baseline `c1ed5e568efc8ae628b32ff71d4f9a343a3f8f94`.

## Qualification

| Gate | Evidence | Result |
| --- | --- | --- |
| Exact preimage admission | Ten SHA-256 source fingerprints | Passed |
| Actual OpenRewrite execution | JUnit proof in canonical recipe | 6 passed, 0 failed |
| Idempotence and negative cases | Missing/drift/duplicate, order independence, fixed point | Passed |
| Reviewable generated diff | `git diff --check` and ten one-line metadata changes | Passed |
| SWT Tycho owner/dependency build | Java 8/11/17/21/25 toolchains, Maven 3.9.16/Tycho 5.0.4 | Passed for initial host POM correction |
| Full Eclipse aggregator packaging | `-Pbree-libs -DskipTests verify` | After additional UI and Compare owner repairs, reached the optional GPU POM dependency; separate full replay after this revision required |
| SWT runtime and native OS matrix | Linux GTK, Windows, macOS plus native JNI checks | Not qualified |

The reactor has separately required JFace marker-helper and Compare source
repairs in their own owner forks and recipes. Those remain reviewable apart
from this SWT metadata change. A new reactor build must establish the next
failing module; passing the recipe's unit tests is not a whole-SDK proof.

## Scope guard

The inactive `org.eclipse.swt.gtk.linux.loongarch64` directory is not in the
eight-module `binaries/pom.xml` reactor and remains unchanged. Future
activation needs an independent version/API/native review.

Keep this PR in review until the assembled Eclipse SDK and platform-specific
runtime gates pass. The canonical recipe and this receiver PR should be
reviewed together; no force push, squash of unrelated work, or source rewind
is intended.
