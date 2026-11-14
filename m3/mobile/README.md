# SWT mobile implementation

This directory builds the new mobile source set in the **existing SWT packages**.
The first application path is `Display` → `Shell` → `Canvas`, including the
original event classes, `EventTable`, `TypedListener`, geometry and resource
owners selected by `common-sources.txt`. It is an additive, initial port. Desktop
GTK, Win32 and Cocoa files remain the owners of their existing builds.

## Implemented scope

| Owner | Implemented behavior | Evidence route |
|---|---|---|
| `Display` | Dedicated SWT thread; `readAndDispatch`/`sleep`, async/sync calls, identity-based timer replacement/cancellation, bounded frame scheduling, native lifecycle queue, exception-safe disposal | Java contract tests; Android application lifecycle instrumentation |
| `Widget` | Existing SWT typed/untyped event dispatch, listener removal and selection veto, data, identity, disposal | Original common event code plus Java contract tests |
| `Control` / `Composite` | Parent-owned primitive bounds/flags, stable exposed child identities, client coordinates, clipping, focus, visible/enabled state, repaint lock and recursive cleanup | Java contract tests; native touch and pixel tests |
| `Shell` / `Canvas` | Native application surface with ordinary SWT construction and paint listeners | Android SWT demo; iOS embedding API |
| `GC` / `Color` / `Font` | Rectangles, lines, ovals, arcs, rounded rectangles, basic text, alpha, antialias mode, clipping, direct control drawing, font measurement | Android Canvas/Skia renderer; iOS CoreGraphics/CoreText renderer |
| Android | Activity/native View, immutable frames, primary pointer/hardware keys, density/insets, pause/resume/destroy | API 35 Java/DEX/APK build and real Instrumentation |
| iOS | Statically embedded UIKit view, JNI registration, native input/lifecycle, CoreGraphics/CoreText, bounded frame publication | Java/JNI header check and macOS/Xcode native simulator lane |

The Android build runs DEX on **ART**, using Java 21 source/compiler and an
explicit API-35 minimum. It does not supply a standalone M3JDK21 VM. The iOS
bridge requires an embedding application to provide its JavaVM and UIView;
the bridge does not supply that VM, a signed application or a complete M3JDK21
iOS runtime. See [Android build and execution](android/README.md) and the
[`SWTIOSAttach` native API](../../../bundles/org.eclipse.swt/Eclipse%20SWT%20PI/ios/library/swt_mobile.h).

## Remaining product scope

This initial source set is **not complete SWT or complete Nebula support**.
Tree/Table/List and other native widgets, full text/IME, accessibility trees,
images and display screen-copy, drag-and-drop, menus/dialogs, browser, clipboard,
printing and full graphics/font equivalence still require their owning platform
implementations and tests. Common event classes compile for compatibility; that
does not establish the corresponding native capability. Unsupported owners are
not supplied as empty widget implementations.

The Nebula mobile sample compiles the existing `RoundedCheckbox` and its two
existing helpers directly from the Nebula checkout. No mobile widget clone or
Synexia runtime service is involved. Its native test checks paint, touch,
selection, veto, disabled state and disposal. Other Nebula widgets remain outside
this initial acceptance scope.

## Reproduce verification

Run the shared Java contract suite with JDK 21 and the official JUnit Platform
Console Standalone 1.10.2 JAR (the script verifies its SHA-256):

```sh
python3 tools/mobile/verify-java.py --jdk /path/to/jdk-21 \
  --junit /path/to/junit-platform-console-standalone-1.10.2.jar
```

It compiles the actual mobile and iOS Java sources and checks the committed JNI
header against real `javac -h`. Its recording platform exercises Java contracts;
it makes no native pixel, device or iOS JavaVM execution claim. Android has its
own SDK-only build and instrumentation commands. On macOS, run
`bash tools/mobile/verify-ios.sh` with `JAVA_HOME` selecting JDK 21; it uses the
actual Apple simulator SDK and executes native renderer assertions.

Build success, Java tests, native renderer tests and complete application
execution are separate results. The passive delivery receipt names the exact
canonical Maven/OpenRewrite recipe and emitted file hashes. Recipes and templates
remain in Synexia; no recipe dependency enters the installed toolkit.
