# Pass 6 (the model's review, recorded so it can be re-applied): verdict, fix pattern and owner for every
# MODEL_LISTENER row of CORRECTION_SITES.tsv. Verdicts: DELTA_DEFECT (the event already carries the change and the view
# rebuilds), FILTER_LANE, PREFERENCE_OR_TOGGLE, STRUCTURE_PREFERENCE, DIALOG_LOW.
BEGIN { FS = OFS = "\t" }
NR == 1 { print "repo", "verdict", "fix_pattern", "owner", "enclosing_method", "category", "path", "line", "permalink", "snippet"; next }
$3 != "MODEL_LISTENER" { next }
{ p = $5; m = $4; v = "DELTA_DEFECT"; fx = "apply the change delta to the lane model and patch the window"; ow = "platform-views"
  if (p ~ /CachedMarkerBuilder/) { fx = "Problems view reference: working-set scope change as resource-id set-diff (FALLBACKS_TO_DELTAS.md)"; ow = "jface" }
  else if ($1 ~ /pde/ && m ~ /^(modelChanged|stateChanged)$/) { fx = "PDE form sections: IModelChangedEvent (INSERT/REMOVE/CHANGE + objects) to M3Patch in the shared TableSection/TreeSection base"; ow = "platform-views" }
  else if (m == "textChanged") { v = "FILTER_LANE"; fx = "filter typing: superseding-generation filter lane (sort-filter pattern)"; ow = (p ~ /CommonFiltersTab/ ? "platform-ui-surfaces" : "platform-views") }
  else if (p ~ /PreferencePage|AllReferencesInView|Show(Types|SupportedBreakpoints)Action|SelectLogicalStructure/ || m == "preferenceChange" || m == "valueChanged") { v = "PREFERENCE_OR_TOGGLE"; fx = "presentation toggle: re-render the resident window from the same lanes"; ow = "platform-views" }
  else if (p ~ /Wizard|Dialog|BundleWizardPage|TabbedPropertyViewer|CustomizePerspective|PropertyPage|BreakpointPage/) { v = "DIALOG_LOW"; fx = "dialog/wizard/property page: low volume; convert with the surfaces track or justify with numbers"; ow = "platform-ui-surfaces" }
  else if (p ~ /PackageExplorer|MembersView|LogicalPackagesProvider|JavaNavigatorContentProvider/) { v = "STRUCTURE_PREFERENCE"; fx = "layout preference: rebuild topology lanes once, keep interned ids/labels, patch the window"; ow = "platform-views" }
  else if (p ~ /JavaOutlinePage|JavaEditorBreadcrumb|JavaOutlineInformationControl/) { fx = "JDT outline/breadcrumb: IJavaElementDelta to M3Patch"; ow = "platform-views" }
  else if (p ~ /SearchTableContentProvider|LevelTreeContentProvider|FileTreeContentProvider|CommitSearchResultsPage/) { fx = "search results: elementsChanged(Object[]) is already a delta; result lanes, patch rows"; ow = "platform-views" }
  else if (p ~ /Rendering/) { fx = "Memory view renderings: address-range lanes, viewport over memory"; ow = "platform-views" }
  else if ($1 == "gef-classic") { fx = (p ~ /Palette/ ? "GEF palette: child add/remove deltas, no refreshChildren()" : "Zest: graph input change as model delta, no per-element maps"); ow = "gef" }
  else if ($1 == "org.eclipse.emf") { fx = "EMF Notification / changed resources to M3Patch on the item-provider tree, no setInput rebuild"; ow = "databinding-emf" }
  print $1, v, fx, ow, m, $2, p, $6, $7, $8 }
