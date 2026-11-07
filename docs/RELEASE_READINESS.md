<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT fork release preparation: naming, documentation and coverage

Status: **preparation / no release or binary compatibility claim** (2026-11-07).

## Product naming and public contracts

This repository remains the Eclipse Standard Widget Toolkit (SWT), with
`org.eclipse.swt` bundle/module identity and the existing `org.eclipse.swt.*`
Java packages. Its public types remain `Display`, `Shell`, `Control`,
`Widget`, `Canvas`, `GC`, `Image`, `Table`, `Tree`, `ScrollBar`, etc.
Internal M3/residency names are implementation details, not replacement public
Swing facades. Preserve Java, OSGi, PDE/API Tools, JNI/generated C and
reflection/service contracts. The bundle manifest's `JavaSE-21` minimum is
**not** the same as CI's current **JDK 25** build toolchain.

Do not change upstream package names, exported APIs, native entry points,
generated JNI glue, licensing or native binaries in a naming-only PR.
Treat changes to public signatures or serialized/native symbols as release
blockers requiring a separate ABI/behavior review.

## Required documentation audit

Source-verify `README.md`, `docs/DEVELOPMENT_GUIDE.md`,
`bundles/org.eclipse.swt/Readme.md`, backend-specific native instructions,
`NOTICE`, `LICENSE`, `AGENTS.md`, this document, and source/test links.
Update version and release claims only from the exact tag/commit and published
evidence. The upstream project is the Eclipse SWT baseline; M3 viewport/GPU
work is downstream experimental until separately qualified.

The main [matrix](../.github/workflows/maven.yml), [reusable build](../.github/workflows/build.yml),
[JNI and Javadoc checks](../.github/workflows/pr-checks.yml) and
[JUnit publisher](../.github/workflows/junit.yml) are **separate checks**.
A published JUnit summary is not line or branch instrumentation.

## Coverage matrix and gates

| Runtime | Existing CI lane | Release qualification |
| --- | --- | --- |
| GTK3 X11 | Linux GTK3 x11 | JUnit + API/JNI + visible-screen and resource gates |
| GTK4 X11 | Linux GTK4 x11 | same plus GTK4-specific regressions |
| GTK4 Wayland | Linux GTK4 wayland | compositor/scale/clip/selection regressions |
| Windows | Win32 x86_64 | native load, JUnit, API/JNI and UI screenshots |
| macOS Intel | Cocoa x86_64 | native load, JUnit, display-thread behavior |
| macOS ARM | Cocoa aarch64 | native load, JUnit, display-thread behavior |

A test case counts as executed only if the **backend's current run** emitted
a valid, nonempty JUnit XML report. Keep tests, failures, errors and skips
separate. Source inventory and the number of test files do **not** establish
method/line/branch coverage. Do not substitute `Control.print` screenshots
for actual screen-captured pixels or use a virtual Tree's Java facade count
as the native row-count/resource proof.

For atomized/new M3 code, aim for at least **90% line and branch coverage**,
with a **99% objective** for release-critical contract atoms, verified using
real instrumented reports (e.g. JaCoCo XML) and documented exclusions.
The 99% figure appearing in older viewport design documents is a **target**,
not independently verified against the current master. The current
`tools/release/readiness.py` checks **JaCoCo report-wide totals** only;
report-wide 90% does *not* prove 90% for each changed behavioral atom.
Per-method/atom coverage and scope completeness need separate source-pinned
review before product release. Unmeasured coverage must remain `PENDING`.
JUnit green + zero pixel-differential errors + matched native builds +
measured CPU/heap/RSS are independently necessary.

## Read-only release surface verifier

The included Python stdlib tool checks current OSGi naming and required
CI/test/docs anchors. Its default invocation is source-only and **does not
claim UI tests or code coverage passed**.

```sh
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
python3 tools/release/readiness.py --root .
```

After running *all six* real backend matrix jobs, export their complete
JUnit XML trees and a source-pinned aggregated JaCoCo XML. The following
command is a **template**, not a command that has been run here:

```sh
python3 tools/release/readiness.py --root . --release \
  --junit gtk3-x11=/evidence/gtk3-x11 \
  --junit gtk4-x11=/evidence/gtk4-x11 \
  --junit gtk4-wayland=/evidence/gtk4-wayland \
  --junit win32=/evidence/win32 \
  --junit cocoa-x86_64=/evidence/cocoa-x86_64 \
  --junit cocoa-aarch64=/evidence/cocoa-aarch64 \
  --jacoco /evidence/jacoco.xml --min-line 0.90 --min-branch 0.90
```

It fails closed on missing backend reports, failures, missing JaCoCo counters
or low coverage. Its `surface_ready` is **only this documentation/naming and
reported-test surface**. It cannot verify that the external reports were
produced for the exact release commit; capture the commit, Maven/JDK/OS/arch,
native hash, report SHA-256, command and workflow run ID in a separately
reviewed source-bound release evidence manifest before publishing.

## Still required before a tag

An upstream-diff review; binary/API Tools comparison; JNI generator diff;
native rebuild per OS/architecture; six platform UI/JUnit runs;
visible-screen geometry/repaint/clip tests; resource lifetime/CPU/heap/RSS
probes; signed/legal provenance; exact release notes, immutable tag and
SHA-256 evidence. None is inferred from this preparation PR.
