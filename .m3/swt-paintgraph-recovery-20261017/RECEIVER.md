<!-- Copyright (c) 2026 Hitesh Soliwal (Synexia). -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# ViewportPaintGraph recovery — current-source successor, 2026-10-17

This successor receives the previously committed atomic-growth implementation from
[SWT #190](https://github.com/hsoliwal/eclipse.platform.swt/pull/190) into the
current viewport lineage. History inspection found that #190 had merged into its
original owner branch, while all four affected paths on master
`678da68f34007af0aab0689aa72bd85fb44b800e` still matched their preimages.

The existing graph remains responsible for parent-owned node/edge arrays and dirty
state. Its seven node columns and two dirty/pending word arrays are reserved
before any replacement is published; nodeCount advances after the reserve.
The edge columns and their sentinels are likewise staged before publication.
This retains the predecessor #185 live dirty traversal and callback semantics.

## Source and recipe lineage

Original canonical recipe:
[Synexia #11681](https://github.com/hsoliwal/com.synexia/pull/11681), exact
head `a7ac28f39218adad2d4b80d557d8de67a111ab72`.
Original SWT receiver head:
`70e450aeb429587e31ea452dd41ab422db012cbc`.

The new canonical crate is
`synexia-openrewrite-recipes/recipe-crates/m3-swt-paintgraph-recovery-20261017`,
named recipe `com.synexia.swt.PaintGraphRecovery20261017`.
It reuses Synexia's existing source-sealed Maven/OpenRewrite engine, with serial,
path-allowlisted source admission and before/after hashes. The canonical PR
records actual SDK execution, fixed-point and refusal evidence. The crate is a
new successor; its publication revision is distinct from the reused engine's
input revision.

| Source | Exact SHA-256 |
| --- | --- |
| Graph preimage | `fd00903d07343044836963f54cf34e98cf24b794d38cc8242fcddfdf72527d7c` |
| Graph postimage | `2a7a8ce1564bc78f9fda1d49aee3c5fa5352a7f77c11521e8f498d57fec4e837` |
| Recovered atomic-growth JUnit | `d0c4b0062ba2413bf6c59670474b9922ce16109440796a31bc1de2badc410069` |
| Suite preimage after coordinate-visibility repair | `a50d54c3ec4d2174361b392f4011870ff1522a1ac794c8287d2d8d1475604af4` |
| Suite postimage with added growth registration | `486e76aa4145f86b342841c9eded7feb2a878b099b60d67db1a0b7a06fbef597` |

The graph and new JUnit source match the original #190 output byte-for-byte.
The suite preserves both tests added by the preceding Tree coordinate-visibility
repair and adds the atomic-growth test. The original
`.m3/swt-paintgraph-atomic-growth-20261015/RECEIVER.md` is retained byte-for-byte
as historical evidence; its older NOT_RUN statements describe its original run.

## Fresh executed evidence

These results were executed on the exact candidate sources on 2026-10-17:

- Strict Java 21 compilation with `--release 21 -proc:none -Xlint:all -Werror`,
  using the actual `ViewportRuntime`, `ViewportLayerState` and
  `ViewportAffineTransform` production sources. No SWT or JUnit stand-ins.
- Public/package descriptor comparison with `javap -package -s`: identical.
- The reused independent 1,700-node/2,000-edge contract probe: 298 callback nodes
  and identical original/candidate SHA-256
  `168c389ff895675f9d92cbd885e54d2750d2e0a962eebccb8514d20e4cce3817`.
- Real constrained JVM allocation failures under `-Xmx48m`: the original
  published 3 node-column replacements and 1 edge-column replacement before
  OutOfMemoryError. The candidate published zero replacements in both tests,
  then successfully retried normal node/dependency/dirty operations.
- Real JUnit engine: **12 tests found, 12 passed, zero skipped** — the four
  recovered growth tests plus eight existing live dirty-traversal tests.
- Candidate graph and tests compiled against the complete 546-source GTK SWT
  baseline output; the same real JUnit selection passed 12/12.

The canonical verification directory retains the original
`SwtPaintGraphGrowthProof.java`, a reproducible `run-real-owner-proof.py`,
the proof driver's SHA-256 in `result.json`, executed argument arrays,
stdout/stderr logs and JUnit XML. It accepts an explicit before root, generated
after root, Java home, real JUnit console jar and output directory, and refuses
unexpected graph bytes.

## Scope

The defect and correction concern shared Java array publication. The executed
tests establish source behavior, actual bounded-heap failure recovery and real
JUnit discovery for this owner. Native Win32/Cocoa UI parity, whole-reactor Tycho
tests, viewport screen capture and end-to-end CPU/heap/RSS performance are
separate acceptance gates. These helper results do not qualify the outstanding
Tree/Table/List checkpoint and rendering-mode convergence.
