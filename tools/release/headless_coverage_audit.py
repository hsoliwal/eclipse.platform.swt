#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Read-only source-pinned audit of one focused SWT JaCoCo receipt.

An observed headless pass, source file hash or coverage ratio is not an SWT
product release: this audit never infers untested native backends or atoms.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re

SHA40 = re.compile(r"[0-9a-f]{40}\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate receipt key: " + key)
        result[key] = value
    return result


def safe_source(root: Path, rel: str) -> Path:
    if not isinstance(rel, str) or not rel or "\\" in rel:
        raise ValueError("invalid source path")
    posix = PurePosixPath(rel)
    if (posix.is_absolute() or ".." in posix.parts or "." in posix.parts
            or not rel.startswith("bundles/org.eclipse.swt/")
            or not rel.endswith(".java")):
        raise ValueError("out-of-scope source path: " + rel)
    target = root.resolve()
    for part in posix.parts:
        target = target / part
        if target.is_symlink():
            raise ValueError("source symlink refused: " + rel)
    if not target.is_file() or not target.resolve().is_relative_to(root.resolve()):
        raise ValueError("source missing or outside repository: " + rel)
    return target


def safe_test_source(root: Path, rel: str) -> Path:
    """Resolve one recorded JUnit test input without path/symlink escape."""
    prefix = "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/"
    if not isinstance(rel, str) or not rel.startswith(prefix) or "\\" in rel:
        raise ValueError("invalid or out-of-scope recorded test input")
    path = PurePosixPath(rel)
    if (path.is_absolute() or ".." in path.parts or "." in path.parts
            or any(not part for part in rel.split("/")) or not rel.endswith(".java")):
        raise ValueError("invalid recorded test path: " + rel)
    node = root.resolve()
    for part in path.parts:
        node = node / part
        if node.is_symlink():
            raise ValueError("recorded test source symlink refused: " + rel)
    if not node.is_file() or not node.resolve().is_relative_to(root.resolve()):
        raise ValueError("recorded test source missing or escaping root: " + rel)
    return node


def counts(raw: dict, name: str) -> tuple[int, int]:
    if not isinstance(raw, dict):
        raise ValueError(name + " counts are missing")
    covered, missed = raw.get("covered"), raw.get("missed")
    if (type(covered) is not int or type(missed) is not int
            or covered < 0 or missed < 0 or covered + missed == 0):
        raise ValueError(name + " needs nonempty integer coverage counts")
    return covered, missed


def evaluate(root: Path, receipt: dict, *, candidate_sha: str | None = None,
             minimum: float = 0.90) -> dict:
    if not math.isfinite(minimum) or not 0.90 <= minimum <= 1:
        raise ValueError("release minimum must be finite and at least 90%")
    if (receipt.get("schema_version") != 1 or receipt.get("product") != "SWT M3"
            or receipt.get("status") != "PASS_FOCUSED_HEADLESS_ONLY"
            or receipt.get("release_status") != "HOLD"):
        raise ValueError("unexpected or self-promoted focused receipt")
    revision = receipt.get("source_commit")
    if not isinstance(revision, str) or not SHA40.fullmatch(revision):
        raise ValueError("missing exact source commit")
    if candidate_sha is not None and not SHA40.fullmatch(candidate_sha):
        raise ValueError("candidate commit must be exact")
    execution = receipt.get("execution")
    if not isinstance(execution, dict):
        raise ValueError("execution ledger missing")
    values = ("tests_found", "tests_started", "tests_succeeded",
              "tests_failed", "tests_skipped", "tests_aborted")
    if any(type(execution.get(k)) is not int or execution[k] < 0 for k in values):
        raise ValueError("invalid executed-test counters")
    if (execution["tests_found"] < execution["tests_started"]
            or execution["tests_started"] == 0
            or execution["tests_started"] != sum(execution[k] for k in
                ("tests_succeeded", "tests_failed", "tests_skipped", "tests_aborted"))):
        raise ValueError("executed-test totals disagree")
    rows = receipt.get("source_counters")
    if not isinstance(rows, list) or not rows:
        raise ValueError("no measured production files")
    seen = set()
    total = {"line": [0, 0], "branch": [0, 0]}
    report = []
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("invalid coverage row")
        rel, claimed = row.get("source"), row.get("source_sha256")
        if not isinstance(rel, str) or rel in seen:
            raise ValueError("duplicate or invalid source path")
        seen.add(rel)
        if not isinstance(claimed, str) or not SHA256.fullmatch(claimed):
            raise ValueError("source SHA-256 missing")
        actual = hashlib.sha256(safe_source(root, rel).read_bytes()).hexdigest()
        if actual != claimed:
            raise ValueError("source identity drift: " + rel)
        item = {"source": rel}
        for kind in ("line", "branch"):
            covered, missed = counts(row.get(kind), kind)
            total[kind][0] += covered
            total[kind][1] += missed
            denominator = covered + missed
            needed = max(0, math.ceil(minimum * denominator - 1e-10) - covered)
            item[kind] = {
                "covered": covered, "missed": missed, "total": denominator,
                "ratio": covered / denominator, "additional_needed": needed,
            }
        report.append(item)
    claimed_totals = receipt.get("scoped_totals")
    if not isinstance(claimed_totals, dict):
        raise ValueError("scoped totals missing")
    for kind in ("line", "branch"):
        data = claimed_totals.get(kind)
        if not isinstance(data, dict):
            raise ValueError("missing " + kind + " total")
        covered, missed = total[kind]
        if (type(data.get("total")) is not int or data["total"] != covered + missed
                or data.get("covered") != covered or data.get("missed") != missed
                or type(data.get("percent")) not in (float, int)
                or not math.isfinite(data["percent"])
                or abs(data["percent"] - 100 * covered / (covered + missed)) > 0.0001):
            raise ValueError("reported " + kind + " totals disagree with source rows")
    report.sort(key=lambda x: (-x["branch"]["additional_needed"],
                               -x["line"]["additional_needed"], x["source"]))
    inputs = receipt.get("test_inputs")
    if not isinstance(inputs, list) or not inputs:
        raise ValueError("no recorded test source inputs to verify")
    seen_test_paths = set()
    test_drift = []
    for item in inputs:
        if not isinstance(item, dict):
            raise ValueError("invalid recorded test input item")
        rel, claimed = item.get("path"), item.get("sha256")
        if not isinstance(rel, str) or rel in seen_test_paths:
            raise ValueError("duplicate or missing recorded test path")
        seen_test_paths.add(rel)
        if not isinstance(claimed, str) or not SHA256.fullmatch(claimed):
            raise ValueError("recorded test input SHA-256 missing")
        current = hashlib.sha256(safe_test_source(root, rel).read_bytes()).hexdigest()
        if claimed != current:
            test_drift.append({"path": rel, "receipt_sha256": claimed,
                               "current_sha256": current})
    blockers = []
    if test_drift:
        blockers.append("recorded test inputs changed: rerun the focused JUnit/JaCoCo campaign")
    if candidate_sha != revision:
        blockers.append("historical receipt: source commit is not the exact candidate")
    if execution.get("native_display_created") is not True:
        blockers.append("no actual native display execution")
    if receipt.get("java_compile", {}).get("native_build") != "PASS":
        blockers.append("native build not proved")
    if not all(execution[k] == 0 for k in ("tests_failed", "tests_skipped", "tests_aborted")):
        blockers.append("failures/skips/aborts are not release passes")
    if any(x[kind]["additional_needed"] for x in report for kind in ("line", "branch")):
        blockers.append("one or more measured files are below the per-file line/branch floor")
    blockers.append("focused 10-file headless scope is not complete SWT/JNI/platform coverage")
    return {
        "schema": "SWT_HEADLESS_COVERAGE_GAP_AUDIT_V1",
        "receipt_source_commit": revision, "candidate_sha": candidate_sha,
        "minimum_ratio": minimum, "measured_files": len(report),
        "test_inputs_recorded": len(inputs),
        "test_inputs_matching": len(inputs) - len(test_drift),
        "test_input_drift": test_drift,
        "observed_headless_tests": execution["tests_started"],
        "scoped_line_ratio": total["line"][0] / sum(total["line"]),
        "scoped_branch_ratio": total["branch"][0] / sum(total["branch"]),
        "files": report, "release_ready": False, "release_blockers": blockers,
        "scope": "historical JaCoCo source and test-input audit only; not an executed product gate",
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--candidate-sha", help="exact 40-character candidate Git SHA")
    parser.add_argument("--minimum", type=float, default=0.90)
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--release", action="store_true",
                        help="fail closed if any release blocker remains")
    args = parser.parse_args(argv)
    try:
        receipt = json.loads(args.receipt.read_text(encoding="utf-8"),
                             object_pairs_hook=unique_keys,
                             parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
        result = evaluate(args.root, receipt, candidate_sha=args.candidate_sha,
                          minimum=args.minimum)
    except (OSError, ValueError, TypeError, KeyError) as exc:
        parser.error(str(exc))
    output = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output, encoding="utf-8")
    print(output, end="")
    return 1 if args.release and not result["release_ready"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
