# Maya native image viewport residency and overscan

This is an **opt-in** SWT viewport membership policy for existing
`LayeredViewport → NativeLayers → MayaNativeLayers → MayaComposition`.
It does not introduce a new renderer, JNI symbol, scene type, native
Control peer, background worker, GPU thread, or memory-resident surface cache.

## Motivation

An image layer with `setNative(true)` previously could create a
DirectComposition surface even when thousands of world-coordinate
pixels outside the visible viewport. The existing per-composition
`MayaSurfaceBudget` bounds **nominal** BGRA image bytes but cannot
by itself decide which images deserve residency. A distant layer can
consume admission quota without contributing visible native pixels.

The viewport now optionally uses its **existing projected layer bounds**
as a pure primitive admission test, before checking for or creating
the native backend. This keeps the backing SWT image, logical layer
identity, Z order, hit-testing, accessibility, paint ownership, mouse
capture, world clips, pinned/sticky/fixed-axis projection, and all
existing NativeLayers and Maya JNI signatures unchanged.

## New per-viewport SWT API

```java
viewport.setNativeResidencyOverscanPixels(128);
int overscan = viewport.getNativeResidencyOverscanPixels();
```

All calls require SWT's owning GUI thread via `checkWidget()`.

- **-1 (default)**: disables viewport-residency culling and retains
  the historical native admission behavior, including invisible and
  offscreen layers where otherwise supported.
- **0**: admits a requested native image surface only when the
  projected image/clip overlaps the current SWT client rectangle.
- **Positive 1..1,048,576**: admits images within the configured
  *client-coordinate* overscan radius. Once allocated, an image is
  retained up to **twice** that radius before its native surface is
  released, avoiding repeated resource creation for tiny pans.
- Invalid limits below -1 or greater than 1,048,576 report the SWT
  invalid-argument error. Setting the current value is a no-op.

The overscan is expressed in SWT client-coordinate **pixels**, not
physical GPU texture bytes, OS virtual surface tiles, or world-space
units. The existing native-scene code continues applying shell-local
DPI scaling to actual native frame records.

## Membership and lifecycle

`NativeResidencyWindow` is one small package-private Java primitive
utility with **zero per-layer allocation** and no SWT dependencies.
Each native membership pass uses the already captured client
`Rectangle` and the current world-to-client projection.

For a layer with a source-local `WorldBounds` clip, the policy
intersects that clip with its world bounds before projection. Its
primitive double interval overlap is finite-checked and treats
touching the client edge as no visible area. Overflowed
`Long.MIN_VALUE` or `Long.MAX_VALUE` pans fail closed rather
than narrowing projected coordinates to `int`.

The call occurs **before** `nativeAvailable()`. A far-offscreen
`setNative(true)` request therefore does not eagerly create a GPU
composition when this policy is enabled. Ineligible layers retain
their image and request, but `Layer.isNative()` returns false
until the layer moves into the admission window.

On entry the original `syncNative` obtains the borrowed ImageData,
creates one native image layer, uploads its pixels, and publishes the
original Z/opacity/clip/affine frame. On exit it calls the pre-existing
`dropNative`, which releases the native layer and its logical BGRA
surface-budget reservation and damages the painted region where needed.
This also applies to zero-opacity and invisible layers while the
policy is enabled. No layer ID, borrowed Image or native Control is
disposed by a residency decision.

Pinned/fixed/sticky axes use the existing `layerOriginX/Y` and
`layerScaleX/Y`, so a fixed header does not appear to move in
the residency test just because its underlying world origin pans.
The original optional Maya scene feature-negotiation path is
unchanged; if affine or clipping is unsupported, the layer still
falls back to SWT software painting.

When all resident image layers leave the viewport, their surfaces
are released but the existing composition may remain allocated
for subsequent frames. **This is not a full GPU device eviction**.
The optional logical surface budget and explicit device-loss retry
continue owning their own policies.

## Algorithm and complexity

Given visible projected source bounds `[left,right) × [top,bottom)`,
client rectangle `[x,x+w) × [y,y+h)`, and nonnegative overscan `m`,
admit an unmaterialized layer if the two positive-area intervals
overlap with margin `m`. Retain an already-native layer with margin
`2m`. Invisibility, empty/invalid clipped bounds, a zero-sized
client area or non-finite projections reject admission when enabled.

One membership decision is **O(1)** time and primitive storage.
The existing `syncNative` retains its current bottom-to-top
snapshot traversal; this change does not claim to make a full
million-layer admission pass sublinear. A future viewport-index-driven
materializer can use the existing `LayerRectIndex` and compact
offset/mask arrays, but must preserve fixed/sticky and callback
mutation semantics before replacing this traversal.

## Executed verification

The standalone proof
`tests/org.eclipse.swt.tests/native/org/eclipse/swt/custom/NativeResidencyWindowProof.java`
compiles with Java 21 `--release 21 -Xlint:all -Werror` without
an SWT display or platform native library. It passed **200,000
deterministic interval comparisons** against an independent
positive-area intersection oracle, plus exact touching-edge, enter
versus exit hysteresis, overflow-safe `Integer.MAX_VALUE` and
`Integer.MIN_VALUE`, hidden layers, non-finite projection and
disabled-compatibility checks.

The production SWT widget test class also contains regressions with
its existing `RecordingNativeLayers`: unchanged default, a
far-offscreen surface released when enabled and recreated when
disabled, native reupload on pan/reentry, one-radius entry/two-radius
release, invisible-layer retirement, and stable logical IDs and
borrowed Image ownership. These widget tests are **committed, not
executed** in a real SWT display in this pass.

The existing `maya-native-safety.yml` workflow compiles/runs the
independent pure Java proof. Its Windows and native portability
checks remain unchanged.

## Mandatory native release qualification

Before switching the default from -1 or claiming a performance gain:
run the exact SWT Java21/25/Tycho/JUnit suite on Windows, GTK and
Cocoa; exercise native `AllMayaGcTests` with the newly built,
versioned Maya/Skija DLL under `-Xcheck:jni`; include scale, per-Shell
DPI, moving/sticky/fixed clips, rapid 0/1/64/1,000-layer panning,
nested callback mutation, native device loss and GPU resource teardown.

Compare `MayaComposition.readMetrics`, `readSurfaceBudget`,
CPU-side snapshot time, native surface issue/retirement,
p50/p95/p99 frame latency, actual GPU memory from OS diagnostics,
and visible pixel screenshots against the exact old-membership
baseline. Nominal BGRA byte reservations are **not** actual VRAM
consumption; this policy is surface admission/release, not GPU
memory paging, caching or Windows overlay Z-order virtualization.
