# Correction-site caller classes (multipass mechanical, pass 4 + 4b). Input/output: CORRECTION_SITES.tsv; recomputes
# column 3 (caller_class) from column 2 (category) and column 4 (enclosing method). One source for the recipe.
# Priority for fixing: MODEL_LISTENER (a model change rebuilds the view: must become a delta) > ASYNC_OR_REFRESH_METHOD
# (a refresh path triggered by a change) > QUERY_PATH_ALLOCATION (per-call allocation on a hot query) > REVIEW;
# INITIAL_OR_SETUP, USER_ACTION, MAINTENANCE and FIELD_OR_STATIC are usually justified (DELTA_GATE JUSTIFIED).
BEGIN { FS = OFS = "\t" }
NR == 1 { print; next }
{
  m = $4; c = "REVIEW"
  if (m ~ /^(resourceChanged|handleChangedResources|handleResourceChange|resourceSetChanged|elementChanged|elementsChanged|handleDebugEvents|notifyChanged|propertyChange|propertyChanged|markersChanged|modelChanged|launches(Added|Removed|Changed|Terminated)|breakpoints?(Added|Removed|Changed)|documentChanged|documentAboutToBeChanged|changed|stateChanged|valueChanged|handleChange|handleListChange|handleSetChange|handleValueChange|inputChanged|problemsChanged|annotationModelChanged|textChanged|actionSetAvailabilityChanged|refsChanged|indexChanged|repositoryChanged|onRefsChanged|onIndexChanged|configChanged|onConfigChanged|workingSetChanged|preferenceChange|registryChanged|added|removed)$/) c = "MODEL_LISTENER"
  else if (m ~ /^(run|runInUIThread|runInWorkspace|done|scheduled|aboutToRun|execute|refresh|refreshVisuals|refreshChildren|refreshPage|update|updateView|updateControls|updateMembers|refreshView|refreshViewer|doRefresh|asyncRefresh|scheduleRefresh|internalRefresh|refreshTree|refreshTable|reset|rebuild|reload|updateContent|updateTable|updateTree|updateViewer|updateInput|refreshContent|refreshInput|refreshAll)$/) c = "ASYNC_OR_REFRESH_METHOD"
  else if (m ~ /^(getText|getImage|getElements|getChildren|getContainerChildren|getTreePath|getParent|hasChildren|compare|paths|getForeground|getBackground|getFont|getStyledText|getColumnText|getColumnImage|getToolTipText|select|isMatch|findOtherMarkers)$/ && $2 ~ /^(element_map|per_element_object|per_row_item)$/) c = "QUERY_PATH_ALLOCATION"
  else if (m ~ /^(selectionChanged|widgetSelected|widgetDefaultSelected|handleEvent|partActivated|partVisible|partOpened|partBroughtToTop|focusGained|modifyText|keyPressed|keyReleased|mouseDown|mouseUp|doubleClick|treeExpanded|treeCollapsed|checkStateChanged)$/) c = "UI_EVENT"
  else if (m ~ /^(select|addPressed|removePressed|editPressed|upPressed|downPressed|handleBrowse|handleRemove|handleAdd|handleEdit|performDefaults|performOk|okPressed|buttonPressed|setVisible|selectAll|deselectAll|handleStatus|restoreDefaults)$/) c = "USER_ACTION"
  else if (m ~ /^(createPartControl|createControl|createControls|createContents|createDialogArea|createViewer|createTreeViewer|createTableViewer|createTable|createTree|createClient|createNewRowPart|internalCreateNewRowPart|init|initialize|initializeViewer|initializeTreeViewer|initializeFrom|setInput|doSetInput|feedInput|fillEditorTable|open|create|configure|hookViewer|bind|createPage|createFormContent)$/ || m ~ /^[A-Z]/) c = "INITIAL_OR_SETUP"
  else if (m ~ /^(dispose|clear|removeAll|remove|add|addAll|syncExec|asyncExec|getInstance|getShellStyle|main)$/) c = "MAINTENANCE"
  else if (m == "-") c = "FIELD_OR_STATIC"
  # pass 4c (multipass, dimension = category): DOOP and thread defects regardless of caller
  if (c == "REVIEW" && $2 ~ /^(element_map)$/) c = "DOOP_STORE"
  else if (c == "REVIEW" && $2 ~ /^(per_element_object|per_row_item)$/) c = "DOOP_OBJECTS"
  else if (c == "REVIEW" && $2 == "sync_exec") c = "THREAD_BLOCKING"
  # pass 4d (multipass, dimension = method-name shape) for the remaining rebuild sites
  if (c == "REVIEW") {
    if (m ~ /^(handle|on)[A-Z]/ || m ~ /(Changed|Change|Updated|Added|Removed)$/ || m ~ /^notify/) c = "MODEL_LISTENER_LIKE"
    else if (m ~ /^(update|refresh|redraw|rebuild|reload|repopulate|recompute|reveal|sync)/) c = "ASYNC_OR_REFRESH_METHOD"
    else if (m ~ /^set[A-Z]/) c = "STATE_SETTER_REBUILD"
    else if (m ~ /^(create|init|build|fill|populate|load|setUp|configure|add[A-Z].*(Viewer|Table|Tree|Section|Page|Controls?))/) c = "INITIAL_OR_SETUP"
    else if (m ~ /^(dispose|clear|remove|reset)/) c = "MAINTENANCE"
  }
  # pass 4e (multipass, dimension = words in the handler name): split MODEL_LISTENER_LIKE
  if (c == "MODEL_LISTENER_LIKE") {
    if (m ~ /(Button|Pressed|Delete|Remove|RemoveAll|Add[A-Z]|AddAll|Move|Up$|Down|Edit|Browse|Reset|Unlink|ToVisible|ToNonVisible|Hide|Show|Toggle|Clicked|Selected$)/) c = "USER_ACTION"
    else if (m ~ /(SelectionChanged|Selection|Select|Validate|Activate|Link|Modified|Filter|Focus|Expand|Collapse|Key|Mouse|Text)/) c = "UI_EVENT"
    else c = "MODEL_LISTENER"
  }
  $3 = c; print
}
