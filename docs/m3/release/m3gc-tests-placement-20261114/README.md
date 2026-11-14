# m3gc tests: their own test module (2026-11-14)

## Failure

The Eclipse SDK build on JDK 21 (aggregator rerun `p2fix-r4`, Tycho 5.0.4, ECJ) built 368 of 369 modules and stopped in
`org.eclipse.swt.tests` with 484 compile errors. 17 of them are fixed on master by #351-#354. The other 467 are in
`JUnit Tests/org/eclipse/swt/tests/junit/m3gc/`: those tests import `org.eclipse.swt.internal.render.m3.*`
(`M3ExternalGC`, `M3TextDrawing`, `IM3Surface`, `IM3Resources`, `DpiScalerUtil`, ...) and Skija
(`io.github.humbleui.skija`, `io.github.humbleui.types`), none of which is on that bundle's classpath.

## Placement decision

Keep the tests and keep compiling them (leaving them out of a compile would weaken a gate).

1. Option 1, the normal OSGi way (Require-Bundle / Import-Package / fragment host to a bundle that ships the render
   tree, with friend exports like #351): **does not apply.** The render sources exist
   (`bundles/org.eclipse.swt/Eclipse SWT Render M3/win32`, 31 files), but no bundle of the reactor compiles them: the
   win32 fragment's `build.properties` does not list the folder, and `bundles/org.eclipse.swt.gpu.m3` (a plain Maven
   jar, not a Tycho bundle) compiles three sources of `Eclipse SWT GPU M3/win32` only. Skija 0.116.3 is no bundle of the
   target platform either, and the SDK build compiles the test bundle for linux/gtk while the render tree is win32 only.
2. Option 2, **taken**: the 33 m3gc sources move unchanged (git rename, R100) to `tests/org.eclipse.swt.tests.m3gc`, a
   plain Maven module modelled on `bundles/org.eclipse.swt.gpu.m3`, registered in the SWT root `pom.xml` right after
   `tests/org.eclipse.swt.tests`. It compiles the render tree as test code plus the tests against the reactor's
   `org.eclipse.swt.win32.win32.x86_64` fragment, Skija 0.116.3, types 0.1.1, JUnit 5.14.4 and Platform 1.14.4 in every
   build (also with `-DskipTests`, so the Eclipse SDK build compiles them), and runs the `AllM3GcTests` suite on Windows
   x86_64 with `-DskipNativeTests=false`. See `tests/org.eclipse.swt.tests.m3gc/Readme.md`.

`org.eclipse.swt.tests` keeps its optional `Import-Package` of the Skija packages:
`Test_org_eclipse_swt_internal_render_Win32Backends` still probes `M3RowCanvas` and Skija by name at run time.

## Gates (JDK 21.0.9, branch head a4dc3f04bf27, base master ff1ab2359c4d)

| Gate | Result | Evidence |
| --- | --- | --- |
| api_loss audit v1.1 (rename-aware), master..head | java_files=33 lost=0 gained=0 | `gates/api-loss.tsv` |
| (the same census without rename pairing, v1) | 227 keys lost in the old paths = the 227 gained in the new paths | - |
| javac win32 x86_64 | 893 sources, 1,373 classes, 0 errors | `gates/javac-win32.summary.tsv` |
| javac GTK linux x86_64 | 682 sources, 1,020 classes, 0 errors | `gates/javac-gtk.summary.tsv` |
| javac Cocoa macosx aarch64 | 821 sources, 1,129 classes, 0 errors | `gates/javac-cocoa.summary.tsv` |
| org.eclipse.swt.tests, javac | 393 sources (9 performance sources excluded), 0 errors | `gates/javac-tests.summary.tsv` |
| org.eclipse.swt.tests, ECJ under the SDK build's access rules (`ecj_access_gate.sh` of #351) | 393 sources, **0 errors** (was 918 unresolved m3gc errors on #351..#354), 0 forbidden accesses, 3,970 warnings (3,920 discouraged access) | `gates/ecj-swt-tests.summary.tsv` |
| org.eclipse.swt.tests.m3gc, `mvn clean verify -DskipNativeTests=false` (invisible desktop) | BUILD SUCCESS: 64 sources compiled (31 render + 33 test), 97 tests, 0 failures, 0 errors, 0 skipped, 0 `-Xcheck:jni` diagnostics | `gates/module.summary.tsv`, `gates/module.steps.txt`, `gates/TEST-*.xml.gz` |
| org.eclipse.swt.tests.m3gc, `mvn clean verify` (no flags) | BUILD SUCCESS: 64 sources compiled, tests skipped | `gates/module-default.summary.tsv` |

The module run used the win32 x86_64 fragment jar packaged from the gate's own javac classes with the fork natives
(`build_superset_jar.sh`, 4975r19), placed in a scratch local repository as the
`org.eclipse.swt.win32.win32.x86_64:3.138.0-SNAPSHOT` reactor artifact. 97 tests is the count of the earlier m3gc gate
on master 6060fce913 (`docs/m3/release/swt-tests-sdk-compile-20261114/gates/junit-m3gc.*`).

## Reproduce

```
bash docs/m3/release/m3gc-tests-placement-20261114/tools/placement_gates.sh <worktree as C:/... path> <base-ref> <scratch> [api compile jar tests module]
python docs/m3/release/m3gc-tests-placement-20261114/tools/api_loss_audit.py <git-dir> <before> <after> <out.tsv>
```
