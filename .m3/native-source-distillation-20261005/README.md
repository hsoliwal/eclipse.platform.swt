# SWT native source distillation

Canonical owner: `com.synexia.rewrite.M3SwtNativeSourceReceipt` from
`hsoliwal/com.synexia:synexia-openrewrite-recipes:1.0.0-SNAPSHOT`.

This lane complements the already-merged Java statement/atom receipt. It does not mutate native
source; it parses C-family files as OpenRewrite PlainText, runs the canonical byte-preserving native
atomizer, and generates:

- `.m3/native-source-distillation-20261005/native-files.tsv`
- `.m3/native-source-distillation-20261005/COMPLETE.properties`

Current SWT master pin when this lane was added:
`b22afa0f8b16d0099730c6e3a484a3483c40a5b1`.

Current semantic native source universe:

- 124 files total;
- 58 `.c`;
- 54 `.h`;
- 11 `.cpp`;
- 1 `.mm`.

The broader historical count of 170 native payloads includes build/native assets that remain under
byte custody but are not C-family semantic source.

## Regenerate

Install the canonical Synexia recipe artifact into the local Maven repository, then run:

```bash
mvn -f .m3/analysis-pom.xml -Pm3-native-source-distillation \
  org.openrewrite.maven:rewrite-maven-plugin:run
```

The recipe adds plain-text masks for `.c/.h/.cc/.cpp/.cxx/.hpp/.m/.mm`.

A second run must produce no source or receipt changes. Native source files must remain byte exact.
Only the two receipt files are mutation targets.

## Invariants

- lossless native source recomposition;
- fixed-point atom replay;
- comments/strings shield syntax delimiters;
- multiline preprocessor directives remain whole atoms;
- `for (;;)` semicolons do not split statements;
- native source mutation is forbidden;
- public SWT/JNI behavior is unchanged.
