# Print the method name of each public method declaration. Handles JFace style (one tab, "name(") and SWT style
# (column 0, "name ("). Nested-class members at deeper indentation are skipped.
/^[ \t]?public [^=;]*\(/ && !/[ \t](class|interface|enum|new)[ \t]/ {
  s = $0; sub(/\(.*/, "", s); sub(/[ \t]+$/, "", s); n = split(s, w, /[ \t]+/); print w[n]
}
