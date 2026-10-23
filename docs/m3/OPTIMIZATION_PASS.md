# Optimization pass (whole programme), started 2026-10-20

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "proceed with optimizing the whole". Every track runs this pass on its own branch, leaf by leaf.
Behaviour stays identical: the differential stays at zero mismatches and coverage at 99%.

## Order of work (stop a leaf when the numbers say no)

1. **Measure first.** Pick the track's hot operations (bulk insert, setItemCount, expand/collapse a 10^5 subtree, scroll a
   page, sort, filter keystroke, model delta of 1/100/10^4 rows, theme switch, paint of one page). Measure at 10^5 and 10^6
   rows: wall time (median of 5), allocations (JFR or -Xlog:gc + ThreadMXBean.getThreadAllocatedBytes), retained heap
   after GC, native calls (count of OS.* / JNI per operation). Same JVM flags before and after.
2. **One model, deltas end to end** (brief rule 18 and memory "one model"). Remove duplicate stores and per-item hash maps;
   replace full rebuilds/refresh-all with delta application (M3Patch); a delta outside the resident window touches only
   extents and the scrollbar.
3. **Hot paths allocation-free** (HP-LAW). Packed ints, bitsets, reusable arrays, interned labels; no boxing, no iterator
   or lambda allocation per row; objects only when a public API asks for one.
4. **Catalogue-driven algorithms** (brief rule 21). Name the catalogue entry in the commit: Fenwick/prefix sums,
   order-statistic tree, two-pointer diff, LIS for moves, bitset range ops, k-way merge for multi-column sort,
   superseding-generation debounce.
5. **Native work O(window).** Count native calls per operation; anything proportional to model size is a defect.

## Rules

- A leaf without a before/after number is not done. Record every leaf, including a measured "no", as one row in
  `docs/m3/<track>/OPTIMIZATION_LEDGER.tsv`:
  `leaf  operation  size  before  after  unit  method  catalogue_id  commit  evidence_path`.
- Keep a cutoff where a bulk path loses on small inputs; revert what the numbers reject before committing.
- Benchmarks are headless where possible; UI runs go through `ui_lock.sh` (tickets carry the owner pid since
  2026-10-19, dead head tickets are dropped automatically). Output under `S:/m3-scratch/<track>/`, never G: or D:.
- Commit and push after every leaf, with no trailers. JUL logging only. Java only (M3JDK may change JNI/HotSpot).
- Upstream issues from the GitHub harvest (`reference-github-pr-harvest`) that a leaf answers are cited by number.
