<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# SWT paint-graph allocation recovery

This successor restores the previously committed failure-atomic `ViewportPaintGraph` reserve implementation from SWT #190. Current SWT master still contained its exact preimages even though GitHub recorded the old PR as merged. The new target change is stacked on the coordinate-visibility source commit `ca77c0272fb6c1ea12594ac00baca8fb9b50ce3d` and preserves both new suite registrations from that first change.

The graph allocates all replacement lanes into locals before publishing any lane. An allocation failure therefore leaves every original graph lane reachable and mutually consistent. A subsequent retry can proceed. The historical graph and atomic-growth test bytes are preserved; the suite is reconciled additively with current work. No existing branch or historical PR is rewritten.

## Exact source custody

The graph's old SHA-256 is `fd00903d07343044836963f54cf34e98cf24b794d38cc8242fcddfdf72527d7c`; its recovered SHA-256 is `2a7a8ce1564bc78f9fda1d49aee3c5fa5352a7f77c11521e8f498d57fec4e837`. SWT donor head is `70e450aeb429587e31ea452dd41ab422db012cbc`. `crate.json` and the Java/text manifests bind all five exact receiving paths, including both historical and new receiving receipts.

Synexia #11681 originally supplied this recipe family at `a7ac28f39218adad2d4b80d557d8de67a111ab72`. Its named crate and manifest are absent from the inspected current canonical tree. All fourteen files changed by that PR are retained byte for byte under `history/synexia-pr11681`, with original repository paths, Git blobs and SHA-256 identities in `SOURCE.json`. Their original claims remain historical. This new packet uses the currently present canonical recipe engines and SDK harness.

## Executed target proof

`verification/run-real-owner-proof.py` compiles the exact graph with its real production dependencies using Java 21, `-proc:none -Xlint:all -Werror`. It does not substitute SWT stubs. The retained independent finite contract observation covers 1,700 nodes and 2,000 edges; both versions produce SHA-256 `168c389ff895675f9d92cbd885e54d2750d2e0a962eebccb8514d20e4cce3817`. Declared `javap -package -s` descriptors are identical.

Real constrained-heap (`-Xmx48m`) negative controls make the original implementation publish three node lanes or one edge lane before `OutOfMemoryError`. The recovered implementation publishes zero lanes in both cases and then passes a retry. The actual JUnit run passes four atomic-growth tests and eight existing dirty-traversal tests. A separate compilation against the full 546-source current GTK baseline also passes these twelve tests. Commands, raw diagnostics, result hashes and the source-bound runner identity are retained under `evidence/target`.

These are finite contract and failure-recovery proofs. They do not establish all-widget viewport performance budgets, full SWT/Tycho reactor completion, Win32/Cocoa native behavior or all accessibility/IME/DPI acceptance.

## Recipe qualification and receiving

The final five-target packet runs the unchanged shared five-method SDK qualification suite. `evidence/sdk` is the final source-bound run; `evidence/preliminary-receipt-wording` preserves the earlier successful run before the new receipt's wording changed from “reviewed” to “committed.” The correction avoids implying a submitted human PR review. Only the final run can authorize the corrected receipt.

From this canonical crate, using Java 21 and an approved Maven cache:

```sh
mvn -B -o -ntp verify
python3 receive.py --target /absolute/path/to/eclipse.platform.swt --check
python3 receive.py --target /absolute/path/to/eclipse.platform.swt
python3 receive.py --target /absolute/path/to/eclipse.platform.swt --check
```

The first receiving checkout must contain the exact stacked source preimages. The SDK checks named YAML execution, exact Java lossless-tree output, fixed point, input-order invariance, mixed pre/postimages, untouched decoys and refusal of malformed or changed sources. The receiver validates the actual test report and every bound recipe/input/output hash before it admits all paths and writes serially.

The reused engines, SDK harness and receiver are pinned to canonical engine commit `892e3f6c51d78bc0948e8a8cf736055b4c70eafa`. The new containing canonical commit supplies custody for this new task packet. Recipe/proof source is Apache-2.0; each exact Eclipse or Synexia-origin payload retains its original source notice and licence.
