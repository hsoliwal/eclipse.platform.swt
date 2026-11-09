# SWT candidate packaging, naming and coverage

This is the candidate packaging guide for `hsoliwal/eclipse.platform.swt`, the
SWT downstream fork used by the M3 programme. It complements the existing
[release-readiness surface and evidence intake](RELEASE_READINESS.md). A build candidate becomes a release
only after the exact source revision and its artifacts meet the applicable
platform, compatibility, test, licensing and coverage gates below. The packaging
workflow records `candidate-unqualified`; creating an archive does not promote it.

## Names and compatibility

| Surface | Name or authority |
| --- | --- |
| Product in documentation | **SWT (M3 fork)**; identify upstream as Eclipse SWT |
| Public Java packages and widget types | Keep `org.eclipse.swt.*`, Canvas, GC, Image, Control, Table, Tree and existing APIs |
| OSGi identities | Existing `Bundle-SymbolicName`, `Fragment-Host`, exported packages and module names |
| Bundle versions | The actual `Bundle-Version` in each built JAR; record the full qualifier |
| Maven/Tycho project version | The version in the project POM and parent; it is not the SWT bundle version |
| Native library names and JNI symbols | Existing platform conventions and generated JNI contract |
| Primary Actions artifact | `swt-windows-macos-candidate-<full-source-sha>-attempt-<run-attempt>` |
| Primary ZIP | `swt-windows-macos-candidate-<first-12-source-sha>.zip` |
| Integrity sidecar | The ZIP filename followed by `.sha256` |
| Per-platform Actions artifacts | Existing `swt-windows-x86_64`, `swt-windows-aarch64`, `swt-macos-x86_64`, `swt-macos-aarch64`, `swt-macos-universal` |
| Deprecated aggregate alias | `swt-all-platforms-bundle` containing `swt-all-platforms.zip`; byte-identical to the primary ZIP |

Each run attempt gets a distinct primary candidate artifact and an attempt-specific
run URL. Stable per-platform input names and the aggregate alias are refreshed on
a retry; previous primary candidate bundles retain their packaged bytes. The
aggregate download selects only the five declared platform names, so it cannot
consume a previous attempt's aggregate output.

The historical aggregate alias remains for existing automation. It contains only
the Windows/macOS candidates listed in its manifest. Migrate consumers to the
primary artifact name. Neither the alias nor the presence of native fragments
establishes support for another operating system or CPU. Read the full source SHA
from `candidate-manifest.json`; the short filename suffix is a convenience.

Donor names belong in provenance and original notices. Keep existing
interoperability APIs intact. Naming cleanup must not rename compatible Java/JNI,
OSGi, serialized or reflective contracts. The product remains SWT; this process
does not introduce a second toolkit API or a Synexia runtime dependency.

At the inspected source baseline
[`62460082d7caa06b21c49da99abbab53f73b3ffc`](https://github.com/hsoliwal/eclipse.platform.swt/commit/62460082d7caa06b21c49da99abbab53f73b3ffc),
the root parent/project version is `4.42.0-SNAPSHOT` and the main SWT/native
fragment manifests declare `3.138.0.qualifier`. Preserve this distinction; use
the emitted JAR manifests for candidate metadata. This guide does not allocate a
new release number or tag.

## What the current packaging workflow produces

[`build-swt-jars.yml`](../.github/workflows/build-swt-jars.yml) builds native JARs
using Java 21, with tests skipped in those build steps. It builds Windows x86_64
and aarch64, macOS x86_64 and aarch64, then combines the two macOS native sets into
the existing universal artifact. Both Linux packaging jobs remain disabled; the
workflow documents the older glibc/sysroot requirement. Android and iOS artifacts
are not produced by this workflow.

Each platform upload includes `swt-source-sha.txt` from its actual checkout.
The final packaging job runs the Java 21 source launcher for
[`SwtCandidateBundle`](../tools/swt-release/src/main/java/org/eclipse/swt/tools/release/SwtCandidateBundle.java).
Before producing the ZIP, it requires:

- Exactly the five declared platform artifact directories, each bound to the
  expected full source SHA. Missing or unexpected platforms fail packaging.
- Exactly one expected runtime fragment per platform, and one SWT host bundle in
  every non-universal platform artifact. Duplicate or absent runtime bundles fail.
- Valid bundle identities and matching numeric SWT bundle versions, including
  any source JARs. Full build qualifiers are retained separately and may differ
  between modules.
- A nonempty SWT native library in each runtime fragment. Empty files and
  unresolved Git LFS pointers are rejected in all shipped native libraries,
  including support libraries such as `WebView2Loader.dll`. This checks presence,
  not binary loadability or ABI parity.
- Safe, unique JAR entry paths and regular, nonsymlink input files. Unexpected
  files, invalid ZIPs and missing manifests fail instead of being silently omitted.
  Every JAR entry is streamed with bounded memory to verify its decompressed size
  and CRC, including class/resource entries that native checks do not inspect.

The ZIP includes `candidate-manifest.json`, `CANDIDATE-STATUS.txt` and
`SHA256SUMS`, alongside the existing platform directory layout. The manifest
records the producing run URL, full source SHA, every input path, byte length,
SHA-256, and JAR bundle identity/version. It explicitly records absent Linux
artifacts and unverified native tests/coverage. `SHA256SUMS` covers the inputs and
metadata; the external `.zip.sha256` covers the complete archive. Input hashes
are checked again while archiving. The same inputs, SHA and run URL, using the same Java toolchain, produce
byte-identical output archives. Cross-toolchain ZIP-compressor identity is not
asserted.

The packaging tests use small synthetic JARs and deliberately non-loadable native
fixtures. They qualify the packaging tool only. The source marker is an identity
record from the build, not a signature or independent attestation.

## Test and coverage gates

Use the existing upstream and M3 suites; preserve their native, behavioral and
resource oracles. Keep failures, skipped tests, aborted tests, exclusions and
unexecuted platforms visible. A green packaging job cannot replace these gates.

| Gate | Existing route and required evidence |
| --- | --- |
| Candidate packaging | `mvn -f tools/swt-release/pom.xml clean verify`; JUnit results plus JaCoCo XML/HTML for the packaging tool |
| Shared and backend widget behavior | [`maven.yml`](../.github/workflows/maven.yml) invoking [`build.yml`](../.github/workflows/build.yml); Surefire XML, test counts, exact command and environment |
| API and Javadoc | Existing `api-check` and `javadoc` profiles; retain their diagnostics for each affected OS implementation |
| JNI generation | `mvn --non-recursive -Pjni-generator exec:exec@run-jni-generator`; review generated-file diff, rebuild and load the matching native libraries |
| Native architecture/load | Existing Windows PE checks and macOS universal construction, followed by runtime loading and widget tests on each claimed architecture |
| Viewport rendering and lifecycle | Existing screenshot/differential/lifecycle suites; SWT screen captures, pixel assertions and OpenCV negative controls where applicable |
| Resource and performance behavior | Existing isolated CPU, Java heap and process/native RSS gates; keep capture work outside measurement intervals |
| M3JDK21 interoperability | Run the relevant SWT suites on the exact candidate M3JDK21 image and record both source/artifact identities |
| Recipe replay | Canonical Synexia Maven/OpenRewrite recipes; exact pre/postimages, repeat-application fixed point and drift/missing-input refusal |
| Licensing and delivery | Preserve upstream/donor licenses, NOTICE and provenance; archive the qualified artifacts, checksums and complete test receipts |

The main matrix currently selects **Java 25**, with GTK3/X11, GTK4/X11,
GTK4/Wayland, Win32/x86_64, Cocoa/x86_64 and Cocoa/aarch64. That does not establish
Java 21 or M3JDK21 execution, nor Windows/aarch64 or Linux/aarch64 runtime
qualification. GTK TODO exclusions and property-gated viewport/resource suites
must be listed in the release receipt. The compile/build JDK and the JDK used to
execute a suite are separate evidence fields.

### Coverage scope and reproduction

The new build-time tool has an isolated Maven project, with JUnit failure on zero
tests and JaCoCo instrumentation. Its `verify` gate requires at least 95% line and
90% branch coverage of this tool's classes. These scoped floors do not apply to,
or assert coverage of, the SWT widget product. Run:

```sh
mvn --batch-mode -f tools/swt-release/pom.xml clean verify
```

Reports are generated at:

- `tools/swt-release/target/surefire-reports/` — executed JUnit cases and outcomes.
- `tools/swt-release/target/site/jacoco/jacoco.xml` — exact instruction, line,
  branch, method and class counters for the candidate packager.
- `tools/swt-release/target/site/jacoco/index.html` — source coverage view.
- `tools/swt-release/target/jacoco.exec` — raw execution data for the same classes.

The workflow retains these as `candidate-packager-test-evidence-attempt-<run-attempt>`. They do not
measure SWT widget or native C coverage. Use raw covered/missed counters and name
the denominator; do not combine unrelated class or backend reports into a single
whole-product percentage. Counts of tests are not a coverage percentage.

Existing feature-specific evidence includes
[all-widgets coverage](m3/allwidgets-viewport/coverage/COVERAGE.md),
[enhanced API coverage](m3/enhanced-api/coverage/COVERAGE.md),
[table coverage](m3/table-viewport/coverage/COVERAGE.md),
[tree gaps](m3/tree-viewport/COVERAGE_GAPS.tsv), and
[render-mode coverage tooling](m3/render-modes/tools/coverage_unit.sh).
Those records describe their stated historical revisions, source/class sets,
backends and test modes. Re-run them against the candidate when claiming coverage
for that candidate. Keep raw missed branches visible even when a report explains
why a branch is unreachable or not recorded by the instrumenter.

## Release receipt and promotion

Prepare one receipt for the exact candidate. Include source SHA, recipe revision,
base/parent SHAs, artifact names and hashes, producing run URL, actual bundle
versions, OS/CPU/backend/display/DPI, build and execution JDKs, Maven version,
commands, suite results, exclusions, coverage numerator/denominator, resource
measurements, license/provenance checks and unresolved gates. Preserve the
append-only history; a changed source SHA requires new qualification evidence.

Promotion requires completed evidence for every advertised platform. An absent
platform, skipped test, queued job, failed build, or historical receipt remains
an open gate. Keep the candidate state until those gates close. This preparation
workflow does not publish a release, sign artifacts, create a tag or change
branch protection. Retained Actions artifacts expire after their configured
retention period; a released distribution needs its qualified files and receipts
retained with the release.
