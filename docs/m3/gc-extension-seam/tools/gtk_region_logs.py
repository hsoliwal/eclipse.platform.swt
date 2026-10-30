"""gtk_region_logs.py <Region.java> : insert the I31 RegionLog call into every public add/intersect/subtract/translate
method of the gtk Region, right before the first statement that is not an argument check (if-block, SWT.error, guard
return, closing brace, blank). Byte preserving. Prints one line per insertion; exit 2 if a method got none."""
import re
import sys

path = sys.argv[1]
data = open(path, "rb").read()
nl = b"\r\n" if b"\r\n" in data else b"\n"
lines = data.split(nl)
sig = re.compile(rb"^public void (add|intersect|subtract|translate) ?\((.*)\) \{$")
check = re.compile(rb"^\s*(if \(|SWT\.error\(|\}\s*$|return;|$)")


def arg(params):
    p = params.decode()
    if p.startswith("int[]"):
        return "pointArray"
    if p.startswith("Rectangle"):
        return "rect"
    if p.startswith("Region"):
        return "region"
    if p.startswith("Point"):
        return "pt"
    if p == "int x, int y, int width, int height":
        return "new Rectangle(x, y, width, height)"
    if p == "int x, int y":
        return "new Point(x, y)"
    return None


out, i, done, missing = [], 0, 0, []
while i < len(lines):
    l = lines[i]
    out.append(l)
    m = sig.match(l)
    if m:
        op, a = m.group(1).decode(), arg(m.group(2))
        j = i + 1
        while j < len(lines) and check.match(lines[j]):
            out.append(lines[j])
            j += 1
        if a is None or j >= len(lines) or lines[j].strip() == b"}":
            missing.append(f"{op}({m.group(2).decode()})")
        else:
            out.append(f"\tlog.{op}({a});".encode())
            done += 1
            print(f"INSERT line {j + 1}: log.{op}({a}) before {lines[j].strip()[:60].decode(errors='replace')!r}")
        i = j
        continue
    i += 1
open(path, "wb").write(nl.join(out))
print(f"inserted={done} missing={missing}")
sys.exit(2 if missing else 0)
