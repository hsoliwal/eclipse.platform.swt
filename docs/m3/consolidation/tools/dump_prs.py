#!/usr/bin/env python
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0
"""Dump every open PR of a repo through the REST API: list, mergeable state (GitHub computes it lazily: a null is
re-read once after a pause), changed files. Output: prs.<repo>.json (list of dicts) and files.<repo>.tsv.
Usage: dump_prs.py <owner/repo>"""
import json, subprocess, sys, time, os

def gh(*args):
    p = subprocess.run(["gh", "api"] + list(args), capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip()[:200])
    return p.stdout

repo = sys.argv[1]; name = repo.split("/")[1]; here = os.path.dirname(os.path.abspath(__file__))
raw = gh("--paginate", "--slurp", f"repos/{repo}/pulls?state=open&per_page=100")
pages = json.loads(raw); prs = [p for page in pages for p in page]
out, files = [], []
for i, p in enumerate(prs, 1):
    n = p["number"]; d = {"number": n, "title": p["title"], "head": p["head"]["ref"], "head_sha": p["head"]["sha"], "base": p["base"]["ref"],
                          "draft": p["draft"], "created_at": p["created_at"], "updated_at": p["updated_at"], "user": p["user"]["login"],
                          "labels": [l["name"] for l in p["labels"]]}
    try:
        one = json.loads(gh(f"repos/{repo}/pulls/{n}"))
        if one.get("mergeable") is None:
            time.sleep(2); one = json.loads(gh(f"repos/{repo}/pulls/{n}"))
        d.update({"mergeable": one.get("mergeable"), "mergeable_state": one.get("mergeable_state"), "additions": one.get("additions"),
                  "deletions": one.get("deletions"), "changed_files": one.get("changed_files"), "commits": one.get("commits")})
    except RuntimeError as e:
        d.update({"mergeable": "ERR", "mergeable_state": str(e)})
    try:
        fl = json.loads(gh("--paginate", "--slurp", f"repos/{repo}/pulls/{n}/files?per_page=100"))
        for page in fl:
            for f in page:
                files.append((n, f["filename"], f["status"], f.get("additions", 0), f.get("deletions", 0)))
    except RuntimeError as e:
        files.append((n, "ERR", str(e), 0, 0))
    out.append(d)
    if i % 25 == 0: print(f"{name}: {i}/{len(prs)}", file=sys.stderr, flush=True)
json.dump(out, open(os.path.join(here, f"prs.{name}.json"), "w", encoding="utf-8"), indent=0)
with open(os.path.join(here, f"files.{name}.tsv"), "w", encoding="utf-8", newline="\n") as f:
    f.write("number\tpath\tstatus\tadditions\tdeletions\n")
    for r in files: f.write("\t".join(str(c) for c in r) + "\n")
print(f"{name}: {len(out)} PRs, {len(files)} file rows")
