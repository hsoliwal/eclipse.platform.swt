#!/usr/bin/env python
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0
"""Superset merge order for a PR backlog (CPU, deterministic): PRs on the default branch first, mergeable (clean,
unstable, has_hooks, blocked) before dirty (conflicts with the default branch), then oldest first so later PRs
that build on earlier ones follow them; PRs whose base is another PR's head follow that PR. Also the overlap
table (PRs sharing a path) and the track classification by path prefix, for the consolidation report.
Usage: build_order.py <repo-name> <default-branch> -> order.<repo>.tsv, overlaps.<repo>.tsv, tracks.<repo>.tsv"""
import json, sys, os, collections

name, default = sys.argv[1], sys.argv[2]; here = os.path.dirname(os.path.abspath(__file__))
prs = json.load(open(os.path.join(here, f"prs.{name}.json"), encoding="utf-8"))
files = collections.defaultdict(list)
for line in open(os.path.join(here, f"files.{name}.tsv"), encoding="utf-8"):
    c = line.rstrip("\n").split("\t")
    if c[0] != "number" and len(c) >= 2 and c[1] != "ERR": files[int(c[0])].append(c[1])
heads = {p["head"]: p["number"] for p in prs}
rank = {"clean": 0, "unstable": 0, "has_hooks": 0, "blocked": 1, "behind": 1, "unknown": 2, "draft": 2, "dirty": 3}
def key(p): return (0 if p["base"] == default else 1, rank.get(p.get("mergeable_state") or "unknown", 2), p["created_at"])
ordered = sorted(prs, key=key)
# a PR based on another PR's head goes right after that PR
placed, out = set(), []
def place(p):
    if p["number"] in placed: return
    dep = heads.get(p["base"])
    if dep and dep not in placed:
        place(next(q for q in prs if q["number"] == dep))
    placed.add(p["number"]); out.append(p)
for p in ordered: place(p)
with open(os.path.join(here, f"order.{name}.tsv"), "w", encoding="utf-8", newline="\n") as f:
    for p in out: f.write(f'{p["number"]}\t{p["head_sha"]}\t{p["title"].replace(chr(9), " ")}\n')
touched = collections.defaultdict(set)
for n, ps in files.items():
    for path in ps: touched[path].add(n)
with open(os.path.join(here, f"overlaps.{name}.tsv"), "w", encoding="utf-8", newline="\n") as f:
    f.write("path\tprs\tcount\n")
    for path, ns in sorted(touched.items(), key=lambda kv: -len(kv[1])):
        if len(ns) > 1: f.write(f'{path}\t{",".join(str(n) for n in sorted(ns))}\t{len(ns)}\n')
def track(paths):
    c = collections.Counter()
    for p in paths:
        top = p.split("/")[0] if "/" in p else "(root)"
        second = "/".join(p.split("/")[:2]) if p.count("/") >= 1 else top
        c[second if top in ("m3", ".m3", "docs", "src", "synexia-openrewrite-recipes", ".github") else top] += 1
    return c.most_common(1)[0][0] if c else "(no files)"
with open(os.path.join(here, f"tracks.{name}.tsv"), "w", encoding="utf-8", newline="\n") as f:
    f.write("number\tbase\tstate\tfiles\ttrack\tcreated\tuser\ttitle\n")
    for p in out:
        f.write(f'{p["number"]}\t{p["base"]}\t{p.get("mergeable_state")}\t{len(files.get(p["number"], []))}\t{track(files.get(p["number"], []))}\t{p["created_at"][:10]}\t{p["user"]}\t{p["title"][:90]}\n')
states = collections.Counter(p.get("mergeable_state") for p in prs); bases = collections.Counter(p["base"] for p in prs)
print(f"{name}: {len(out)} PRs ordered; states {dict(states)}; bases {bases.most_common(4)}; overlapping paths {sum(1 for v in touched.values() if len(v) > 1)}")
