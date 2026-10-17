<!-- SPDX-License-Identifier: EPL-2.0 -->
# Original SWT ViewportPaintGraph — second-pass atomic backing growth

**DRAFT / NOT product-admitted.** Canonical source-changing recipe and tests:
[Synexia #11681](https://github.com/hsoliwal/com.synexia/pull/11681),
stacked on the existing original SWT paint-graph optimization
[Synexia #11607](https://github.com/hsoliwal/com.synexia/pull/11607) →
[SWT #185](https://github.com/hsoliwal/eclipse.platform.swt/pull/185).

## Exact source custody

- Before branch: `m3/original-viewport-paint-dirty-word-scan-20261015`
  at `1ff4acc817069c7e366bd2c929a3c89bcc72e118`.
- Existing `ViewportPaintGraph.java` before Git blob:
  `cf774d12e68c96ccd46cb4f63f66543474a14440`,
  SHA-256 `fd00903d07343044836963f54cf34e98cf24b794d38cc8242fcddfdf72527d7c`.
- Current staged growth source Git blob:
  `254a324952adf97e50bd49ee5646547524e3c68b`,
  SHA-256 `2a7a8ce1564bc78f9fda1d49aee3c5fa5352a7f77c11521e8f498d57fec4e837`.
- Original `AllWidgetTests.java` is extended by one class registration; no existing
  suite registrations removed. New dedicated EPL-2.0 test class is ABSENT-before.
  Product Java/test/suite Git blobs equal their canonical Synexia recipe templates.

## Actual original SWT behavior

- Original class remains the retained graph; no new widget/Canvas, data model,
  JNI symbol, renderer, GC/resource owner or public/package method signatures.
- The second-pass change stages all seven node columns and two parent-owned
  dirty/pending word arrays before publication. `nodeCount` advances only after
  all necessary capacity is available.
- Edge columns stage together and preserve `-1` sentinel initialization.
- No-growth appends allocate nothing additional. Sparse/dense hybrid dirty
  traversal, CPU flags, DAG ordering, SWT immediate paint and events retain
  first-pass behavior.

## Executed vs pending proof

**Executed, bounded CPU:** exact old/new graph source compiled under Java21
`-Xlint:all -Werror` against minimal unchanged external-type stubs;
`javap -package -s` equal; 1,700-node/2,000-edge behavior digest equal
`168c389ff895675f9d92cbd885e54d2750d2e0a962eebccb8514d20e4cce3817`;
the existing 13,000-case paint callback differential passes. At `-Xmx48m`,
baseline failure partially replaced **3 node columns / 1 edge column**;
staged candidate failure replaced **0** and retained usable graph backing.
Four regression JUnit method bodies passed with a *labelled offline API shim*.
Native C17 bitmap verification from the first PR remains unchanged and is
not a production JNI optimization.

**NOT_RUN / HOLD:** actual Maven/OpenRewrite SDK, native SWT/Tycho/JUnit
engine, Win32/GTK/Cocoa, SWT Display/GC screenshots, DPI, IME, accessibility,
Skija GPU behavior and CPU/heap/RSS measurements. This narrow candidate does
not qualify any of the 360 original SWT/Nebula widget catalogue entries.
