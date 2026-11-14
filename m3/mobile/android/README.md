# SWT Android host

This source set supplies an Android `Activity` and native Android `View`/`Canvas`
backend for SWT's mobile `Display`, `Shell`, `Canvas`, event and graphics owners.
The demo creates ordinary SWT controls. Android's UI thread paints immutable
frames; one dedicated SWT thread owns widgets, timers and listener dispatch.
Activity pause/resume/close and primary-pointer/hardware-key events enter the
queued SWT event sink. Insets, logical coordinates and native backing density
are applied consistently to drawing and input.
Mouse down/up and modifier key down/up report the state before the transition,
matching the existing Win32/Cocoa `Widget.setInputState` contract: down excludes
the newly pressed button/modifier and up includes the just-released one.

The initial build targets **Android API 35** with a **Java 21 compiler and class
files**. Android executes the D8-produced DEX on ART. This does not deliver a
standalone M3JDK21 VM on Android. The unchanged common SWT `Resource` owner uses
`Cleaner` and the five-argument `Thread` constructor; these are Android APIs from
level 33, so the earlier API-26 assumption is not a supported target. This build
uses API 35 as its explicit minimum and compilation target.

This slice does not provide the complete SWT widget/graphics surface, IME/text
editing, multi-touch, per-widget accessibility, browser embedding, clipboard,
printing, all fonts/shaping behavior, or desktop/mobile equivalence qualification.
Adding a common event type to the source closure does not implement that native
capability. The existing desktop source sets and product build remain separate.

## Build with the official SDK tools

Install JDK 21, Python 3.10+, Android platform `android-35`, and Android build-tools
`35.0.0`. The standard platform package must include both `android.jar` and
`core-for-system-modules.jar`.

```sh
python3 m3/mobile/android/build-apk.py \
  --swt-checkout /path/to/eclipse.platform.swt \
  --android-sdk /path/to/android-sdk \
  --jdk /path/to/jdk-21 \
  --output /path/to/swt-android-build
```

The script reads `m3/mobile/common-sources.txt`, compiles the existing common files
and new mobile/Android files, and builds `app.apk` plus `instrumentation.apk`.
It does not replace the original common files with approximations. A compiler
system image made from the official Android core API JAR prevents the host JDK's
extra library methods from silently satisfying unavailable Android symbols.
`-XDstringConcat=inline` selects Java 21 StringBuilder concatenation because the
Android SDK's `StringConcatFactory` API stub omits the compiler bootstrap method.
The temporary system image is only an API input to `javac`, not a runnable JVM.

Both APKs use a locally generated **debug signing key**. The output includes the
source hashes, SDK JAR hashes, every build stage, APK hashes and signature checks
in `build-receipt.json` and `build.log`. A successful build records native device
execution as `NOT_RUN`.

An Android Gradle Plugin 8.13.0 project is also provided. With Gradle 8.13, JDK 21
and the SDK configured, run `gradle -p m3/mobile/android :demo:assembleDebug
:demo:assembleDebugAndroidTest`. Both build routes use the same product sources;
each route needs its own actual build result.

## Native screen, touch and lifecycle test

Use an API-35-or-newer test device/emulator and pass its exact `adb devices` serial:

```sh
python3 m3/mobile/android/run-instrumentation.py \
  --build-output /path/to/swt-android-build \
  --adb /path/to/android-sdk/platform-tools/adb \
  --serial emulator-5554
```

The runner verifies the APK hashes, installs those exact debug packages, and runs
the real Android `Instrumentation`. The test requires actual blue oval pixels
from `UiAutomation.takeScreenshot`, injects native touch events, observes the SWT
listener, and requires shell disposal after Activity destruction. Native mouse/key
state transitions are asserted after real Android event injection. PNGs and a
separate native receipt are collected. Missing pixels, failed assertions, missing
captures and missing explicit PASS results fail the run. This is Android screen
evidence; it does not replace the existing desktop SWT `GC.copyArea` acceptance
or establish support for the unimplemented mobile `Image`/screen-copy APIs.

## Sources used

- [Android View lifecycle and input API](https://developer.android.com/reference/android/view/View)
- [Android Canvas drawing API](https://developer.android.com/reference/android/graphics/Canvas)
- [Android Cleaner API](https://developer.android.com/reference/java/lang/ref/Cleaner)
- [Android Thread API](https://developer.android.com/reference/java/lang/Thread)
- [AGP 8.13 release requirements](https://developer.android.com/build/releases/agp-8-13-0-release-notes)

The backend is authored against these native APIs; no third-party implementation
body or separately licensed SWT-compatible widget clone is embedded.
