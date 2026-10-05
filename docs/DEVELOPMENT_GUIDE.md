# Developing this SWT fork

SWT combines Java APIs with native window-system implementations. This guide helps contributors choose the correct source owner and validation lane in `hsoliwal/eclipse.platform.swt`; the [root README](../README.md), [AGENTS.md](../AGENTS.md) and [bundle development guide](../bundles/org.eclipse.swt/Readme.md) remain the detailed references.

## Checkout and toolchain

```sh
git clone https://github.com/hsoliwal/eclipse.platform.swt.git
cd eclipse.platform.swt
git rev-parse HEAD
java -version
mvn -version
```

Use Git LFS if relying on the checked-in precompiled native fragments. Without resolved LFS objects, rebuild the native libraries on the matching host. The fork's main [matrix workflow](../.github/workflows/maven.yml) currently selects JDK 25; its [reusable build](../.github/workflows/build.yml) selects Maven 3.9.16. AGENTS.md still describes Java 21. Record which lane you reproduce and consult the actual POM/workflow at your revision rather than silently changing project compatibility to resolve that discrepancy.

For the ordinary reactor:

```sh
mvn clean verify
```

`mvn clean verify -DskipTests` is a build-only diagnostic, not test acceptance. Linux UI tests need the GTK dependencies and a usable display/session; the reusable workflow shows the X11/Xvfb and Wayland setup. A headless shell alone does not exercise widget behavior.

## Find the source owner

| Area | Start here |
| --- | --- |
| Shared widget API | `bundles/org.eclipse.swt/Eclipse SWT/common/` |
| Window-system implementation | `bundles/org.eclipse.swt/Eclipse SWT/{gtk,win32,cocoa}/` |
| Native declarations and JNI glue | `bundles/org.eclipse.swt/Eclipse SWT PI/{gtk,win32,cocoa}/` |
| Platform/architecture fragment | `binaries/org.eclipse.swt.<ws>.<os>.<arch>/` |
| Tests and runnable examples | `tests/org.eclipse.swt.tests/`, `examples/` |
| Tooling and SVG integration | `bundles/org.eclipse.swt.tools/`, `bundles/org.eclipse.swt.svg/` |

Choose the matching OS, window system and CPU architecture together. A Java-only change can use existing matching natives, but a native-interface change requires regeneration and a native rebuild. One platform's successful build is not evidence for another.

## Native changes: generate, build, exercise

Read the platform instructions linked from the [bundle guide](../bundles/org.eclipse.swt/Readme.md). Change the Java native declarations and their annotations, then regenerate JNI glue:

```sh
mvn --non-recursive -Pjni-generator exec:exec@run-jni-generator
```

Do not hand-edit generated `os.c`, stats or structs files. Review and commit generated source alongside its Java owner, but do not commit built native binaries.

For a Linux GTK x86_64 native-only build, after installing the documented native dependencies:

```sh
cd binaries/org.eclipse.swt.gtk.linux.x86_64
mvn clean antrun:run@build-native-binaries -Dnative=gtk.linux.x86_64
```

Alternatively, from the root, `mvn clean verify -Dnative=gtk.linux.x86_64` includes native compilation in the full reactor. Use the actual host's fragment for Windows or macOS; do not relabel Linux output as cross-platform proof.

`GTK_VERSION` selects which GTK native libraries to build. Runtime selection uses `SWT_GTK4`; the CI also records `GDK_BACKEND`. Keep these distinctions in a reproduction report.

## Focused tests and visual evidence

The repository documents this pattern for a selected test:

```sh
mvn verify -pl :THE_BUNDLE_WITH_THE_ACTUAL_TEST -am \
  -DskipNativeTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=ClassName
```

Replace the bundle and class with the real test owner. Confirm the intended test actually appears in the report; allowing unrelated modules to contain no selected test does not validate an empty run. Keep the full reactor and affected platform matrix as separate gates.

For UI changes, reproduce the behavior with the relevant snippet/test and preserve screenshots when appropriate. Exercise resource disposal, UI-thread access, sizing/layout and relevant GTK/Win32/Cocoa differences. The reusable workflow's screenshot and performance steps are opt-in; their presence in YAML does not prove they ran.

## Fork-specific work and upstream contributions

Existing [distillation pipeline](m3-swt-distillation-pipeline.md) and viewport documents describe additional fork lanes. Follow their recipe and evidence boundaries before promoting candidates. A catalogue or generated packet is not a replacement for native execution, API compatibility or visible UI behavior.

Open a draft PR against `hsoliwal/eclipse.platform.swt:master` for fork-local work. Contributions intended for Eclipse follow [CONTRIBUTING.md](../CONTRIBUTING.md) and its Eclipse contributor requirements. Preserve existing license notices and upstream README content.

## Review checklist

- Record base/head SHA, JDK/Maven versions and exact fragment/backend.
- List Java, generated JNI, native source and test owners separately.
- Attach command exit codes and `target/surefire-reports` results for actual tests.
- Check the exact head's workflow outcomes; Markdown-only PRs may skip the platform builds by design.
- Identify failed, skipped and unrun platforms explicitly.
- Verify documentation links and ensure the diff contains no unintended binaries or build changes.

A documentation update improves the development map; it makes no claim that all native matrices, screenshots or fork candidates are accepted.
