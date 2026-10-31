# Text leaf (census L13 + L14 GC half) - TextLayout and GC text through M3

Base `origin/m3/superset-20261030b` @ `0e703903e8e` + real-app fidelity PR #304 head `bacabc9caa3` (merged first, at the
coordinator's request, so text is measured on top of the background/row/grid fixes). Branch `m3/text-through-m3-20261031`.
Skija binding `io.github.humbleui` 0.116.3 (L12 not landed). Census: `H:/m3-scratch/i31-census/LEAF_QUEUE.md` L13, L14.
Recipe rows `RECIPE_MEMORY.tsv` L13-01.. ; FOSS rows `FOSS_REUSE_DECISION.tsv` `m3-text:*`.
Mechanical edits: `tools/m3text_deltas.py <wt> <ATOM>` (exact anchors, fail-closed, fixed point on rerun), bodies in
`tools/m3text_bodies/`.

## Atoms and versioned patterns

| atom | pattern | typed shape | files |
|---|---|---|---|
| TEXT_FALLBACK_SAFE v1 | M3-TEXT-FALLBACK v1 | `(TextLayout, origin, selection, flags; GC font/fg/bg/AA) -> stock GC on opaque black + on opaque white -> per pixel a = max_c(255 - (W_c - B_c)), premultiplied colour = B -> BGRA_8888 PREMUL raster -> one composite with the GC alpha` | `RM/gc/M3TextLayoutFallback.java` (new), `RM/gc/M3TextLayoutCall.java` (new), `RM/gc/M3ExternalGC.java` (textLayoutDraw, drawLayoutRaster) |

Decision (TEXT_FALLBACK_SAFE): the GC alpha is applied by the composite only. Carrying it onto the native GC as well would
square it (GF57 family) and switch the stock TextLayout to GDI+ text.

## Results

(per atom: IDE editor diff m3/gpu vs baseline, probes, gates - filled as the atoms land)
