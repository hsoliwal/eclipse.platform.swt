# SWT UIKit platform source set

This directory builds the iOS native side of the mobile SWT source set. The Java
application uses the existing SWT `Display`, `Shell`, `Composite`, `Canvas`,
events, graphics and unchanged Nebula widget classes selected by the mobile
build. `IOSPlatform` binds those owners to UIKit and CoreGraphics. It does not
introduce a second widget toolkit or replace a Nebula widget with a demonstration
control.

The current implementation is a bounded port. It is **not a complete SWT or
Nebula iOS release**. The initial integration closure exercises the original
Nebula `RoundedCheckbox` and its original selection handler. A native library
compile, an application link, and a successful device interaction are separate
gates. The initial source preparation was performed on Linux: Java compilation,
JNI descriptor matching and script/plist checks passed; Apple native compilation,
runtime linkage and device execution were not run there.

## Ownership and event flow

UIKit owns the main thread and a `UIWindowScene`. The bootstrap creates a
dedicated Java worker with a supplied iOS JVM. `IOSPlatform.launch` installs the
mobile platform and invokes the application's existing `main(String[])`. Its
normal SWT event loop runs on that worker. On return or failure, `launch` disposes
the worker's live `Display`, disconnects its platform and preserves the original
exception, including any cleanup failures as suppressed exceptions.

Each admitted SWT control owns a real `UIView` peer. Native view operations copy
their Java inputs before synchronously marshaling to main; a worker's `JNIEnv`
never crosses threads. UIKit callbacks use the main thread's own JNI attachment
and only enqueue `MobilePlatform.EventSink` events. They never wait for Java
listeners. Callback exceptions are retained and rethrown by `checkFailure` on the
Display thread. The shared event loop polls that failure path even while idle.

Image and GC handles belong exclusively to the Display thread. CoreGraphics owns
the bitmap allocation. Presentation takes an immutable `CGImage` snapshot; a
view does not retain a mutable Java array or bitmap pointer. Drawing that
snapshot does not generate another SWT Paint event. Native handles are typed,
generation-tagged IDs backed by reusable parallel C arrays, without boxed keys or
per-entry wrappers; disposed/unknown resources are rejected. Strong ARC slots are
zero-initialized, released before storage is freed, and keep their generations
across reconnection. JNI entry points use
bounded autorelease pools, so a long-running Java loop does not retain a launch's
worth of temporary Objective-C objects.

The host retains its live window and JVM during a temporary disconnection of the
same scene session. Final session disposal enqueues `EventSink.terminate()`;
UIKit does not join the Java thread. The current host admits one session per
process. Creating another session after final destruction requires relaunching
the application. iOS can kill a suspended process without delivering termination
callbacks, so application data must be saved during normal operation.

## Implemented boundary and remaining work

| Area | Current behavior |
| --- | --- |
| Native ownership | UIKit peers for Shell, Composite and Canvas; hierarchy, bounds, visibility, enablement and focus. Owned Shells attach to the window root in display coordinates; Shells matching the viewport follow its size changes. |
| Touch | One primary contact mapped to original SWT mouse events; cancellation sends MouseExit and does not activate a widget's MouseUp handler |
| Hardware keyboard | Key down/up, modifiers and navigation key mapping; Event.character carries one UTF-16 code unit |
| Lifecycle | Scene activation/deactivation and final host/session destruction enqueue shared SWT work |
| Rendering | CoreGraphics lines, rectangles, ovals, polygons, clip/transform, alpha, image crop/scaling and CoreText text/font measurement |
| Text rendering | UTF-16 shaping through CoreText; draw flags for delimiters, tabs, mnemonic underline and transparent background |
| Accessibility transport | Native labels/traits and default activation through the original control's input path; the general SWT Accessible API is not implemented by this transport alone |
| Pixels | Straight ARGB Java ingress/readback with premultiplied native storage; immutable presentation snapshots |
| Metrics | Logical points, with nominal 72-point DPI; physical panel DPI and Retina-resolution backing-store adaptation are not claimed |
| Screen capture | `GC(Display).copyArea` fails explicitly through `captureScreen`; use an actual XCUI screenshot for device-visible evidence |

This lane does not yet provide the native SWT Text/IME owner, marked-text and
selection semantics, a complete Accessible implementation, adjustable accessible
actions, full multitouch/gesture mapping, multiple simultaneous application
scenes, every Shell/window style, or the remaining SWT/Nebula widget closure.
Increment/decrement accessibility roles are rejected explicitly. UIKit layer
rendering is never reported as visible-screen capture. Raster/text parity,
rotation behavior, background/foreground recovery, VoiceOver, resource stress and
original-widget interaction require the Apple simulator/device gate.

## Compile the native library with Xcode

Use a macOS host with a real Xcode iOS SDK, CMake 3.24 or newer, and a JDK 21.
First build the mobile SWT Java artifact for `mobile.platform=ios` using the
shared mobile build. Its classpath must contain the original common owners,
original `ACC`, the mobile owner source set, `IOSPlatform` and `OS`.

```sh
export JAVA_HOME=/absolute/path/to/jdk-21/Contents/Home
bash mobile/ios/build.sh iphonesimulator arm64 /absolute/path/to/swt-mobile-ios.jar
bash mobile/ios/build.sh iphoneos arm64 /absolute/path/to/swt-mobile-ios.jar
```

Use `x86_64` for an Intel simulator. `SWT_IOS_BUILD_DIR` can select a separate
build directory. The script does not install or download a JDK, SDK, runtime or
dependency. CMake regenerates `org_eclipse_swt_internal_ios_OS.h` with `javac -h`
from the actual OS/adapter source and real mobile SWT classpath. Event, role and
state constants therefore come from the original SWT/ACC declarations.

The default build has two targets: `swt_ios` (the native platform library) and
`swt_ios_bootstrap` (the application entry-point object). Both use ARC and
`-Wall -Wextra -Werror`. This is a useful macOS CI compilation gate without a JVM
artifact: it checks real Apple APIs and real JNI declarations. The macOS JDK's
JNI headers used for this compile-only gate do not establish an iOS runtime.
No UIKit/CoreGraphics stub headers are accepted or generated.

## Link an application only with a verified iOS runtime

There is no bundled or automatically selected iOS JVM in this change. A desktop
JDK cannot satisfy the application target. A working target runtime must provide
its static JVM/native-library closure, target JNI headers and deployable runtime
image. The bootstrap calls `JNI_CreateJavaVM` with `-Xint`, then registers this
native owner explicitly with `SWTUIKitRegister`. It does not infer a JIT
entitlement, an AOT pipeline, or missing JVM dependencies.

Set `SWT_IOS_BUILD_APP=ON` only after supplying these CMake inputs:

| Input | Required value |
| --- | --- |
| `SWT_IOS_RUNTIME_ROOT` | Directory containing the prepared iOS runtime artifact |
| `SWT_IOS_RUNTIME_MANIFEST` | Manifest described below |
| `SWT_IOS_RUNTIME_MANIFEST_SHA256` | Previously reviewed exact manifest SHA-256 |
| `SWT_IOS_JDK_SOURCE_REVISION` | Exact 40-character source commit for that runtime |
| `SWT_IOS_MOBILE_JAR` / `SWT_IOS_MOBILE_JAR_SHA256` | Compiled mobile SWT JAR and its reviewed hash |
| `SWT_IOS_APPLICATION_JAR` / `SWT_IOS_APPLICATION_JAR_SHA256` | Existing application plus its actual Java dependency closure and reviewed hash |
| `SWT_IOS_MAIN_CLASS` | Existing application class with `public static void main(String[])` |
| `SWT_IOS_BUNDLE_ID` | Application bundle ID; configure normal Xcode signing inputs for a physical device |

The runtime manifest has `schemaVersion: 1`, `sourceRevision`, `platform`
(`iphoneos` or `iphonesimulator`), `architecture`, `javaHome`, `jniInclude`,
`jniPlatformInclude`, `libraries`, `frameworks`, `systemLibraries`, and `files`.
Paths are relative to the artifact root. `libraries` lists each required static
archive in link order; `frameworks` and `systemLibraries` list the actual SDK
dependencies. Each `files` entry contains `path` and a 64-character `sha256`.
Inventory every runtime-image file, JNI header and linked archive. Include empty
arrays for absent system dependencies.

CMake checks the manifest hash and source revision, verifies every inventoried
file, rejects path escapes/duplicates/missing inputs, checks the selected
platform/architecture, and requires the complete packaged image to be in the
inventory. Materialize runtime-image symlinks before hashing. The app uses the
manifest's target JNI headers; it does not fall back to host headers. Native
archives are force-loaded to preserve static native registration owners. These
checks establish input identity; only a successful link and execution establish
runtime compatibility.

Pass the inputs as `-D...` arguments after the three arguments to `build.sh`, then
build the generated `SWTMobile` target:

```sh
cmake --build /absolute/path/to/configured-ios-build --config Release --target SWTMobile
```

The resulting bundle contains `swt-mobile.jar`, `application.jar` and the verified
`runtime` image. Arguments beginning `--swt-arg=` in the native process arguments
are forwarded to Java. The current bootstrap invokes one unchanged application
main; it does not provide hot deployment or remote code loading.

## Device gate

Run the actual original-widget fixture with the supplied runtime, then use an
XCUI test or the device to tap the original RoundedCheckbox. Verify that its
original Selection listener runs on the Display thread and that cancellation
prevents its original toggle. Capture a simulator/device screenshot externally,
and exercise paint, resize, disposal, background/foreground, same-session
reconnection and final destruction. Add keyboard and VoiceOver checks on hardware
where supported. Preserve runtime identity, Xcode/SDK/device identity, logs and
screenshots with that result. None of those results follows from the source-only
Java gate or native static-library compilation.

## Source provenance

The UIKit/CoreGraphics implementation and bootstrap here are newly authored under
`Apache-2.0 OR EPL-2.0`. No SDL, OpenJDK Mobile helper, or desktop Cocoa
implementation was copied into this source set. Existing SWT and Nebula files
retain their original licenses and source owners.

Reference inspection included the [SDL UIKit backend at
59ccf612c1ee00e3845947503ead7da3f46f6615](https://github.com/libsdl-org/SDL/tree/59ccf612c1ee00e3845947503ead7da3f46f6615/src/video/uikit)
under SDL's zlib license and [OpenJDK Mobile's iOS documentation](https://openjdk-mobile.github.io/ios/).
The [OpenJDK Mobile iOS helpers at
2f0a489b48cdb13901185be051004f7a9c23bf14](https://github.com/openjdk-mobile/ios-tools/tree/2f0a489b48cdb13901185be051004f7a9c23bf14)
were reference-only; their AGPL-3.0 scripts were not imported.

Apple's primary API references cover the [scene lifecycle](https://developer.apple.com/documentation/uikit/uiscene),
[hardware keyboard usages](https://developer.apple.com/documentation/uikit/uikeyboardhidusage?language=objc),
[CoreGraphics-owned bitmap storage](https://developer.apple.com/documentation/coregraphics/cgcontext/data),
and [bitmap snapshots](https://developer.apple.com/documentation/coregraphics/cgcontext/makeimage()).
