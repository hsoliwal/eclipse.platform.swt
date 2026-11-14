# SWT Android / iOS native-host implementation (candidate)

This directory adds a **real, compilable Java ownership/event-loop core** and
platform-specific Android View and iOS UIKit host sources. It deliberately
keeps the existing GTK/Win32/Cocoa source and public SWT contracts untouched.
The new org.eclipse.swt.internal.mobile SPI is an implementation primitive,
**not a parallel SWT API or a completed SWT platform fragment**.

## Source ownership

- bundles/org.eclipse.swt/Eclipse SWT/common/.../mobile/MobileWidgetRuntime.java:
  owner-thread enforcement; stable array-indexed native handles; widget tree,
  rollback, disposal, lifecycle fencing, native event delivery, async/sync dispatch.
- mobile/android: Android View host and a native Activity smoke app.
  Shell/Composite map to FrameLayout, Label/Button/Text to native controls,
  and Canvas to a drawing view. Scroll/Table/Tree are **viewport containers only**;
  their SWT model/selection/column/virtual semantics are not implemented here.
  Touch, focus, click, text, IME, access-label, and scroll callbacks are forwarded.
- mobile/ios: an iOS UIKit JNI bridge with UIView/UILabel/UIButton/UITextField,
  UIScrollView viewport containers, accessibility labels, IME and main-queue posting.
  A working Java/AOT iOS runtime must embed it and hand in a valid UIView pointer;
  packaging and runtime startup are **not supplied** by this patch.
- mobile/tests: fast Java 21 ownership/event/lifecycle regressions.

## Verified / unverified

Run bash mobile/test-core.sh on Java 21. This performs an executable Java-only
compile and assertion test; it is not an Android/iOS compile or device test.
For Android, with Android SDK 35 and Gradle 8.x available, run
cd mobile/android && gradle assembleDebug and launch MobileActivity.
For iOS, UIKit Objective-C (mobile/ios/native/SWTMobileUIKit.m) must be built
against the target iOS SDK and a supported embedded Java/AOT runtime with JNI;
no macOS/Xcode simulator or device qualification is claimed.

## Promotion gates before SWT/Nebula can be called ported

1. Produce Android and iOS **original org.eclipse.swt.widgets backends**, not
   just these mobile host interfaces; retain public signatures and SWT semantics.
2. Implement native graphics/GC and resources, Shell/Display event-loop,
   layouts, dialogs, Text/IME/clipboard, DND, accessibility, Table/Tree/List
   virtualization, touch/gestures and display scaling. Reuse Initiative 31
   licensed painting donors only with exact provenance and contract proofs.
3. Build native Android instrumentation and iOS simulator/device tests, plus
   official SWT/JFace/Nebula differential tests and memory/disposal gates.
4. Implement actual Nebula Grid/custom-widget integration against the mobile
   SWT backend, not merely the mobile viewport projection in the Nebula fork.

All desktop behavior stays unchanged. This is a source-delivered **first backend
slice**, and the gates above are **open** rather than falsely reported as PASSED.

Donor/reference notes: Eclipse SWT (EPL-2.0), Initiative 31 Skia
(EPL-2.0, concepts only), Android SDK and Apple UIKit platform APIs; iOS Java
AOT option MobiVM/RoboVM remains a toolchain candidate, not a bundled runtime.
No third-party donor implementation body is copied in this change.
