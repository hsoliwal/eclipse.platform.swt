#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Read-only SWT naming and reported-test surface gate; never a full release receipt."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

BACKENDS = ("gtk3-x11", "gtk4-x11", "gtk4-wayland",
            "win32", "cocoa-x86_64", "cocoa-aarch64")
REQUIRED = (
    "README.md", "docs/RELEASE_READINESS.md", "docs/DEVELOPMENT_GUIDE.md",
    "NOTICE", "LICENSE", "bundles/org.eclipse.swt/META-INF/MANIFEST.MF",
    ".github/workflows/maven.yml", ".github/workflows/build.yml",
    ".github/workflows/pr-checks.yml", ".github/workflows/junit.yml",
    "tests/org.eclipse.swt.tests",
    "tests/org.eclipse.swt.tests.gtk",
    "tests/org.eclipse.swt.tests.win32",
    "tests/org.eclipse.swt.tests.cocoa",
)


def inspect_source(root: Path) -> list[str]:
    issues = [f"missing {path}" for path in REQUIRED if not (root / path).exists()]
    manifest = root / "bundles/org.eclipse.swt/META-INF/MANIFEST.MF"
    if not manifest.is_file():
        return issues
    content = manifest.read_text(encoding="utf-8")
    for exact in ("Bundle-SymbolicName: org.eclipse.swt;",
                  "Automatic-Module-Name: org.eclipse.swt",
                  "Bundle-RequiredExecutionEnvironment: JavaSE-21",
                  " org.eclipse.swt.widgets", " org.eclipse.swt.graphics"):
        if exact not in content:
            issues.append("missing SWT naming/export invariant: " + exact)
    return issues


def junit_report(path: Path) -> dict:
    """Aggregate leaf/outer JUnit suites without double-counting nested suites."""
    files = sorted(path.rglob("TEST-*.xml")) if path.is_dir() else [path]
    if path.is_dir() and not files:
        files = sorted(path.rglob("*.xml"))
    if not files:
        raise ValueError("no JUnit XML reports: " + str(path))
    totals = dict(tests=0, failures=0, errors=0, skipped=0, reports=0)
    for file in files:
        root = ET.parse(file).getroot()
        if root.tag == "testsuite":
            suites = [root]
        elif root.tag == "testsuites":
            suites = list(root.findall("testsuite"))
        else:
            raise ValueError("not a JUnit XML test suite: " + str(file))
        if not suites:
            raise ValueError("empty JUnit XML: " + str(file))
        for suite in suites:
            for key in ("tests", "failures", "errors", "skipped"):
                value = int(suite.get(key, "0"))
                if value < 0:
                    raise ValueError("negative JUnit count: " + str(file))
                totals[key] += value
        totals["reports"] += 1
    if totals["tests"] == 0:
        raise ValueError("zero executed tests: " + str(path))
    if totals["tests"] < totals["failures"] + totals["errors"] + totals["skipped"]:
        raise ValueError("invalid JUnit outcome counts: " + str(path))
    return totals


def jacoco_report(path: Path) -> dict:
    root = ET.parse(path).getroot()
    if root.tag != "report":
        raise ValueError("not a JaCoCo report")
    counters = {x.get("type"): x for x in root.findall("counter")}
    result = {}
    for kind in ("LINE", "BRANCH"):
        if kind not in counters:
            raise ValueError("missing JaCoCo " + kind + " counter")
        item = counters[kind]
        covered, missed = int(item.get("covered", "-1")), int(item.get("missed", "-1"))
        if min(covered, missed) < 0 or covered + missed == 0:
            raise ValueError("invalid/empty JaCoCo " + kind + " counter")
        result[kind.lower()] = {"covered": covered, "missed": missed,
                                "ratio": covered / (covered + missed)}
    result["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def evaluate(root: Path, junit: dict[str, Path], jacoco: Path | None,
             release: bool, min_line: float, min_branch: float) -> dict:
    issues = inspect_source(root)
    summary = {"schema": "SWT_RELEASE_SURFACE_V1", "mode": "release" if release else "source",
               "scope": "source naming and supplied XML results only; not a native/UI or full release admission",
               "source_issues": issues, "junit": {}, "jacoco": None,
               "pending_backends": [], "surface_ready": False}
    for backend in sorted(junit):
        if backend not in BACKENDS:
            issues.append("unknown backend: " + backend)
            continue
        try:
            report = junit_report(junit[backend])
            summary["junit"][backend] = report
            if report["failures"] or report["errors"]:
                issues.append("failed JUnit: " + backend)
        except (OSError, ValueError, ET.ParseError) as exc:
            issues.append(f"JUnit {backend}: {exc}")
    summary["pending_backends"] = [b for b in BACKENDS if b not in summary["junit"]]
    if jacoco is not None:
        try:
            summary["jacoco"] = jacoco_report(jacoco)
            for kind, threshold in (("line", min_line), ("branch", min_branch)):
                if summary["jacoco"][kind]["ratio"] < threshold:
                    issues.append(f"{kind} coverage below {threshold:.2%}")
        except (OSError, ValueError, ET.ParseError) as exc:
            issues.append(f"JaCoCo: {exc}")
    if release:
        if summary["pending_backends"]:
            issues.append("missing platform JUnit reports: " + ", ".join(summary["pending_backends"]))
        if summary["jacoco"] is None:
            issues.append("instrumented line/branch coverage report missing")
    summary["source_issues"] = issues
    summary["surface_ready"] = release and not issues
    return summary


def parse_label_path(argument: str) -> tuple[str, Path]:
    name, sep, value = argument.partition("=")
    if not sep or not name or not value or name not in BACKENDS:
        raise argparse.ArgumentTypeError("expected backend=path (known SWT backend)")
    return name, Path(value)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--junit", type=parse_label_path, action="append", default=[])
    parser.add_argument("--jacoco", type=Path)
    parser.add_argument("--min-line", type=float, default=0.90)
    parser.add_argument("--min-branch", type=float, default=0.90)
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args(argv)
    if not (0 <= args.min_line <= 1 and 0 <= args.min_branch <= 1):
        parser.error("coverage minimum must be between zero and one")
    label_paths = dict(args.junit)
    if len(label_paths) != len(args.junit):
        parser.error("duplicate backend label")
    result = evaluate(args.root.resolve(), label_paths, args.jacoco,
                      args.release, args.min_line, args.min_branch)
    data = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(data, encoding="utf-8")
    print(data, end="")
    return 1 if result["source_issues"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
