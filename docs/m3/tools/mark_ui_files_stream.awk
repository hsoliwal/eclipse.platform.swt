# Stream marker (user 2026-10-20: "mark all Eclipse UI files", "mark all files using a stream", "scaffold everything",
# "all eclipse files"). Input: the output of `git cat-file --batch` (header "<sha> blob <size>", then <size> bytes, then
# LF), with the paths in the same order in the file named by -v PATHS. LC_ALL=C so length() counts bytes. One pass,
# every file read once. Output (stdout): one UI_FILE_MARKS row per UI file; (-v SPEC=file): one SPEC_SCAFFOLD row per
# UI file that creates a viewer. Non-UI files are counted (-v ALL=file gets one row per file: repo, path, ui flag).
BEGIN { inobj = 0; OFS = "\t" }
function reset() {
  ui = 0; ext = "-"; impl = ""; cls = "-"; viewers = 0; swtTT = 0; rebuild = 0; thread = 0; io = 0; maps = 0
  delete fam; src = ""; pres = ""
}
function famadd(k) { fam[k]++ }
function addsrc(k) { if (index("," src ",", "," k ",") == 0) src = (src == "" ? k : src "," k) }
function emit(   kind, needs, fl, k, beh) {
  if (ALL != "") print REPO, path, ui >> ALL
  if (!ui) return
  kind = "UI_OTHER"
  if (ext ~ /^(ViewPart|PageBookView|CommonNavigator|ExtendedMarkersView|MarkerSupportView|AbstractDebugView|AbstractInfoView|ResourceNavigator)$/) kind = "VIEW"
  else if (ext ~ /(EditorPart|FormEditor|TextEditor|MultiPageEditorPart|AbstractDecoratedTextEditor|StatusTextEditor|SharedHeaderFormEditor)$/) kind = "EDITOR"
  else if (ext ~ /(Dialog|SelectionDialog)$/) kind = "DIALOG"
  else if (ext ~ /(WizardPage|Wizard)$/) kind = "WIZARD"
  else if (ext ~ /(PreferencePage|PropertyPage)$/) kind = "PREFERENCE"
  else if (ext ~ /(SectionPart|FormPage|TableSection|TreeSection|PDESection|StructuredViewerSection|PDEFormPage)$/) kind = "FORM_SECTION"
  else if (impl ~ /(ContentProvider|LabelProvider|IStyledLabelProvider)/) kind = "PROVIDER"
  else if (ext ~ /^(AbstractHandler|Action|SelectionProviderAction|SelectionListenerAction|BaseSelectionListenerAction)$/) kind = "ACTION"
  else if (ext ~ /^(Composite|Canvas|Control|Scrollable)$/) kind = "WIDGET"
  needs = ""
  if (viewers + swtTT > 0 && rebuild > 0) needs = needs "DELTA;"
  if (viewers > 0) needs = needs "TEMPLATE;FOOTER;"
  if (fam["filter"] + fam["sort"] > 0) needs = needs "VIEWBAR;"
  if (thread > 0 || io > 0) needs = needs "THREAD;"
  if (maps > 0 && viewers + swtTT > 0) needs = needs "DOOP;"
  if (fam["scrolled"] > 0) needs = needs "SCROLL_CHECKPOINTS;"
  if (fam["sash"] > 0) needs = needs "SPLIT_PANEL;"
  if (fam["decoration"] + fam["validation"] > 0) needs = needs "DECORATOR;"
  if (fam["autocomplete"] > 0) needs = needs "AUTOCOMPLETE;"
  if (kind == "DIALOG" && viewers > 0) needs = needs "TREE_DIALOG_CANDIDATE;"
  fl = ""
  for (k in fam) if (fam[k] > 0) fl = fl k "=" fam[k] ";"
  print REPO, path, kind, cls, ext, viewers, swtTT, rebuild, thread, io, maps, (fl == "" ? "-" : fl), (needs == "" ? "-" : needs), (src == "" ? "-" : src), BASE path
  if (viewers > 0 && SPEC != "") {
    beh = ""
    if (fam["filter"] > 0) beh = beh "filter,"
    if (fam["sort"] > 0) beh = beh "sort,"
    if (fam["decoration"] > 0) beh = beh "decorations,"
    if (fam["autocomplete"] > 0) beh = beh "autocomplete,"
    if (fam["cell_edit"] > 0) beh = beh "inlineEdit,"
    if (fam["dnd"] > 0) beh = beh "dragReorder,"
    if (fam["menu"] > 0) beh = beh "contextMenu,"
    sub(/,$/, "", beh)
    print REPO, path, cls, kind, "view.id=" cls, "source=" (src == "" ? "TODO" : src), "presentation=" (pres == "" ? "tree" : pres), "behaviours=" beh, "footer=counts", (fam["filter"] > 0 ? "viewbar=search" : "viewbar=") >> SPEC
  }
}
inobj == 0 {
  if ($0 ~ /^[0-9a-f]+ blob [0-9]+$/) {
    split($0, h, " "); size = h[3] + 0
    if ((getline path < PATHS) <= 0) path = "?"
    reset(); got = 0; inobj = 1
    if (size == 0) { emit(); inobj = 0 }
    next
  }
  if ($0 ~ /^[0-9a-f]+ missing$/) { getline path < PATHS; next }
  next
}
{
  got += length($0) + 1; l = $0
  if (l ~ /^import (org\.eclipse\.(swt|jface|ui|e4\.ui)|javax\.swing|java\.awt)/) ui = 1
  if (cls == "-" && l ~ /(class|interface|enum|record)[ \t]+[A-Z][A-Za-z0-9_]*/) {
    s = l; sub(/.*(class|interface|enum|record)[ \t]+/, "", s); sub(/[^A-Za-z0-9_].*/, "", s); cls = s
    if (l ~ /extends[ \t]+[A-Za-z0-9_.]+/) { e = l; sub(/.*extends[ \t]+/, "", e); sub(/[^A-Za-z0-9_.].*/, "", e); sub(/.*\./, "", e); ext = e }
    if (l ~ /implements/) { i2 = l; sub(/.*implements[ \t]+/, "", i2); impl = i2 }
  }
  if (l ~ /new (Tree|Table|CheckboxTree|CheckboxTable|List|Combo|Grid|XViewer|ContainerCheckedTree)Viewer[ \t]*\(/ || l ~ /new [A-Za-z]*(TreeViewer|TableViewer)[ \t]*\(/) {
    viewers++
    if (l ~ /Table/) pres = "table"; else if (l ~ /List/ && pres == "") pres = "flat"
  }
  if (l ~ /new (Tree|Table)[ \t]*\(/) swtTT++
  if (l ~ /\.setInput\(|\.refresh\([ \t]*(true|false)?[ \t]*\)|\.removeAll\([ \t]*\)|refreshChildren\([ \t]*\)/) rebuild++
  if (l ~ /\.syncExec\(|BusyIndicator\.showWhile\(|busyCursorWhile\(|\.run\([ \t]*false[ \t]*,|readAndDispatch\(\)/) thread++
  if (l ~ /\.(findMarkers|refreshLocal|members|getContents)\(/) io++
  if (l ~ /new (Linked)?Hash(Map|Set)[ \t]*</) maps++
  if (l ~ /ControlDecoration|FieldDecoration|IDecoration|DecorationOverlayIcon|LabelDecorator/) famadd("decoration")
  if (l ~ /ContentProposalAdapter|IContentProposalProvider|AutoCompleteField|ContentAssistCommandAdapter/) famadd("autocomplete")
  if (l ~ /IInputValidator|IValidator|ControlDecorationSupport/) famadd("validation")
  if (l ~ /ViewerFilter|PatternFilter|FilteredTree|FilteredItemsSelectionDialog/) famadd("filter")
  if (l ~ /ViewerComparator|ViewerSorter/) famadd("sort")
  if (l ~ /MenuManager|IMenuListener/) famadd("menu")
  if (l ~ /DragSource|DropTarget|ViewerDropAdapter|LocalSelectionTransfer/) famadd("dnd")
  if (l ~ /CellEditor|EditingSupport/) famadd("cell_edit")
  if (l ~ /ScrolledComposite|ScrolledForm|ScrolledPageBook/) famadd("scrolled")
  if (l ~ /SashForm|new Sash[ \t]*\(/) famadd("sash")
  if (l ~ /FormToolkit|ExpandableComposite/) famadd("forms")
  if (l ~ /BusyIndicator|ProgressMonitorPart|ProgressBar/) famadd("busy")
  if (l ~ /ToolTip|ColumnViewerToolTipSupport/) famadd("tooltip")
  if (l ~ /IResourceChangeListener|IResourceDelta/) addsrc("resources")
  if (l ~ /IMarkerDelta|IMarker[^A-Za-z]/) addsrc("markers")
  if (l ~ /IElementChangedListener|IJavaElementDelta/) addsrc("java-elements")
  if (l ~ /IModelChangedListener|IModelChangedEvent/) addsrc("pde-model")
  if (l ~ /ISearchResultListener/) addsrc("search-results")
  if (l ~ /IDebugEventSetListener|handleDebugEvents|IBreakpointListener|ILaunchesListener/) addsrc("debug-events")
  if (l ~ /notifyChanged|org\.eclipse\.emf\.common\.notify/) addsrc("emf-notifications")
  if (l ~ /IObservable|ObservableList|WritableList/) addsrc("observables")
  if (l ~ /IDocumentListener|IConsoleDocument/) addsrc("document")
  if (l ~ /ISyncInfoSetChangeListener|ISynchronizeParticipant/) addsrc("team-sync")
  if (got >= size) { emit(); inobj = 0 }
}
