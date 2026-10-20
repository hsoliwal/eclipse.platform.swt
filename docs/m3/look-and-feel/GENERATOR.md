# Look and feel generator (LF7, user 2026-10-19)

"Add a look and feel generator for SWT and Swing using an LLM." The LLM proposes data; the CPU decides. One
generated theme applies in SWT (the LF6 seam) and in Swing (UIManager, FlatLaf format), and exports as an IntelliJ
theme. Design first (D2C); donors in [`FOSS_REUSE_DECISION.tsv`](FOSS_REUSE_DECISION.tsv); algorithm choices in
[`ALGORITHM_CATALOGUE.tsv`](ALGORITHM_CATALOGUE.tsv) (rule 21).

## Pipeline

```
prompt (+ brand colours, + reference image)
  -> M3ThemeModel (LLM: Ollama first, then the Anthropic API; a recorded fixture in tests)
  -> JSON text (untrusted)
  -> M3ThemeSpec.parse     strict: closed schema, unknown keys and wrong types rejected; an unreadable colour is
                           recorded as a repair and treated as missing
  -> M3ThemeCompleter      pure CPU, deterministic: missing values, WCAG contrast, derived states, every catalogue key
  -> M3ThemeExport         LF2 table (SWT), FlatLaf .properties (Swing), IntelliJ .theme.json
```

## The closed schema (what the LLM must answer)

```json
{
  "name": "Harbour Night",
  "dark": true,
  "palette": { "background": "#1b1f24", "foreground": "#d8dee9", "accent": "#5e81ac", "selection": "#3b4252",
               "border": "#434c5e", "error": "#bf616a", "warning": "#ebcb8b", "info": "#88c0d0" },
  "font": { "family": "Segoe UI", "size": 9 },
  "density": "normal"
}
```

Every member is optional except `palette.background` or `dark`; `density` is `compact`, `normal` or `comfortable`;
`font.size` is 6 to 32 points. Anything else (another member, a nested object where a string belongs, a number out of
range) rejects the answer.

## The completer (`org.eclipse.swt.internal.laf.M3ThemeCompleter`, no dependencies)

1. Missing palette colours from the present ones: background and foreground from `dark`, accent `#2f6fd0`, selection
   = accent, border = foreground mixed into the background (20 %), error/warning/info from fixed hues.
2. Contrast (WCAG 2.x relative luminance): text on its background at least 4.5:1 (foreground on background, selection
   text on selection, tooltip text), UI elements at least 3:1 against the background (border, focus, accent, grid
   when shown). A colour that fails moves in OKLCH lightness (hue and chroma kept) away from its background by
   bisection until it passes; the pair is fixed by black or white text when no lightness passes.
3. Derived states by OKLCH arithmetic: hover and pressed backgrounds (lightness +-4 and +-8 points towards the
   foreground), disabled text (60 % of the way to the background), focus (the accent, at least 3:1), inactive
   selection (selection mixed 50 % into the background, chroma halved), stripes (+-2 points), headers (+-3 points).
4. Metrics from the density and the font size: row heights, insets, scroll bar width, arcs.
5. Every key of `LookAndFeelThemes.KEYS` gets a value; `twin ()` answers the theme of the other brightness (OKLCH
   lightness mirrored for background and foreground, accent kept and contrast solved again).
6. The output header carries `M3ThemeCompleter.VERSION` and the SHA-256 of the canonical input JSON: the same input
   and version give the same bytes.

## Emitters (`M3ThemeExport`)

- LF2 table for SWT (`M3LookAndFeel.register`, `M3ThemeImport` reads it back): variables `@background`,
  `@foreground`, `@accent` (so `M3LookAndFeel.setAccent` keeps working) and every catalogue key;
- FlatLaf `.properties` for Swing: `@baseTheme`, `@background`, `@foreground`, `@accentColor` and the Swing key names
  (`TableHeader.*`, `TabbedPane.*`, `TextField.*`, ...), read by FlatLaf and by `M3ThemeImport.flatLaf`;
- IntelliJ `.theme.json`: `name`, `dark`, `colors` (the palette) and `ui`, read by IntelliJ and by
  `M3ThemeImport.intelliJ`.

The three round-trip through the importers to the same catalogue values (tested).

## The LLM side (`tools/m3-themegen`, outside the SWT bundle)

The SWT bundle has no dependencies, so the LLM clients, the command line and the Swing applier live in a separate
Maven module that uses the SWT jar:

- `M3ThemeModel` (one method: a system prompt, a user prompt and an optional image, answering text);
  `M3OllamaModel` (HTTP to `http://localhost:11434/api/generate` with `format: json`, model from
  `-Dm3.themegen.ollama.model`), `M3AnthropicModel` (the official `com.anthropic:anthropic-java` SDK, model from
  `-Dm3.themegen.anthropic.model`, default `claude-sonnet-5-5`, key only from the `ANTHROPIC_API_KEY` environment
  variable), `M3FixtureModel` (a recorded answer; the tests never use the network). CPU-LAW order: Ollama first, the
  API only when Ollama is unreachable or asked for.
- `M3ThemeGenerator` (the command line): `--prompt`, `--brand #hex[,#hex]`, `--image <file>`, `--ollama [model]`,
  `--anthropic [model]`, `--fixture <file>`, `--twin`, `--out <directory>`; writes the three files and the accepted
  JSON. Logs through `java.util.logging` (rule 22).
- `M3SwingTheme`: applies the FlatLaf-format file to `UIManager` (`ColorUIResource` per key) and updates the open
  windows, without FlatLaf on the class path.

## Tests

- completer: seeded palettes, adversarial answers (missing keys, unreadable colours, unknown keys, wrong types,
  low contrast, identical foreground and background), determinism of the hash, every catalogue key; 99 % coverage;
- emitters: round trip through `M3ThemeImport` and the LF2 loader;
- generator: the recorded fixture, and the Ollama client against a local `com.sun.net.httpserver` server;
- a hidden-desktop pixel test that applies one generated theme to SWT (drawn rows) and to a Swing `JTable`.

## Status (2026-10-19)

Implemented: `M3Oklch`, `M3ThemeSpec`, `M3ThemeCompleter`, `M3ThemeExport` in the SWT bundle (tested by
`Test_org_eclipse_swt_internal_laf_M3ThemeCompleter`, 10 cases, no display); `tools/m3-themegen` with
`M3ThemeModel`, `M3OllamaModel`, `M3AnthropicModel`, `M3FixtureModel`, `M3ThemeGenerator` (the command line),
`M3SwingTheme` and `M3ThemeGeneratorExample` (tested by `M3ThemeGeneratorTest`, 7 cases headless, and
`M3ThemePixelTest` on the hidden desktop with `-Dm3.themegen.desktop=true`). Build and test the module with the M3 SWT
jar:

```
cd tools/m3-themegen
mvn -Dswt.jar=<the M3 SWT jar> test
java -cp target/classes;<the M3 SWT jar>;<dependency jars> org.eclipse.swt.m3.themegen.M3ThemeGenerator \
  --prompt "a calm harbour at night" --brand "#5e81ac" --twin --out S:/m3-scratch/allwidgets/themes/harbour
```

The dependency jars come from `mvn dependency:build-classpath`. The Anthropic SDK is `com.anthropic:anthropic-java`
2.70.0 (MIT); the model id is `-Dm3.themegen.anthropic.model` (default `claude-sonnet-5-5`); the key is read from
`ANTHROPIC_API_KEY` only. Ollama is `-Dm3.themegen.ollama.url` (default `http://localhost:11434`) and
`-Dm3.themegen.ollama.model` (default `llama3.2`).
