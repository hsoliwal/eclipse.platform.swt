# SWT initial Android/iOS platform source set

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

## Build prerequisites

Use JDK 21, Maven 3.9 or later, and Python 3.10 or later. Python uses only its
standard library. Maven is the Java compiler, JAR, and JUnit/Surefire entrypoint;
`prepare_sources.py` selects and copies source files without transforming them.
No build command installs SDKs, signs a distribution, or installs an application
on a device.

The Android source lane requires an installed Android SDK platform 35. The
example APK lane also requires Android build-tools 36.0.0. **The initial APK
minimum is Android 15 / API 35.** This source selection retains common SWT uses
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

The `contracts` profile executes `MobileCoreContractJUnitTest` using JUnit 5 and
Maven Surefire. Its five cases cover the unchanged RoundedCheckbox's Selection
order/cancellation, rendering and disposal; SWT event/thread/timer contracts;
graphics and reentrant Paint disposal; termination and blocked synchronous
callers; and owned-Shell coordinate mapping. The original 44 assertions are
retained, with three coordinate regression assertions added.

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

To build the original Nebula application with Android's real SDK tools, use
Nebula's `mobile/pom.xml` and pass this SWT checkout as `-Dswt.checkout=...`.

## Button and Label original owner slice

The mobile source manifest now selects the original SWT `Button extends Control`
and `Label extends Control` API families, using the unchanged SWT shared
`WidgetStylePolicy` and `StyleBits` algorithms. These controls draw through the
existing platform-owned Image/GC and real Android/UIKit native peer rather than
forking a second widget model. Button has selection/keyboard/pointer handling,
radio sibling grouping and the existing platform accessibility role transport;
Label has text, image, alignment, separator, wrap painting and no-focus semantics.
Both preserve caller-owned Image lifetimes and the normal SWT disposal path.

**Boundaries:** these are mobile Canvas-rendered controls, not wrappers around
Android's `Button`/`TextView` or UIKit's `UIButton`/`UILabel`. Native text
editing/IME, accessibility action parity, full mnemonic focus traversal,
complex text layout, RTL, multi-touch and original desktop raster parity remain
open. The qualified `-Pcontracts` lane includes a new Button/Label owner test,
but a source commit alone does not prove a CI pass or native runtime parity.

## Implemented integration path and remaining scope

The implemented path is a Shell/Composite/Canvas application, including the
original Nebula RoundedCheckbox, standard SWT layouts and typed events,
Image/GC raster drawing, text measurement/drawing, affine transforms, and
resource/disposal ownership. `GC(Display).copyArea(...)` delegates to actual
platform capture. `Control.print` is separately scoped offscreen composition and
must not be presented as visible-screen evidence.

This is **not the complete SWT or Nebula widget catalogue**. Button and Label
have bounded Canvas-rendered source implementations; ProgressBar and Scale now
offer native-backed GC painting with original SWT range/selection methods.
Text, List, Combo,
Table, Tree, menus, ToolBar, TabFolder, StyledText, Browser, printing,
drag-and-drop, OpenGL, complete IME/text composition, and the public Accessible
API are not implemented by this source closure. Other Nebula widgets are not
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
