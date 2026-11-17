# maya: the native side

`maya.dll` (win32 x86_64) is the JNI library under maya's Java classes in `../common`. It contains only the Skija JNI
functions SWT reaches, renamed for `org.eclipse.swt.internal.maya`, and is statically linked with the Skia release
that Skija 0.116.3 was made with. The fragment `org.eclipse.swt.maya` ships the built library as
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
- `sources.txt`: the 42 sources.
- `maya.def`: the exports, which are `JNI_OnLoad` and the 233 JNI functions reached by SWT's code and the Maya backend
  tests (com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/REACHABLE_NATIVES_WITH_TESTS.txt`, traced by
  `tools/skija_reach.py`). Production code alone reaches 209 of them.
- `build_maya.py`: compiles with MSVC (C++17, `/MT`, Skija's own defines) against the prebuilt Skia release
  `m116-d2c211228d` (HumbleUI/SkiaBuild, Windows Release x64). When `--skia` is not given, it downloads the release and
  checks its sha256. Each source folder compiles into its own object folder. The link uses the export list,
  `/OPT:REF /OPT:ICF` and `/Brepro`.

```
python build_maya.py --out <dir> [--skia <extracted release>] [--jdk <JDK>] [--vcvars <vcvars64.bat>]
```

To update the fragment, copy `<dir>/maya.dll` to
`bundles/org.eclipse.swt.maya/native/org/eclipse/swt/internal/maya/windows/x64/`. Then set `maya.version` beside it
to `0.116.3-` followed by the first 12 hex digits of the new file's sha256. The test
`Test_org_eclipse_swt_maya_MayaLibrary` checks that the version matches the library.

## What was checked (win32 x86_64, Visual Studio 2022 build tools, JDK 21)

- `BUILD-OK`. `maya.dll` is 7,449,600 bytes; the stock `skija.dll` of `skija-windows-x64-0.116.3.jar` is
  10,471,424 bytes. maya.dll exports exactly 234 functions, against 811 for the stock library. Its strings contain no
  `io/github/humbleui` or `io_github_humbleui`.
- Two builds give the same bytes (sha256 `17136a5a7a814aceed219c4c6e0fe344270ac784ede1154b2d4ac1feb60b6d2c`).
- The Maya backend suite (`AllMayaGcTests`, 105 tests) passes under `-Xcheck:jni` with no JNI warning. The suite was
  run with the classes and this `native/` folder packed in one jar, as the fragment ships them. The loader extracted
  `maya.dll` and `icudtl.dat` into `<java.io.tmpdir>/maya_0.116.3-17136a5a7a81_x64`. No Skija jar was on the class
  path.

## Not done yet

- Other platforms (Linux, macOS, Android NDK, iOS): the same selection, built against the matching Skia releases.
