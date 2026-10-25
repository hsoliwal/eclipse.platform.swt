# Viewport gate failures, 2026-10-24

Analysis workflow wf_ae366fd4-6f7 over the 7 tests the all-widget gate failed on jar tv20261024n (head 2dcbcb50f1):
one analyst per unit (code reading, stock oracles on the official 3.135.0 jar, replays of the test scripts) and an
adversarial verifier per analysis. The account session limit stopped the verifiers of list-count and combo-readonly and
the plan agent: those causes are unverified. Full results: GATE_FAILURE_ANALYSIS_20261024.json; per-unit evidence
(proposed diffs, oracle sources, small result tables): analysis-20261024/<unit>.

## combo-editable

Verification: done

- `P-stale-patch-empties-edit-field` (viewport, confidence high, holds): 88 kinds; first example: seed 0 step 70 'drop true' (steps 67/68 'addAt 85 764'/'addAt 50 817' made the closed list stale): recorded 'state sel 14 count 152 text 'berry 163' dropped true | list 0..151/19@14'; this run 'sel -1 ... text '' ... | l
- `B-bar-lane-follows-window-not-stock` (viewport, confidence high, holds): 15 kinds; first example: seed 0 step 19 'altdown' (step 14 was 'setItems 149'): recorded 'list 0..148/21@0 40085f3d', this run 'list 0..100/21@0 c2c35849'; state, events and face are equal. The viewport diag shows barState=0 and a window of 0+10
- `D-deselectAll-keeps-stale-selection-lane` (viewport, confidence high, holds): 1 kinds; first example: seed 8 step 35 'deselectAll' on a closed, stale combo (stale=true, staleSelection=0): recorded 'state sel -1 count 304 text '' dropped false | events M'; this run 'sel 0'. The text, the Modify event and the face match. T
- `H-pixel-probes-depend-on-desktop-slot` (harness, confidence medium, does not hold): 0 kinds; first example: This explains none of the 96 kinds; it is a risk for future gate runs. The official 3.135.0 jar replayed on m3-ui-tests-1 and m3-ui-tests-2 gives face 883bf1d9 at seed 0 step 8 'key DOWN', against recorded f62953c1. The
  - corrected fix: Origin: the environment (the session's system colours), plus a missing harness guard; the slot is not the cause. (1) Before gating, restore the session colours: log off and on, or re-apply HKCU\Control Panel\Colors through SetSysColors so COLOR_HIGHLIGHT is back to 0,120,212. Otherwise, re-record the references in the current session with record_stock_references.sh. (2) Add a harness guard. Write

## combo-readonly

Verification: not run (session limit)

- `C1-bar-lane-misses-hidden-deltas` (viewport, confidence high, unverified): 9 kinds; first example: seed 0 step 19 'altdown'. The ops come from Random(0xC0B0+0): step 14 'setItems 149', 15 'wheel 120', 16 'visible 21', 19 'altdown'. Recorded: list 0..148/21@0 40085f3d. This run: list 0..100/21@0. Steps 20-26 and 36 fol
- `C2-caret-lane-captured-from-stale-window` (viewport, confidence high, unverified): 5 kinds; first example: seed 6 step 14 'key RIGHT'. The earlier ops from Random(0xC0B0+6): 5 'select 256', 7 'key PRIOR' (moves to 252), 8 'addAt 184 694' (moves the selection to 253), 9 'key RETURN', 11 'visible 13', and 12/13 'hover', which d
- `H1-list-pixels-depend-on-jvm-history (explains none of the 14 kinds)` (harness, confidence medium, unverified): 0 kinds; first example: On the official 3.135.0 jar in a fresh JVM running only these two methods, seed 0 step 19 gives list 0..148/21@0 c7fe9416. The stored reference has 40085f3d. The bar is identical.

## combo-closed

Verification: done

- `RC1-setItem-reselect-patches-stale-window` (viewport, confidence high, holds): 2 kinds; first example: seed 0, step 0 'select 176' (Random(0xC0B0): nextLong, then 100+nextInt(100)), step 1 'deltas 5250155769187346188'. Its 1000 deltas are 501 add, 250 remove and 249 setItem. Oracle attribution on the gate jar: add 0 row o
- `RC2-dropped-list-pixels-not-reproducible-in-reference` (reference, confidence high, does not hold): 1 kinds; first example: Seed 0, step 11 'drop' (after 10 deltas steps; recording line 13). State, events, face and bar all match. Only the pixel hash of the dropped ComboLBox capture differs.
  - corrected fix: No viewport change and no probe change. Root cause: environment drift. The session's live COLOR_HIGHLIGHT went from (0,120,212) to (0,120,215), and COLOR_MENUHILIGHT to (51,153,255), between 20:16:10 and 20:38:56, after both the recording and the gate. The K: reference is valid for the persisted colours. It affects every highlight-bearing probe (Combo dropped list and editable face, List and Tree

## list-count

Verification: not run (session limit)

- `C1-top-past-page-after-tail-cut` (viewport, confidence high, unverified): 146 kinds; first example: seed 0 step 54 'setItemCount 131' (295 rows, top 235, caret 238): recorded 'top 110 count 131 | bars 106/130', this run 'top 106 count 131 | bars 106/130'. The cascade continues at step 55 'setItemCount 167' (bars 110/16
- `C2-ctrlshift-click-without-anchor-clears-all` (viewport, confidence high, unverified): 3 kinds; first example: seed 7 step 8 'click 12 ctrlshift', after selectRange 124 135 and key DOWN ctrl, with no anchor. Recorded 'sel 12 [124..135] | focus 12*', this run 'sel 0 []'. The cascade covers steps 9-11 (setItemCount 167, setItemCoun
- `C3-click-below-last-row-reads-stale-native-index` (viewport, confidence medium, unverified): 5 kinds; first example: Latent: it needs a plain click or drag start below the last row (count - top < page). In this survey C1 cascades hide it. With only C1 fixed it surfaces: - seed 8 step 131 'drag 13 down 23' (top 67 of 75 rows): recorded

## list-simple

Verification: done

- `LS-1 cross-test wheel remainder (harness test order)` (harness, confidence high, holds): 31 kinds; first example: seed 0 step 57 is 'wheel -240'. The op was recovered from Random(0x1157+0) in the scratchpad file analysis/list-simple/ops-random.tsv:58. Before the step the top is 181 (list-simple-seed0.txt:58). The recording has top 1

## table-seeded

Verification: done

- `T1-stock-removeItems-stale-keys-throw-and-row-cascade` (reference, confidence high, holds): 25 kinds; first example: Variant 2 (SWT.MULTI|BORDER|VIRTUAL, API script; Test_org_eclipse_swt_widgets_Table_ViewportDifferential.java:74). Variants 0 and 1 pass (run-tv20261024n.log:9,11). Seed 3, Random(0x7AB1E5EEDL+62+3), :209. Step 48 is op
- `T2-stock-removeItems-stale-keys-wrong-item-identity` (reference, confidence high, holds): 4 kinds; first example: Seed 4, step 60: op 17 at 586115, read 147. Recorded: 'read 147 -> new 41 / index 147 ... | events ' (no SetData). This run: 'read 147 -> data 147 ... | events SD:147'. The other instances, listed at UPSTREAM_DEFECTS.tsv
- `T3-gate-runs-without-the-upstream-defect-list` (harness, confidence high, holds): 25 kinds; first example: Seed 3, step 48 is listed at UPSTREAM_DEFECTS.tsv:5 with exactly the recorded stock answer and this run's answer, but the gate survey still reports it. Its survey example shows the raw stock line ('recorded: error java.l
