<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT M3 fork: release preparation and evidence matrix

Status: **RELEASE HOLD — review and qualification required** (2026-11-07).
This fork is a separately qualified SWT product target, **not** an implicit
component of a successfully tested M3JDK21 image. Do not publish a tag or
claim cross-platform compatibility merely because a source branch builds.

## Naming and compatibility boundaries

| Contract | Preserved release identity |
| --- | --- |
| Product | Eclipse Standard Widget Toolkit (SWT), experimental M3 downstream fork |
| OSGi symbolic name | `org.eclipse.swt`, unchanged; do not rebrand it as `com.m3.swt` |
| Java automatic module | `org.eclipse.swt`, unchanged |
| Public Java API | Existing `org.eclipse.swt.*` package names, type/member descriptors and behavioral contracts |
| Required execution environment | `JavaSE-21` in the current bundle manifest |
| CI compiler/test JDK | Java **25** in the current `maven.yml` matrix; not the same as the minimum supported API runtime |
| Internal M3 implementation | Existing `org.eclipse.swt.internal.*` packages and `M3*` helpers only as explicitly qualified internal owners |
| Native ABI | Platform-specific JNI entry points and generated glue must match Java declarations and the native fragment |
| Licensing | Preserve upstream EPL-2.0 and rights-holder-specific license/NOTICE headers; do not apply another license to upstream files |

Maintain the platform-specific `gtk`, `win32`, and `cocoa` backends,
`META-INF/MANIFEST.MF`, and the Javadoc/API baseline. An implementation
optimization may not change the public symbol spelling, event order,
thread-affinity, disposal rules, accessibility, clipboard/drag-and-drop,
printing or native resource ownership.

The [developer guide](DEVELOPMENT_GUIDE.md) is the build/setup authority.
`SYNEXIA_CONVERGENCE_MODEL.md` and `AGENTS.md` govern recipe provenance and
receiving history. Do not copy donor recipe projects into this public target.

## Executed test coverage, not just test filenames

The current main Maven test module selects `AllTests` in
`tests/org.eclipse.swt.tests/pom.xml`. `AllTests.java` itself has an
explicit `@SelectClasses` list. A test file appearing in the checkout is
**not** evidence that JUnit discovered or executed it.

This preparation change makes the following two tests mandatory:

| Test | Primary purpose | Acceptance requirement |
| --- | --- | --- |
| `Test_ItemBitSetDifferential` | Seeded `java.util.BitSet` parity for sparse/dense/ranged item-state operations, 65,536-position boundaries and next/count queries | Included in `AllTests`, compiled and reported as passed in Surefire XML |
| `Test_ItemBitSetMaximumPosition` | Existing `Integer.MAX_VALUE`, high-container and shift boundary proof | Included in `AllTests`, compiled and reported as passed in Surefire XML |

[`check_release_surface.py`](../tools/release/check_release_surface.py)
fail-closes on manifest naming changes, missing matrix declarations, missing
JUnit sources or missing `AllTests` registrations.
[`check_junit_coverage.py`](../tools/release/check_junit_coverage.py)
reads actual `TEST-*.xml` Surefire results and requires **at least one
successful testcase per required class, with no failed or skipped cases**.
The common platform build workflow now runs this XML gate after Maven; both
static and XML checkers have synthetic refusal tests. Static discovery and
JUnit execution remain different receipts.

These tests intentionally cover only the **M3 sparse-state release slice**.
They do not establish all-widget statement/branch coverage, JNI/native
memory safety, or correctness of every viewport implementation. A full
inventory of unregistered tests and a broader per-class coverage ledger are
still release work, not implied by the two new gates.

## Required platform qualification

| Lane | Release acceptance |
| --- | --- |
| Linux GTK3/X11 | Source and native build, JUnit suite, focused Table/Tree/List/scrolling and event-loop tests, JNI consistency, screenshot/viewport geometry evidence |
| Linux GTK4/X11 and GTK4/Wayland | Own green native, event, selection, disposal and screenshot checks; an ignored `gtk4-todo` group is not evidence of compatibility |
| Windows Win32 | Win32 native fragment, test suite, DPI/scaling, focus, accessibility, printing, native handle and resource teardown |
| macOS Cocoa x86_64 and aarch64 | Native fragment, headful JUnit, coordinate/DPI, selection, rendering, event scheduling and disposal |
| Shared `org.eclipse.swt.*` contracts | ABI/API/Javadoc consistency, preserved module and OSGi identities, no behavior-changing rename |
| M3 viewport/residency | CPU model and GPU/window surface are independently tested; distant offsets, huge virtual lists/trees, overscan, selection, scrollbars and memory bounds have verified native evidence |
| License/provenance | EPL-2.0 and donor attribution preserved; original source and generated JNI inputs reconciled |
| Distribution | Installed JARs and matching native fragments load in a standalone application; version and SHA-256 are tied to the exact tagged source |

Use actual host-native CI reports, not an unrelated stock JDK/JAR run or
cross-compilation alone. `viewport-screenshots.yml` and the
`superset-viewport-matrix.yml` provide additional evidence surfaces where
configured. Flag all missing, canceled, or skipped platform lanes as
**PENDING**, not `PASS`.

## Commands and required receipts

```sh
git rev-parse HEAD 'HEAD^{tree}'
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
python3 tools/release/check_release_surface.py --root . --strict \
  --json-out target/release-preflight.json
mvn --non-recursive -Pjni-generator exec:exec@run-jni-generator
git diff --exit-code
# Then use the OS/display-specific Maven matrix documented in .github/workflows/maven.yml.
```

The JNI generation command mutates generated files; review its diff and keep
Java declarations/generated native glue consistent. To establish **executed**
test coverage, run a properly provisioned platform build, preserve
`tests/org.eclipse.swt.tests/target/surefire-reports/TEST-*.xml`, then run:

```sh
python3 tools/release/check_junit_coverage.py \
  --reports-root tests/org.eclipse.swt.tests/target/surefire-reports \
  --json-out tests/org.eclipse.swt.tests/target/release-junit-coverage.json \
  --strict
```

Record exact commit and tree, toolchain, OS/architecture, selected native
fragment, JUnit test and skip totals, screenshot diffs, JNI generation diff,
leaks/GC/native-memory checks, artifact hashes, exclusions and known failures.
A passing preflight only proves its stated static checks; the release remains
blocked until the complete native/GUI/platform and license gates pass.
