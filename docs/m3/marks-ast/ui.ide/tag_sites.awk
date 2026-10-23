# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Annotate pass (MOP pillar 3, CNPCDA): the compiler-AST marks become inline anchors at the marked lines, so the
# agent that fixes a file reads what needs fixing in the file itself. One anchor per (line, template), appended at
# the end of the line (no line numbers shift), in the CNPCDA form that survives every refactoring pass:
#   //FLLDCIM: MARK — <template> <target> <kind>
# Fixed point: a line that already carries the anchor for that template is left alone. Lines javac did not
# attribute to a tree start are never inside a text block, so appending a comment is safe.
# Usage: awk -v BINMODE=3 -v MARKS=<file-marks.tsv (line<TAB>template<TAB>target<TAB>kind)> -f tag_sites.awk File.java
BEGIN { while ((getline l < MARKS) > 0) { split(l, a, "\t"); k = a[1] "\t" a[2]; if (!(k in seen)) { seen[k] = 1; tag[a[1]] = tag[a[1]] " //FLLDCIM: MARK \xe2\x80\x94 " a[2] " " a[3] " " a[4] } } }
{
	line = $0; cr = ""; if (line ~ /\r$/) { cr = "\r"; sub(/\r$/, "", line) }
	if (FNR in tag) {
		n = split(tag[FNR], parts, " //FLLDCIM: "); add = ""
		for (i = 2; i <= n; i++) { t = "//FLLDCIM: " parts[i]; split(parts[i], w, " "); if (index(line, "//FLLDCIM: MARK \xe2\x80\x94 " w[3] " ") == 0) add = add " " t }
		line = line add
	}
	printf "%s%s%s", line, cr, RT
}
