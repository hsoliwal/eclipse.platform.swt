#!/usr/bin/env python
# Re-fetch the PRs whose mergeable state or file list failed in dump_prs.py (network errors), in place.
# Usage: redump_failed.py <owner/repo>
import json, subprocess, sys, time, os
repo = sys.argv[1]; name = repo.split("/")[1]; here = os.path.dirname(os.path.abspath(__file__))
def gh(*args):
    p = subprocess.run(["gh", "api"] + list(args), capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0: raise RuntimeError(p.stderr.strip()[:200])
    return p.stdout
pp = os.path.join(here, f"prs.{name}.json"); fp = os.path.join(here, f"files.{name}.tsv")
prs = json.load(open(pp, encoding="utf-8"))
rows = [l.rstrip("\n").split("\t") for l in open(fp, encoding="utf-8")]
good = [r for r in rows if r and r[0].isdigit() and len(r) >= 5 and r[1] != "ERR"]
have_files = {int(r[0]) for r in good}
failed = [p for p in prs if str(p.get("mergeable_state", "")).startswith("error") or p.get("mergeable") == "ERR" or p["number"] not in have_files]
print(f"{name}: {len(failed)} PRs to re-fetch", file=sys.stderr)
for i, p in enumerate(failed, 1):
    n = p["number"]
    for attempt in range(3):
        try:
            one = json.loads(gh(f"repos/{repo}/pulls/{n}"))
            if one.get("mergeable") is None: time.sleep(2); one = json.loads(gh(f"repos/{repo}/pulls/{n}"))
            p.update({"mergeable": one.get("mergeable"), "mergeable_state": one.get("mergeable_state"), "additions": one.get("additions"),
                      "deletions": one.get("deletions"), "changed_files": one.get("changed_files"), "commits": one.get("commits")})
            if n not in have_files:
                fl = json.loads(gh("--paginate", "--slurp", f"repos/{repo}/pulls/{n}/files?per_page=100"))
                for page in fl:
                    for f in page: good.append([str(n), f["filename"], f["status"], str(f.get("additions", 0)), str(f.get("deletions", 0))])
            break
        except RuntimeError as e:
            time.sleep(5 * (attempt + 1))
    if i % 20 == 0: print(f"{name}: {i}/{len(failed)}", file=sys.stderr, flush=True)
json.dump(prs, open(pp, "w", encoding="utf-8"), indent=0)
with open(fp, "w", encoding="utf-8", newline="\n") as f:
    f.write("number\tpath\tstatus\tadditions\tdeletions\n")
    for r in good: f.write("\t".join(r[:5]) + "\n")
left = sum(1 for p in prs if str(p.get("mergeable_state", "")).startswith("error"))
print(f"{name}: re-fetched; {left} still failing; {len(good)} file rows")
