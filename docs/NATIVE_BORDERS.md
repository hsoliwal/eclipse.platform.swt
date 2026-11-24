# Native inset and outset borders

Controls that already have a native frame can select an inset, outset or
etched appearance for that frame. The API is the opt-in Control relief
property:

```java
Composite panel = new Composite(parent, SWT.BORDER);
panel.setBorderRelief(SWT.SHADOW_IN);  // Sunken edge.
panel.setBorderRelief(SWT.SHADOW_OUT); // Raised edge.
panel.setBorderRelief(SWT.DEFAULT);    // Restore the original platform edge.
```

`Control.getBorderRelief()` initially returns `SWT.DEFAULT`. The setter accepts
`SWT.DEFAULT`, `SWT.SHADOW_NONE`, `SWT.SHADOW_IN`, `SWT.SHADOW_OUT`,
`SWT.SHADOW_ETCHED_IN` and `SWT.SHADOW_ETCHED_OUT`. Any other value raises
`SWT.ERROR_INVALID_ARGUMENT`. `Control.getBorderStyle()` and
`Control.setBorderStyle(int)` are aliases of `getBorderRelief()` and
`setBorderRelief(int)`. There is one relief state and one renderer per
platform.

The full contract is in [native-border-relief.md](native-border-relief.md),
whose backend table is reconciled with master's code. Its "API and
precedence" section says that `SWT.SHADOW_NONE` removes only the
supplemental relief and that `WS_BORDER` may remain; master's code does not
do that. On Win32, `SWT.SHADOW_NONE` removes the whole native edge,
including `WS_BORDER`, as the Win32 row below and the Win32 sections of
[M3_NATIVE_CONTROL_BORDERS.md](M3_NATIVE_CONTROL_BORDERS.md) describe.
Those Win32 sections also describe the interaction with
`Composite.setBevelBorder(int)`. Both files now describe master's Cocoa bezel-band relief. This page keeps the
user-facing notes of SWT #534 that hold under master's code, and documents
the Win32 and GTK frame-owner details added together with it.

## Method values, not constructor styles

These are individual **method arguments**. They must not be ORed into an
arbitrary widget's constructor styles: SWT reuses those bit values for other
widget-specific features. Existing `Group`, `Label`, and `CLabel` constructor
shadow behavior is unchanged.

## The frame stays where it is

The border belongs to the stationary native frame. A `Canvas` or
`LayeredViewport` continues scrolling and painting inside its existing client
area. This API does not create a wrapper control or a border object per item.
Outset describes the shading direction; it does not paint outside the
control's allocation.

## Native frame owners

| Backend | Frame owner and rendering |
| --- | --- |
| Win32 | The control's existing HWND. `Control` saves the original edge bits on the first override. `SHADOW_IN` selects `WS_EX_CLIENTEDGE` and `SHADOW_OUT` selects `WS_EX_WINDOWEDGE`. Direct `Composite` and `Canvas` use `WS_EX_CLIENTEDGE` with their existing `DrawEdge` bevel painter. The etched values select `WS_EX_CLIENTEDGE` and draw `OS.DrawEdge` (`EDGE_ETCHED` / `EDGE_BUMP`) after the native non-client paint. `SHADOW_NONE` removes the whole native edge: the extended edge bits and `WS_BORDER`. `getBorderWidth()` reports the selected edge, and a layout is requested only when that width changes. |
| GTK 3 and GTK 4 | A per-Control CSS provider, separate from the colour provider, attached to `borderHandle()`. That is the `GtkScrolledWindow` for a `Scrollable` that has one (for example a bordered or scrolling `Composite` or `Canvas`, `Table`, `Tree` and multi-line `Text`), and the `GtkEntry` / `GtkSpinButton` itself for single-line `Text` and `Spinner`. |
| Cocoa | Relief is drawn over the existing `NSScrollView` bezel of a bordered `Scrollable`. The etched values need at least two points of bezel on each side. `SHADOW_NONE` and other frame types refuse with `SWT.ERROR_NOT_IMPLEMENTED`. |
| Mobile (Android, iOS) | Delegated to `MobilePlatform.setBorderRelief`. A transport that does not support a value refuses it with `SWT.ERROR_NOT_IMPLEMENTED`. |

### GTK single-line Text and Spinner

`Spinner` and single-line `Text` now override `borderHandle()` to return their
own entry widget, and multi-line `Text` keeps the `Scrollable` owner. Neither
widget creates a `GtkScrolledWindow`, so `Scrollable.borderHandle()` already
resolved to the same widget. The overrides pin that owner explicitly and do
not change which widget receives the CSS today.

### Win32 custom-colored frames

With custom colors, `DateTime` and `TabFolder` paint their own `SWT.BORDER`
frame instead of the themed one. Once an explicit relief is selected
(`getBorderRelief() != SWT.DEFAULT`), both skip that custom frame, on screen
(`WM_NCPAINT`) and when printed (`WM_PRINT`), so the selected relief is what
appears. At `SWT.DEFAULT` their custom frame is unchanged.

### Win32 printing

Direct `Composite` and `Canvas` already print their bevel through
`Composite.wmPrint`. Other controls with an etched relief now print the same
edge that `Control.WM_NCPAINT` paints on screen: one shared drawing kernel
serves both messages. In `WM_PRINT` the caller's HDC state is saved and
restored around the edge. The client area is excluded, and a hidden HWND is
skipped under `PRF_CHECKVISIBLE`, just as the native procedure skips it. Inset
and outset on these controls come from the native edge styles and still
print through the native procedure.

## Not taken from SWT #534

SWT #534 defined `setBorderStyle(int)` as a property with its own state and
semantics, separate from the relief. Under that design, `SWT.DEFAULT`
restored Win32 constructor bevels and `SWT.SHADOW_NONE` was refused. Inset
and outset were drawn with `DrawEdge` on every control, and geometry never
changed. That contradicts the alias contract above. Those parts of #534, and
the #534 tests that assert them, wait for the owner's decision and are not
part of this document.

## Acceptance

Java compilation does not establish native platform acceptance, and neither
do portable transport tests. Each backend still needs its own native build
plus runtime checks of rendering, themes, DPI and printing. The Win32
custom-frame and printing paths above have no dedicated pixel tests yet.
