<!-- SPDX-License-Identifier: Apache-2.0 -->
# Viewport resource regression gate

`ViewportResourceRegressionTest` measures Java heap, whole-process resident memory (RSS)
and process CPU separately. Counting Java TreeItem facades cannot establish native-memory bounds.
The dedicated GTK3/X11 CI job enables `-Dswt.viewport.resourceRegression=true` and requires
all five tests to execute without skips on JDK 21 and 25. Other platforms do not silently inherit
Linux evidence.

## Frozen workload and budgets

Each positive scenario runs in three fresh JVMs with `-Xms64m -Xmx256m -XX:+UseSerialGC`.
Class loading and measurement-provider initialization precede the sample. Retained heap is
sampled after full GC; GC is outside the CPU interval. RSS includes native GTK/JNI memory,
committed heap, JIT code and other process pages; it is not an allocation counter. Negative
deltas remain visible in the raw before/after values and count as zero growth for the budget.

| Workload | RSS growth, each trial | Retained heap growth, each trial | Median process CPU |
| --- | ---: | ---: | ---: |
| Cold virtual root count 64 -> 1,000,000 | 24 MiB | 8 MiB | 1 second |
| 96 painted scrolls alternating roots 32 and 96, after 16 warm steps | 24 MiB | 8 MiB | 8 seconds |
| 96 expand/collapse cycles with 1,000,000 logical children, after 16 warm cycles | 24 MiB | 8 MiB | 8 seconds |

The median of the three maximum per-step CPU samples must be at most 150 ms. The child
wall-clock timeout is 60 seconds; elapsed time is reported but never substituted for CPU.
Each measured interactive step must receive an actual paint event and reach its requested top
item within two seconds. Draining an empty event queue alone does not settle GTK frame-clock work.
The tests also preserve item identity, text/check state, logical counts, scroll position and
bounded SetData callbacks. They check native resident row counts in addition to process memory.

Two executed negative controls establish that the budget assertions detect resource growth:
a touched, retained 64 MiB direct buffer must fail the memory validator while Java heap stays
below 8 MiB growth; two seconds of additional process CPU must fail the one-second CPU validator.
Reports and child logs are archived under `target/viewport-resources/`.

## Scope and unresolved work

This gate qualifies cold logical count growth and repeated use of the visited near viewport.
It does **not** establish constant memory for arbitrary distant access: the current default
GTK3 frontier can build a native prefix when `getItem(100000)` is requested. The standalone
`far-diagnostic` scenario records that cost with an explicitly unqualified scope. It is not
counted as a passing bounded-memory test. `logical-cold` is a separate opt-in model diagnostic.
Neither diagnostic changes the default model.

These bounded workloads do not prove leak freedom, universal latency, screenshot parity or
cross-platform correctness. Existing screenshot, widget, topology, API and platform gates remain
required. A changed budget or corpus is a new experiment with preserved previous receipts.

## Reproduce

Build/install the SWT reactor and its GTK3 natives using the existing build instructions. Then,
from `tests/org.eclipse.swt.tests` with JDK 21 or the CI JDK:

```sh
SWT_GTK4=0 GDK_BACKEND=x11 xvfb-run -a mvn -B \
  -Dtest=ViewportResourceRegressionTest -Dswt.viewport.resourceRegression=true \
  -DforkCount=1 -DfailIfNoTests=true -DskipNativeTests=true integration-test
```

The source recipe and exact pre/postimage seals live in Synexia's
`synexia-openrewrite-recipes/recipe-crates/viewport-resource-gates-20261006`.
The newly authored resource test, this document and Synexia recipe tooling are Apache-2.0.
Existing SWT files retain their upstream notices; running a recipe does not change their license.
See `docs/licenses/synexia-viewport-Apache-2.0.txt` for the license and
`docs/licenses/synexia-viewport-NOTICE.txt` for the exact scope.
