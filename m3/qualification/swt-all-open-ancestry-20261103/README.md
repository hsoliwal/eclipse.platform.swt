<!-- SPDX-License-Identifier: EPL-2.0 -->
# Exact ancestry retention of 26 inventoried open SWT PRs

Date: 2026-11-03. **SOURCE HISTORY ONLY / REVIEW DRAFT / HARD HOLD.**
This contract is committed BEFORE the new two-parent merge objects. It does **not** authorize an `eclipse.platform.swt/master` change or claim that divergent SWT live source, JavaSE baseline, JNI/native GUI behavior, APIs or runtime tests are equivalent.

## Exact source inventory and absence check

The live GitHub REST open-PR page for `hsoliwal/eclipse.platform.swt` returned **26 open PRs** at inventory time (none paginated off the first 100-result page). Receiving [PR #316](https://github.com/hsoliwal/eclipse.platform.swt/pull/316) exact head `19b9442840c9c072aa17a752af3a2b883104533a`, based on protected master `108fe6f117a8004fff3846bff8322554040b3dd8`. Its received source retains the original #307 SWT source and #315 archival history, including the exact 746-blob passive archive. Before this step, GitHub's actual ancestry comparisons of each other head showed **23 already ancestors** (behind_by=0), and precisely **two distinct missing ancestral heads**, #288 and #273:

- #288 exact `a6771ad91ae4c33a49fe9d18c765b722ecb3d982`: `compare(#288, #316)` reports **12 commits behind** and its full file differences are too large for the first 300-entry GitHub compare response. It contains earlier different live widget source.
- #273 exact `3ff176c23747afcacca2395fe9a54737a6b6201c`: `compare(#273, #316)` reports **3 commits behind**; `compare(#273, #288)` is **diverged**, so neither history is a substitute for the other. Both need explicit independent parents to preserve all original commit ancestry.
- The other original 23 donor heads must stay ancestors. Original donor PR heads and protected master are immutable for this task.

| Original open PR | Frozen head SHA | Ancestry before this step |
| --- | --- | --- |
| [#316](https://github.com/hsoliwal/eclipse.platform.swt/pull/316) | `19b9442840c9c072aa17a752af3a2b883104533a` | Ancestor of #316 per GitHub compare |
| [#315](https://github.com/hsoliwal/eclipse.platform.swt/pull/315) | `1e8c6e5066eff44336c52aa35db483998c624177` | Ancestor of #316 per GitHub compare |
| [#307](https://github.com/hsoliwal/eclipse.platform.swt/pull/307) | `6820a3b978170b0c42ac32f92054e5542be84b80` | Ancestor of #316 per GitHub compare |
| [#306](https://github.com/hsoliwal/eclipse.platform.swt/pull/306) | `ebc8ba448107fe9f5124d22f56074ccabfe52a09` | Ancestor of #316 per GitHub compare |
| [#305](https://github.com/hsoliwal/eclipse.platform.swt/pull/305) | `8d8526180e03469b0ec659bfa6164cd6f13cee0e` | Ancestor of #316 per GitHub compare |
| [#303](https://github.com/hsoliwal/eclipse.platform.swt/pull/303) | `db17c70d0e93c6dc32935a10148e95f0cb010e2d` | Ancestor of #316 per GitHub compare |
| [#301](https://github.com/hsoliwal/eclipse.platform.swt/pull/301) | `438667631a329d701d652d8f456373314e82b3d0` | Ancestor of #316 per GitHub compare |
| [#300](https://github.com/hsoliwal/eclipse.platform.swt/pull/300) | `2efbc2897c7613d23920a0ce5c99030835f27f19` | Ancestor of #316 per GitHub compare |
| [#299](https://github.com/hsoliwal/eclipse.platform.swt/pull/299) | `2b243513fee15ac9dc5317dec79a6c5a4cff9d44` | Ancestor of #316 per GitHub compare |
| [#298](https://github.com/hsoliwal/eclipse.platform.swt/pull/298) | `1de7f5777739b45a953ffdaf4fbce5f8d802524b` | Ancestor of #316 per GitHub compare |
| [#297](https://github.com/hsoliwal/eclipse.platform.swt/pull/297) | `54e21d145db8b7ddde31c8fd20091f30686fe9be` | Ancestor of #316 per GitHub compare |
| [#296](https://github.com/hsoliwal/eclipse.platform.swt/pull/296) | `48e95f929ba990504789b6d15438b659d1612b03` | Ancestor of #316 per GitHub compare |
| [#294](https://github.com/hsoliwal/eclipse.platform.swt/pull/294) | `2454ef4bb7abdf97f74c0c0cddd01cd721f38b84` | Ancestor of #316 per GitHub compare |
| [#293](https://github.com/hsoliwal/eclipse.platform.swt/pull/293) | `b358dd7d8d7dffe54ba6e6a764f49bdb139ef386` | Ancestor of #316 per GitHub compare |
| [#292](https://github.com/hsoliwal/eclipse.platform.swt/pull/292) | `f25a2a9883b167145c34396c8f2ee27e547deda5` | Ancestor of #316 per GitHub compare |
| [#291](https://github.com/hsoliwal/eclipse.platform.swt/pull/291) | `9cc5250c973d8114af2594213c05fd0acd777c58` | Ancestor of #316 per GitHub compare |
| [#290](https://github.com/hsoliwal/eclipse.platform.swt/pull/290) | `5bd5022f0168536b271b7e450a11c57342b59dc0` | Ancestor of #316 per GitHub compare |
| [#289](https://github.com/hsoliwal/eclipse.platform.swt/pull/289) | `44638fa4e6ec98b12d1dd6e5d357f8a92c009f87` | Ancestor of #316 per GitHub compare |
| [#288](https://github.com/hsoliwal/eclipse.platform.swt/pull/288) | `a6771ad91ae4c33a49fe9d18c765b722ecb3d982` | Not yet ancestor of #316; prospective history-only admission |
| [#287](https://github.com/hsoliwal/eclipse.platform.swt/pull/287) | `924c5baae2feacc5ad4b3c997526624f3f54021f` | Ancestor of #316 per GitHub compare |
| [#284](https://github.com/hsoliwal/eclipse.platform.swt/pull/284) | `fa09710a33d468c349ead318b047c64787bec818` | Ancestor of #316 per GitHub compare |
| [#283](https://github.com/hsoliwal/eclipse.platform.swt/pull/283) | `b59c44414939be6e2952797e2ab926a20f262044` | Ancestor of #316 per GitHub compare |
| [#277](https://github.com/hsoliwal/eclipse.platform.swt/pull/277) | `326454941935ac617c0b00a4ff51d2cee88c7850` | Ancestor of #316 per GitHub compare |
| [#274](https://github.com/hsoliwal/eclipse.platform.swt/pull/274) | `578bdc98997f73eac3d1a09439e063b91b5a9883` | Ancestor of #316 per GitHub compare |
| [#273](https://github.com/hsoliwal/eclipse.platform.swt/pull/273) | `3ff176c23747afcacca2395fe9a54737a6b6201c` | Not yet ancestor of #316; prospective history-only admission |
| [#27](https://github.com/hsoliwal/eclipse.platform.swt/pull/27) | `7a1014279ac56dd1728decb3cf893133c53fd657` | Ancestor of #316 per GitHub compare |

## Exact proposed serial source-history integration

1. Create a new private integration branch `m3/swt-all-open-ancestry-20261103` at the #316 exact receiving head; publish this README **first**, then freeze its real Git tree.
2. **#288:** create a real two-parent Git commit, first parent the source/documentation-bearing receiver and second parent **exact #288 head**, using the first parent's source tree **unchanged**, then non-force expected-head ref update and Git object/readback verification. This is an **intentional history-only merge**: it retains #288 original Git objects and its ability to recover past versions without installing #288 live widget classes over newer #316 ones.
3. **#273:** only after checking the first merge's source tree unchanged, create a second two-parent Git commit with first parent the result and second parent **exact #273 head**, again using the receiving source tree **unchanged**, and non-force expected-head ref update. This preserves the additional three historically missing commits without rewriting any Java, C, SWT, GTK, Cocoa, Win32, native, test or bundle source file.
4. After each stage require GitHub `compare(immediate receiving parent, merge)` return **zero changed files**, zero behind, and verified ordered two-parent Git commit with **identical tree SHA**. Recheck all **26 original exact PR heads** after the second merge; each must now be behind_by=0. Any absent head, moved SHA, diff or partial check is a hard stop.
5. Publish a separate `VERIFICATION.md` with actual Git merge SHAs, source tree SHAs, compare receipts, and explicit remaining semantic differences, and create one DRAFT review PR into `master`. Do not merge default branch, close donor PRs or imply semantic source equivalence.

## Critical semantic distinction and full release HOLD

PR #316 and original #288/#273 have **diverged, different live source trees**: e.g. `compare(#288,#316)` reports many additions, modifications, and removals, capped at 300 file records. **A second-parent ancestry merge with unchanged first-parent Git tree does not contain or activate source changes only present in #288/#273.** This is a historical source-preservation step, not a complete executable/product code superset. Their earlier source content remains retrievable from the actual preserved Git ancestry, while the newer #316 active tree is retained byte-for-byte. Before default-branch promotion, each divergent source leaf must be assessed by canonical preimage-pinned Maven/OpenRewrite recipes, recompilation, API/ABI contract checks, SWTBot/Tycho/JDK JavaSE-11/17 tests and GTK/Windows/Cocoa/RAP native runtime proofs.

LeetCode, HackerRank and GeeksforGeeks are problem-category/test-oracle references only; no challenge answer code is imported. Preserve original EPL-2.0/third-party notices and donor licenses. Git source-object identity and zero-diff ancestry are custody evidence, **not** native compiler/JNI/API or GUI runtime acceptance.

**No protected `master` update or full `hsoliwal/*` PR-fleet completion is claimed.**
