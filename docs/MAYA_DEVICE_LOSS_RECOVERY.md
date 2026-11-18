# SWT Maya DirectComposition: device-loss containment and explicit recovery

Status: **feature branch candidate; not a qualified Windows GPU release**.

## Existing owners preserved

This change stays in the original SWT/Maya layers:

- `LayeredViewport` owns SWT client pixels, borrowed images, Z/XY geometry,
  GUI events and the retained painted fallback.
- `NativeLayers` remains the optional backend contract with the same JNI
  and Java method signatures.
- Original `MayaComposition.cc` owns the Win32 D3D11/DXGI and
  DirectComposition device, visuals, surfaces, commit and native handles.
- `CompositionFrameDelta` owns last successfully committed order/property
  authority. `pendingSurfaceCommit` independently forces Commit for
  content-only EndDraw changes, even when the Z/properties are unchanged.
- `MayaNativeHandleRegistry` owns typed, never-reused native tokens.
- This change introduces **no new event loop, native widget family, public
  Control ABI, native export, packed frame version, or retained renderer**.

## Detection: invalid DComp and D3D resources

Microsoft documents `IDCompositionDevice::CheckDeviceState(BOOL*)`:
when the associated DXGI device is lost, the composition device is invalid
and the application must recreate *both* the DXGI/D3D11 and DirectComposition
device graph plus all device-dependent content. A previously cached no-op
frame is not sufficient proof that the OS visuals are still alive.

The original native owner now checks device validity before:
- creating each new native layer surface;
- a full source-image upload or packed dirty-region upload;
- every `_nApply` or `_nApplyPacked` transaction, including Z/property
  no-ops;
- `_nWaitForCommit` (both before and after waiting).

A portable shared C++17 atom `MayaDeviceHealth.h` normalizes HRESULT and
D3D11 `GetDeviceRemovedReason` diagnostics:
- A failed `CheckDeviceState` returns its original failing HRESULT.
- A valid DComp device returns `S_OK`, regardless of a stale diagnostic.
- A reported invalid DComp device returns the D3D removal/reset reason
  when it is a failure; if that reason reports `S_OK`, fail closed with
  `DXGI_ERROR_DEVICE_REMOVED` instead.
- Any failed frame health check invalidates retained native order/property
  caches and returns nonzero to the existing viewport boundary.
- The native backend cannot silently claim that old GPU pixels were
  committed, and `pendingSurfaceCommit` is not cleared on failure.

**No asynchronous D3D thread, background polling or synthetic WM_PAINT
message is introduced.** These probes run only when SWT makes native calls.
They are intentionally conservative and could add cost per native frame;
the Windows performance gate must measure the overhead before defaulting
to any higher probe frequency.

## Containment and presentation fallback

When `LayeredViewport.applyNative` receives a nonzero native result,
its pre-existing logic detaches `composition` and clears the native
handles in one operation; the backend disposes the failed native
composition and invalidates the projected layer bounds for repaint.

SWT's ordinary software backing store becomes authoritative again.
Layer identity, borrowed Image lifetime, layer visibility, hit testing,
ordering, native child Control ownership, accessibility and SWT thread
affinity remain unchanged. This protection is already tested by the
existing failure/reentrant/disposal widget contracts.

Critically, automatic silent recreation is still **disabled**. Repeated
native failures must not produce runaway device creation, GPU allocation,
recursive paints, or driver reset loops.

## New explicit UI-thread API

```java
// Called from SWT's event thread after the application observes that the
// graphics driver/device is usable again, not from a worker render thread.
boolean recovered = viewport.retryNativeComposition();
```

`retryNativeComposition()`:

1. Requires a live SWT widget and its owning UI thread (`checkWidget`).
2. Refuses to run inside a paint callback, nested native frame, active
   damage batch, or another native retry.
3. Returns false when no layer still requests native presentation.
4. Returns true without allocating when a native composition is already
   healthy/available.
5. Otherwise reuses the existing backend (or probes the optional provider),
   calls its original `create(Control)` once, and refuses a zero result.
6. Rechecks disposal and native-request state after creation, because native
   calls may dispatch application callbacks.
7. On successful creation reuses `syncNative` to reconstruct all eligible
   native image layers from application-owned `Image` pixels, in the
   original Z order and with their original opacity/visibility/positions.
   A failed upload/commit follows the existing software-fallback path.
8. Does not schedule repeated retries. Another try requires another
   explicit application call.

Returns true for an available native composition, not a guarantee that
every layer is native. Layers requiring unsupported zoom, clipped groups,
non-normal blend or other software-only features remain painted. Use
`Layer.isNative()` for each actual layer.

## Tests and acceptance

Portable: `MayaDeviceHealthTest.cc` executes 100,000 deterministic
health transitions and validates successful state, invalid DComp state,
device removal/reset, signed error precedence and fail-closed fallback.
The exact-source GCC and Clang C++17 ASan/UBSan builds passed locally.
The CI gate adds these to the existing `maya-native-safety.yml`.

Widget: `Test_org_eclipse_swt_custom_LayeredViewport` adds tests with
its existing `RecordingNativeLayers` backend for a failed composition,
software fallback, explicit failing then successful `create`, fresh
image reupload, idempotent healthy retry, borrowed image ownership, and
nested native/paint/update guard. Those SWT tests are **committed but
not run in a provisioned native SWT display in this pass**.

Before releasing the Windows native backend, run:
- JDK21 and JDK25 strict SWT/Tycho Java source compile and Win32 MSVC
  C++17 compilation of the original Maya composition implementation;
- full Skija/Maya native DLL build, linker export parity, version/hash
  update, `-Xcheck:jni` and original `AllMayaGcTests` on Windows;
- actual DComp `CheckDeviceState`, forced DXGI device remove/reset,
  invalid HRESULT, old-token rejection, zero/large frame,
  success/failed recreations and multi-layer pixel reupload tests;
- resource/COM lifetime, worker/UI event-thread ownership, resize,
  focus/IME, accessibility and native Control overlay preservation;
- p50/p95/p99 native frame latency, extra DComp probes/frame,
  JNI calls, reupload bandwidth, heap/direct/native/GPU memory and
  fallback time against the exact current SWT master.

Do not claim complete device-loss recovery, an automatic retry service,
cross-platform native compositor support or a performance improvement
from a compiled header alone.

## Sources

- DirectComposition device state: https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositiondevice-checkdevicestate
- D3D11 lost-device recovery: https://learn.microsoft.com/en-us/windows/uwp/gaming/handling-device-lost-scenarios
- Device removed reason: https://learn.microsoft.com/en-us/windows/win32/api/d3d11/nf-d3d11-id3d11device-getdeviceremovedreason
- DirectComposition commit semantics: https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositiondevice-commit
