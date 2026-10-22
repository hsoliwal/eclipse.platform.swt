# SWT 3.138 host/fragment alignment — receiver receipt

Date: 22 October 2026

This PR is a **nine-file metadata-only correction** in the existing Eclipse SWT
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
- Input/output SHA-256 for all nine source paths:
  `resources/com/synexia/rewrite/hash-pinned-text/m3-eclipse-swt-version-20261022/manifest.tsv`
  in that canonical crate. Every baseline Git blob was compared against
  the receiver's actual `master` contents before submission.
- The only changes are the Maven host version
  `3.137.0-SNAPSHOT -> 3.138.0-SNAPSHOT` and eight native fragments'
  `Bundle-Version: 3.137.0.qualifier -> 3.138.0.qualifier`.
  The unchanged host manifest already declared `3.138.0.qualifier`.

## Qualification

| Gate | Evidence | Result |
| --- | --- | --- |
| Exact preimage admission | Nine SHA-256 source fingerprints | Passed |
| Actual OpenRewrite execution | JUnit proof in canonical recipe | 6 passed, 0 failed |
| Idempotence and negative cases | Missing/drift/duplicate, order independence, fixed point | Passed |
| Reviewable generated diff | `git diff --check` and nine one-line source changes | Passed |
| SWT Tycho owner/dependency build | Java 8/11/17/21/25 toolchains, Maven 3.9.16/Tycho 5.0.4 | Passed for initial host POM correction |
| Full Eclipse aggregator packaging | `-Pbree-libs -DskipTests verify` | Advanced past SWT; stopped at `org.eclipse.jface` NLS diagnostics |
| SWT runtime and native OS matrix | Linux GTK, Windows, macOS plus native JNI checks | Not qualified |

The full reactor's next blocker is in a **different owner**:
`eclipse.platform.ui/bundles/org.eclipse.jface/src/org/eclipse/jface/viewers/TreeViewer.java`,
with ten non-externalized literals. It requires a separate UI recipe/receiver
and proof; this SWT PR must not obscure or silently edit UI source.

## Scope guard

The inactive `org.eclipse.swt.gtk.linux.loongarch64` directory is not in the
eight-module `binaries/pom.xml` reactor and remains unchanged. Future
activation needs an independent version/API/native review.

Keep this PR in review until the assembled Eclipse SDK and platform-specific
runtime gates pass. The canonical recipe and this receiver PR should be
reviewed together; no force push, squash of unrelated work, or source rewind
is intended.
