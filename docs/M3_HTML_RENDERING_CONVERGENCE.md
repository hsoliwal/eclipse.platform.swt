# M3 layered SWT rendering convergence: native widgets, HTML/CSS, and mobile

Status: architecture and test gates; **not** a claim of complete HTML/CSS rendering or mobile native compositor support.
Existing owner: `org.eclipse.swt.custom.LayeredViewport`, `ViewportRuntime`, `ViewportPaintGraph`,
`LayerRectIndex`, `LayerArgbCompositor`, and the optional Maya native backend.
Do not introduce a competing Canvas or change SWT's `Control` ownership.

## Already present

The SWT fork has Java retained backing stores, painter/image/control layers, stable Z order,
rectangle lookup, source-over and separable blend modes, pinned and sticky positions, pointer
routing, hit tests, and the Windows DirectComposition `MayaComposition` path.
The native path currently requires one-to-one pixel mapping and normal blend mode; other
content is painted through SWT's retained store. Native and painted layers are not yet a
general interleaved CSS stacking-context tree. See `docs/LAYERED_VIEWPORT.md`.

## What layers alone cannot render

| HTML/CSS responsibility | Layer machinery already useful | Missing contract / implementation |
| --- | --- | --- |
| Nested stacking contexts | Stable Z stack, isolated painter/image blend | Group surfaces, atomic parent-child stacking, clip/mask inheritance, opacity isolation across children |
| CSS box layout | Coordinates, viewport pan/zoom | Parsing/computed style, intrinsic sizing, margins/padding/borders, containing blocks, min/max, percentage units |
| CSS positioning | Pinned/sticky axes | Relative/absolute/fixed positioning in nested containing blocks; full sticky constraints; transforms and hit inverses |
| Inline and typography | Maya Skia text primitives, SWT GC | Font fallback, bidi shaping, line breaking, vertical writing, text decoration/selection, IME and caret placement |
| Flexbox/Grid/tables | Existing table/tree virtualization | Layout algorithms, track sizing, fragmentation, intrinsic constraints, edge cases and spec oracle tests |
| SVG and vector art | Paths, Skia and retained drawing | SVG element tree, viewBox, filters, masks, gradients, strokes, clipping, inherited paint |
| CSS effects | Separable blend modes and source-over | Filter graphs, backdrop-filter, shadows, non-separable hue/saturation/color/luminosity, color management and HDR |
| Scrolling/overflow | Retained pan, pinned/sticky, damage | Nested scroll containers, scroll anchoring, overflow clip, wheel/touch/overscroll and paint containment |
| DOM/interaction | SWT native controls and pointer routing | HTML parser, CSS selectors/cascade, DOM/CSSOM, event propagation, focus, forms, accessibility tree |
| Browser-grade HTML | None implied by a native widget renderer | JavaScript runtime, web navigation/network, sandboxing, origins, CSP, media, WPT compatibility |

GTK4's GSK render-node and snapshot model is a strong scene-graph reference, but
**GTK's widget CSS is not an HTML/CSS browser engine**. GIMP/GEGL provides layer
and color-operation oracles; Inkscape provides SVG/vector behavior oracles.
Inspect license obligations before borrowing any code: these projects have
different copyleft terms from SWT EPL and Synexia Apache code. Prefer
independent kernels with differential tests where source reuse is unsuitable.

For full unmodified web pages, use an actual embedded browser backend
(WebView2 on Windows, WKWebView on Apple, Android WebView and WebKitGTK where
available) rather than advertising the subset native renderer as browser-complete.

## Native backend boundaries

1. **Windows (implemented)**: DirectComposition visual tree through Maya,
   one JNI `_nApply` per frame; D3D11 premultiplied BGRA image surfaces.
2. **macOS (not implemented)**: `CALayer` / `CAMetalLayer` behind Cocoa UI thread;
   native `NSView` order, secure native ownership and Metal presentation.
3. **iOS (not implemented)**: UIKit `CALayer` / Metal; requires a platform-specific
   SWT peer and lifecycle; a desktop Cocoa HWND-like handle is not transferable.
4. **Android (not implemented)**: Java/NDK `SurfaceControl` / `ASurfaceControl`
   transactions, `ANativeWindow`, HardwareBuffer or Vulkan where supported.
   API-level feature checks and Android UI thread/surface lifecycle are required.
5. **GTK / Linux (not implemented)**: GTK4 GSK snapshots, OpenGL/Vulkan/cairo render
   nodes, or a single Skia-composited backing surface; Wayland subsurface behavior
   is not assumed to match Windows HWND topmost overlays.

Native abstraction is opt-in, requires feature negotiation and explicit fallback.
Compositor threading, GPU-device loss, resize and buffer fences, native handle
lifetime and accessibility remain platform-owned. JNI is not automatically faster:
**batch stable primitive operations per frame**, upload only damaged tiles and
avoid per-glyph JNI calls. Do not pin Java arrays across OS compositor operations.

## Performance and algorithm catalogue

| Category | Candidate optimization | Acceptance evidence |
| --- | --- | --- |
| Spatial index / interval stabbing | Parent-owned primitive rect arrays with offset masks; dirty tile bitsets | Correct hit/paint union vs brute-force oracle, sparse 100k-node scaling |
| Z-order changes / order statistics | Cached rank + sparse retained-mask, incremental splice, small-stack stable insertion sort | Randomized differential order and native Control Z parity |
| Layer composition | Cache immutable group render nodes and flatten-below segments | Pixel parity for premultiplied alpha, order, clip and blend |
| Dirty rectangles | Sweep/interval union or tile masking only when measured cheaper | Exact damaged pixel set, bounded allocation and paint latency |
| Native transfer | Packed `int[]`/direct-buffer batch, dirty-tile upload, GPU texture reuse | JNI call counts, bytes copied, CPU, GPU fences, peak RSS |
| Text | Glyph/run cache by font, script and scale; ICU/HarfBuzz/Pango shaping donors | Unicode bidi/grapheme/line-break oracle and IME/a11y correctness |
| Layout | Cached style/measure DAG, incremental invalidation after parent constraints | WPT/CSS snapshots, RTL, vertical writing and layout-reflow latency |

LeetCode, HackerRank and GeeksforGeeks should be used as **problem-category
discovery**, never as an unlicensed source dump. Record donor URL, revision,
algorithm, license, baseline, and differential benchmark with each canonical
Synexia Maven/OpenRewrite recipe.

## Existing native safety candidate in this PR

`MayaCompositionChecks.h` defines overflow-safe pixel/frame guards and
finite layer-property predicates. The existing seven JNI exports remain
unchanged. The DirectComposition backend now checks live handles and owner
before dereference, refuses duplicate/foreign layer handles before changing
visuals, and uses a monotonically increasing per-composition frame generation
to avoid a per-frame duplicate-detection container. No Java arrays remain
pinned across GPU work.

Local C++17 `-Wall -Wextra -Werror`, AddressSanitizer and
UndefinedBehaviorSanitizer predicates: **22/22 PASS**. This is not a Win32
runtime or native pixel pass. The new CI job validates the portable source
and Linux JNI stub. Win32 Maya/Java 21 `-Xcheck:jni` and screen pixels
must pass before promotion. iOS/Android support is still NOT IMPLEMENTED.

## Required PR gates

- Source-sealed Synexia recipe (pre/post SHA-256, fixed point, drift refusal),
  followed by product postimage/hash identity and provenance review.
- Java source/API diff and native ABI: original seven symbols unchanged.
- Native sanitizer test, Win32 real-build `maya.dll`, Maya JUnit raster pixels,
  `-Xcheck:jni`, zero native handle leaks and disposal/reentrancy tests.
- OS-specific GTK/Cocoa/iOS/Android builds only where new backends actually
  exist; do not infer platform support from non-Windows stub linkage.
- Measured p50/p95/p99 frame latency, JNI calls/frame, bytes uploaded, heap/RSS,
  native handles and GPU memory vs current master, at 100/1k/10k/100k layers.
- Review source hashes and PR overlap before merging. Never rebase Synexia
  `develop`; do not bypass mandatory CI or use an admin override.

## References

- W3C CSS Positioned Layout: https://www.w3.org/TR/css-position-3/
- W3C CSS Compositing and Blending: https://www.w3.org/TR/compositing-1/
- GTK4 GSK drawing model: https://docs.gtk.org/gtk4/drawing-model.html
- Android SurfaceControl: https://developer.android.com/reference/android/view/SurfaceControl
- Apple Core Animation: https://developer.apple.com/documentation/quartzcore
