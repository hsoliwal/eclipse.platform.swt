<!-- SPDX-License-Identifier: EPL-2.0 -->
# UI donor row geometry — 2026-11-16

The original SWT `VirtualRowExtents` owner gains one-pass Fenwick lookup
and an opt-in asymmetric pixel cache, without replacing native widget
ownership, original public APIs, event flow or existing storage arrays.

Test-first independent Java21 probe: 2,431,868 small-model checks +
200,000 large-sparse checks passed. One diagnostic 100,000-query sample
was 19.6 ms (baseline) vs 10.7 ms (candidate); **not** a native SWT
application benchmark. The CI contract compiles the actual Java21 owner
sources and compares a dense oracle. Native GUI and mobile acceptance
remain independent release gates.

Concept donors only, no third-party source copied; see
`docs/m3/ui-donor-capability-map-20261116.md`.
