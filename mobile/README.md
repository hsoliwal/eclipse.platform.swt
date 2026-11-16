# SWT Android and iOS preview

This module builds an initial mobile implementation of SWT's own `Display`,
`Widget`, `Control`, `Scrollable`, `Composite`, `Canvas`, `Decorations`, and `Shell`
owners. It reuses the selected original common SWT constants, events, listeners,
layouts, geometry, image data, and codecs. The public source list is
[`source-manifest.json`](source-manifest.json); unchanged upstream files have
SHA-256 checks and are copied byte for byte into the Maven source directory.

Each Control owns its native peer and offscreen Image. The SWT application runs
its normal `readAndDispatch`/`sleep` loop on its own thread. Android/UIKit main
thread callbacks enqueue SWT events; application listeners run on the Display
thread. Images and GCs retain their native resources through reentrant painting
and disposal. Owned Shells retain their logical parent but use native-rooted
display coordinates.

The new module is independent of the existing desktop Maven/Tycho build. It does
not make Android or iOS an already qualified target of the desktop reactor.

## Current source owners

| Area | Current owner |
| --- | --- |
| Shared widget, graphics and event-loop implementation | [`Eclipse SWT/mobile`](../bundles/org.eclipse.swt/Eclipse%20SWT/mobile/org/eclipse/swt/) |
| Android Activity, View/Canvas and native Text editor | [`AndroidPlatform` and `SwtActivity`](../bundles/org.eclipse.swt/Eclipse%20SWT/android/org/eclipse/swt/internal/android/) |
| iOS adapter | [`IOSPlatform`](../bundles/org.eclipse.swt/Eclipse%20SWT/ios/org/eclipse/swt/internal/ios/IOSPlatform.java) |
| UIKit/CoreGraphics and JNI declarations | [`Eclipse SWT PI/ios`](../bundles/org.eclipse.swt/Eclipse%20SWT%20PI/ios/) |
| Portable owner tests | [`org.eclipse.swt.tests.mobile`](../tests/org.eclipse.swt.tests.mobile/src/org/eclipse/swt/tests/mobile/) |
| Original Nebula widget consumer | [RoundedCheckbox mobile application](https://github.com/hsoliwal/nebula/tree/master/widgets/roundedcheckbox/org.eclipse.nebula.widgets.roundedcheckbox.mobile) |

The commands below build these accepted original SWT owners. The older host-only
experiments retained elsewhere in this directory are described separately at the
end of this page.

## Build prerequisites

Use JDK 21, Maven 3.9 or later, and Python 3.10 or later. Python uses only its
standard library. Maven is the Java compiler, JAR, and JUnit/Surefire entrypoint;
`prepare_sources.py` selects and copies source files without transforming them.
No build command installs SDKs, signs a distribution, or installs an application
on a device.

The Android source lane requires an installed Android SDK platform 35. The
example APK lane also requires Android build-tools 35.0.0 (the checked-in
Android Gradle Plugin 8.13 default). **The initial APK minimum is Android 15 / API 35.** This source selection retains common SWT uses
of runtime APIs unavailable on older Android releases, and no core-library
desugaring is supplied. Java 21 source compilation and D8 language lowering do
not establish general Java 21 standard-library availability on Android.

On Windows, select the installed Python executable with
`-Dpython.executable=python` if `python3` is not available. Quote checkout and SDK
paths containing spaces. The commands below use absolute paths as examples;
none of the build files contain machine-specific checkout paths.

## Portable owner contracts: actual JUnit 5 execution

Check out Nebula's original sources separately. The source manifest records the
exact baseline commit and hashes of the three original Nebula classes needed by
the tests; a checkout of that baseline does not need any mobile application files.

```sh
mvn -f /path/to/swt/mobile/pom.xml -Pcontracts \
  -Dnebula.checkout=/path/to/nebula clean verify
```

The `contracts` profile executes `MobileCoreContractJUnitTest` and
`MobileLifecycleContractJUnitTest` using JUnit 5 and Maven Surefire.
It selects the original RoundedCheckbox's Selection, cancellation,
painting and disposal checks; SWT event/thread/timer and graphics contracts;
termination and blocked synchronous callers; owned-Shell geometry; and the
Button/Label, ProgressBar/Scale, sparse virtual List, native Text and Combo
owner contracts. Lifecycle cases cover disposal from Show, layout and focus
callbacks; listener failures; focus changes during callbacks and queued native
acknowledgements; hidden and disabled ancestors; and rejection of new native
owners during disposal. They also retain the original null `disposeExec` and
disposed-parent argument contracts. The checked-in test sources define the
current cases and assertions.

Reports are written to `mobile/target/surefire-reports`. A missing, skipped, or
failed report is not an executed test pass. The Java2D transport exists only in
test sources, deliberately refuses OS screen capture, and is excluded from
production JARs and mobile APKs. These tests establish portable owner behavior;
they do not qualify Android or iOS rendering, native lifetime, performance, or ABI.

## Select one production platform

```sh
# Shared Java owners; no Nebula checkout is required.
mvn -f /path/to/swt/mobile/pom.xml clean verify

# Shared owners plus the real Android Activity/View/Canvas binding.
mvn -f /path/to/swt/mobile/pom.xml \
  -Dmobile.platform=android -Dandroid.sdk=/path/to/android-sdk clean verify

# Shared owners plus IOSPlatform and the declared JNI OS class.
mvn -f /path/to/swt/mobile/pom.xml -Dmobile.platform=ios clean verify
```

Without `-Pcontracts`, these are source compilation/package commands: portable
tests are intentionally not selected. The outputs are
`swt-mobile-shared.jar`, `swt-mobile-android.jar`, or `swt-mobile-ios.jar`, plus a
corresponding `-sources.jar` containing the selected original source bodies.
The iOS JAR includes `Display`, `ACC`, `IOSPlatform`, and `OS`; it is the Java
classpath input for the native CMake/JNI-header lane. It is not an AOT executable,
embedded JVM, signed application, or Apple device result. See the iOS build
instructions in `mobile/ios` for the separate native and runtime requirements.

`-Dmobile.output=/path/to/build-directory` changes the output directory. Source
selection clears its compiled-class directories so changing platform cannot
retain obsolete native classes. `selected-sources.json` records the actual
relative source paths and hashes used by that invocation.

## Build the current Nebula Android application

Use the Gradle application beside the original RoundedCheckbox widget in the
Nebula checkout. The older `m3/mobile` delivery paths and a Nebula `mobile/pom.xml`
are not the current application build. The consumer now uses the original SWT
List, Text and Combo owners, so the initial SWT #369 backend alone is insufficient.
Follow the exact SWT revision pinned by Nebula's
[mobile acceptance workflow](https://github.com/hsoliwal/nebula/blob/master/.github/workflows/m3-nebula-mobile-original-list.yml)
and the [consumer build guide](https://github.com/hsoliwal/nebula/blob/master/widgets/roundedcheckbox/org.eclipse.nebula.widgets.roundedcheckbox.mobile/android/README.md).

After building the Android SWT JAR above, run from the Nebula checkout with
Gradle 8.13+ and the Android SDK/build-tools installed:

```sh
gradle -p widgets/roundedcheckbox/org.eclipse.nebula.widgets.roundedcheckbox.mobile/android \
  -PswtMobileJar=/absolute/path/to/swt/mobile/target/swt-mobile-android.jar \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

Run `:app:connectedDebugAndroidTest` with the same project and JAR arguments on
an API-35-or-newer emulator or device for the native instrumentation lane. APK
assembly and a declared workflow are separate from an executed native test.

## Compile the iOS native bridge

First build `swt-mobile-ios.jar` with `-Dmobile.platform=ios`. On macOS with a
real Xcode iOS SDK, CMake 3.24+ and `JAVA_HOME` pointing to JDK 21, run from the
SWT checkout:

```sh
bash mobile/ios/build.sh iphonesimulator arm64 \
  /absolute/path/to/swt/mobile/target/swt-mobile-ios.jar
```

Use `x86_64` for an Intel simulator or `iphoneos arm64` for the device library.
This compiles the native library and bootstrap; it does not supply an iOS JVM,
link a deployable Java application, sign it, or execute simulator/device tests.
The [iOS runtime inputs](ios/README.md#link-an-application-only-with-a-verified-ios-runtime)
remain necessary for the application target.

## Button and Label original owner slice

The mobile source manifest now selects the original SWT `Button extends Control`
and `Label extends Control` API families, using the unchanged SWT shared
`WidgetStylePolicy` and `StyleBits` algorithms. These controls draw through the
existing platform-owned Image/GC and real Android/UIKit native peer rather than
forking a second widget model. Button has selection/keyboard/pointer handling,
radio sibling grouping and the existing platform accessibility role transport;
Label has text, image, alignment, separator, wrap painting and no-focus semantics.
Both preserve caller-owned Image lifetimes and the normal SWT disposal path.

**Boundaries:** Button and Label paint through the mobile Canvas/Image/GC path.
The separate original Text owner supplies the native editor integration described
below. Full mnemonic focus traversal, complex text layout, RTL, multi-touch,
accessibility parity and original desktop raster parity remain open. The
`-Pcontracts` lane includes a Button/Label owner test; source presence does not
establish an executed CI pass or native runtime parity.

## Native safe-area API (Android and iOS)

The additive `Display.getSafeAreaInsets()` API reports the current native root
safe-area as four SWT logical coordinates: `[left, top, right, bottom]`.
It is confined to the owning SWT Display thread and returns an independent
array on each call. It performs no widget allocation or automatic relayout.
Existing `getBounds()`, `getClientArea()`, Shell and viewport behaviors remain
unchanged.

On Android API 35, the binding reads the root window's
`WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()`, excluding
IME/soft-keyboard insets. The Android host already applies window padding to
its native root, so clients must not subtract the safe area again from the
existing Android `Display.getClientArea()`. The host may apply separate IME
padding while the keyboard is shown.

On iOS, the binding reads `UIView.safeAreaInsets` on UIKit's main thread via
the original JNI owner and returns logical points. It uses the original SWT
Display-thread entrance and never calls application listeners on UIKit main.
The returned insets can be used by application layouts that choose to remain
outside notches, bars and rounded corners; they do not move existing Shells.
Before the root is installed or visible, an OS may report zero insets.

The shared Java owner contract checks snapshots, rotation-like updates, caller
mutation, thread ownership, disposal and invalid host responses. It is not a
substitute for real Android edge-to-edge/IME instrumentation or iOS
rotation/VoiceOver/simulator tests. The JNI header is regenerated from the
current `OS.java` declaration by the iOS build, not edited manually.

## Build the Android native library (NDK)

`AndroidPixels` moves retained pixels (a scroll of a Canvas) through one native call when
`libswtpixels.so` is in the application, and through its Java lane when the library is not there.
Build the library for every ABI with an installed Android NDK, CMake 3.22 or later and Ninja:

```bash
python mobile/android/build_native.py --ndk /path/to/android-ndk --output /path/to/out
```

The script configures
[`CMakeLists.txt`](../bundles/org.eclipse.swt/Eclipse%20SWT%20PI/android/library/CMakeLists.txt)
with the NDK's toolchain file for `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86` (API 26, the
`minSdk` of `mobile/android`; `--abi` and `--api` select others), builds with warnings as errors,
and reads each library back with the NDK's `llvm-readelf`: every JNI function of the C sources
is exported, the library needs `libjnigraphics.so`, and the 64-bit libraries have load segments
aligned to 16 KB pages. It writes `out/jniLibs/<abi>/libswtpixels.so` and
`out/native-build.tsv` (one row per ABI with size and SHA-256) and exits with 1 if a build or a
check fails. It installs nothing and writes nothing outside the output folder.

`out/jniLibs` has the layout of an application's `src/main/jniLibs`: copy it there and the Android
Gradle Plugin packages the libraries. An application can instead point
`android.externalNativeBuild.cmake.path` at the CMake file. Neither Gradle route was run here.

This was run on Windows with NDK r30 (30.0.16248370): four ABIs built and passed the checks. The
library was not loaded on a device or an emulator.

## Implemented integration path and remaining scope

The implemented path is a Shell/Composite/Canvas application, including the
original Nebula RoundedCheckbox, standard SWT layouts and typed events,
Image/GC raster drawing, text measurement/drawing, affine transforms, and
resource/disposal ownership. On Android, `GC(Display).copyArea(...)` delegates to
actual platform capture. iOS screen capture is currently unsupported and fails
explicitly; use actual XCUI/device screenshots for that separate lane.
`Control.print` is offscreen composition and is not visible-screen evidence.

This is **not the complete SWT or Nebula widget catalogue**. Button and Label
have bounded Canvas-rendered source implementations; ProgressBar and Scale now
offer native-backed GC painting with original SWT range/selection methods.
The original List API uses the existing common SWT `ItemSparseTable` for
materialized text and reentrant SetData tracking, and `ViewportRuntime.Selection`
for range/complement selection. High logical indices therefore do not require
storage up to the highest row. The portable contract suite includes a separate
64 MiB process for billion-row/high-index operations, plus structural/selection
oracle and reentrant SetData checks. These are Java owner checks, not native
memory or performance measurements.

List retains software viewport scrolling, on-demand SWT.SetData and native-backed
Canvas paint/input on Android and iOS. It does **not** provide
native ScrollBar objects, full list-item accessibility semantics, platform
gesture physics, RTL layout or drag-and-drop.
The original Text widget now has Java-owned UTF-16 text, Verify/Modify event
ordering, selection, text limits, password/single/multiline style entrypoints,
Android EditText and UIKit UITextField/UITextView native editors, and native
clipboard-backed copy/cut/paste. The native UI thread reports immutable text
snapshots to SWT's original Display event loop; user callbacks run only there.
It does NOT yet provide qualified marked-text/IME composition, platform-specific
spellcheck/autocorrect parity, bidi text segments, full SWT Accessible semantics
or complete clipboard/DND integration.
The original Combo now composes native-backed SWT Text, List, Button and
an owned popup Shell, preserving the standard editable/READ_ONLY and SIMPLE/
DROP_DOWN API surface, input/selection/Verify event ownership and system
clipboard behavior. Its [original keyboard handler (#390)](https://github.com/hsoliwal/eclipse.platform.swt/pull/390)
moves selection on Up/Down, closes the dropdown on Escape, and ignores navigation
when disabled or hidden. A separate Java 21 array atom has deterministic randomized
insertion/deletion tests; complex native popup routing, bidi SegmentListener,
complete child accessibility, and full keyboard/IME parity are not qualified.

The original Table, TableItem and TableColumn APIs now have a native-backed
Android/UIKit Canvas peer, Java-owned sparse row arrays, on-demand SWT.SetData,
header/column text and images, selection interval masks, row styling,
keyboard/touch selection and accessibility paging. A billion-row virtual Table
requires storage proportional to the materialized viewport and selected
interval count, not the full logical row count. The source is a bounded
implementation: native ScrollBar objects, full per-cell accessibility,
complete owner-draw/MeasureItem and table-specific DND/editor parity remain
unqualified.
Tree, menus, ToolBar, TabFolder, StyledText, Browser, printing, drag-and-drop,
OpenGL and the remaining SWT/Nebula widget catalogue are not implemented by
this source closure. Other Nebula widgets are not
qualified merely because they derive from Canvas.

Native scrollbars, RTL control layout, font enumeration/registration, nonempty
tooltips, and unsupported accessibility semantic actions fail explicitly where
those methods are exposed. Android TalkBack and iOS VoiceOver now route Scale
increment/decrement through its original SWT Selection/keyboard owner, while
the general SWT Accessible API and other adjustable widget roles remain open. Current native pointer input is translated into
standard SWT mouse events; `Display.getTouchEnabled()` is false because the
separate SWT Touch/TouchSource API is not implemented. Native accessibility
activation follows the original widget's cancellable mouse activation path;
this is not a claim of complete accessibility support.

Actual Android instrumentation, Apple compilation/device execution, visible
screen pixel assertions, and native resource/CPU/memory gates remain separate
acceptance lanes. Compilation, a source JAR, the headless fixture, or a queued CI
job cannot substitute for them.

## Source and licence custody

Existing source and licence headers are preserved. `DeviceData.java` is selected
from the pure Java GTK source, and `NativeImageLoader.java` from the pure Java
Win32 codec dispatcher; neither imports that desktop platform's native backend.
The existing SWT licence is included in binary artifacts. Source selection is
public product build wiring, not a transformation recipe or private execution
harness. Canonical source-generation recipes and their promotion receipts remain
in their established Synexia owner.

## Historical host-only experiments

The earlier [`MobileWidgetRuntime`](../bundles/org.eclipse.swt/Eclipse%20SWT/common/org/eclipse/swt/internal/mobile/MobileWidgetRuntime.java),
[`mobile/android` host smoke application](android/),
[`IosMobileHost`](ios/src/main/java/org/eclipse/swt/internal/mobile/ios/IosMobileHost.java)
and [`SWTMobileUIKit.m`](ios/native/SWTMobileUIKit.m) remain in source history and
this checkout. Their host SPI, viewport containers and smoke commands predate
the original SWT platform source set above. They are not the Maven-built SWT
artifact or the current Nebula application entrypoint.

`bash mobile/test-core.sh` exercises the older host ownership/event/lifecycle
core. Its result must remain separate from the original-widget `-Pcontracts`
lane, Android instrumentation and Apple runtime gates. Historical reference
notes included Eclipse SWT under EPL-2.0, Initiative 31 Skia concepts, Android
and UIKit APIs, and MobiVM/RoboVM as an unselected toolchain option. They do not
establish a bundled iOS runtime or full widget integration.
