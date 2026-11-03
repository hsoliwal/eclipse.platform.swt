<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT source-history #315 / existing source superset #307 — actual merge receipt

Date: 2026-11-03. **Source/ancestry staging completed on a dedicated branch; no product compile, SWT runtime or protected master admission.**

## Actual Git SHA, archive and byte-identity proof

- Receiving frozen #307 head `6820a3b978170b0c42ac32f92054e5542be84b80`, Git tree `f2510d4e5808b6664b5c5e4566e8afa0ebf31c41`.
- Original independent #315 archive donor head `1e8c6e5066eff44336c52aa35db483998c624177`, tree `105cf8c09dcaab3217bacff02b64d8b2942ed73b`; the two lines diverge by hundreds of commits. The donor branch's **live SWT tree is not automatically substituted** for the receiving implementation.
- Documentation-first `README.md` committed at `23239f287533b303d88b314e7610d13512ca6973`; its Git tree `2e0895e6fb7cd61ebf4a2cfcd38c5553be146f68`.
- Original donor's `m3/qualification/history-source-recovery-20261102` exact Git tree `d84b53c285cddb4b05576921a065c1c4b92bb64a`. Recursive Git tree enumeration: **989 entries, 746 blob leaves**, zero truncated, all modes allowed. This is **742 original historical source blob objects + four index/receipt blobs**, stored inertly. The donor has this tree; the original receiving #307 tree does not.
- Created Git candidate tree `688238636d8461c94d1027d87e4368640e592be4` from documentation tree, importing *only that one exact archive subtree* at `m3/qualification/history-source-recovery-20261102`, mode `040000`. Root comparison: 29 entries before/after, only `m3` subtree changed; nested comparison: only `qualification` changed inside `m3`; inside `qualification`, **one absent-before archive tree added**, all three existing qualification entries remained Git-object-identical. Therefore all existing SWT source and resource Git tree entries were preserved exactly.
- Created a real two-parent Git merge commit **`e8c1ae2b106a489f36e6c59076193b059ab9d8fb`**, parents in order `[23239f287533b303d88b314e7610d13512ca6973, 1e8c6e5066eff44336c52aa35db483998c624177]`, resulting tree `688238636d8461c94d1027d87e4368640e592be4`. GitHub commit metadata was reread and matched the exact two parent SHAs and candidate tree.
- Advanced **only** `m3/additive-swt-pr315-history-wave-20261103` via a non-force expected-SHA CAS and verified the new remote ref `e8c1ae2b106a489f36e6c59076193b059ab9d8fb`. The original #307/#315 branches and protected `master` were untouched by this operation.

## Comparison constraints and refusal of misleading counts

- `compare(documentation parent, merge)`: 18 commits ahead/0 behind; first 300 returned rows all **added** historical archive files, none modified or removed.
- `compare(original #307 head, merge)`: 19 commits ahead/0 behind; first 300 returned rows all **added** historical archive files, none modified or removed.
- Those compare results are **capped at 300 rows** and cannot prove the entire number 746. The **native Git donor subtree SHA and complete 746-blob recursive tree census** supply the exhaustive archive evidence. After the merge this source candidate has 746 archive additions and one original merge-plan document, before this verification document.
- `compare(#315 donor head, merge)` reports 890 ahead, 0 behind; its first 300 rows include modified/deleted paths because #315 and #307 diverged in their **live code**. The exact archive tree and donor commit ancestry are preserved; **this is not a claim that the result is byte-equivalent to #315's live widget tree**. A full source semantic union needs separate per-file reconciliation and source-compiled proof; never silently call an ancestry merge complete product equivalence.
- No existing receiving live Java/SWT/native source path was edited by the **archive subtree operation**. This record does not certify unrelated previous #307 changes.

## Release and governance holds

Source archive blobs are passive historical evidence. Do not place them directly under live bundle Java paths or replace `TreeViewport`, `ListViewport`, or `RowRenderer`. The original JavaSE 11→17 baseline, plugin/OSGi manifests, Tycho Maven, SWTBot UI event ordering, Cairo/Win/Cocoa/RAP native resource lifetimes, public API and JNI/runtime compatibility must be checked independently.

Real Maven/OpenRewrite `Recipe.run` postimage/fixed-point/stale refusal, JDK baseline compiler, SWT native tests across platforms, build logs, and reproducible runtime evidence **have not been executed on this head**. The branch is review-only DRAFT/HOLD and must not automatically merge to `master`.

This operation retains the original source-archive objects and full Git history without deleting another PR. It is **one bounded source-history addition**, not the complete all-`hsoliwal/*` PR fleet.
