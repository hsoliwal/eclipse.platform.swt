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
- **Android / iOS mobile:** not yet migrated. The mobile source manifest
  requires a separately qualified native peer implementation.

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

No border creates a new native window, new SWT child control, duplicate
accessibility object, or JNI export. Existing `SWT.BORDER` semantics are
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
- Cocoa bevel drawing + mobile native peers in separately reviewed follow-ups.

This remains a **draft** feature and must not be merged or presented as fully
cross-platform until those gates are satisfied.
