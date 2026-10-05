# Current SWT source coverage

Source baseline: master `6a59025b0adc1f3e3aa93e5dc397346b250e851e`, including
the tested GTK Tree frontier improvement from PR #74. Canonical recipe owner:
[Synexia PR #9153](https://github.com/hsoliwal/com.synexia/pull/9153),
`native-packets-20261005` Maven crate.

| Delivered source set | Files | Atoms | Verification |
| --- | ---: | ---: | --- |
| Java, including tracked M3 tooling | 2,471 | 371,604 behavioral atoms plus 2,471 compilation roots | Every original canonical packet restored exactly |
| C / headers / C++ / Objective-C++ | 124 | 60,054 lexical atoms | Every byte restored independently from Base64 payloads |

`java-files.tsv` is the current complete Java manifest. `statement-atoms.tsv.gz`
reuses exact source payloads through sealed ranges; the canonical `RestoreAtoms`
runner reconstructs each original JSON packet and verifies its SHA-256. Eleven
changed/new files were freshly processed through OpenRewrite; their payload
packets are in `java-atoms.json.gz` and their receipts in
`java-supplement-files.tsv`. Earlier packet receipts remain intact as history.

Native payload packets and their pattern catalogue live in
`../native-source-distillation-20261005/`. UTF-8 and UTF-16 BOMs, original offsets,
raw strings, continued comments, directives and line endings are retained. Kind
and nesting depth are lexical classifications, not native type or macro semantics.

`coverage.json` verifies exact tracked Java/native file coverage with no missing
source. It also verifies all 412 original PNG hashes and records the two later
GTK stress screenshots separately. The earlier corrupt/truncated image fixtures
remain byte exact.

The canonical proof run passed 15 tests, 32 C/C++ compile/run pairs, seven sealed
Java recipe targets and seven source-drift refusals. The full native OpenRewrite
pass generated 127 exact receipt/packet outputs from 124 native sources and reached
a fixed point. The SWT workflow/documentation recipe has two sealed targets and
two drift refusals. Local execution of the new CI compile/check commands passed.

This delivery changes SWT receipts and the native receipt workflow, not SWT widget
or JNI source. Runtime source hashes match the previously qualified PR #74 tree:
its 4,493-test GTK reactor (20 existing skips) and 11 focused viewport/screenshot
tests are reused evidence, not a new reactor run. The current work does not claim
new Windows/macOS native execution, canonical full-reactor or strict repository
promotion, or repository-owned offline dependency closure.

## Remaining measured runtime criterion

Bounded GTK Tree native residency is **not satisfied**. The live store still
requires a native prefix for distant items. A separate fresh-JVM probe shows
that attaching even the dormant sparse logical model to GtkTreeView with 500,000
roots increases process RSS by 30,760 KiB (single sample; not a benchmark claim).
The GTK view itself retains linear row storage. Activating that model alone cannot
meet the bounded-residency invariant. The probe and exact results are retained in
the canonical crate; no unqualified backend replacement is activated here.
