# SWT tests and release coverage

Requires the `org.eclipse.test.performance` plug-in from the Eclipse releng target.
The test bundle uses JUnit Jupiter and the JUnit Platform suite engine. The normal
Maven selector is `AllTests`, configured in this bundle's `pom.xml`; adding a class
to this source directory alone does not put it in that explicit suite.

## Normal platform tests

From the repository root, on a provisioned native host:

```sh
mvn --batch-mode --no-transfer-progress clean verify \
  -Dnative=gtk.linux.x86_64 -DskipNativeTests=false
```

Use the corresponding `win32.win32.x86_64`, `cocoa.macosx.x86_64` or
`cocoa.macosx.aarch64` native coordinate on those hosts. Cocoa tests need the
existing `-XstartOnFirstThread` configuration. GUI tests require a desktop (Xvfb
or a Wayland compositor is used in Linux CI). See the
[native build guide](../../bundles/org.eclipse.swt/Readme.md) and the
[matrix](../../.github/workflows/maven.yml).

The matrix executes Java 21 and Java 25. Its API/Javadoc profiles remain on the
Java 25 legs because the current parent configures that documentation toolchain.
A Java source compile and a native JUnit run are recorded as different gates.

## Enhanced M3 API discovery

`AllTests` includes `AllNonBrowserTests`, whose `AllWidgetTests` now explicitly
selects the 26 ordinary `Test_M3*` classes in the proposed source tree. Their 228
`@Test` methods are an inventory; the JUnit XML from an actual run is the pass,
failure and skip authority. Maintain explicit selectors when a new ordinary
contract test class is added.

For a focused native run of ordinary enhanced-API contracts:

```sh
mvn --batch-mode --no-transfer-progress verify -pl :org.eclipse.swt.tests -am \
  -Dnative=gtk.linux.x86_64 -DskipNativeTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-Dtest=Test_M3*,!Test_M3*Differential'
```

Run viewport and original modes in fresh JVMs by repeating with
`-Dswt.widget.mode=viewport` and `-Dswt.widget.mode=original`. Keep both result sets;
a merged coverage file does not establish equivalent outcomes in each mode.

`AllNonBrowserTests` also selects the seven `Test_M3*Differential` classes once
through `AllM3DifferentialTests`, and selects the four-method
`Test_org_eclipse_swt_internal_render_RowPaintRetention` class once. Together
with the unchanged 26 ordinary selections, this is a 34-class / 245-method source
inventory. Six differential classes require independent official SWT recordings
for candidate replay. Recording creation remains a separate reference stage.
The scale-only `Test_M3SortFilterDifferential` runs its existing seeded campaign
up to one million rows when the default suite selects it; qualify the added
runtime and memory cost on each release platform.

Use the [existing differential harness](https://github.com/hsoliwal/com.synexia/blob/develop/forks/eclipse.platform.swt/docs/m3/VIEWPORT_DIFFERENTIAL_HARNESS.md)
and [release guide](https://github.com/hsoliwal/com.synexia/blob/develop/forks/eclipse.platform.swt/docs/m3/RELEASE.md) to supply the independent oracle,
mode and budget. A missing recording may be reported as a JUnit skip, so the
[exact M3 report gate](https://github.com/hsoliwal/com.synexia/blob/develop/forks/eclipse.platform.swt/docs/m3/release/REPORT_GATE.md) must follow a
successful complete `AllTests` run. It requires 245 passes in viewport mode and
244 passes plus the existing one-method RowMath skip in original mode; all 11
recorded-oracle methods must pass in both modes. The focused ordinary-only
command above does not satisfy this complete release-report contract.

## Headless contract scope

These eight existing classes execute without creating a native Display:

- `Test_org_eclipse_swt_internal_Collation`
- `Test_org_eclipse_swt_internal_ColumnFilter`
- `Test_org_eclipse_swt_internal_ColumnSort`
- `Test_org_eclipse_swt_internal_ItemRange`
- `Test_org_eclipse_swt_internal_RowPatch`
- `Test_org_eclipse_swt_internal_TextMatch`
- `Test_org_eclipse_swt_internal_TextSegments`
- `Test_org_eclipse_swt_internal_TokenMatch`

They can be selected individually with `-Dtest` after the target is resolved and
compiled. The recorded 7 November 2026 run used the actual GTK Java source set,
Java 21, JUnit Platform 1.14.4 and JaCoCo 0.8.15. It passed 77/77 tests with zero
skips or aborts. It did not execute native widget tests. Its exact class/source
scope and raw counters are in
[HEADLESS_20261107.json](https://github.com/hsoliwal/com.synexia/blob/develop/forks/eclipse.platform.swt/docs/m3/enhanced-api/coverage/HEADLESS_20261107.json).
That historical run predates the two added ItemRange methods and one added
RowExtents method in the reviewed PR #324 source. All three remain required
in current reports, and the ItemRange adjacency repair needs fresh Java and
JaCoCo evidence. Do not increase the old 77-test result or reuse its source
hash for the repaired owner. The unapplied supplement adds four further
RowExtents methods for SMART and AUTO alignment, anchoring boundaries and sparse
maximum row counts. Execute them through the actual JUnit provider and bind
any focused Java17 instrumentation to its exact source, test and classfile
hashes. A separate independent model harness cannot substitute for JUnit or
measured JaCoCo evidence. Java21/25 and native qualification remain separate.

`VirtualRowExtentsTests`, a test of a package-private type, runs headless from the SWT bundle's own
`Eclipse SWT Tests/win32` folder.

## Coverage evidence

The Maven test JVM consumes `tycho.testArgLine`. To collect execution data for a
focused run, supply a pinned JaCoCo runtime agent through that property, for
example `-Dtycho.testArgLine=-javaagent:/absolute/path/jacocoagent.jar=destfile=/absolute/path/run.exec`.
Use the matching class files from that build when generating the report. Keep the
same exact source checkout, agent version and classfile hashes; recompiled or
historical class files are not interchangeable inputs.

Retain JUnit XML, the execution data, JaCoCo XML/CSV, source and classfile hashes,
full command/toolchain identity, platform, mode and candidate commit. Report
measured line/branch counters without replacing uncovered throwing paths with
manually increased percentages. The per-widget coverage scripts under
`docs/m3/*/coverage/` have platform and oracle prerequisites; a historical CSV is
not a current release test result.

The complete SWT M3 release requirements are in
[RELEASE.md](https://github.com/hsoliwal/com.synexia/blob/develop/forks/eclipse.platform.swt/docs/m3/RELEASE.md).
