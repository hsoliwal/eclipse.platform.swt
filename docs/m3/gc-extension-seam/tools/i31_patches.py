"""i31_patches.py <outdir> <commit>... : for each swt-initiative31/skija-canvas commit (oldest first) write one
unified diff of its core-bundle files (bundles/org.eclipse.swt/**, not the org.eclipse.swt.skia bundle), ready for
`git apply --3way`, plus DONOR_PINS.tsv rows (commit, status, path, blob sha, additions, deletions)."""
import json
import os
import subprocess
import sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
pins = open(os.path.join(out, "DONOR_PINS.tsv"), "w", encoding="utf-8", newline="\n")
pins.write("commit\tstatus\tpath\tblob_sha\tadditions\tdeletions\n")
for commit in sys.argv[2:]:
    data = json.loads(subprocess.run(["gh", "api", f"repos/swt-initiative31/skija-canvas/commits/{commit}"],
                                     capture_output=True, check=True).stdout)
    parts = []
    for f in data["files"]:
        p = f["filename"]
        if not p.startswith("bundles/org.eclipse.swt/") or "org.eclipse.swt.skia" in p:
            continue
        pins.write(f"{data['sha']}\t{f['status']}\t{p}\t{f.get('sha', '')}\t{f['additions']}\t{f['deletions']}\n")
        patch = f.get("patch")
        if patch is None:
            continue
        if f["status"] == "added":
            head = f"diff --git a/{p} b/{p}\nnew file mode 100644\n--- /dev/null\n+++ b/{p}\n"
        elif f["status"] == "removed":
            head = f"diff --git a/{p} b/{p}\ndeleted file mode 100644\n--- a/{p}\n+++ /dev/null\n"
        elif f["status"] == "renamed":
            prev = f["previous_filename"]
            head = f"diff --git a/{prev} b/{p}\nrename from {prev}\nrename to {p}\n--- a/{prev}\n+++ b/{p}\n"
        else:
            head = f"diff --git a/{p} b/{p}\n--- a/{p}\n+++ b/{p}\n"
        parts.append(head + patch + ("\n" if not patch.endswith("\n") else ""))
    path = os.path.join(out, f"{commit}.diff")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("".join(parts))
    print(commit, len(parts), "files")
pins.close()
