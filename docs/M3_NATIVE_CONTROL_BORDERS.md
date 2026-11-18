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
- **Cocoa:** API is present for compile-time parity; rendering a true
  *two-tone native bevel* is not implemented. All non-default values
  reject with `SWT.ERROR_NOT_IMPLEMENTED` rather than silently painting a
  flat CALayer border and claiming it is a bevel.
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
- Cocoa AppKit bevel drawing remains unsupported rather than silently falling
  back to a flat line. Android/iOS native screenshot/instrumentation acceptance
  and regenerated `javac -h` JNI registration verification remain outstanding.

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

## Post-merge single-owner correction contract (2026-11-18)

The original Control relief implementation (#517) has merged, alongside
Win32 `Composite.setBevelBorder` (#523). The old draft convergence PR #526
branched before that merge. Its full file replacements are **not** a valid
reconciliation: they would erase the now-present Android/iOS implementation.

The existing public signatures are retained. This follow-up makes the single
native peer authoritative for both convenience spellings:

- `Control.setBorderRelief(relief)` owns the effective native effect.
- `Composite.setBevelBorder(relief)` is an alias for the same effect rather
  than an independent native painter or state variable. The pre-existing
  `getBevelBorder()/setBevelBorder()` public methods are not removed.
- `setBevelBorder(SWT.NONE)` or `setBevelBorder(SWT.SHADOW_NONE)` restores
  `SWT.DEFAULT`, the original platform frame. `Control.setBorderRelief(
  SWT.SHADOW_NONE)` explicitly suppresses native relief. Both map to
  `getBevelBorder() == SWT.NONE`; `getBorderRelief()` distinguishes them.
- A direct Win32 `Composite` or `Canvas` uses its existing
  `Composite.wmNCPaint` as the **only** custom edge painter. The generic
  `Control.WM_NCPAINT` handles native etched drawing for other eligible
  HWND-backed controls, but must not draw a second edge over direct
  `Composite`/`Canvas` instances. The direct Win32 raised case reserves
  `WS_EX_CLIENTEDGE` and paints `EDGE_RAISED`; other HWND controls retain
  the existing raised `WS_EX_WINDOWEDGE` path.
- A same-width etched-to-etched transition invalidates the non-client frame
  using the existing `RedrawWindow(RDW_FRAME | RDW_INVALIDATE)` JNI method.
  Layout is re-requested only if the effective native border width changes.
- Existing GTK native relief is used by `Composite.setBevelBorder`, rather
  than rejecting a feature that `Control.setBorderRelief` already supports.
  CSS declarations live in a widget-scoped provider with higher precedence
  than the GTK4 Group constructor shadow and separate from the original
  foreground/background provider. GTK's border metric query, not a hard-coded
  two-pixel getter, supplies the override width.
- The mobile `Composite` alias uses the existing `MobilePlatform` peer's
  Android/iOS path; no new JNI export, UIView or Android View is created.
  All modified manifest-pinned mobile Java/test sources require refreshed
  SHA-256 after complete source readback. Pre-existing manifest drift is
  reported, never silently treated as semantic equivalence.
- Cocoa continues to reject unimplemented native two-tone relief; no false
  AppKit/CALayer equivalence is claimed. Shared Swing BorderFactory parity is
  expressly outside this native-edge correction.

**Source, API, and release gates:** keep the new work on a draft PR until
actual SWT Java/Tycho compilation, the existing desktop/mobile JUnit suites,
Windows non-client screenshots at multiple DPIs and themes, GTK3/GTK4 CSS
pixels and Group overrides, Android/iOS device rendering, and Maya/Nebula
regression checks execute. Static source inspection or an isolated fixture
is not a substitute for these proofs.
