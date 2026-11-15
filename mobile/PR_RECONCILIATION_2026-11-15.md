# Android/iOS SWT pull-request reconciliation — 2026-11-15

## Scope and immutable snapshots

This is a **read-only convergence gate**, not a declaration of source equivalence or mobile release qualification.

- Pending, unmerged SWT proposal: [SWT #362](https://github.com/hsoliwal/eclipse.platform.swt/pull/362), head `3501dca71e25b88f6464cb6855a74c0fe5edbb0a`.
- Selected baseline: SWT `master` `2d626848385bb14a991cc749dfa293db7e35e26c` (after merged [#369](https://github.com/hsoliwal/eclipse.platform.swt/pull/369) and source-pin correction [#372](https://github.com/hsoliwal/eclipse.platform.swt/pull/372)).
- Paired Nebula releases: [#396](https://github.com/hsoliwal/nebula/pull/396) merged; [#403](https://github.com/hsoliwal/nebula/pull/403) closed without merge; [#452](https://github.com/hsoliwal/nebula/pull/452) open as a release verification change.
- GitHub commit comparison for SWT #362 against its recorded base reports one candidate commit, 234 baseline commits not in the candidate, and divergent histories. This count is **not** an assertion of missing behavior.

## Non-negotiable invariants

1. Do not overwrite a currently delivered SWT mobile owner with the corresponding #362 owner, or merge #362 as-is. There are **17** same-path files, and all 17 have different Git blob IDs at the pinned snapshots. File identity and semantic equivalence are unproven.
2. No changes to public SWT constructors, methods, event ordering, JNI signatures, native resources, or desktop targets without a separately reviewed contract and test.
3. Preserve both histories. Candidate-only files remain evidence to inspect, **not** automatically accepted implementation. In particular, `m3/mobile/**`, `tools/mobile/**`, and `tests/org.eclipse.swt.tests/**` from #362 are a separate build/test closure; their dependencies and imports must be validated against the selected baseline.
4. Treat Java compilation, APK packaging, Android instrumentation, Apple native compilation, simulator/device execution, and JVM embedding as different gates. Do not substitute one for another.
5. Use SHA-1 Git blob identity for each path (which includes raw bytes and size). Do not infer equivalence from filenames, similarly named methods, or byte counts.

## Verified architectural divergence

| Component | #362 candidate | Selected merged baseline | Consequence |
| --- | --- | --- | --- |
| `MobilePlatform` | Static binding plus `submit(MobileFrame)` and frame/text dispatch abstract methods | Platform-owned Graphics/native-peer API and lifecycle support | Different interface; port atomic capabilities only after an explicit contract adapter decision |
| Android `AndroidPlatform` | View-wide frame rendering and native input dispatch | Native control-peer lifecycle, per-control images, text editor and PixelCopy support | Replacing baseline would regress delivered owner semantics |
| iOS `OS` JNI class | `create/submit/measureText` frame bridge | Control/image creation, presentation, text/clipboard/accessibility JNI bridge | Different native ABI, cannot overwrite or mix headers |
| `Display`, `Control`, `GC`, graphics resources | Candidate-specific event-loop, frame and rendering state | Current peer ownership, thread/event and GC/image resource model | Method names and signatures differ; compare behavior test-by-test |
| `Composite`, `Widget`, `Shell` | Alternative layout/event shell surface | Current hierarchy, event, layout and native control lifecycle | Preserve current behavior; no class-level replacement |

## Evidence inventory and deterministic gate

[`pending-pr-blobs.tsv`](pending-pr-blobs.tsv) records exactly the **17** verified overlapping paths and the Git blob IDs returned by GitHub from each immutable snapshot. [`tools/mobile/reconcile_pending_pr.py`](../tools/mobile/reconcile_pending_pr.py) operates on two **existing local worktrees**; it refuses path traversal, missing inputs, unexpected content drift and duplicate paths. It creates a deterministic JSON report with both file hashes and `different`/`identical` identity classification. A passed report proves only that the supplied local bytes match this manifest; it does **not** certify semantic parity or merge readiness.

```sh
python3 tools/mobile/reconcile_pending_pr.py \
  --candidate /absolute/path/to/swt-362 \
  --baseline /absolute/path/to/swt-master-pinned \
  --manifest mobile/pending-pr-blobs.tsv \
  --report /absolute/path/to/reconciliation-report.json
```

The two worktrees must be checked out at the exact commits above. Check the report into an evidence record only after it has passed and the source roots have been independently verified; do not place the report over a different baseline without updating the manifest through another review.

## Candidate-only atoms to evaluate (not yet transferred)

- Android build/instrumentation driver and API-35 test app under `m3/mobile/android/**`: compare to current SWT Maven/Gradle and Nebula original-widget consumers, and accept only nonduplicated checks.
- Java test suite `MobileDisplayTest` in `tests/org.eclipse.swt.tests/mobile/**`: map every assertion to current `MobileCoreContractJUnitTest`, SWT source-owner tests and native contract coverage. Preserve missing cases in the **current** test harness, not its abandoned runtime architecture.
- CoreGraphics/CoreText simulator test `swt_mobile_render_test.m`: checks clipping, elliptical arcs, text and modifier input against a different `swt_mobile.m` renderer. Do not compile unchanged against `SWTUIKit.m` without a deliberate adapter and ABI review.
- `tools/mobile/verify-java.py` and `verify-ios.sh`: compare actual JDK 21, `javac -h`, Xcode SDK and simulator assertion gates to the current `mobile/pom.xml`, `mobile/ios/build.sh` and GitHub Actions workflows.

## Release acceptance holds

- Existing primary SWT source remains selected. Do not switch mobile source manifests or platform JNI bindings on the basis of this audit.
- Full Android instrumentation on a functioning API-35 device/emulator must produce a real terminal result and retained logs/screenshots; #362 reports an infrastructure crash before instrumentation ran.
- iOS requires Apple native compilation and simulator/device execution. The current source explicitly does not supply a verified embedded JavaVM or signed app; standalone Java bytecode compilation is not a device pass.
- Nebula widget preview/build checks are separate from full Nebula Grid and original widget API parity. The release workflow in Nebula #452 must also expose previously skipped test failures before any release claim.

## Execution record

This reconciliation was derived from read-only GitHub PR metadata, changed-file inventories and full GitHub file fetches on 2026-11-15. Each of the 17 paired blobs was inspected for identity and selected declarations. **No production implementation was modified, no PR was merged, and no native device/simulator test was executed by this reconciliation.**
