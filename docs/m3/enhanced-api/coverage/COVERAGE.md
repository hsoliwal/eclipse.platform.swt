# Enhanced API coverage evidence

## 7 November 2026: focused headless recheck

[HEADLESS_20261107.json](HEADLESS_20261107.json) records the actual run against
source `62460082d7caa06b21c49da99abbab53f73b3ffc`: Java 21 compiled all 682 GTK
production sources and all 26 ordinary M3 test classes; JUnit Platform 1.14.4
executed the nine headless contract classes, **77/77 successful, no skips or
aborts**. Native display tests and the complete Tycho reactor were not executed.

JaCoCo 0.8.15 reports the following counters for the ten explicitly named
production source files in the receipt, including their nested classes. This is
a bounded headless measurement, not coverage of all enhanced APIs or all SWT.

| Measure | Covered | Total | Measured percentage |
| --- | ---: | ---: | ---: |
| Lines | 926 | 1,024 | 90.43% |
| Branches | 745 | 854 | 87.24% |

The source set includes M3Collation, M3ColumnFilter, M3ColumnSort, M3ItemRange,
M3Patch, M3RowExtents, M3TextMatch, M3TextSegments, M3TokenMatch and
M3TokenVocabulary. The unexecuted native install/widget paths remain uncovered.
These numbers do not meet the programme's 99% line/branch target, and no
historical run is substituted for those missing executions.

The reviewed PR #324 test source at
`b0b91b4d9ae468adef48ec6255a83acbd2e5df3b` contains 26 ordinary M3 contract
classes with 219 methods, selected by `AllWidgetTests`; those Java inputs
are unchanged at preparation head `494247dc23f4115abf5a3bb358c5f6eeb8831d21`.
The release supplement
also selects seven differential classes (13 methods) and RowPaintRetention
(4 methods), originally giving 34 classes / 236 methods. The current
unapplied supplement adds four RowExtents methods, giving 26 ordinary
classes / 223 methods and 34 total classes / 240 methods. SMART and AUTO alignment,
exact anchoring boundaries and sparse maximum row counts are the new
coverage targets. Any focused Java17 JUnit and JaCoCo results require separate
source/test/classfile-bound reports and do not qualify Java21/25, the default
suite or native backends. An independent model harness is not instrumentation. Independent official recording generation remains separate
from candidate replay. These source counts do
not revise the historical 77-test execution above. See
[the test guide](../../../../tests/org.eclipse.swt.tests/Readme.md) and
[release requirements](../../RELEASE.md).

## Reproducible gap audit and new regression tests

The source-hash-checked `tools/release/headless_coverage_audit.py` audits the
actual ten-file numerator/denominator pairs in `HEADLESS_20261107.json`.
It does not change those historical results or infer native/GUI coverage.
Run its refusal tests and read-only inventory from the same source tree:

```sh
python3 -m unittest discover -s tools/release -p test_headless_coverage_audit.py -v
python3 tools/release/headless_coverage_audit.py --root . \
  --receipt docs/m3/enhanced-api/coverage/HEADLESS_20261107.json \
  --candidate-sha "$(git rev-parse HEAD)" \
  --json-out /tmp/swt-headless-coverage-gaps.json
```

### Source and test-input hash verification

A read-only SHA-256 comparison against this branch's GitHub source was completed
on 2026-11-07 for unmodified preparation head
`494247dc23f4115abf5a3bb358c5f6eeb8831d21`, before the release supplement
repairs M3ItemRange. All **10/10 measured production source inputs** still match
the original focused receipt. Of its **9 recorded JUnit test source files**,
**7 match** and two have changed through this release-preparation branch:

| Recorded JUnit input | Recorded SHA-256 | Current SHA-256 |
| --- | --- | --- |
| `Test_M3ItemRange.java` | `80bbef121d897524cad974ea2093b6691ba5c999015e4747ee32bfcf745dc23e` | `a7ec3d89bec8e17085b3f67e49cc6c0f29315ce49903f7fccbf1e3197fc05027` |
| `Test_M3RowExtents.java` | `035b2a9f11c53b59f5c69d4efccea9e44d77dc6248ae943923df1451d3aec089` | `424438aba6ab642722b6ef765bafd386e0a700a1e78fa016f5b39060f1293dba` |

Those current hashes are a **snapshot of the inspected branch**, not persistent
expected values: recompute after later commits. The audit tool now checks
production-file hashes *and* every recorded test input and lists drift in its
output. Its historical `77/77` success and `90.43%/87.24%` coverage therefore
**do not apply to the changed test suite**. A fresh instrumented run against the
final candidate, and a new immutable execution receipt, are mandatory.

The report is **HOLD** regardless of its exit-0 read-only inventory mode.
`--release` fails closed on its acknowledged blockers. Measured per-source
branch deficits to meet a minimum 90% are:

| Class | Measured lines | Additional lines for 90% | Measured branches | Additional branches for 90% |
| --- | ---: | ---: | ---: | ---: |
| M3ColumnFilter | 251/321 (78.19%) | **38** | 182/235 (77.45%) | **30** |
| M3ColumnSort | 136/161 (84.47%) | **9** | 74/92 (80.43%) | **9** |
| M3ItemRange | 51/51 (100%) | 0 | 59/68 (86.76%) | **3** |
| M3RowExtents | 54/54 (100%) | 0 | 30/42 (71.43%) | **8** |

All other six measured source files already meet the 90% **branch** floor in
this historical headless run, but `M3ColumnFilter` and `M3ColumnSort` also
fall below the 90% **line** floor. The numbers above are arithmetic thresholds
under unchanged denominators, not predicted results of the new tests.

On this release-preparation branch, `Test_M3RowExtents` adds negative range
endpoint, zero-length edit, reset and reinsert cases, while `Test_M3ItemRange`
adds safe upper-int positive controls **and an explicit release-blocking
regression** for duplicate `Integer.MAX_VALUE` indices. The release repair
widens the adjacency bound in both run counting and emission while retaining
the regression unchanged. These additions and the repaired owner have
**not** been counted in the 77/77 execution receipt. Qualify the repair through
the authorized source-sealed recipe, then run the regression and passing
controls together and regenerate current source-pinned JaCoCo counters.
The historical receipt intentionally fails source-hash validation against
the repaired M3ItemRange; keep it as historical evidence, not current coverage.

The per-file gap workflow uploads exact source commit/tree and audit JSON,
but is **not** a substitute for Java21/JNI/platform tests or a current-head
whole-SWT coverage report.

## Historical campaign: original scope retained

The following record describes its original hidden-desktop campaign and inputs.
Its percentages must not be interpreted as the current candidate's qualification.
The probe explanation describes separately exercised paths; JaCoCo's measured
branch count remains 311/330 and is not rewritten to 330/330.

# Coverage of the enhanced API classes (JaCoCo 0.8.15)

Run: `Test_M3ItemRange`, `Test_M3ItemSelection`, `Test_M3Scrolling`, `Test_M3TextSegments`, `Test_M3TokenMatch` (55 tests) on the hidden
desktop, once with `-Dswt.widget.mode=viewport` and once with `-Dswt.widget.mode=original`; both 55/55; the two
execution files merged. Classes: the M3 API classes compiled from this branch, placed ahead of jar `ta20261017a` on
the class path. Per-class numbers: `jacoco-m3-api.csv`.

| measure | covered | total | percent |
|---|---|---|---|
| lines | 428 | 428 | 100.0% |
| branches | 311 | 330 | 94.2% |

All 19 missed branches are listed in `COVERAGE_GAPS.tsv`. Each is the true side of a one-condition
`if (...) SWT.error (...)` argument check. Every one is exercised by an `assertThrows` test, named in the TSV. JaCoCo
cannot record them: `SWT.error` always throws, so the probe at the end of the single-predecessor block never fires.
A two-condition check (`a || b`) gets its probe on the edge and is recorded, which is why `M3Mode.name`,
`M3Scrolling` alignment and `M3TextSegments` checks show as covered. With these artifacts counted as exercised,
every branch (330 of 330) is reached. The code keeps the SWT `SWT.error` idiom on purpose, for upstream style.
