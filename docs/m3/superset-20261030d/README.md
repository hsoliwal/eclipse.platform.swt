# Chronological, additive SWT superset landing — 2026-10-30d

This is a **draft integration candidate**, not a qualification certificate or a direct merge into `master`.
The first parent is the earlier #275 superset branch, not the superseded #273 alternative.
Each new two-parent commit retains the corresponding PR's head as its second parent.
Never replace a full catalogue or test suite with the most recent competing branch's version.

## Chronological source and disposition

| PR | Scope | Status in this branch |
| --- | --- | --- |
| #259 | Upstream SWT sync | **RETAINED, NOT LIVE** through #275; GTK4 List/Table event ordering remains unresolved |
| #271 | GC seam leaf 4 | Live through #275 |
| #272 | M3 feature switches | Live through #275 |
| #273 | Earlier superset round b | Superseded by #275; do not duplicate archival variants |
| #274 | GC seam leaf 5 | Live through #275 |
| #275 | Superset round c | First parent and baseline, not merged to master |
| #276 | Gallery / combination tests | 50 exact new paths from PR head; two-parent merge |
| #277 | Composite paint hook | 17 non-conflicting paths; two-parent merge |
| #278 | M3 row math | One catalogue row plus documentation/tests; two-parent merge |
| #279 | M3 diff apply | One catalogue row plus documentation/tests; first copy of `M3OfficialScene` |
| #280 | M3 scroll restore | One catalogue row plus documentation/tests; byte-identical helper reused |
| #281 | M3 proxy model | One catalogue row plus documentation/tests; byte-identical helper reused |
| #282 | M3 estimated heights | One catalogue row plus documentation; two-parent merge |
| #283 | M3 bulk updates | One catalogue row plus documentation/tests; **open original-virtual-Table defect retained** |
| #284 | GC fidelity leaf 6 | 41 paths, preserving #277 Composite paint hook via four exact hunks; append 13 fidelity test classes next to #277 CompositeCoverage; two-parent merge |

PRs #273 and #275 are alternative *superset landing PRs*, not independent functional features. This branch selects #275 as the authoritative baseline and does not replay #273's conflicting retained-variant archive.

## Verified integration facts

- Git compare to current `master` at staging: ahead by 62 commits; 225 changed paths, 165 added and **zero removed** (before this README).
- The six enhanced-API records each occur **once**, retaining each original PR's distinct qualification notes.
- The combined GC suite contains `CompositeCoverage` and the 13 fidelity cases.
- The combined win32 `Composite` retains `paintThroughM3` and `drawBackgroundThroughGC`.
- The merge avoided replacement of any pre-existing Gallery path; 50 paths were new.
- No GitHub Actions workflow runs were found for this new branch when checked. **No Java/JNI build or runtime suite was executed for this combined tree by this integration operation.**

## Promotion blockers (do not waive)

1. **Upstream #259 is retained, not applied live.** Reconcile GTK4 `DragSource`, `Display`, `List` and `Table`, preserving press selection, MouseDown, first click, double click, keyboard activation and drag/default-selection semantics. Apply canonical recipe plus GTK4 traces and native/LFS checks.
2. **#283 original-mode virtual Table**: fix and rerun the `TableStockItems.checkGrow` / `CDDS_SUBITEMPREPAINT` batched-burst AIOOBE regression, preserving stock contracts.
3. **#277 + #284 renderer fidelity**: qualify combined runtime after the two overlapping Java/suite files were composed. Check CBanner antialias curve, CTabFolder ink, color/gradient/clip/transform/GC-state restoration, pixel golden references, paint exceptions, child clipping, leakage, DPI, and UI-thread behavior. #284 documents RTL and external-GC limitations.
4. **Combined build gates**: source and native SWT JNI builds, win32/GTK/Cocoa source builds, Maven/Tycho reactor, all M3 GC and gallery/covering-array tests, differential-off/stock tests, native platform integration and API/ABI compatibility. Do not infer cross-platform tests from per-PR local reports.
5. **Recipe/provenance**: register any conflict-resolution pattern in the canonical Synexia recipe catalogue; preserve source ownership, donor attribution, reproducible input blobs and generated afterimages. No cherry-pick-by-recency or silent deletion.

## Merge policy

Advance from file-local additions to caller/callee and module closure only after contract proofs.
The first-parent order is #275 -> #276 -> #277 -> #278 -> #279 -> #280 -> #281 -> #282 -> #283 -> #284.
Keep this PR **draft** until the blockers are closed and the combined tree is actually qualified.
