# SWT M3 release preparation

Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

## Candidate identity and current status

The source baseline for this preparation is
`hsoliwal/eclipse.platform.swt@62460082d7caa06b21c49da99abbab53f73b3ffc`
(7 November 2026 inspection). The candidate remains **HOLD** until the complete
candidate commit, native artifacts and platform test results are bound together.
A successful packaging job or an old per-widget report does not close that gate.

This pass freezes the following names before changing documentation, CI or test
selection. It does not rename public APIs, OSGi identities or native entry points.

| Surface | Release naming contract |
| --- | --- |
| Product documentation | **SWT M3**, a downstream Eclipse SWT fork |
| Existing Java API | Keep `org.eclipse.swt.*`, widget names and signatures |
| Additive M3 API | Keep existing `M3*` names in their present SWT packages |
| Bundle and automatic module | Keep `org.eclipse.swt` and existing platform fragment names |
| Current source version | `3.138.0.qualifier` / Maven `3.138.0-SNAPSHOT`; this is not a published version |
| Native loader version | Keep the source-derived SWT major/minor/revision and matching generated native version |
| CI Windows/macOS package | `swt-windows-macos.zip`, artifact `swt-windows-macos-bundle` |
| Donor identifiers | Retain them in provenance, licences, binding dependencies and existing interoperability APIs |

SWT terminology remains Canvas, GC, Image, Control, Table, Tree, ScrollBar and
ScrolledComposite. The existing [naming recipe](naming/recipe/META-INF/rewrite/m3-naming.yml)
does not authorize a blanket replacement of donor strings in licences, reflected
names, serialized forms, native symbols or compatible public APIs. Canonical
recipe custody is in `hsoliwal/com.synexia`; target changes are generated from
source-pinned, fixed-point-checked postimages.

Do not substitute the bundle version for the `eclipse.swt` capability version:
the current host requires native capability `3.135.100`, and the inspected GTK
fragment provides that same capability. They are separate version contracts.
The `org.eclipse.swt.m3` package is present in source but is not currently listed
in the host or inspected GTK fragment's `Export-Package`; release documentation
must not promise access to that package from independent OSGi bundles until the
export and API-baseline decision is qualified.

## Current source-pinned test-coverage blockers (7 November 2026)

The focused JaCoCo receipt in
[enhanced API coverage](enhanced-api/coverage/COVERAGE.md) is historical evidence
from the exact source baseline `62460082d7caa06b21c49da99abbab53f73b3ffc`.
A SHA-256 audit of the **release-preparation branch** found **10/10**
production source inputs unchanged but only **7/9** original JUnit
test-source inputs unchanged. The changed `Test_M3ItemRange` and
`Test_M3RowExtents` tests require new compilation/execution/Jacoco evidence.
Do not attach the old **77/77 passing tests** to the new source tree or
call the old **90.43% lines / 87.24% branches** current coverage.

The documented per-file 90% branch floor still needs additional covered
branches in `M3ColumnFilter` (30), `M3ColumnSort` (9),
`M3ItemRange` (3) and `M3RowExtents` (8), given the original
denominators. The aggregate 87.24% also fails the release floor.
The branch contains new boundary JUnit tests, including
`test_duplicateAtMaximumIntCoalescesOnce`, which is **expected to fail**
against the current coalescing source due to signed 32-bit adjacency
overflow. Fix both source comparisons through an executed, source-sealed
canonical Synexia recipe, then rerun the focused and default suites.
The read-only [coverage audit](../../tools/release/headless_coverage_audit.py)
reports test-input drift and source hashes; it never qualifies the product.

The audit, package assembly and test-suite registration PRs must be
converged before freezing a candidate. GitHub-hosted SWT runs were not
available for these PR heads at this inspection, so all platform/native,
Java 21 runtime, Javadoc/JNI, performance and source-bound JaCoCo
results remain **PENDING/HOLD**. A checkout with a test source change
requires a fresh receipt even when its production files remain identical.

## Build and platform qualification

Use Java 21 for the M3JDK21 compatibility baseline. The ordinary SWT matrix keeps
its Java 25 legs and adds Java 21 for GTK/Linux x86_64, Windows x86_64 and Cocoa
macOS x86_64/aarch64. Both compiler and executed runtime versions belong in the
receipt. API/Javadoc checks stay on Java 25: the inspected parent snapshot
`4.42.0-20261106.230639-146` pins Tycho 5.0.4 (JDK 21 minimum), but configures
its Eclipse-run environment as JavaSE-25 and Javadoc source level 25. See the
[Tycho 5.0.4 requirements](https://tycho.eclipseprojects.io/doc/5.0.4/tycho-eclipse-plugin/plugin-info.html).
The workflow can also be started with `workflow_dispatch` for a frozen candidate
commit. The parent is still a SNAPSHOT coordinate, so record the resolved parent
identity again for each release campaign.

The [matrix](../../.github/workflows/maven.yml) runs the existing
[build workflow](../../.github/workflows/build.yml). Native builds and JUnit run
on their actual host platforms. GTK3/X11, GTK4/X11 and GTK4/Wayland remain separate
Linux legs. The existing GTK TODO exclusions are still exclusions, not passes.
Windows ARM64 packaging does not establish a Windows ARM64 test result.

On a provisioned Linux x86_64 GTK host, from the repository root:

```sh
java -version
mvn -version
git rev-parse HEAD
mvn --batch-mode --no-transfer-progress clean verify \
  -Dnative=gtk.linux.x86_64 -DskipNativeTests=false
```

GUI tests need a working display; in headless CI the existing workflow uses
`dbus-run-session` and Xvfb or a Wayland compositor. Native requirements and JNI
regeneration are documented in [the SWT build guide](../../bundles/org.eclipse.swt/Readme.md).
The JNI generator and Javadoc-basher must report an empty generated diff. Local
native binaries are build outputs and are not committed by this preparation.

## Test selection and coverage

The default chain is `AllTests` -> `AllNonBrowserTests` -> `AllWidgetTests`.
The last suite now explicitly includes all **26 ordinary `Test_M3*` classes**
currently present in the live test source tree, exposing their **216 `@Test`
methods** to normal test discovery. Those numbers are a source inventory, not an
executed pass count or a percentage of SWT coverage.

The following specialized suites remain explicit jobs:

| Suite | Required evidence / reason for separate execution |
| --- | --- |
| `Test_M3BulkUpdatesDifferential` | Official SWT recording, then candidate replay |
| `Test_M3DiffApplyDifferential` | Official SWT recording, then candidate replay |
| `Test_M3ProxyModelDifferential` | Official SWT recording, then candidate replay |
| `Test_M3RowMathDifferential` | Official SWT recording, then candidate replay |
| `Test_M3ScrollBarModelDifferential` | Official SWT recording, then candidate replay |
| `Test_M3ScrollMementoDifferential` | Official SWT recording, then candidate replay |
| `Test_M3SortFilterDifferential` | Seeded scale campaign through one million rows and explicit resource budget |

The recording/replay suites must not silently create their own candidate result
and treat it as an independent official oracle. Use the existing
[differential harness](VIEWPORT_DIFFERENTIAL_HARNESS.md) and preserve oracle jar,
recording, candidate, mode, platform and JVM identities. Run viewport and original
modes in separate JVMs because mode selection is a startup contract.

See [the test guide](../../tests/org.eclipse.swt.tests/Readme.md) for focused
commands and [enhanced API coverage](enhanced-api/coverage/COVERAGE.md) for the
scope of recorded measurements. Report measured line and branch counters as
produced by JaCoCo. A separately exercised throwing branch may explain a missing
probe; it does not increase JaCoCo's measured numerator. Keep historical reports
with their original scope and identity. Do not pool platform/mode percentages or
call a focused headless run full SWT coverage.

## Additive PR convergence and source-closure plan (7 November 2026)

The release is a **reviewable union of existing target postimages**, not four
independent product versions. Preserve every tested behavior and provenance
record. Exact-head source/JNI/runtime qualification must be repeated after
the union because Git ancestry or individually green PRs cannot substitute.

| PR | Scope / dependency | Merge and review condition |
| --- | --- | --- |
| [#321](https://github.com/hsoliwal/eclipse.platform.swt/pull/321) | Source-only naming/docs/JUnit and JaCoCo intake; branch `release/prep-naming-docs-coverage-20261107` | Review and merge baseline intake first; never claim it ran GUI/JNI tests |
| [#323](https://github.com/hsoliwal/eclipse.platform.swt/pull/323) | **Stacked on #321**: source/fragment integrity and Windows/macOS candidate packager | Retarget after #321 is merged; compare its remaining diff against master, preserve the new all-skipped refusal and source-bound native packaging. Check packaging overlap with #324 |
| [#322](https://github.com/hsoliwal/eclipse.platform.swt/pull/322) | Default-suite omission inventory, Java 21 headless and platform preflight | Preserve `AllTests` graph and each pre-existing test class; compare the focused paths with #324's `AllWidgetTests` expansion |
| [#324](https://github.com/hsoliwal/eclipse.platform.swt/pull/324) | Java 21 CI matrix, 26 ordinary M3 classes in `AllWidgetTests`, source-bound headless coverage and boundary tests | Keep the tested source and historical receipt separate; reconcile `README.md`, `docs/m3/README.md` and `.github/workflows/build-swt-jars.yml` with #321–#323 |

The inspected diff overlap is explicit: `README.md` appears in **all four**
PRs; #321 and its dependent #323 share the readiness parser/tests and release
workflow; #321 and #324 share `docs/m3/README.md`; #323 and #324 both
modify `build-swt-jars.yml`. Resolve conflicts by preserving **both**
independent release gates where safe, not by choosing a single branch's
overwrite or dropping a test. Produce a file-by-file before/after/selection
receipt for every nontrivial conflict.

After convergence, re-run the product in this order: source/version/notice
inventory → naming and OSGi/JNI source parity → Java 21 and Java 25
compilation → default and specialized JUnit tests including the
`MAX_VALUE` coalescing blocker → instrumented JaCoCo line/branch coverage
bound to final source and **test** hashes → GTK3/GTK4/Wayland/Win32/Cocoa
native GUI and screenshot/differential suites → per-platform candidate
artifacts with SHA-256 → final release-admission report.

As of this inspection the SWT repository has not reported exact-head
hosted CI runs for these release branches. **No merge/tag/publication is
authorized by this integration plan alone.**

## Packaging and release evidence

The existing [JAR packaging workflow](../../.github/workflows/build-swt-jars.yml)
builds with `-DskipTests=true`. Its combined archive is now named for the platforms
it actually includes: Windows and macOS. Linux jobs remain disabled there while
the existing older-glibc build route is reconciled; the archive makes no Linux
availability claim. `BUILD.txt` records the exact source commit, included platform
families and the fact that tests were skipped by that workflow.

Before promotion, retain all of the following for the same candidate:

- Full source commit and tree, canonical recipe revision, source and generated
  artifact SHA-256 values, JDK/Maven/compiler/native toolchain versions.
- Normal JUnit discovery and nonempty test reports for every claimed host/JDK,
  with failures, errors, skips and quarantines counted separately.
- API/OSGi resolution and JNI parity, including public M3 package availability,
  source-built native libraries, and host/fragment/loader version consistency.
- The relevant widget differential, screen-capture/pixel, disposal, reentrancy,
  accessibility, IME and DPI evidence. Keep heap, process RSS and CPU gates
  separate from screenshot collection and facade counts.
- Exact JaCoCo class/source scope, execution inputs and line/branch denominators;
  current reports must not borrow a percentage from a historical source image.
- The distribution inventory and notices, including the still-open native
  dependency inventory in [the Skija packaging notice](../licenses/swt-gpu-skija-packaging-NOTICE.txt).
- Explicit dispositions for source retained only under
  `m3/qualification/master-landing-20261028/retained-variants/`; Git ancestry and
  archived files alone do not prove their behaviour exists in the live artifact.

This document prepares the candidate and its evidence requirements. Tag creation,
release publication and a GA compatibility statement follow executed gates.
