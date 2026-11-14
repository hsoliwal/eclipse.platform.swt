# SWT raster backend tests

JUnit tests of the raster render backend (`org.eclipse.swt.internal.canvasext.raster` and
`org.eclipse.swt.internal.render`, source folder `bundles/org.eclipse.swt/Eclipse SWT Render/win32`), in package
`org.eclipse.swt.tests.junit.raster`. They moved here from `org.eclipse.swt.tests` on 2026-11-14.

## Why a module of their own

The render tree uses the Skija 0.116.3 binding (`io.github.humbleui.skija`, `io.github.humbleui.types`), which is not an
OSGi bundle of the target platform, and no bundle of the reactor compiles the render tree: the win32 fragment's
`build.properties` does not list it, and `bundles/org.eclipse.swt.gpu` compiles three GPU sources only. Inside
`org.eclipse.swt.tests` (Tycho, ECJ) these types could not resolve, which stopped that bundle in the Eclipse SDK
build (467 errors on 2026-11-14).

## What the module does

A plain Maven module, like `bundles/org.eclipse.swt.gpu`, registered in the SWT root `pom.xml` after
`tests/org.eclipse.swt.tests`:

- compiles the render tree as test code (build-helper `add-test-source`) and then the tests, against the reactor's
  `org.eclipse.swt.win32.win32.x86_64` fragment, Skija 0.116.3, types 0.1.1 and JUnit 5.14.4 / Platform 1.14.4, in
  every build, also with `-DskipTests`;
- runs the `AllRasterTests` suite (it fixes the order: `drawText_NoCache` turns the text cache off and never turns it
  back on) on Windows x86_64 with `-DskipNativeTests=false`, as the other native SWT tests; other platforms compile
  the module and skip its tests (the natives are win32 x86_64);
- ships nothing: its jar is empty and no feature names it.

## Run

From the repository root on Windows x86_64 (the tests create a `Display`; use a desktop session):

```sh
mvn --batch-mode clean verify -pl tests/org.eclipse.swt.tests.raster -am -DskipNativeTests=false
```
