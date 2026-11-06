"""IDE_SMOKE v2 (text leaf): richer Java source, a selection step, the editor's StyledText rectangle in the TSV."""
import sys
from pathlib import Path
f = Path(sys.argv[1]); s = f.read_text(encoding="utf-8")
if "editor_rect" in s:
    print("fixed point"); sys.exit(0)
R = [
 ('String java = "public class Hello {\\n\\t/** M3 IDE smoke. */\\n\\tpublic static void main(String[] args) {\\n\\t\\tSystem.out.println(\\"hello m3\\");\\n\\t}\\n}\\n";',
  'String java = "// line comment: braces { } brackets [ ] parentheses ( ) semicolons ;\\npublic class Hello {\\n\\t/** M3 IDE smoke. */\\n\\tprivate static final int[] VALUES = { 1, 2, 3 };\\n\\n\\tpublic static void main(String[] args) {\\n\\t\\tSystem.out.println(\\"hello m3\\"); /* block */\\n\\t\\tfor (int i = 0; i < VALUES.length; i++) {\\n\\t\\t\\tif (args.length > 0 && VALUES[i] != 0) {\\n\\t\\t\\t\\tSystem.out.printf(\\"%d;%s%n\\", VALUES[i], args[0]);\\n\\t\\t\\t}\\n\\t\\t}\\n\\t}\\n}\\n";'),
 ('\t\t\t\trow (display, "workbench_java_editor");\n\t\t\t\tpage ().showView ("org.eclipse.jdt.ui.PackageExplorer");\n',
  '\t\t\t\trow (display, "workbench_java_editor");\n\t\t\t\tStyledText st = editorText (PlatformUI.getWorkbench ().getActiveWorkbenchWindow ().getShell ());\n\t\t\t\tif (st != null) { int at = st.getText ().indexOf ("System.out.println"); st.setSelection (at, at + 30); }\n\t\t\t} else if (step == 2) {\n\t\t\t\trow (display, "java_editor_selection");\n\t\t\t\tpage ().showView ("org.eclipse.jdt.ui.PackageExplorer");\n'),
 ('} else if (step == 4) {\n\t\t\t\trow (display, "editor_tab_switched_back");', '} else if (step == 5) {\n\t\t\t\trow (display, "editor_tab_switched_back");'),
 ('} else if (step == 3) {\n\t\t\t\trow (display, "second_editor_tab");', '} else if (step == 4) {\n\t\t\t\trow (display, "second_editor_tab");'),
 ('} else if (step == 2) {\n\t\t\t\trow (display, "package_explorer_active");', '} else if (step == 3) {\n\t\t\t\trow (display, "package_explorer_active");'),
 ('\\terrors_so_far\\tactive_part\\teditors\\tpng");', '\\terrors_so_far\\tactive_part\\teditors\\tpng\\teditor_rect");'),
 ("\t\t\tpng.toString ().replace ('\\\\', '/')));", "\t\t\tpng.toString ().replace ('\\\\', '/'), editorRect (shell)));"),
 ('\tstatic void collect (Control c, List<Control> out) {',
  '\t/* the visible StyledText showing Hello.java (the Java editor\'s text widget) */\n\tstatic StyledText editorText (Shell shell) {\n\t\tList<Control> all = new ArrayList<> ();\n\t\tcollect (shell, all);\n\t\tfor (Control c : all) if (c instanceof StyledText t && t.isVisible () && t.getText ().contains ("class Hello")) return t;\n\t\treturn null;\n\t}\n\n\t/* x,y,w,h of the Java editor\'s client area in the printed shell image (the editor area the text leaf diffs) */\n\tstatic String editorRect (Shell shell) {\n\t\tStyledText t = editorText (shell);\n\t\tif (t == null) return "-";\n\t\tRectangle r = t.getDisplay ().map (t, shell, t.getClientArea ());\n\t\tRectangle trim = shell.computeTrim (0, 0, 0, 0);\n\t\treturn (r.x - trim.x) + "," + (r.y - trim.y) + "," + r.width + "," + r.height;\n\t}\n\n\tstatic void collect (Control c, List<Control> out) {'),
]
for old, new in R:
    assert s.count(old) == 1, old[:60]
    s = s.replace(old, new)
f.write_bytes(s.encode("utf-8")); print("applied")
