# Native inset and outset borders

Controls that already have a supported native frame can select its appearance
without changing its geometry. Normally that frame is created with `SWT.BORDER`:

```java
Composite panel = new Composite(parent, SWT.BORDER);
panel.setBorderStyle(SWT.SHADOW_IN);  // Sunken edge.
panel.setBorderStyle(SWT.SHADOW_OUT); // Raised edge.
panel.setBorderStyle(SWT.DEFAULT);   // Restore the original platform appearance.
```

`Control.getBorderStyle()` initially returns `SWT.DEFAULT`. The setter accepts
`SWT.DEFAULT`, `SWT.SHADOW_IN`, `SWT.SHADOW_OUT`, `SWT.SHADOW_ETCHED_IN`, and
`SWT.SHADOW_ETCHED_OUT`. The etched variants select inward and outward etched
edges where the existing frame can represent them.

These are individual **method arguments**. They must not be ORed into an
arbitrary widget's constructor styles: SWT reuses those bit values for other
widget-specific features. Existing `Group`, `Label`, and `CLabel` constructor
shadow behavior is unchanged.

Win32's existing constructor shadow hints for direct `Composite` and `Canvas`
also remain supported, including the native frame they create without
`SWT.BORDER`. The setter can override those frames; `SWT.DEFAULT` restores the
original constructor-selected bevel. Constructor hints retain their exact-class
restriction, while the setter uses its independent property on supported controls.

## Contract

The setting changes appearance only. It preserves the original native peer,
constructor style, border width, client area, `computeTrim`, child placement,
scrollbars, input mapping, and accessibility identity. Outset describes the
shading direction; it does not paint outside the control's allocation.

The border belongs to the stationary native frame. A `Canvas` or
`LayeredViewport` continues scrolling and painting inside its existing client
area. This API does not create a wrapper control or a border object per item.

`SWT.DEFAULT` restores the original platform rendering. `SWT.NONE` and
`SWT.SHADOW_NONE` are not supported by this setter: removing a frame would
change layout. A control without an existing supported frame rejects an
explicit appearance with `SWT.ERROR_NOT_IMPLEMENTED` (`SWTError`), preserving
the previous selection. Unknown or combined values produce
`SWT.ERROR_INVALID_ARGUMENT`. Normal SWT thread and disposal checks apply.

## Native implementations

| Backend | Frame owner and rendering | Supported appearance |
| --- | --- | --- |
| Win32 | Existing child-window border, drawn through the existing `OS.DrawEdge` JNI binding after native nonclient work; native printing uses the same path. Direct `Composite` and `Canvas` constructor bevels use this same renderer. | Inset, outset, etched inward/outward. A one-pixel frame clips a multi-line edge to its existing ring. Shell frames are excluded. |
| GTK 3 and GTK 4 | Existing `GtkScrolledWindow` frame for controls such as `Composite`, `Canvas`, `Table`, `Tree`, and multiline `Text`; existing entry/spinbutton frame for single-line `Text` and `Spinner`. A separate native CSS provider changes only `border-style`, preserving theme colors, widths, padding, and focus outlines. | Inset, outset, groove, ridge when all four native frame edges have positive width. Other frame owners explicitly refuse. |
| Cocoa | Existing `NSScrollView` bezel, decorated after its native paint using AppKit drawing through existing JNI bindings. Native frame metrics remain unchanged. | Inset/outset; etched appearances require at least two existing logical points per side. Other frame types, including single-line text field bezels, explicitly refuse. |
| Android | Existing `PeerView` border, drawn by native Android `Canvas` independently of the client snapshot and native children. | Inset/outset within the existing one-point border. |
| iOS | Existing UIKit view border, drawn by Core Graphics; the Java declaration and explicit JNI registration include the border setter. | Inset/outset within the existing one-point border. |

Mobile etched styles are rejected because the existing one-point frame cannot
faithfully contain two rings. Older `MobilePlatform` providers remain binary
compatible through a default method and reject unsupported explicit styles.
The setter does not claim that every SWT widget or every external transport
has an interchangeable native border.

## Tests

`Test_org_eclipse_swt_widgets_ControlBorder` is selected by `AllWidgetTests`.
It covers native screen pixels, native print direction and GC-state retention,
restoring default pixels, exact client-pixel preservation, settled geometry,
child coordinates, selection, tiny controls, invalid values, and thread/disposal
contracts. GTK entry/spinner coverage also checks text, selection, and preferred
size. Screen capture uses SWT `GC(display).copyArea`; printing is tested
separately.

The Win32 suite selects dedicated tests for custom-colored `TabFolder` and
`DateTime` nonclient/print paths, as well as constructor bevel override/reset
and unchanged trim. The mobile contract suite covers transport
refusal and failure atomicity, unchanged peers and geometry, printing, and
lifecycle. The existing UIKit simulator renderer harness includes native
inset/outset pixel assertions.

Java compilation and portable transport tests do not establish native platform
acceptance. Each backend still requires its own native build and runtime
rendering, theme, DPI, and printing checks. Recipe execution and qualification
evidence are maintained with the paired Synexia change.
