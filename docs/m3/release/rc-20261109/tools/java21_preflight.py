#!/usr/bin/env python3
"""Java 21 preflight: BREE of every tracked MANIFEST.MF <= JavaSE-21, maven.compiler.release values <= 21,
class-file major version of every class in the jar <= 65.

Usage: java21_preflight.py <worktree> <jar> <out.tsv>   -> exit 0 PASS, 1 FAIL. (Javac --release 21 is a separate gate.)
"""
import re
import subprocess
import sys
import zipfile

MAX_RELEASE, MAX_MAJOR = 21, 65


def tracked(root, pattern):
    out = subprocess.run(["git", "-C", root, "ls-files", "-z", pattern], capture_output=True).stdout
    return [p for p in out.decode("utf-8").split("\0") if p]


def main():
    root, jar, out = sys.argv[1:4]
    rows, fail = [], 0
    for path in tracked(root, "*MANIFEST.MF"):
        if path.startswith((".superset/", "docs/")):
            continue
        text = open(f"{root}/{path}", encoding="utf-8", errors="replace").read()
        for m in re.finditer(r"Bundle-RequiredExecutionEnvironment:\s*([^\r\n]+(?:\r?\n [^\r\n]+)*)", text):
            for ee in re.sub(r"\r?\n ", "", m.group(1)).split(","):
                ee = ee.strip()
                v = re.fullmatch(r"JavaSE-(\d+)(?:\.\d+)?", ee)
                ok = bool(v) and int(v.group(1)) <= MAX_RELEASE
                fail += not ok
                rows.append(("BREE", path, ee, "PASS" if ok else "FAIL"))
    for path in tracked(root, "*pom.xml"):
        if path.startswith((".superset/", "docs/")):
            continue
        text = open(f"{root}/{path}", encoding="utf-8", errors="replace").read()
        for tag in ("maven.compiler.release", "maven.compiler.target", "maven.compiler.source", "release"):
            for m in re.finditer(rf"<{re.escape(tag)}>\s*([^<\s]+)\s*</{re.escape(tag)}>", text):
                val = m.group(1)
                if not re.fullmatch(r"\d+", val):
                    rows.append(("POM", path, f"{tag}={val}", "INFO_PROPERTY"))
                    continue
                ok = int(val) <= MAX_RELEASE
                fail += not ok
                rows.append(("POM", path, f"{tag}={val}", "PASS" if ok else "FAIL"))
    majors = {}
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith(".class"):
                head = z.read(name)[:8]
                majors.setdefault(int.from_bytes(head[6:8], "big"), []).append(name)
    for major, names in sorted(majors.items()):
        ok = major <= MAX_MAJOR
        fail += not ok
        rows.append(("CLASSFILE", jar.rsplit("/", 1)[-1], f"major={major} classes={len(names)}", "PASS" if ok else "FAIL"))
    with open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("check\tpath\tvalue\tverdict\n")
        fh.writelines("\t".join(r) + "\n" for r in rows)
    print(f"java21-preflight rows={len(rows)} failures={fail}")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
