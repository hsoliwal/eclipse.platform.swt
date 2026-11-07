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

Source-only mode keeps this invocation and succeeds when its source anchors
are present; it always reports `surface_ready: false` and
`release_admitted: false`. The Python tests use synthetic fixtures. Their
coverage measures this verifier, not SWT widgets or native backends. The
independent Java candidate-packaging suite is described in the
[packaging guide](RELEASE_PACKAGING.md).

After running *all six* real backend matrix jobs for one immutable source
commit and one workflow run/attempt, export their complete JUnit XML trees
and the aggregated JaCoCo XML. Capture the report-binding manifest with those
reports. Keep reports, the manifest and verifier output **outside the source
checkout**. The following command is a **template**, not a command that has
been run here; replace `FULL_LOWERCASE_RELEASE_COMMIT` with the expected
40-character commit ID from the release workflow:

```sh
python3 tools/release/readiness.py --root . --release \
  --source-sha FULL_LOWERCASE_RELEASE_COMMIT \
  --evidence-manifest /evidence/report-binding.json \
  --junit gtk3-x11=/evidence/gtk3-x11 \
  --junit gtk4-x11=/evidence/gtk4-x11 \
  --junit gtk4-wayland=/evidence/gtk4-wayland \
  --junit win32=/evidence/win32 \
  --junit cocoa-x86_64=/evidence/cocoa-x86_64 \
  --junit cocoa-aarch64=/evidence/cocoa-aarch64 \
  --jacoco /evidence/jacoco.xml --min-line 0.90 --min-branch 0.90
```

Release mode requires both new arguments. It checks that `--root` is the Git
worktree root, `HEAD` equals the expected commit, and Git reports no staged,
unstaged, deleted or nonignored untracked files. Ignored build outputs remain
outside this cleanliness check; it does not prove complete build-input
provenance. Missing/failed backend tests, all-skipped backends, missing JaCoCo
counters and coverage below 90% still fail independently of manifest binding.

### Source/report binding contract

The manifest is strict JSON with no duplicate or unknown keys. Its top-level
fields are:

| Field | Required value |
| --- | --- |
| `schema` | `SWT_REPORT_BINDING_V1` |
| `repository` | `hsoliwal/eclipse.platform.swt` |
| `source_sha` | The same full lowercase commit as `--source-sha` and the checked-out `HEAD` |
| `run_id` | A positive integer GitHub Actions run ID |
| `run_attempt` | A positive integer run attempt |
| `run_url` | Exactly `https://github.com/hsoliwal/eclipse.platform.swt/actions/runs/<run_id>/attempts/<run_attempt>` |
| `junit` | An object with exactly the six backend keys in the matrix below |
| `jacoco` | The coverage binding record described below |

Each `junit` record must contain exactly `backend`, `source_sha`, `run_id`,
`run_attempt`, and `files`. The backend value must equal its object key. Source,
run and attempt must match the top-level values, including integer types.
For example, the **template for the `win32` record** is:

```json
{
  "backend": "win32",
  "source_sha": "<full lowercase release commit>",
  "run_id": 123456789,
  "run_attempt": 2,
  "files": [
    {
      "path": "TEST-org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Display.xml",
      "sha256": "<64 lowercase SHA-256 characters>"
    }
  ]
}
```

Supply a record for every backend; this example is not a complete manifest:

| Required JUnit key | Backend identity |
| --- | --- |
| `gtk3-x11` | GTK3 on X11 |
| `gtk4-x11` | GTK4 on X11 |
| `gtk4-wayland` | GTK4 on Wayland |
| `win32` | Windows Win32 |
| `cocoa-x86_64` | macOS Intel |
| `cocoa-aarch64` | macOS ARM |

`files` must exactly inventory the parser's `TEST-*.xml` files beneath the
corresponding `--junit` directory. Paths are relative, canonical POSIX paths
using `/`; an explicit XML-file argument uses that file's basename. Each entry
has exactly `path` and `sha256`. Order does not matter. Missing, additional,
duplicated, renamed or changed reports fail. Symlinks and reuse of a physical
report file through another input are refused. Identical report bytes copied
between backend inputs also fail; each backend must supply its own reports.
The verifier parses and hashes the same byte snapshot for each XML file.

The `jacoco` record contains exactly `source_sha`, `run_id`, `run_attempt`,
`backends`, and `files`. Its source/run/attempt must match the top-level values.
Its `backends` must be this ordered list:

```json
["gtk3-x11", "gtk4-x11", "gtk4-wayland", "win32", "cocoa-x86_64", "cocoa-aarch64"]
```

Its `files` contains exactly one `path`/`sha256` entry for the basename and bytes
of the `--jacoco` XML. A correct report binding cannot waive test failures or
coverage floors, and report-wide coverage cannot establish per-atom scope.

### What a successful result establishes

`surface_ready: true` means only that naming/source anchors, supplied report
results, thresholds, clean checkout and **declared content binding** agree.
`report_binding` records the checked commit, manifest digest and run identity,
and explicitly reports `producer_authenticity: "not-verified"`. The tool makes
no GitHub API query or signature verification. A consistent caller-written
manifest can describe synthetic reports; it does not prove that the reports
were produced by the named backend, source revision or workflow. Do not
manufacture a receipt after the fact to turn arbitrary XML into qualification.

The result therefore **always contains `release_admitted: false`**, including
when the process exits zero. It also names the remaining release gates. A
publisher must not treat this tool's exit code or `surface_ready` as product
release authorization. Independently authenticate the producer/run/artifact
provenance and review the exact commit, Maven/JDK/OS/architecture, native
hashes, build/test commands and instrumentation scope before using the report
set as runtime evidence. API/JNI, native/UI, visible-screen and resource gates
remain separate requirements below.

## Still required before a tag

An upstream-diff review; binary/API Tools comparison; JNI generator diff;
native rebuild per OS/architecture; six platform UI/JUnit runs;
visible-screen geometry/repaint/clip tests; resource lifetime/CPU/heap/RSS
probes; signed/legal provenance; exact release notes, immutable tag and
SHA-256 evidence. None is inferred from this preparation PR.
