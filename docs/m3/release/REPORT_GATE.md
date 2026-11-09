# M3 test-report acceptance

This gate checks actual Surefire reports for the M3 release test inventory. It
does not measure line or branch coverage and does not qualify a release by
itself. Complete the existing platform build, behavioral, screenshot,
CPU/RSS/heap, API and coverage gates separately.

The receiving Java test inventory starts from frozen master
`62460082d7caa06b21c49da99abbab53f73b3ffc` and integrates PRs 321–324 plus the
previous reviewed receiving fixes and five deterministic Filter/Sort methods.
All 34 direct Java sources are bound by exact hashes in the contract. The
`reviewedSource.commit` identifies that receiving base; its `commitRole` and
proposal metadata distinguish the generated source. Supply the actual resulting
candidate commit in run properties, not the receiving base.
`m3-test-report-contract.json` names 33 M3 classes with 241 methods, plus the
four methods in `Test_org_eclipse_swt_internal_render_RowPaintRetention`.
Only matching XML test cases can satisfy the report gate. The maximum-index
regressions, five added RowExtents boundaries, and five added Filter/Sort
contracts cannot be omitted, failed or skipped in either mode. A focused Java
harness does not waive execution through the actual JUnit provider.

## Required results

| Lane | Discovered classes | Passing methods | Exact expected skip |
| --- | ---: | ---: | --- |
| `viewport` | 34 | 245 | None |
| `original` | 34 | 244 | `Test_M3RowMath.test_millionRowVirtualTreeAnswersFromTheLanes` |

The original-mode exception follows that method's existing `M3Mode.VIEWPORT`
assumption. It is not permission to skip a class or any other method. The gate
requires that method to be reported as skipped in original mode. A different
result requires review of the source and the contract.

All 11 methods in these recorded-oracle classes must pass in both lanes:

| Class | Required methods |
| --- | ---: |
| `Test_M3BulkUpdatesDifferential` | 2 |
| `Test_M3DiffApplyDifferential` | 2 |
| `Test_M3ProxyModelDifferential` | 1 |
| `Test_M3RowMathDifferential` | 2 |
| `Test_M3ScrollBarModelDifferential` | 2 |
| `Test_M3ScrollMementoDifferential` | 2 |

`ViewportDifferential.compareWithRecording` skips when a recording directory or
a required seed file is missing. The report gate rejects that skip. It also
rejects recording generation, survey mode, seed filtering, theme/LAF switching
and per-widget mode overrides in a qualifying run. These switches can change
the comparison's meaning; triage runs retain their own evidence.

`Test_M3SortFilterDifferential` is self-contained and is not in the recorded
oracle list. The mode-sensitive row counts in `Test_M3Prefetch` and
`Test_M3Residency` remain the existing source behavior: original mode uses
10,000 rows and viewport mode uses 100,000 rows.

## Run and validate

First qualify and apply the canonical recipe that adds `AllM3DifferentialTests`
and the two `AllNonBrowserTests` registrations, then complete lint and platform
compilation. Keep the 26 ordinary selections in `AllWidgetTests` unchanged;
the added suite selects only the seven existing differential classes, and
RowPaintRetention is selected separately. The resulting scoped inventory has
one selection path for each of its 34 classes.
The test bundle's default selector is `AllTests`. The gate requires the explicit
selector in each report so a narrowed command cannot substitute for that run.
Run each mode in a fresh JVM and a distinct report directory; preserve its
recordings, build logs and test logs.

From `tests/org.eclipse.swt.tests`, after the prerequisite stages pass, append
these parameters to the existing native/display-qualified test invocation:

```text
-DskipTests=false
-DskipNativeTests=false
-DfailIfNoTests=true
-Dsurefire.failIfNoSpecifiedTests=true
-Dtest=AllTests
-Dswt.widget.mode=<viewport-or-original>
-Dswt.viewport.record=false
-Dswt.viewport.survey=false
-Dswt.viewport.recording=<qualified-recording-directory>
-Dm3.release.commit=<full-candidate-commit>
-Dm3.release.run=<unique-build-and-attempt-and-lane-id>
-Dm3.release.platform=<native-platform-and-backend-id>
```

Do not define `swt.viewport.seeds`, even as an empty string: the current harness
interprets its presence as a filter. Do not define `swt.widget.mode.<Widget>`,
`swt.viewport.themeSwitch` or `swt.viewport.lafSwitch` for these lanes. Keep
recording creation in a separate reference run. Archive its jar hash,
reference provenance, platform/backend, seed files and generation evidence.

After that test command exits successfully, run the stdlib-only gate. Supply
the exact values supplied to Maven; the run ID must be fresh for this attempt.

```bash
python3 verify_m3_test_reports.py \
  --commit "$candidate_commit" \
  --run-id "$release_run_id" \
  --platform "$release_platform" \
  --mode viewport \
  --recording-directory "$qualified_recordings"
```

Repeat in the original-mode lane with `--mode original`. The default report
directory is `target/surefire-reports`; `--reports` accepts the separately
retained directory for the lane. The default receipt is
`target/m3-test-report-validation-<mode>.json`; `--receipt` may select a distinct
artifact path. The gate removes an old receipt before checking and creates a
new PASS receipt only after every scoped class and method qualifies.

The XML must use the existing Surefire convention `TEST-<fully-qualified-class>.xml`
with a single `testsuite` root and UTF-8 encoding. DTD/entity declarations and
other declared encodings are refused. Every scoped report must contain the explicit
properties above. Missing, duplicate, mismatched or stale properties fail.
An effective provider configuration that omits these properties or emits a
different report shape must be qualified explicitly; the gate does not silently
weaken its contract. Reports for other `AllTests` classes are outside this
adapter's scope; their failures still fail the Maven test stage.

The receipt includes actual class/method outcomes, XML hashes, Java source
hashes, the expectations hash, mode and run identity. Source hash and inventory
checks require the reviewed Java test source set to match. If tests change,
review and regenerate the explicit contract together with its fixture tests.
Report properties do not authenticate a binary or recording: separately bind
the candidate jar, loaded classes, resolved dependencies and reference artifact
to the archived build evidence.

Run both lanes on each supported release platform: GTK3/X11, GTK4/X11,
GTK4/Wayland, Win32 x86_64, Cocoa x86_64 and Cocoa aarch64 as selected by the
existing Maven workflow. This parser has no platform-specific skip allowance.
An unsupported or failing lane stays an explicit release gate. PR #324 declares
Java 21 and Java 25 for each native platform/architecture lane. Keep both
compiler/runtime identities in the receipts. Strict Javadoc uses Java 25 legs,
consistent with the inspected parent tooling; this supplement does not change
the matrix or infer successful execution from its declaration.

## Strict release Javadoc

The reusable `build.yml` input `strict_javadoc` defaults to false. A release
caller sets it to true to add `-Pjavadoc -DfailOnJavadocErrors=true`; ordinary
callers retain their current behavior. The existing parent `javadoc` profile
binds `maven-javadoc-plugin:jar` at `package` and sets `failOnError` from that
property. The command-line property overrides the SWT bundle's local false
value. Preserve normal test-bundle Javadoc exclusions.

Before accepting this gate, archive the resolved effective POM showing the
active Javadoc execution and `failOnError=true`, the Javadoc log and generated
API artifact. The inspected parent source is
[`eclipse-platform-parent/pom.xml` at `481b9f832969add4d88f4ad62c9549af8ab9d4ec`](https://github.com/eclipse-platform/eclipse.platform.releng.aggregator/blob/481b9f832969add4d88f4ad62c9549af8ab9d4ec/eclipse-platform-parent/pom.xml),
version `4.42.0-SNAPSHOT`; the actual resolved snapshot remains build evidence.
Strict errors do not claim warnings, API compatibility or rendered-doc quality
have passed.

## Validator qualification

The adapter reuses `qualified_suite` from `verify_viewport_screenshots.py`.
Existing screenshot/resource callers retain zero-skip behavior. Its optional
named-skip argument requires both an exact method identity and an actual skip.
The M3 caller uses that argument only for the single original-mode exception.

After patch and lint, compile the three Python files, then run
`python3 -m unittest -v test_verify_m3_test_reports.py` from the test-bundle
directory. The fixture suite checks viewport and original positive evidence,
legacy zero-skip behavior, and rejected missing classes/methods, skipped
recording comparisons, duplicate evidence, spoofed counters, malformed XML,
wrong run/mode/selector, dangerous harness switches and source drift. Its
temporary Java and XML files are synthetic inputs; passing fixtures is evidence
for the parser, not execution of SWT tests.

The integrated receiving candidate adds five deterministic Filter/Sort methods:
invalid updated parents with reset recovery, finite ancestor-cycle fallback,
synchronous generation and criteria copies, stable duplicate keys through edits,
and rejected sort-key configuration preservation. Each is source-hash bound and
required to appear and pass in the complete native AllTests report.
