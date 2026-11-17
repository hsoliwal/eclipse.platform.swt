# maya: the native side

`maya.dll` (win32 x86_64) is the JNI library under maya's Java classes in `../common`. It contains the Skija JNI
functions SWT reaches, renamed for `org.eclipse.swt.internal.maya`, and SWT's own M3Canvas. It is statically linked
with the Skia release that Skija 0.116.3 was made with. The fragment `org.eclipse.swt.maya` ships the built library as
`native/org/eclipse/swt/internal/maya/windows/x64/maya.dll`, with `icudtl.dat` and `maya.version` beside it
(`../README.md`).

## What is here

- `cc/`: 42 Skija JNI sources and their 9 headers from github.com/HumbleUI/Skija at tag `0.116.3`
  (`90e6bc12a3be9f84b88c077e0c23178499e21b7b`), `platform/cc/`, under the Apache License 2.0 (notices in
  `docs/licenses/swt-gpu-skija-*`). They are the sources that define one of the functions of `maya.def`, plus:
  - the shared `interop.cc` and `impl/` files;
  - the shaper sources;
  - the `interop.cc` of each module that `JNI_OnLoad` initialises.

  The only change from the tag is the package: JNI function names `Java_io_github_humbleui_skija_...` became
  `Java_org_eclipse_swt_internal_maya_...`, and class names and signatures `io/github/humbleui/skija/...` and
  `io/github/humbleui/types/...` became `org/eclipse/swt/internal/maya/...` and `org/eclipse/swt/internal/maya/types/...`.
  This was done with the same rule table as the Java side (`../README.md`).
- `cc/swt/M3Canvas.cc`: SWT's own native source (Apache-2.0 OR EPL-2.0). M3Canvas is maya's canvas layer above the
  platform's lowest common denominator (a window and its pixels). It draws a whole
  `org.eclipse.swt.internal.gpu.OpStream` in one JNI call:
  - every command, with the pixel conventions of `OpStreamReplay` (which makes one call per command);
  - paints built from their (PaintKeys mask, ARGB) keys, and gradients and paths built in place;
  - text blobs looked up beforehand by the caller, in stream order.
  `OpStreamReplay.run` uses it. `-Dswt.m3canvas=false` keeps the command-by-command replay.
- `sources.txt`: the 43 sources.
- `maya.def`: the exports, which are `JNI_OnLoad`, `M3Canvas._nReplay` and the 233 Skija JNI functions reached by
  SWT's code and the Maya backend tests (com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/REACHABLE_NATIVES_WITH_TESTS.txt`, traced by
  `tools/skija_reach.py`). Production code alone reaches 209 of them.
- `build_maya.py`: compiles with MSVC (C++17, `/MT`, Skija's own defines) against the prebuilt Skia release
  `m116-d2c211228d` (HumbleUI/SkiaBuild, Windows Release x64). When `--skia` is not given, it downloads the release and
  checks its sha256. Each source folder compiles into its own object folder, emptied first so that no object of an
  earlier build is linked. The link uses the export list,
  `/OPT:REF /OPT:ICF` and `/Brepro`.

```
python build_maya.py --out <dir> [--skia <extracted release>] [--jdk <JDK>] [--vcvars <vcvars64.bat>]
```

To update the fragment, copy `<dir>/maya.dll` to
`bundles/org.eclipse.swt.maya/native/org/eclipse/swt/internal/maya/windows/x64/`. Then set `maya.version` beside it
to `0.116.3-` followed by the first 12 hex digits of the new file's sha256. The test
`Test_org_eclipse_swt_maya_MayaLibrary` checks that the version matches the library.

## What was checked (win32 x86_64, Visual Studio 2022 build tools, JDK 21)

- `BUILD-OK`. `maya.dll` is 7,455,744 bytes; the stock `skija.dll` of `skija-windows-x64-0.116.3.jar` is
  10,471,424 bytes. maya.dll exports exactly 235 functions, against 811 for the stock library. Its strings contain no
  `io/github/humbleui` or `io_github_humbleui`.
- Three builds give the same bytes (sha256 `681bb96db032f32e6f067372072fb522513346ac72f5fb2782f64cf595fc99cb`).
- The Maya backend suite (`AllMayaGcTests`, 108 tests) passes under `-Xcheck:jni` with no JNI warning. The suite was
  run with the classes and this `native/` folder packed in one jar, as the fragment ships them. The loader extracted
  `maya.dll` and `icudtl.dat` into `<java.io.tmpdir>/maya_0.116.3-681bb96db032_x64`. No Skija jar was on the class
  path.

## Not done yet

- Other platforms (Linux, macOS, Android NDK, iOS): the same selection, built against the matching Skia releases.
- M3Canvas on graphics hardware: it is measured on raster surfaces only (`Test_org_eclipse_swt_maya_M3Canvas`; 1.6x
  faster than command-by-command replay at 2,000 and more commands per frame, equal within noise for small frames).
