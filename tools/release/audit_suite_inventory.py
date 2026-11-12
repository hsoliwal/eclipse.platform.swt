#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Review default SWT JUnit suite reachability without hiding existing omissions.

A strict *preflight* ratchets the known omission baseline: newly unreferenced
test-like classes or disappearing baseline sources are errors. Release mode
requires ZERO unreferenced test-like classes, but this is not a substitute for
Surefire execution receipts or line/branch/native coverage.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

SUITE_PATTERN = re.compile(r"@SelectClasses\s*\(\s*\{(?P<body>.*?)\}\s*\)", re.DOTALL)
CLASS_PATTERN = re.compile(r"\b([A-Za-z_][A-Za-z_0-9]*)\.class\b")
TEST_DIR = Path("tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit")
DEFAULT_BASELINE = Path("tools/release/unreferenced-suite-baseline.txt")


def selections(source: str) -> list[str]:
    match = SUITE_PATTERN.search(source)
    if not match:
        return []
    without_blocks = re.sub(r"/\*.*?\*/", "", match.group("body"), flags=re.DOTALL)
    without_comments = re.sub(r"//[^\n]*", "", without_blocks)
    return CLASS_PATTERN.findall(without_comments)


def fingerprint(root: Path, paths: set[Path]) -> str:
    """Domain-separated exact-content digest of one stable path set.

    Records contain the POSIX relative path, byte length and SHA-256 of each
    file. Never treat the digest as executed tests, a line coverage statistic,
    or a replacement for an exact Git commit/tree receipt.
    """
    digest = hashlib.sha256(b"SWT_RELEASE_SOURCE_SET_V1\0")
    for relative in sorted(paths, key=lambda p: p.as_posix()):
        name = relative.as_posix().encode("utf-8")
        data = (root / relative).read_bytes()
        digest.update(len(name).to_bytes(4, "big"))
        digest.update(name)
        digest.update(len(data).to_bytes(8, "big"))
        digest.update(hashlib.sha256(data).digest())
    return digest.hexdigest()


def audit(root: Path, baseline: Path = DEFAULT_BASELINE) -> dict:
    root = root.resolve()
    directory = root / TEST_DIR
    base = baseline if baseline.is_absolute() else root / baseline
    if not directory.is_dir():
        raise FileNotFoundError(directory)
    if not base.is_file():
        raise FileNotFoundError("reviewed baseline required: " + str(base))
    existing = {file.stem for file in directory.glob("*.java") if file.is_file()}
    candidates = {name for name in existing
                  if name.startswith("Test_") or name.endswith("Test")}
    remembered = {line.strip() for line in base.read_text(encoding="utf-8").splitlines()
                  if line.strip() and not line.lstrip().startswith("#")}
    registered: set[str] = set()
    suite_nodes: dict[str, list[str]] = {}
    missing_references: set[str] = set()
    to_visit = ["AllTests"]
    while to_visit:
        suite = to_visit.pop()
        if suite in suite_nodes:
            continue
        file = directory / (suite + ".java")
        if not file.is_file():
            missing_references.add(suite)
            continue
        source = file.read_text(encoding="utf-8")
        if not SUITE_PATTERN.search(source):
            missing_references.add(suite)
            continue
        selected = selections(source)
        suite_nodes[suite] = selected
        for name in selected:
            linked = directory / (name + ".java")
            if not linked.is_file():
                missing_references.add(name)
            elif SUITE_PATTERN.search(linked.read_text(encoding="utf-8")):
                to_visit.append(name)
            else:
                registered.add(name)
    unreferenced = candidates - registered
    new_omissions = unreferenced - remembered
    missing_baseline_sources = remembered - candidates
    nonregression_pass = bool(candidates) and not (
        new_omissions or missing_baseline_sources or missing_references)
    candidate_files = {TEST_DIR / (name + ".java") for name in candidates}
    suite_files = {TEST_DIR / (name + ".java") for name in suite_nodes}
    return {
        "schema": "SWT_DEFAULT_SUITE_INVENTORY_V1",
        "candidate_sources_sha256": fingerprint(root, candidate_files),
        "suite_sources_sha256": fingerprint(root, suite_files),
        "reviewed_baseline_sha256": hashlib.sha256(base.read_bytes()).hexdigest(),
        "scope": "single JUnit Tests directory; candidate file names Test_* or *Test",
        "root_suite": "AllTests",
        "candidate_classes": len(candidates),
        "reachable_candidate_classes": len(candidates & registered),
        "unreferenced_candidate_classes": len(unreferenced),
        "unreferenced": sorted(unreferenced),
        "new_omissions": sorted(new_omissions),
        "baseline_source_disappearances": sorted(missing_baseline_sources),
        "missing_suite_references": sorted(missing_references),
        "suites_seen": {key: len(value) for key, value in sorted(suite_nodes.items())},
        "nonregression_pass": nonregression_pass,
        "suite_complete": nonregression_pass and not unreferenced,
        "disclaimer": (
            "not observed test execution; focused CI may run unreferenced classes; "
            "other SWT test fragments are not in this inventory"
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--strict", action="store_true",
                        help="refuse new omissions, removed baseline files and missing references")
    parser.add_argument("--release", action="store_true",
                        help="additionally require zero omissions (still not full release readiness)")
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()
    report = audit(args.root, args.baseline)
    output = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output, encoding="utf-8")
    print(output, end="")
    return 0 if ((not args.strict or report["nonregression_pass"])
                 and (not args.release or report["suite_complete"])) else 1


if __name__ == "__main__":
    raise SystemExit(main())
