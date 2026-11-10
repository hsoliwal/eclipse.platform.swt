<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# SWT scrollbar visibility callback lifetime

GTK `ScrollBar.setVisible` dispatches application Show/Hide callbacks before sending its owner's Resize event. Those callbacks may dispose the scrollbar, its owning control, or both. The existing method then dereferences the scrollbar's cleared `parent` field and throws `NullPointerException`. This packet keeps a local reference to the original owner and checks the scrollbar and owner independently after callbacks. A surviving owner still receives Resize when only its scrollbar was disposed; a disposed owner receives no further Resize.

The native policy calculation, unchanged-policy no-op, synchronous event ordering, nested visibility changes, selection state and listener-exception propagation remain in the original owners. The correction adds no widget field, native handle, listener registry, runtime service, public API or replacement control.

## Source custody and stacking

The four targets are the original GTK `ScrollBar.java`, a focused regression class, its additive `AllWidgetTests` registration and a receiving receipt. The suite retains both coordinate-visibility registrations and the recovered atomic paint-graph registration from the preceding SWT changes.

`crate.json` declares the accessible remote source revision `bff9c98564a575781d9a19f61dc5561c1d13ce2a`, the head of SWT graph recovery PR #220. Local native qualification used `b42becc14a4522d5342649cb527bd5e70aa7c86b`. These complete trees differ. The exact two existing receiving files have equal Git blobs, and both new paths are absent at the immutable remote commit; that scoped source equivalence was independently checked through GitHub. No whole-tree equality is inferred.

The unchanged GTK owner's SHA-256 is `966855c88fbe0fa13c25b5df89ec62ddbead1850bc94ef935d27f9d2c83f15ca`; the corrected owner's SHA-256 is `b2cc90ce5b7f3f706a758099eacdca8199cae5ac53f7b44cdb9beb779aa09966`. All four exact preimages/postimages and source identities are sealed in the manifests and `crate.json`.

The canonical packet stacks on Synexia PR #12108, commit `a14a26e92fb65d0fbcd7f71e7ae1653222ccc439`. It reuses the existing engines, five-method SDK harness and receiver without changing their code. Their original source custody remains `892e3f6c51d78bc0948e8a8cf736055b4c70eafa`. Earlier packets and historical references remain intact.

## Executed proof

The real GTK3/X11 negative control executes twelve focused JUnit tests: eight fail with the original null-parent defect, four controls pass, and none skip. The candidate passes all twelve. The cases cover owner disposal, scrollbar-only disposal, nested visibility and Resize disposal, display-filter disposal, live nested event order, ordinary idempotence and listener exceptions.

All 546 current GTK Java sources compile to supply the real dependencies. Before/after owner compilation uses Java 21 and retains the same nine pre-existing warnings with no new diagnostic. The regression test compiles with `-Werror`, and declared ScrollBar descriptors are identical. This is actual native SWT execution under an isolated Xvfb display, with source, dependency, driver, command and process observations retained under `evidence/target`.

`verification/run-native-callback-proof.py` is the reusable source-bound native driver, SHA-256 `99a47c78b1f256acb5a5d99fd24129c941955cd2f67f8bd3fb1d0bea90738b33`. `candidate-native-complete` retains the before/after qualification. Final received-source evidence is retained separately so recipe output and candidate observations remain distinguishable.

The canonical SDK qualification passes all five tests with zero failures, errors or skips. It executes the named Java/text recipe, checks exact Java lossless-tree output, source order invariance, mixed pre/postimages, fixed point, untouched decoys and refusal of missing, changed, duplicate, untyped or post-scan-mutated inputs. `evidence/sdk` retains the actual report and full source/input/output binding.

## Reproduce

Use Java 21 and the declared Maven dependencies. From the canonical crate:

```sh
mvn -B -o -ntp verify
python3 receive.py --target /absolute/path/to/eclipse.platform.swt --check
python3 receive.py --target /absolute/path/to/eclipse.platform.swt
python3 receive.py --target /absolute/path/to/eclipse.platform.swt --check
```

The receiver admits every exact target before serial writes and materializes only bound SDK Results. It refuses unrelated source drift. A completed target is a zero-change fixed point. In an exported authoring mirror, add `-Drecipe.owner.root="$PWD/sdk-owner"` to Maven; the receiver detects that local owner directory.

Read the native driver's `--help` for the real source roots, compiled GTK dependency directory, Java home, JUnit console, native library directory and externally supplied display. Native proof is separate from SDK recipe qualification. No compiled product or Maven build output belongs in the source handoff.

## Rights and remaining gates

Existing Eclipse source payloads retain their exact original notices and licences. Recipe, driver and metadata are Apache-2.0; the licence texts and scoped notice travel with the authoring mirror. Nothing introduces a Synexia product runtime dependency.

The executed native lane is GTK3/X11. GTK4, Win32, Cocoa, full Maven/Tycho reactor completion, all-widget viewport acceptance and controlled CPU/memory/pixel-performance budgets remain outside this proof. The separate Nebula startup-scroll correction must satisfy its own qualification; this packet can be reviewed and received independently.
