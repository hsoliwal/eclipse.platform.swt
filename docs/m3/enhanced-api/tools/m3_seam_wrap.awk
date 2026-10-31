# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Text-lane recipe of the M3 seams (the win32 shape of leaf 5): a top-level method whose signature line equals SIG
# keeps its name, Javadoc and callers and becomes a wrapper that calls its former body, now the package-private
# method HELPER placed right after it, and then the seam. KIND=changed: "HELPER (args); M3Emptiness.changed (this);";
# KIND=hold: "M3Emptiness.hold (this); try { HELPER (args); } finally { M3Emptiness.release (this); }".
# KIND=observe: "HELPER (args); M3VisibilityObserver.changed (this);" (a scroll the observer must see).
# The line ending of the signature line (LF or CRLF) is kept on every emitted line.
# Fixed point: a signature already followed by its wrapper is left as it is (second pass UNCHANGED).
# Usage: awk -v BINMODE=3 -v SIG='public void add (String string) {' -v HELPER=addItem -v KIND=changed -f m3_seam_wrap.awk F
function args(sig,   inner, n, parts, i, words, w, out) {
	inner = sig; sub(/^[^(]*\(/, "", inner); sub(/\)[^)]*$/, "", inner)
	if (inner ~ /^[ \t]*$/) return ""
	n = split(inner, parts, ","); out = ""
	for (i = 1; i <= n; i++) {
		w = split(parts[i], words, /[ \t.\[\]]+/)
		while (w > 1 && words[w] == "") w--
		out = out (i > 1 ? ", " : "") words[w]
	}
	return out
}
function helper(sig,   h) {
	h = sig; sub(/^public /, "", h); sub(/^void [A-Za-z0-9_]+ /, "void " HELPER " ", h); return h
}
function out(text) {
	printf "%s%s\n", text, eol
}
function emit(   a) {
	a = args(SIG)
	if (KIND == "hold") {
		out("\tM3Emptiness.hold (this);"); out("\ttry {"); out("\t\t" HELPER " (" a ");")
		out("\t} finally {"); out("\t\tM3Emptiness.release (this);"); out("\t}")
	} else {
		out("\t" HELPER " (" a ");"); out(KIND == "observe" ? "\tM3VisibilityObserver.changed (this);" : "\tM3Emptiness.changed (this);")
	}
	out("}"); out(""); out(helper(SIG))
}
{ line = $0; cr = sub(/\r$/, "", line) }
pending {
	pending = 0
	if (index(line, HELPER " (") == 0 && index(line, "M3Emptiness") == 0 && index(line, "M3VisibilityObserver") == 0) emit()
	print; next
}
line == SIG { eol = cr ? "\r" : ""; print; pending = 1; next }
{ print }
