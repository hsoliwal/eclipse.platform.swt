# Pass five: the per-file plan for all of Eclipse (how the LLM would approach each file, precomputed)

Generated 2026-10-20 by `pass5_plan.sh` from passes three (`../enhance-targets`) and four
(`../code-mapping`). Pure awk over the file marks, 3 seconds for the whole of Eclipse. Marks only.

An LLM approaching a file does the same thing every time: it reads what the file registers and calls, picks the
model it should bind to, moves the change sources, then the view, then the forwarding, the text API, the
collections, the behaviours, and only at the end the threading, leaving what it cannot classify for review.
`PLAN.tsv` precomputes exactly that for every main-code file, so the final pass (after the token reset) spends
its tokens on review and on new patterns, not on re-deriving the order.

| Column | Content |
| --- | --- |
| `leaf` | 1..N in fill order: platform.ui, PDE, JDT, m2e, the rest; then sites descending |
| `repo`, `bundle`, `path` | where |
| `sites` | main-code sites of passes three and four |
| `targets` | `TARGET=n;...` |
| `approach` | the ordered steps: `1. lane (kind, sites) > 2. ...`, in the lane precedence of `LANES.tsv` |
| `recipe_kind` | JAVA (OpenRewrite lane), TEXT, TEXT+JAVA or REVIEW |
| `state` | PLANNED; the final pass moves rows to DONE / DEFERRED / NOT_A_SITE |

`LANES.tsv` maps each target to its lane, recipe kind and owner track, in precedence order:
model-api > part-visible > swt-events > widget-api > change-source > delta-views > label-ids > jface-forward >
proxy-model > viewport-delta > damage > text-api > databinding > collections > precompute > interning > m3string >
m3file > residency > debounce > behaviours > enablement > dialogs > synchronizer > model-thread > review.

Files planned: 13919.

| Repo | Files |
| --- | --- |
| eclipse.platform.ui | 2620 |
| eclipse.platform | 2007 |
| initiative31-eclipse.pde | 1835 |
| eclipse.jdt.ui | 1673 |
| nebula | 878 |
| org.eclipse.emf | 767 |
| eclipse.jdt.core | 764 |
| p2 | 607 |
| egit | 606 |
| equinox | 543 |
| eclipse.platform.swt | 433 |
| eclipse.jdt.debug | 428 |
| m2e-core | 406 |
| gef-classic | 331 |
| eclipse.platform.releng.aggregator | 21 |

| Lane | Files with a step |
| --- | --- |
| collections | 7819 |
| m3string | 3330 |
| text-api | 2919 |
| precompute | 2612 |
| model-thread | 2468 |
| viewport-delta | 2095 |
| model-api | 1574 |
| delta-views | 1493 |
| widget-api | 1447 |
| synchronizer | 1278 |
| proxy-model | 1227 |
| swt-events | 1092 |
| residency | 966 |
| behaviours | 917 |
| change-source | 909 |
| jface-forward | 748 |
| label-ids | 728 |
| debounce | 498 |
| m3file | 251 |
| damage | 146 |
| databinding | 117 |
| part-visible | 113 |
| enablement | 56 |
| interning | 43 |
| dialogs | 34 |

The recipe compiler (brief rule 31) reads PLAN.tsv and LANES.tsv: every lane becomes a recipe crate, every row a
leaf; the ledger of each crate records the corrections the LLM makes while applying it (RECIPE_MEMORY.tsv).
