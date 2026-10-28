#!/usr/bin/env python
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0
"""Requirements from PR history: every PR (open, merged, closed) with title and body, every issue comment and
review comment of the repo (repo-wide endpoints: no per-PR calls), then the requirement sentences:
the owner's own comments and bodies in full, and from agents' text the sentences that quote the user or state
a requirement, invariant, law, rule or must. Usage: harvest_prs.py <owner/repo> [since-ISO-date]
Output: prs_all.<repo>.jsonl, comments.<repo>.jsonl, REQUIREMENTS_PR.<repo>.tsv (repo pr source author ts text)"""
import json, re, subprocess, sys, os
repo = sys.argv[1]; since = sys.argv[2] if len(sys.argv) > 2 else ""; name = repo.split("/")[1]; here = os.path.dirname(os.path.abspath(__file__))
OWNER = "hsoliwal"
def gh(path):
    p = subprocess.run(["gh", "api", "--paginate", "--slurp", path], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0: raise RuntimeError(p.stderr.strip()[:200])
    return [x for page in json.loads(p.stdout) for x in page]
prs = gh(f"repos/{repo}/pulls?state=all&per_page=100&sort=created&direction=desc")
with open(os.path.join(here, f"prs_all.{name}.jsonl"), "w", encoding="utf-8") as f:
    for p in prs:
        f.write(json.dumps({"number": p["number"], "state": p["state"], "merged_at": p.get("merged_at"), "created_at": p["created_at"], "user": p["user"]["login"], "title": p["title"], "body": p.get("body") or ""}) + "\n")
q = f"&since={since}" if since else ""
comments = gh(f"repos/{repo}/issues/comments?per_page=100{q}") + gh(f"repos/{repo}/pulls/comments?per_page=100{q}")
with open(os.path.join(here, f"comments.{name}.jsonl"), "w", encoding="utf-8") as f:
    for c in comments:
        n = (c.get("issue_url") or c.get("pull_request_url") or "").rsplit("/", 1)[-1]
        f.write(json.dumps({"pr": n, "user": c["user"]["login"], "created_at": c["created_at"], "body": c.get("body") or ""}) + "\n")
REQ = re.compile(r"\b(user|owner|hitesh)\b.{0,40}\b(said|asked|wants|want|requires|required|directive|decided|decision|rule|ruled|instruct)|\b(requirement|invariant|hard limit|non-negotiable|must never|must not|must\b|never\b|always\b|law\b|rule \d+|standing)", re.I)
def sentences(text):
    text = re.sub(r"```.*?```", " ", text, flags=re.S)
    for s in re.split(r"(?<=[.!?])\s+|\n+", text):
        s = s.strip(" -*#>|")
        if 20 <= len(s) <= 600: yield s
rows, seen = [], set()
def add(pr, source, author, ts, text):
    key = re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()
    if key in seen: return
    seen.add(key); rows.append((name, str(pr), source, author, ts[:16], text.replace("\t", " ").replace("\n", " ")))
for p in prs:
    body = p.get("body") or ""
    if p["user"]["login"] == OWNER:
        for s in sentences(p["title"] + ". " + body): add(p["number"], "pr-body-owner", OWNER, p["created_at"], s)
    else:
        for s in sentences(body):
            if REQ.search(s): add(p["number"], "pr-body", p["user"]["login"], p["created_at"], s)
for c in comments:
    n = (c.get("issue_url") or c.get("pull_request_url") or "").rsplit("/", 1)[-1]
    body = c.get("body") or ""
    if c["user"]["login"] == OWNER:
        for s in sentences(body): add(n, "comment-owner", OWNER, c["created_at"], s)
    else:
        for s in sentences(body):
            if REQ.search(s): add(n, "comment", c["user"]["login"], c["created_at"], s)
with open(os.path.join(here, f"REQUIREMENTS_PR.{name}.tsv"), "w", encoding="utf-8", newline="\n") as f:
    f.write("repo\tpr\tsource\tauthor\tts\ttext\n")
    for r in rows: f.write("\t".join(r) + "\n")
owner_rows = sum(1 for r in rows if r[3] == OWNER)
print(f"{name}: {len(prs)} PRs, {len(comments)} comments, {len(rows)} requirement sentences ({owner_rows} by the owner)")
