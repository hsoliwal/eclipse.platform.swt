#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Require real Surefire XML evidence for the M3 sparse-state JUnit tests.

This is an execution gate, not a statement/branch coverage percentage, and
cannot substitute for UI/native/ABI test results on the corresponding platform.
"""
from __future__ import annotations

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED = ("Test_ItemBitSetDifferential", "Test_ItemBitSetMaximumPosition")


def audit(reports_root: Path) -> dict:
    reports = sorted(reports_root.glob("TEST-*.xml")) if reports_root.is_dir() else []
    counts = {name: {"passed": 0, "failed": 0, "skipped": 0} for name in REQUIRED}
    # All testcase observations make CI execution breadth measurable without
    # confusing class/test counts with instrumented statement/branch coverage.
    total = {"passed": 0, "failed": 0, "skipped": 0}
    observed = {}
    errors = []
    for file in reports:
        try:
            root = ET.parse(file).getroot()
        except ET.ParseError as error:
            errors.append(f"{file.name}: {error}")
            continue
        for case in root.iter():
            if case.tag.rsplit("}", 1)[-1] != "testcase":
                continue
            full_name = case.get("classname", "").strip()
            name = full_name.rsplit(".", 1)[-1]
            kinds = {child.tag.rsplit("}", 1)[-1] for child in case}
            status = ("failed" if kinds & {"failure", "error"} else
                      "skipped" if "skipped" in kinds else "passed")
            total[status] += 1
            if full_name:
                counters = observed.setdefault(
                    full_name, {"passed": 0, "failed": 0, "skipped": 0})
                counters[status] += 1
            if name in counts:
                counts[name][status] += 1
    valid = bool(reports) and not errors and all(
        stats["passed"] > 0 and stats["failed"] == 0 and stats["skipped"] == 0
        for stats in counts.values()
    )
    return {
        "schema": "SWT_M3_JUNIT_EXECUTION_V1",
        "scope": "Surefire XML for two mandatory registered JUnit classes",
        "reports_read": len(reports),
        "testcase_counts": counts,
        "all_junit_testcases": total,
        "observed_junit_classes": len(observed),
        "failed_class_names": sorted(name for name, counts in observed.items()
                                     if counts["failed"] > 0),
        "skipped_class_names": sorted(name for name, counts in observed.items()
                                      if counts["skipped"] > 0),
        "parse_errors": errors,
        "passed": valid,
        "disclaimer": "does not establish complete SWT test/line coverage or native UI parity",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports-root", type=Path, required=True)
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    report = audit(args.reports_root)
    output = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output, encoding="utf-8")
    print(output, end="")
    return 0 if report["passed"] or not args.strict else 1


if __name__ == "__main__":
    raise SystemExit(main())
