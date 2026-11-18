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
  This was done with the same rule table as the Java side (`../README.md`). One file has a second change:
  `interop.cc` passes `AttachCurrentThread` the type of environment pointer each `jni.h` declares (`JNIEnv**` on
  Android, `void**` in the JDK). The Windows library is the same bytes either way.
- `cc/swt/M3Canvas.cc`: SWT's own native source (Apache-2.0 OR EPL-2.0). M3Canvas is maya's canvas layer above the
  platform's lowest common denominator (a window and its pixels). It draws a whole
  `org.eclipse.swt.internal.gpu.OpStream` in one JNI call:
  - every command, with the pixel conventions of `OpStreamReplay` (which makes one call per command);
  - paints built from their (PaintKeys mask, ARGB) keys, and gradients and paths built in place;
  - text blobs looked up beforehand by the caller, in stream order.
  `OpStreamReplay.run` uses it. `-Dswt.m3canvas=false` keeps the command-by-command replay.
- `cc/swt/MayaComposition.cc`: SWT's own native source (Apache-2.0 OR EPL-2.0). Layers composited by the window system
  above a window's own pixels (its backing store): on Windows DirectComposition (a D3D11 device, a composition device,
  a topmost target for the window, one visual per layer with a premultiplied BGRA surface and an effect group for
  opacity). Z order, offset, opacity and visibility are properties the system composites, so changing them repaints
  nothing; `MayaComposition._nApply` carries every layer's properties in one call and commits once per frame. Other
  platforms compile the same exports answering "not supported" (0); Android (ASurfaceControl) and Cocoa/UIKit
  (CALayer) take their place there. Linked with `d3d11.lib` and `dcomp.lib`.
- `sources.txt`: the 44 sources.
- `maya.def`: the exports, which are `JNI_OnLoad`, `M3Canvas._nReplay`, the seven `MayaComposition` natives and the 233 Skija JNI functions reached by
  SWT's code and the Maya backend tests (com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/REACHABLE_NATIVES_WITH_TESTS.txt`, traced by
  `tools/skija_reach.py`). Production code alone reaches 209 of them.
- `build_maya.py`: compiles with MSVC (C++17, `/MT`, Skija's own defines) against the prebuilt Skia release
  `m116-d2c211228d` (HumbleUI/SkiaBuild, Windows Release x64). When `--skia` is not given, it downloads the release and
  checks its sha256. Each source folder compiles into its own object folder, emptied first so that no object of an
  earlier build is linked. The link uses the export list,
  `/OPT:REF /OPT:ICF` and `/Brepro`.
  `--target android-x64` and `--target android-arm64` build `libmaya.so` instead:
  - the compiler is the NDK's clang (API 26);
  - Skia is JetBrains skia-pack `m116-47d3027-1` (the same milestone), whose ICU entry points carry the suffix
    `_skiko`;
  - Skia was built without RTTI, so maya is compiled with `-fno-rtti`;
  - ART implements JNI 1.6, so `JNI_VERSION_1_8` maps to it;
  - the link uses a version script made from `maya.def` and `--no-undefined`.

```
python build_maya.py --out <dir> [--skia <extracted release>] [--jdk <JDK>] [--vcvars <vcvars64.bat>]
python build_maya.py --target android-x64|android-arm64 --ndk <android-ndk-r30> --skia <extracted skia-pack> --out <dir>
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

## What was checked on Android (NDK r30, API 35 x86_64 emulator)

- `BUILD-OK` for both ABIs. Each `libmaya.so` exports exactly the 235 functions:
  - x86_64: 16,960,616 bytes; two builds give the same bytes (sha256 `e7adb86e6ae6...`);
  - arm64: 16,180,768 bytes (sha256 `2bfe08d5b492...`).
  They are larger than the Windows library because the ICU data is linked in.
- On the emulator, ART loads `libmaya.so`. `JNI_OnLoad` finds all its classes. M3Canvas draws through it.
- A probe compared Android with Windows: per command kind, 40 seeded OpStreams drawn on a raster surface, pixels
  hashed (com.synexia `docs/M3-SCALE/runs/20261117-maya-android/`). It runs under `dalvikvm64` with the maya classes
  dexed. 9 of 10 kinds give the same pixels on both:
  - FILL_RECT, DRAW_RECT, DRAW_LINE, DRAW_POINT;
  - FILL_OVAL, DRAW_OVAL;
  - FILL_POLY, DRAW_POLY;
  - clips with transforms.
  FILL_GRADIENT differs in 1 pixel of 768,000, by 1 in one channel.

## Not done yet

- Linux, macOS and iOS: the same selection, built against the matching Skia releases.
- Android:
  - packaging `libmaya.so` into the mobile port's application (`jniLibs`);
  - running the arm64 library on a device (only the x86_64 library has run, on the emulator).
- M3Canvas on graphics hardware: it is measured on raster surfaces only (`Test_org_eclipse_swt_maya_M3Canvas`; 1.6x
  faster than command-by-command replay at 2,000 and more commands per frame, equal within noise for small frames).
