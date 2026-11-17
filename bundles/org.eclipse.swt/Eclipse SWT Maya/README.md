# maya

maya is the graphics library under SWT's Skia backend (`Eclipse SWT Skia`, `Eclipse SWT GPU`, fragment
`org.eclipse.swt.skia`). It is made of two parts:

- `common/`: the Java side, packages `org.eclipse.swt.internal.maya` and `org.eclipse.swt.internal.maya.types`;
- `native/`: the JNI side, `maya.dll`, built by `native/build_maya.py` (see `native/README.md`).

Both started as HumbleUI Skija 0.116.3. They have been cut down to what SWT uses and renamed, so maya is SWT's own
library and no Skija jar is used.

## Origin and licence

- HumbleUI Skija 0.116.3, Apache License 2.0 (`docs/licenses/swt-gpu-skija-Apache-2.0.txt`, packaging notice
  `docs/licenses/swt-gpu-skija-packaging-NOTICE.txt`, Skia's licence `docs/licenses/swt-gpu-skija-Skia-LICENSE.txt`).
- Java sources: `io.github.humbleui:skija-shared:0.116.3:sources` (SHA-1 `aade0466dbfc76736ea8135a1fcd14f4722ca023`)
  and `io.github.humbleui:types:0.1.1:sources` (SHA-1 `8ff42ce2e4d9860c84fe4f1bc58030733ccf3fbd`), as published on
  Maven Central. The Java 9 versions of `impl/Cleanable`, `impl/ReferenceUtil` and `impl/ResourceFinder` come from
  `shared/java9/impl/` of github.com/HumbleUI/Skija at tag `0.116.3` (`90e6bc12a3be9f84b88c077e0c23178499e21b7b`).
  The class jar carries them under `META-INF/versions/9`, but the sources jar leaves them out.
- Native sources: `platform/cc/` of the same tag (`native/README.md`).
- The ICU data file `icudtl.dat` that the fragment ships beside `maya.dll` is the one in
  `skija-windows-x64-0.116.3.jar` and in the Skia release `m116-d2c211228d` (both have sha256
  `846942a316b4e38fdbd4de3ad83e4faae78a8bced50f4720acdbdab6ee7c4b8e`). It is covered by the Unicode License v3.

## Names

| Skija | maya |
|---|---|
| package `io.github.humbleui.skija` and its subpackages | `org.eclipse.swt.internal.maya` and its subpackages |
| package `io.github.humbleui.types` | `org.eclipse.swt.internal.maya.types` |
| JNI functions `Java_io_github_humbleui_skija_...` | `Java_org_eclipse_swt_internal_maya_...` |
| library `skija` (`skija.dll`) | `maya` (`maya.dll`) |
| resources `io/github/humbleui/skija/<os>/<arch>/` | `org/eclipse/swt/internal/maya/<os>/<arch>/` |
| `skija.version` | `maya.version` |
| properties `skija.library.path`, `skija.loadFromLibraryPath`, `skija.staticLoad`, `skija.logLevel` | `maya.library.path`, `maya.loadFromLibraryPath`, `maya.staticLoad`, `maya.logLevel` |
| extraction folder `<java.io.tmpdir>/skija_<version>_<arch>` | `<java.io.tmpdir>/maya_<version>_<arch>` |

The renaming tool is `maya_consolidate.py`, a fixed rule table that changes nothing when it is run a second time
(com.synexia `docs/M3-SCALE/runs/20261117-maya-consolidate/`).

## Loading

The fragment carries `org/eclipse/swt/internal/maya/windows/x64/` with three files:

- `maya.dll`;
- `icudtl.dat`: Skia's text shaping loads it from the folder `maya.dll` is in;
- `maya.version`: `0.116.3-` followed by the first 12 hex digits of the sha256 of `maya.dll`.

When the fragment is a jar, the loader extracts `maya.dll` and `icudtl.dat` into `<java.io.tmpdir>/maya_<version>_<arch>`.
It reuses a file already there if the length matches, and because the version is derived from the library's bytes, a
changed `maya.dll` always gets a new folder. When the fragment is a folder on the class path, the library loads where it
is. `maya.library.path` names a folder holding `maya.dll`; `maya.loadFromLibraryPath=true` loads it with
`System.loadLibrary("maya")`.

## What is here

The 138 classes SWT needs and nothing more:

- the 57 classes the Skia GC's compiled code reaches, together with the Skija classes those depend on (measured with
  `skija_closure.py`, com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/`);
- the classes their sources name, so that they compile;
- the 41 classes the native library looks up by name when it loads.

## Changes

Each of the 138 files starts with a line `// Changed for SWT (Apache-2.0 section 4b): ...` that lists its changes:

- all 138: package moved to `org.eclipse.swt.internal.maya`;
- 64: the methods SWT does not reach were removed (942 methods). A method was kept if any of these held:
  - SWT's code or the Skia backend tests reach it (com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/tools/skija_reach.py`,
    which also follows class initialisation, library callbacks and JDK callbacks);
  - it is a constructor, abstract or an override;
  - a kept method calls it (`skija_prune_fixpoint.py` with `SkijaPrune.java`).
- 33: unused `import lombok...;` lines removed (the published sources are already delomboked);
- `impl/Library`:
  - `lombok.Lombok.sneakyThrow` replaced by a private helper that rethrows the same exception;
  - the library name, resource folder, version file, properties and extraction folder use the maya names;
- `impl/Log`: `maya.logLevel`;
- `impl/ResourceFinder`: the Java 9 version, with its `@SneakyThrows` written out the same way;
- `impl/Cleanable`, `impl/ReferenceUtil`: the Java 9 versions.
