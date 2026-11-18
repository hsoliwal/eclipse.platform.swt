<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
# SWT modern native layers: batched DirectComposition dirty regions

An additive evolution of `LayeredViewport → NativeLayers → MayaNativeLayers → MayaComposition`:
no replacement compositor, new child Control per row, new Java renderer, or
change to the pre-existing seven Maya JNI exports.

**Added ABI:** `MayaComposition._nUploadDirty(layer, packedArgb, int[] rectangles, count)`.
Rects are quadruples x,y,width,height in *image-local* pixels. The payload
concatenates tightly packed premultiplied ARGB scanlines for each rect. It is
copied into native memory with `GetIntArrayRegion`, before each native
`BeginDraw`. A single Java-to-native call can update multiple independent
dirty rectangles, avoiding a full Java→native→GPU image upload. `_nApply`
remains the one-per-frame compositor commit.

Microsoft requires the **first** `IDCompositionSurface::BeginDraw` to cover
the entire nonvirtual surface. A native `initialized` bit enforces the
initial full `_nUpload`; dirty updates afterward use the *returned*
updateOffset, which is not necessarily the request coordinates. The last
completed frame remains valid after invalid **arguments** (all regions
validated before touching the GPU); an HRESULT failure during a later patch
can leave earlier patches applied, so callers fall back to full redraw.

**Public image API:** `Layer.redraw(Rectangle imageLocalPixels)` is
additive. It reads only the rows and spans of a changed image through
`ImageDataArgb.premultipliedSpan`, then uses the existing native layer.
An older Maya binary or non-Windows backend returns unsupported, and the
original full redraw is used. It does not dispose borrowed SWT Images,
retain JNI arrays, or alter control-layer z/focus semantics.

## Platform reality

| Target | Existing native route | Remaining modernization |
|---|---|---|
| Win32 | DirectComposition / D3D11; seven original exports, optional new dirty upload | Rebuild DLL, generated exports, screen-pixel and driver acceptance |
| Android API 29+ | Existing Maya raster; `MayaComposition` returns unsupported | Real `ANativeWindow`/ASurfaceControl parent, `AHardwareBuffer` ownership, fences, release callback and accessibility |
| Cocoa/macOS/iOS | Existing SWT Cocoa path, no Maya compositing | CALayer/Metal surface binding, main-thread/focus/IME and native-child ordering |
| GTK 4 | Existing GTK ownership | GSK/GskRenderNode, snapshot lifetime and GPU invalidation |
| GTK 3 | Existing GTK ownership | Preserve current Cairo/native draw fallback |
| Wayland | Existing compositor protocol integration | Owned wl_surface/wl_subsurface, sync/damage/scale and XDG popup policy |

The table is an **ownership and admission roadmap**, not a claim that
Android/Cocoa/GTK/Wayland native compositor layers have been shipped.

## Acceptance

- Standalone Clang C++17 portable dirty-rectangle + pixel-copy oracle
  **PASS 3,915,491** assertions on the tested header.
- The receiving actual SWT Java, JNI/Windows source, Win32 DLL and native
  screen tests remain **NOT RUN** for this source-sealed draft. Existing
  packaged maya.dll does **not** export `_nUploadDirty`; do not merge Java
  and native files into a release without rebuilding and publishing the
  matching binary+maya.version and running `-Xcheck:jni`/screen pixels.
- `native/tests/run_dirty_proof.sh` is an independent portable C++ test.
- Reconcile open SWT #503 frame-scratch work before promotion: it modifies
  `MayaComposition.cc` and `LayeredViewport.java`. Preserve both, reject
  stale preimages, do not rebase develop.
- Differential gates: alpha/transparency/palettes, 0/1/max rectangle counts,
  malformed and overlapping rects, device-loss, resize/disposal/reentrant
  paint, Win32/GTK/Cocoa native focus/accessibility/IME, CPU/GPU/RSS/handles.

Donors: Microsoft DirectComposition API docs and Android NDK docs are
architecture references only. No upstream implementation source is copied.

## Multi-rectangle damage from a CPU-owned backing image

Additive `Layer.redraw(Rectangle... dirtyRegions)` handles up to 4096
independent image-local regions through **one** Java-to-JNI `uploadDirty`
invocation, even when many rows are changed. It snapshots caller-owned
rectangle geometry into primitive `int[4*N]`, checks all bounds and the
total signed-int packed pixel count on the CPU (shared
`NativeDirtyRegions` validation), then copies only those image spans
into a packed ARGB payload. Overlap is allowed and counted per region.
The existing single-rectangle `redraw(Rectangle)` remains unchanged.

Only the SWT UI thread changes image pixels and native visual ownership.
The CPU image/backing store is the authority; DirectComposition is an
optional presentation endpoint. A missing/failed JNI upload or Commit
falls back to the original `Layer.redraw()` full-image sync. A no-op
empty array never allocates native resources. No browser/DOM renderer,
native widget per rectangle or new OS handle is introduced.

The Java21 proof compiles the exact `NativeDirtyRegions.java` with
`-Xlint:all -Werror` and passed **221,313** deterministic argument and
payload cases. The existing native C++ proof separately passed
3,915,491 packed-pixel and 20,467,204 extended observations. The
new Win32 native screen test and exact Maven/OpenRewrite/Tycho runs
are **NOT RUN**; a missing optional DLL symbol skips the normal test,
while `-Dswt.maya.dirty.required=true` must fail release proof.
