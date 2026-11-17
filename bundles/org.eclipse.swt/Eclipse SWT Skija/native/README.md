# maya: the native side of SWT's Skia backend

`maya.dll` (win32 x86_64) is the JNI library under the absorbed Skija classes of `../common`: the Skija JNI functions
SWT reaches and nothing more, statically linked with the Skia release Skija 0.116.3 was made with.

## What is here

- `cc/`: 42 Skija JNI sources and their 9 headers, byte for byte from github.com/HumbleUI/Skija at tag `0.116.3`
  (`90e6bc12a3be9f84b88c077e0c23178499e21b7b`), `platform/cc/`. Apache License 2.0 (notices in
  `docs/licenses/swt-gpu-skija-*`). The sources are those that define one of the functions of `maya.def`, the shared
  `interop.cc` and `impl/` files, the shaper sources and the `interop.cc` of each module `JNI_OnLoad` initialises.
- `sources.txt`: the 42 sources. `maya.def`: the exports, `JNI_OnLoad` and the 233 JNI functions SWT's code and the
  Skia backend tests reach (com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/REACHABLE_NATIVES_WITH_TESTS.txt`,
  traced by `tools/skija_reach.py`; 209 of them are reached by production code).
- `build_maya.py`: compiles with MSVC (C++17, `/MT`, Skija's own defines) against the prebuilt Skia release
  `m116-d2c211228d` (HumbleUI/SkiaBuild, Windows Release x64, downloaded and sha256-checked when `--skia` is not given),
  each source folder into its own object folder, and links with the export list, `/OPT:REF /OPT:ICF` and `/Brepro`.

```
python build_maya.py --out <dir> [--skia <extracted release>] [--jdk <JDK>] [--vcvars <vcvars64.bat>]
```

## What was checked (win32 x86_64, Visual Studio 2022 build tools, JDK 21)

- `BUILD-OK`; `maya.dll` is 7,448,064 bytes against 10,471,424 for the stock `skija.dll` of
  `skija-windows-x64-0.116.3.jar`, and exports exactly 234 functions (the stock library exports 811).
- Two builds give the same bytes (sha256 `f7bda7f326343a8844cb2794a8d33b14839322dbdb6c57782262dcd64fcbb909`).
- The Skia backend suite (`AllSkiaGcTests`, 101 tests) passes on the absorbed classes with this library alone (no Skija
  jar on the class path; the library loaded through `-Dskija.library.path` as `skija.dll`), under `-Xcheck:jni` with no
  JNI warning.

## Not done yet

- The fragment `org.eclipse.swt.skia` still embeds `skija-windows-x64-0.116.3.jar`, and the loader still asks for
  `skija`. Shipping `maya.dll` and loading it by its own name is one step, with this build wired into Maven.
- Other platforms (Linux, macOS, Android NDK, iOS): the same selection against the matching Skia releases.
