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
  $3 = c; print
}
