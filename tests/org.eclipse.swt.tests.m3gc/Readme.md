# SWT M3 render backend tests (m3gc)

JUnit tests of `org.eclipse.swt.internal.render.m3` (source folder
`bundles/org.eclipse.swt/Eclipse SWT Render M3/win32`). They moved here from `org.eclipse.swt.tests` on 2026-11-14;
package `org.eclipse.swt.tests.junit.m3gc` and the 33 sources are unchanged.

## Why a module of their own

The render tree uses the Skija 0.116.3 binding (`io.github.humbleui.skija`, `io.github.humbleui.types`), which is not an
OSGi bundle of the target platform, and no bundle of the reactor compiles the render tree: the win32 fragment's
`build.properties` does not list it, and `bundles/org.eclipse.swt.gpu.m3` compiles three GPU sources only. Inside
`org.eclipse.swt.tests` (Tycho, ECJ) the m3gc types could not resolve, which stopped that bundle in the Eclipse SDK
build (467 errors on 2026-11-14).

## What the module does

A plain Maven module, like `bundles/org.eclipse.swt.gpu.m3`, registered in the SWT root `pom.xml` after
`tests/org.eclipse.swt.tests`:

- compiles the render tree as test code (build-helper `add-test-source`) and then the tests, against the reactor's
  `org.eclipse.swt.win32.win32.x86_64` fragment, Skija 0.116.3, types 0.1.1 and JUnit 5.14.4 / Platform 1.14.4, in
  every build, also with `-DskipTests`;
- runs the `AllM3GcTests` suite (it fixes the order: `drawText_NoCache` sets `M3TextDrawing.NO_TEXT_CACHE` and never
  clears it) on Windows x86_64 with `-DskipNativeTests=false`, as the other native SWT tests; other platforms compile
  the module and skip its tests (the natives are win32 x86_64);
- ships nothing: its jar is empty and no feature names it.

## Run

From the repository root on Windows x86_64 (the tests create a `Display`; use a desktop session):

```sh
mvn --batch-mode clean verify -pl tests/org.eclipse.swt.tests.m3gc -am -DskipNativeTests=false
```

The outside-Maven gate `docs/m3/gc-extension-seam/tools/test_m3gc.sh` reads the sources from this module.
