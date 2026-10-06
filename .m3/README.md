# M3 RECIPE_FIRST sidecar

Isolated Java 21/OpenRewrite recipe crate; native build files are untouched.

- Build: `mvn -f .m3/openrewrite-recipes/pom.xml clean install`
- Inventory: `mvn -f .m3/analysis-pom.xml -Pm3-recipe-first-inventory org.openrewrite.maven:rewrite-maven-plugin:dryRun`
- Task lane requires `m3.llm.taskCrateFile` plus exact SHA-256 `m3.llm.taskCrateRoot`.

Source-changing work must be authored as a reusable tested recipe after these gates. Direct LLM target-file editing is not an allowed lane.

- Native source receipt: install `com.synexia:synexia-openrewrite-recipes:1.0.0-SNAPSHOT`, then run
  `mvn -f .m3/analysis-pom.xml -Pm3-native-source-distillation org.openrewrite.maven:rewrite-maven-plugin:run`.


## Canonical recipe authority

Reusable M3/OpenRewrite/Maven recipe implementations and recipe DAG logic are canonical in
`hsoliwal/com.synexia` under `com.synexia.rewrite`.

This repository remains the SWT product/API/build/runtime oracle and owns final product promotion.
The local `.m3/openrewrite-recipes` tree is retained only as historical target adapter, exact
fixture and proof-harness material. It has no independent reusable-recipe implementation authority.

New reusable source-changing recipes must be authored or moved to Synexia, proven there, and applied
to SWT through a content-addressed pinned handoff before the dependent SWT product change is fully
promoted. No rebase, squash, force-push or direct LLM target-file lane is implied by this policy.
