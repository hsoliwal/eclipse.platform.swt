# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Method length over a stream of Java blobs (git cat-file --batch output, one blob per "<sha> blob <size>" header),
# mechanical (LSEM): a method starts at a declaration-like line (modifiers/type, name, "(") that is not a control
# statement and ends when the brace depth returns to the depth it opened at. Strings, char literals and comments
# are masked before braces are counted (SFA). Emits methods longer than LIMIT (default 15) body lines:
#   path <TAB> line <TAB> method <TAB> body_lines
# Usage: git -C repo.git ls-tree -r master -- '*.java' | awk '{print $3 "\t" $4}' > list  (sha, path)
#        cut -f1 list | git -C repo.git cat-file --batch | awk -v LIMIT=15 -v LIST=list -f method_length.awk
BEGIN { LIMIT = LIMIT ? LIMIT : 15; while ((getline l < LIST) > 0) { split(l, a, "\t"); path[a[1]] = a[2] } }
function mask(s,    out, i, c, n, q) {
	out = ""; i = 1; n = length(s)
	while (i <= n) {
		c = substr(s, i, 1)
		if (inblock) { if (substr(s, i, 2) == "*/") { inblock = 0; i += 2 } else i++; continue }
		if (substr(s, i, 2) == "//") break
		if (substr(s, i, 2) == "/*") { inblock = 1; i += 2; continue }
		if (c == "\"" || c == "'") { q = c; i++; while (i <= n) { if (substr(s, i, 1) == "\\") { i += 2; continue } if (substr(s, i, 1) == q) { i++; break } i++ } continue }
		out = out c; i++
	}
	return out
}
/^[0-9a-f]{40} blob [0-9]+$/ { cur = path[$1]; ln = 0; d = 0; inblock = 0; nopen = 0; delete start; delete depth; delete name; next }
cur == "" { next }
{
	ln++; s = mask($0)
	if (s ~ /^[ \t]*((public|protected|private|static|final|synchronized|abstract|default|native)[ \t]+)*[A-Za-z_$][A-Za-z0-9_$<>,.?\[\] ]*[ \t]+[A-Za-z_$][A-Za-z0-9_$]*[ \t]*\(/ && s !~ /^[ \t]*(if|for|while|switch|catch|return|new|else|throw|do|try)[ \t(]/ && s !~ /;[ \t]*$/ && s !~ /^[ \t]*(class|interface|enum|record)[ \t]/) {
		m = s; sub(/\(.*/, "", m); sub(/[ \t]+$/, "", m); k = split(m, t, /[ \t]+/); pend = t[k]; pendline = ln
	}
	n = length(s)
	for (i = 1; i <= n; i++) {
		c = substr(s, i, 1)
		if (c == "{") { d++; if (pend != "") { nopen++; start[nopen] = pendline; depth[nopen] = d; name[nopen] = pend; pend = "" } }
		else if (c == "}") {
			if (nopen > 0 && depth[nopen] == d) { body = ln - start[nopen] - 1; if (body > LIMIT) printf "%s\t%d\t%s\t%d\n", cur, start[nopen], name[nopen], body; nopen-- }
			d--
		}
	}
	if (pend != "" && s ~ /;[ \t]*$/) pend = ""
}
