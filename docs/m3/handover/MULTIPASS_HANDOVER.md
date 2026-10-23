# Multipass handover: Eclipse at Java 21, marks across all of Eclipse, the compiler pass (2026-10-20 to 23)

Resume here. Everything below is committed and pushed; nothing lives only in a chat.

## Branches and heads

| Repo | Branch | Head | What |
| --- | --- | --- | --- |
| hsoliwal/eclipse.platform.ui | `m3/jdk21-compat-20261020` | 4ccfad9125 | 43 bundles at Java 21, gate green, docs/m3/jdk21 |
| hsoliwal/eclipse.platform | `m3/jdk21-compat-20261020` | ad2a72dec6 | 27 bundles, residue 255 = FFM in org.eclipse.core.filesystem (FILL_ITEMS.tsv) |
| hsoliwal/eclipse.jdt.ui | `m3/jdk21-compat-20261020` | d23c6c5770 | 12 bundles, gate green |
| hsoliwal/eclipse.jdt.core | `m3/jdk21-compat-20261020` | 15d3911d64 | 13 bundles, gate green |
| hsoliwal/eclipse.jdt.debug | `m3/jdk21-compat-20261020` | 9fe98db0d8 | 5 bundles, gate green |
| hsoliwal/equinox, eclipse.platform.releng.aggregator, org.eclipse.emf, gef-classic | `m3/jdk21-compat-20261020` | a879dc0a75, 2089d848e8, f38fc409e4, e47f6bbcc4 | metadata and pom/product pins only |
| hsoliwal/eclipse.platform.ui | `m3/marks-tags-20261020` | 263d025 | org.eclipse.ui.ide with 3,162 CNPCDA anchors at every compiler-AST site, docs/m3/marks-ast |
| hsoliwal/eclipse.platform.swt | `m3/viewport-integration-20261016` | 6b114507b381 | docs/m3: enhance-targets, code-mapping, plan, culprits, marks-ast, brief, plan |
| hsoliwal/com.synexia | `codex/m3-eclipse-jdk21-compat-20261020` | cec9ad5a90e0 | crate m3-eclipse-jdk21-compat-20261020: text-lane recipe, gate, passes 2 to 5, culprits, RECIPE_MEMORY |
| hsoliwal/com.synexia | `codex/m3-eclipse-enhance-marks-20261020` | 2b536b43c4e8 | crate m3-eclipse-enhance-marks-20261020: M3MarksPlugin (javac), compiled mark recipes, RewriteTest gate |
| hsoliwal/M3jdk21 | `m3/jbs-enhancements-scaffold-20261020` | c262f27220 | m3/backports/jbs: 5,299 fixed JBS enhancements of 22 to 27, 733 already in jdk21u |
| hsoliwal/M3jdk21 | `m3/mop-invariant-20261020` | 2d817f3bbe | redundant with PR #1592 (m3/mop/SELF_IMPROVING_RECIPES.md); delete or ignore |

## The passes

| Pass | Tool (crate m3-eclipse-jdk21-compat unless noted) | Output |
| --- | --- | --- |
| 1 census | `census/census_all.sh`, `census/census_lines.sh` | JDK21_CENSUS_ALL.tsv, CENSUS.tsv per fork |
| recipe | `recipe/apply_jdk21.sh`, `recipe/unnamed_to_named.awk`, `recipe/apply_jdk21_pins.sh`, `recipe/fix_collisions.sh` | bundles at Java 21; `test/test_recipe.sh` 27 cases |
| gate | `gate/compile_gate.sh`, `gate/regate.sh` | errors(--release 21) minus errors(--release 25), BOM files excluded |
| 2 site marks | `passes/jdk21_pass2.sh` | PASS2_MARKS.tsv per fork (bundle, scope, type, enclosing method, construct, permalink) |
| 3 enhance targets | `passes/sweep.sh` with `catalogues/TEMPLATES.tsv` (40) | docs/m3/enhance-targets (43,933 main sites) |
| 4 code mapping | `passes/sweep.sh` with `catalogues/CODE_MAPPING.tsv` (33) | docs/m3/code-mapping |
| 5 plan | `passes/pass5_plan.sh`, `passes/LANES.tsv` | docs/m3/plan/PLAN.tsv: 13,919 files with their ordered approach |
| culprits | `passes/culprits.sh`, `passes/method_length_stream.sh` | docs/m3/culprits: collections 27,358; StringBuilder/concat 90,948; IO 7,481; regex 3,005; loops 60,073; methods over 15 lines 56,417 |
| 6 compile | crate m3-eclipse-enhance-marks: `compiler/learn_cpu.py`, `compiler/compile_marks.py` | 407 method patterns, 58 listener types, 55 mark recipes + composite; `mvn test` green |
| 7 compiler AST | `M3MarksPlugin` (javac plugin in the crate jar) | docs/m3/marks-ast/ui.ide: 4,319 marks; differential BOTH 1,993 / AST 1,184 / grep 3,673 |
| annotate | `compiler/tag_sites.awk` | anchors `//FLLDCIM: MARK — <template> <target> <kind>` on `m3/marks-tags-20261020` |

Run the compiler pass on a bundle: `javac -processorpath <crate jar> -Xplugin:"M3Marks <crate-dir> <out.tsv>"
--release 21 -implicit:none -XDshould-stop.ifError=FLOW @<gate args> @<sources>` (the gate args carry the p2 pool
class path; `S:/m3-scratch/jdk21/pass7/` holds the ui.ide run). Quote the -Xplugin line inside an @argfile.

## Rules that came out of this work (VIEWPORT_AGENT_BRIEF.md)

30 Eclipse stays Java 21 for M3JDK21. 31 MOP: every recipe correction is a RECIPE_MEMORY.tsv row. 32 four
mechanical passes before the agent's pass; trail and hints; culprit order collections > StringBuilder > IO >
regex > loops > method length. 33 no second AST: marks come from the compiler's AST during the build.

## Next leaves (after the token reset)

1. `eclipse.platform` FFM residue: restore the JNA Win32Handler from upstream 9dc9a9359b (FFM came in 31b3f6abb)
   and port the four later fixes; gate to 0 (FILL_ITEMS.tsv on the branch).
2. Run the compiler pass on every Java-25 bundle of the five forks (the gate args exist per fork) and tag them on
   `m3/marks-tags-20261020` branches; publish per-bundle differentials; feed grep-only rows back into the
   catalogues (MOP), AST-only rows into the regexes.
3. Maven plugin wrapper (user: "save this multipass approach as recipe or Maven plugin"): a mojo that runs javac
   with M3Marks on the project's sources and class path, the tag pass, and the method-length/culprit tables.
4. Transform lanes in culprit order, as recipes consuming the anchors: collections (M3 lanes), m3string, m3file,
   precompute, loops to lanes, method split. Each lane: a crate with RECIPE_MEMORY.tsv, gate, differential.
5. Learn-from-interfaces workflow (`multipass-compiler-learn-interfaces`, run wf_4c2d0557-a90) when agents are
   available: it refines the CPU-learned patterns (declaring supertypes, accessibility) and leaves trail and hints.
6. JBS rows to classify (4,566); jdt.core test class path for a sharper gate; merge the JDK21 branches into the
   working branches and move the markers harness to Temurin 21.

Pitfalls paid for are the RECIPE_MEMORY.tsv rows of both crates (22 so far): read them before touching the tools.
