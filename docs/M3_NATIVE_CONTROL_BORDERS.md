# M3 native Control border relief (draft)

This is an **additive, opt-in native-control feature**. It is not an implementation
of Swing's arbitrary `Border` hierarchy and does not wrap a Control in a Canvas.
The original `SWT.BORDER` constructor bit, public widget events and native
control identity are unchanged.

## Public API

```java
control.setBorderRelief(SWT.SHADOW_IN);         // inset / lowered
control.setBorderRelief(SWT.SHADOW_OUT);        // outset / raised
control.setBorderRelief(SWT.SHADOW_ETCHED_IN);  // etched inward
control.setBorderRelief(SWT.SHADOW_ETCHED_OUT); // etched outward
control.setBorderRelief(SWT.SHADOW_NONE);       // explicitly remove native edge
control.setBorderRelief(SWT.DEFAULT);           // original platform edge
int current = control.getBorderRelief();
```

Existing `SHADOW_*` numeric constants are reused **only as method values**,
not as new Control constructor style bits: their bits overlap unrelated widget
styles and must not be interpreted as universally applicable style flags.
These properties are independent from widget-level Group shadow styles.

## Implementation

- **Win32:** save the native edge bits on the first override; preserve all
  other extended style bits; select `WS_EX_CLIENTEDGE` for sunken and
  `WS_EX_WINDOWEDGE` for raised; use the existing `DrawEdge` JNI binding
  for the two etched styles. `SetWindowPos(SWP_FRAMECHANGED)` recalculates
  native client geometry. The etched paint uses the existing window's
  non-client paint path; no new HWND, JNI symbol or compositor is introduced.
  The original edge bits are restored by `SWT.DEFAULT`.
- **GTK3 / GTK4:** attach one per-Control CSS provider to its existing
  GtkStyleContext. Its declaration is inset/outset/groove/ridge/none
  respectively. This provider is **separate** from the already owned
  foreground/background CSS provider, so color changes cannot wipe a border.
  Clearing uses an empty CSS provider; context ownership handles its disposal.
- **Cocoa:** two-tone relief is drawn only over an existing `NSScrollView`
  bezel. On any other frame a visible relief fails with
  `SWT.ERROR_NOT_IMPLEMENTED` before any state changes. Line numbers below
  refer to files in
  `bundles/org.eclipse.swt/Eclipse SWT/cocoa/org/eclipse/swt/widgets/` at
  master `c269ea222f1b`. A line number without a file name is in
  `Control.java`:
  - `setBorderRelief` (L1788-1819) accepts `SHADOW_IN`, `SHADOW_OUT`,
    `SHADOW_ETCHED_IN` and `SHADOW_ETCHED_OUT` only on a `Scrollable` created
    with `SWT.BORDER` (L1800-1803) whose `scrollView` has the
    `OS.NSBezelBorder` border type (L1804-1807). It measures the bezel trim
    with `NSScrollView.frameSizeForContentSize(..., OS.NSBezelBorder)`. It
    refuses a zero trim, and refuses an etched style when the trim is under
    two points per side (L1808-1814). The `scrollView` peer, its border type,
    `computeTrim` and the client area stay native.
  - `drawRect` (L1308-1348) runs after AppKit has painted that same
    `scrollView`. It fills the bezel bands with `NSColor.controlShadowColor`
    and `controlHighlightColor` (L1330): one ring for inset/outset, and two
    rings with opposite shading for the etched styles (L1325-1344).
    `fillNativeReliefBand` (L1350-1354) fills each band with
    `NSBezierPath.fillRect`. No `CALayer` border is used.
  - `SWT.SHADOW_NONE` is always refused (L1792): this API cannot remove the
    native bezel. `SWT.DEFAULT` always succeeds: with a visible relief active it clears the relief
    and repaints the plain bezel (L1793-1799); when the relief is already
    `SWT.DEFAULT` it returns early and does nothing (L1791).
  - Frames that can take relief are `Scrollable`s created with `SWT.BORDER`
    whose `scrollView` gets the `NSBezelBorder` type. These include a
    `Composite`/`Canvas` (`Composite.java` L339-350), `Table` (`Table.java`
    L669), `Tree` (`Tree.java` L914), `List` (`List.java` L335), multi-line
    `Text` (`Text.java` L556-564) and a `ToolBar` built with the public
    constructor (`ToolBar.java` L291-298).
  - The `SWT.SMOOTH` style bit does not decide whether a `ToolBar` takes
    relief. The public constructor passes `internal = false` (`ToolBar.java`
    L92-97), so `normalizeStyle` clears `SWT.SMOOTH` (`ToolBar.java`
    L177-179) before the handle exists. So
    `new ToolBar(parent, SWT.SMOOTH | SWT.BORDER)` gets the same bezel
    `NSScrollView` as `new ToolBar(parent, SWT.BORDER)` and accepts relief.
    The only `NSToolbar`-backed `ToolBar` comes from `Shell.getToolBar()`,
    which creates it internally with `SWT.HORIZONTAL | SWT.SMOOTH` and no
    `SWT.BORDER` (`Shell.java` L1336-1344). Its `createHandle` adopts the
    window's `NSToolbarView` as its view, creates no `scrollView`, and then
    clears `SWT.SMOOTH` from its style (`ToolBar.java` L259-288). It is
    refused because it has no `SWT.BORDER` (L1800-1803). If the Shell's
    window already has a toolbar, `normalizeStyle` clears `SWT.SMOOTH` for
    this internal `ToolBar` as well (`ToolBar.java` L186-194). The result is
    a borderless `ToolBar` without a `scrollView`, refused for the same
    reason.
  - Refused: controls without `SWT.BORDER` (L1800-1803), the
    `Shell.getToolBar()` toolbar described above, single-line `Text` (an
    `NSTextField` with no `scrollView`, `Text.java` L520-555), `Link` (it
    extends `Control`, not `Scrollable`, at `Link.java` L45, so L1800-1803
    refuses it even though its bordered peer is an `NSScrollView` bezel at
    `Link.java` L188), and any other control whose `scrollView` is missing
    or does not have the `NSBezelBorder` type (L1804-1807).
- **Android:** the original `AndroidPlatform.PeerView` performs two-tone native
  inset/outset/groove/ridge drawing in `onDraw`, with two logical-pixel trim,
  density-rounded view padding and UI-thread native peer updates. The default
  legacy `SWT.BORDER` path keeps its existing one-pixel grey edge.
- **iOS:** the original `SWTUIKitView` performs two-tone CoreGraphics edge
  drawing with native client insets and pointer-coordinate conversion. The
  `OS.setBorderRelief(JI)V` JNI binding is registered in `SWTUIKit.m`;
  `javac -h` must regenerate the matching exported header. The previous
  raster is invalidated on geometry changes, and old native peers are not
  replaced or retained.
- **Mobile bridge:** `MobilePlatform.setBorderRelief` has a binary-compatible
  default method. Existing third-party transports can still load unchanged,
  but non-default relief fails explicitly until the transport implements it.
  The original mobile `Control` owns selection, geometry, image invalidation
  and print-path two-tone edge rendering. The `mobile/source-manifest.json`
  SHA-256 pins the seven changed Java/test sources and new common validator.

Only controls already backed by an OS widget are modified. Native child controls
inside `LayeredViewport` retain their own independent border handles; native
image layers and the Maya/DirectComposition composition tree are not modified.
No arbitrary mixed native/painted Z-order is claimed.

## Layout, ownership, and guarantees

The Win32 branch uses the platform's own non-client geometry and
`getBorderWidthInPixels()` now recognizes the raised extended edge.
GTK asks the platform to recompute widget size after CSS changes; its SWT
border-width getter reports the opt-in two-pixel CSS border. Platform-native
layout and `computeTrim`/`getClientArea` parity **must still be measured**
before merger, including composed widgets with separate border handles.

No border creates a new native window, new SWT child control or duplicate
accessibility object. Only the UIKit backend adds a JNI registration; Win32
and GTK reuse their already-generated native bindings. Existing `SWT.BORDER` semantics are
unmodified until the first explicit call to `setBorderRelief`.

## Required acceptance gates (not claimed)

- Native Win32 non-client screenshots for inset, outset, both etched cases,
  reset, themes, 100/125/150/200% DPI, RTL, and composition with SWT.BORDER.
- GTK3/4 rendered pixels and client/trim parity in a real display, including
  when a color is set before/after border selection and GtkStyleContext themes.
- A `Control` and `Composite` test on actual OS handles, including Text and
  Scrollable subclasses with a different `borderHandle()`.
- The existing three combined Maya native-layer tests on the pinned JNI build;
  changing a child Control border must not re-order its native visual.
- Lifecycle/disposal/reentrancy, keyboard focus, accessibility, JUnit/PDE/Tycho,
  source/binary signature comparison and resource leak checks.
- Cocoa has no pixel proof for its bezel-band relief. Gates: AppKit
  screenshots of inset, outset, both etched cases and reset on each accepted
  bezel frame, in light/dark appearance at 1x and 2x scale, with the focus
  ring, plus a JUnit check that every refused frame leaves `getBorderRelief()`
  unchanged. Android/iOS native screenshot/instrumentation acceptance
  and regenerated `javac -h` JNI registration verification remain outstanding.

## Follow-up: one owner for Composite/Canvas border APIs

A separately accepted Composite API, `getBevelBorder()/setBevelBorder(int)`,
arrived on the master branch after the original Control relief work was
authored. It has a different default sentinel: `SWT.NONE` means no explicit
Composite bevel while `SWT.DEFAULT` means no explicit Control relief.

These interfaces converge on **one native HWND border authority in
`Control`**, while preserving the two distinct *logical requests* in the
merged Win32 compatibility contract (SWT #532):

- Win32 `Control` holds both the explicitly requested temporary relief
  (`nativeBorderRelief`) and the stored Composite/Canvas bevel
  (`savedCompositeBevel`). Both are fields of **the same Control owner**;
  `Composite` has no competing `bevelBorder` field or native style snapshot.
  The effective native border is the Control override unless it is
  `SWT.DEFAULT`, in which case the prior Composite bevel is restored.
- `getBorderRelief()` returns the explicit Control override, even when
  `SWT.DEFAULT`; `getBevelBorder()` returns the stored Composite choice
  even while Control is overriding it. This preserves the original merged
  two-API observable contract and the latest-writer behavior.
- Direct Win32 `Composite` and `Canvas` retain their original GDI
  `DrawEdge` painter; it reads the effective Control relief without calling
  a public getter during widget construction. All four visible variants
  reserve `WS_EX_CLIENTEDGE` native trim; unrelated Control subclasses
  retain their existing OS edge style handling.
- `Control` alone captures the first `WS_BORDER` and extended
  `CLIENTEDGE`, `STATICEDGE`, and `WINDOWEDGE` bits. Setting the
  Composite bevel updates the fallback and clears the Control override;
  `setBorderRelief(SWT.SHADOW_NONE)` removes the visible native edge
  without discarding the stored bevel; `SWT.DEFAULT` restores that bevel,
  or the original constructor border when none was selected.
- `Control.WM_NCPAINT` avoids a duplicate etched drawing after direct
  Composite/Canvas GDI handling. Unrelated native Control subclasses
  retain their Control-level etched path.
- On GTK3/GTK4 and Android/iOS, `setBevelBorder` is a compatibility alias
  of the already-implemented `Control.setBorderRelief`. A single GTK CSS
  provider or mobile view remains the rendering owner.
- On desktop Cocoa, `setBevelBorder` (`Composite.java` L116-127) passes the
  four visible bevels to `Control.setBorderRelief`, and maps `SWT.NONE` and
  `SWT.SHADOW_NONE` to `SWT.DEFAULT`. So a bordered `Composite`/`Canvas`
  gets the same relief painted over its `NSScrollView` bezel, and a
  borderless one still throws `SWT.ERROR_NOT_IMPLEMENTED`. `getBevelBorder`
  (`Composite.java` L105-109) reports `SWT.NONE` for `SWT.DEFAULT`. The alias adds no AppKit
  border type: the relief is SWT-painted bands over the existing bezel.
- Subclasses keep their original behavior: Win32's `setBevelBorder` still
  refuses non-direct `Composite/Canvas` subclasses; the inherited
  `setBorderRelief` remains independently usable.

This is a source-level convergence only. Native pixel/trim/focus/reentrancy and
mixed-API round-trip JUnit tests still require execution on all applicable
platforms. Do not promote either feature without the native release gates.

## Donor and convergence inventory

Existing Win32 `OS.DrawEdge`, `Composite.wmNCPaint` and current master
`OS.EDGE_RAISED/EDGE_BUMP/EDGE_ETCHED/BF_RECT` constants are native
authorities. Synexia #18560 is a *separate* draft style-bit recipe for
Composite/Canvas only; that behavior is intentionally not copied or enabled
by existing style bits because overloaded SWT constants could change legacy
widget interpretations. Synexia #18558 documents GTK4 Group frame CSS and
Synexia #18564 the independent Nebula Draw2D border-path candidate.
No unverified donor merge is part of this feature.

The shared public shape is the opt-in `Control.setBorderRelief` property,
with immutable integer values and owned native peers; it is not an API-equivalent
implementation of Swing's LineBorder, CompoundBorder or a generalized border
factory. Any broader public border object hierarchy requires a separate
compatibility proposal.

This remains a **draft** feature and must not be merged or presented as fully
cross-platform until the specified tests, Apple/Android native pixels,
Win32/GTK trim and lifecycle contracts, and current-master collision gates
are satisfied.

## Win32 post-#532 frame invalidation and direct-control relief

PR #532 defines Win32's existing *last explicit setter wins* contract:
`Composite.setBevelBorder` retains its own saved selection; a later
`Control.setBorderRelief` temporarily overrides the visible border; restoring
`SWT.DEFAULT` reveals that saved Composite bevel. A subsequent
`setBevelBorder` resets the Control override. This contract is preserved.

A direct `Composite` or `Canvas` has one GDI `DrawEdge` painter in
`Composite.wmNCPaint`, which requires `WS_EX_CLIENTEDGE` non-client trim.
The existing `Control.setBorderRelief(SWT.SHADOW_OUT)` originally selected
`WS_EX_WINDOWEDGE` uniformly, preventing that painter from handling the
outset case. For exact direct `Composite` or `Canvas`, select CLIENTEDGE
for an explicit SHADOW_OUT; all other controls retain the existing WINDOWEDGE
selection. Constructor SWT style bits, native HWND and JNI bindings are
unchanged.

Two etched modes use the same extended Win32 frame bits but different
`DrawEdge` flags. A client-only `InvalidateRect` does not guarantee a new
non-client paint. Use the *existing* `OS.RedrawWindow` JNI method with
`RDW_INVALIDATE | RDW_FRAME` after any genuine relief transition, including
SWT.DEFAULT restoration and etched-to-etched swaps. Unchanged relief remains
a no-op. Request layout if and only if the measured native border width
changed, with original widget disposal checks preserved.

Win32-specific regression coverage must assert native CLIENTEDGE selection
for direct Composite/Canvas raised relief, preserve WINDOWEDGE selection
for other native peers, verify inverse setter precedence, and exercise
etched transitions/reset without falsely equating geometry assertions with
screen-pixel proof. Native Win32 screenshots at multiple DPIs, dark/high-
contrast themes, RTL, scrolling, printing and Maya compatibility remain
release gates and must not be claimed from static inspection alone.
