# Maya native composition telemetry — version 1

Status: **experimental diagnostic ABI**, not a frame-rate claim and not a
Windows GPU qualification. Existing owner:
`org.eclipse.swt.internal.maya.MayaComposition`,
`MayaComposition.cc` / `Composition`. No new widget or scene graph.

The preexisting SWT/Maya runtime uses a typed `jlong` native composition
handle, two native frame paths (legacy arrays and versioned packed
ByteBuffer), retained DirectComposition visuals, grouped layers, and
dirty-region image uploads. To evaluate future native/JNI optimizations
we need owner-local, observer-readable counters rather than conjecture.

## Design and precise ownership

`MayaCompositionTelemetry.h` implements a **16-slot primitive
`std::array<uint64_t,16>`** owned directly by the existing native
`Composition`. Mutations stay on SWT Display's established UI/natively
owned thread, consistent with the existing thread-local typed handle
registry. No object graph, allocation, atomic operation, extra JNI
round trip or GPU call occurs to update one counter. Counters saturate
at `Long.MAX_VALUE` and remain nonnegative to Java.

The sole additive native method is
`MayaComposition._nReadMetrics(long composition, long[] destination)`.
It writes exactly the first **16** output slots, requires a live typed
composition handle and enough array capacity, and returns 0 on success.
An invalid argument or unsupported native platform yields nonzero;
the optional `readMetrics(long)` convenience method returns null
on failure or a previously shipped Maya DLL without this export.
No existing JNI descriptor or export changes, and no existing rendering
path polls the metrics automatically.

### ABI v1 slot schema

| Slot | Java constant | Meaning |
| --- | --- | --- |
| 0 | `METRIC_VERSION` | Always 1 on successful snapshot |
| 1 | `METRIC_FRAME_ATTEMPTS` | Entered the shared native transaction for a valid composition, including failed shared preflights |
| 2 | `METRIC_FRAME_COMMITS` | **Successful** `IDCompositionDevice::Commit` calls from frame apply, not screen presentation |
| 3 | `METRIC_FRAME_NOOPS` | Valid successful frame that did not need COM Commit |
| 4 | `METRIC_Z_REBUILDS` | Committed frame whose native visible order required rebuilding |
| 5 | `METRIC_FULL_UPLOADS` | Successful full-surface `EndDraw` submissions |
| 6 | `METRIC_DIRTY_PATCHES` | Individual successful dirty-rectangle `EndDraw` submissions |
| 7 | `METRIC_UPLOAD_BYTES` | Sum of premultiplied 4-byte pixel payloads for those successful uploads (not PCIe/GPU transfers) |
| 8 | `METRIC_HEALTH_FAILURES` | Synchronous native device-health probes returning failure |
| 9 | `METRIC_LAYERS_CREATED` | Successfully registered native layers |
| 10 | `METRIC_LAYERS_RETIRED` | Previously registered native layers destroyed or disposed |
| 11 | `METRIC_FRAME_REJECTIONS` | Rejected/failed shared native transaction (includes failed Commit) |
| 12 | `METRIC_COMMIT_FAILURES` | Failed COM Commit attempt within a shared native frame |
| 13 | `METRIC_LIVE_LAYERS` | **Current** owner `Composition.layers.size()`, not cumulative |
| 14 | `METRIC_PENDING_SURFACE` | **Current** pending successful `EndDraw` requiring an eventual Commit (0 or 1) |
| 15 | `METRIC_HEALTH_PROBES` | Synchronous DComp device validity checks attempted |

**Bounds of observation:** JNI packet-validation failures rejected *before*
the original native transaction entry are not included in
`METRIC_FRAME_ATTEMPTS` or `METRIC_FRAME_REJECTIONS`. Creation/removal
COM calls are not included in `METRIC_FRAME_COMMITS`; that counter
covers native frame application only. Upload bytes record CPU-staged
successful `EndDraw` payload size, not actual GPU residency, compressed
bytes, DMA traffic, or bytes physically presented. `Commit` queues
DirectComposition commands; a separate successful
`WaitForCommitCompletion` is needed to establish display readiness.
Device-health probes may repeat the same persistent fault, so the
health-failure counter is not a count of distinct device-loss incidents.

Snapshots are only valid **for the lifetime of their composition**.
After GPU loss the existing SWT recovery disposes that owner and creates
a new composition with new, reset counters. Collect the final snapshot
before disposal when available; do not merge counters across instances
without recording each owner generation. No cross-thread telemetry
access is authorized.

## Example

```java
long[] before = MayaComposition.readMetrics(composition);
// Null means an old library, unsupported platform or invalid handle.
if (before != null && before[MayaComposition.METRIC_VERSION] == 1) {
    long queued = before[MayaComposition.METRIC_FRAME_COMMITS];
    long noops = before[MayaComposition.METRIC_FRAME_NOOPS];
    long dirtyBytes = before[MayaComposition.METRIC_UPLOAD_BYTES];
    // Use these as diagnostics, not an FPS or displayed-GPU-byte reading.
}
```

The API is an **internal Maya primitive**, not a new public SWT
application facade. Normal `LayeredViewport` calls are unchanged and
never request snapshots implicitly.

## Verification and acceptance gates

Executed locally on **the exact GitHub-committed source**:
- GCC and Clang C++17 `-Wall -Wextra -Werror -pedantic`
  with ASan/UBSan on `MayaCompositionTelemetryTest.cc`:
  100,000 deterministic simulated transaction events, counter
  monotonicity, error/no-op/Commit semantics and `Long.MAX_VALUE`
  saturation.
- GCC- and Clang-built JNI smoke shared libraries under Java 21
  `-Xcheck:jni`: 16 slots, version 1, output prefix, wrong length,
  deterministic event counts and legacy native library lacking export.
  The smoke uses a Java mirror of the production constants locally;
  CI compiles the **actual** `MayaComposition.java` owner.
- The committed C++ header/test and JNI stub/smoke Git blobs exactly
  match locally executed sources. The existing CI `maya-native-safety.yml`
  now runs them with the real Java source and Windows MSVC owner compile.

Committed but **not yet executed on actual Windows GPU hardware**:
- JUnit `Test_org_eclipse_swt_maya_Composition` tests successful
  full upload + pending content, first Commit, no-op frame, subsequent
  changed pixels with identical layer properties, invalid handle,
  prefix-only snapshot and layer removal.
- The optional test skips on an old DLL unless the release gate sets
  `-Dswt.maya.metrics.required=true`, which must fail if the new
  native symbol is absent. No skipped pixel test is a GPU pass.

Before packaging a new `maya.dll` require Windows JDK21/25 MSVC
full Maya/Skija link and exact `maya.def` symbol closure, new binary
version/hash, real `AllMayaGcTests` under `-Xcheck:jni`, GPU
device-loss/recreate, cross-Display token ownership, disposal/leaks,
dirty uploads, accessibility/IME and native-control Z order parity.
Compare p50/p95/p99 frame latency, JNI copies, Commit count, native
heap and measured GPU memory on 0/1/64/1,000 native layers. Do not
assert a speedup from counters alone.

## Relevant authoritative references

- <https://docs.oracle.com/en/java/javase/21/docs/specs/jni/functions.html>
- <https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositiondevice-commit>
- <https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositiondevice-checkdevicestate>
