#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Read-only SWT naming and reported-test surface gate; never a full release receipt."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
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
    """Count executed JUnit testcases, refusing fabricated/inconsistent suite totals.

    Directory inputs must contain TEST-*.xml (Surefire/Tycho naming).
    The returned file digests allow a separate source-bound evidence manifest
    to identify the exact XML bytes accepted by this read-only parser.
    """
    files = sorted(path.rglob("TEST-*.xml")) if path.is_dir() else [path]
    if not files:
        raise ValueError("no TEST-*.xml JUnit reports: " + str(path))
    totals = dict(tests=0, failures=0, errors=0, skipped=0, reports=0, files=[])
    for file in files:
        root = ET.parse(file).getroot()
        if root.tag not in ("testsuite", "testsuites"):
            raise ValueError("not a JUnit XML test suite: " + str(file))
        if root.tag == "testsuites" and root.find(".//testsuite") is None:
            raise ValueError("empty JUnit XML suite collection: " + str(file))
        cases = list(root.iter("testcase"))
        if not cases:
            raise ValueError("no executed testcases in: " + str(file))
        counts = dict(tests=len(cases), failures=0, errors=0, skipped=0)
        for case in cases:
            if not case.get("name"):
                raise ValueError("JUnit testcase has no name: " + str(file))
            # One testcase can report several XML failure elements; count the
            # testcase once, with error > failure > skipped precedence.
            if case.find("error") is not None:
                counts["errors"] += 1
            elif case.find("failure") is not None:
                counts["failures"] += 1
            elif case.find("skipped") is not None:
                counts["skipped"] += 1
        # Validate reported counters at the outermost scope only. Nested suites
        # can report roll-up counts and must not be counted a second time.
        summary = root.attrib
        if root.tag == "testsuites" and not any(k in summary for k in counts):
            suites = root.findall("testsuite")
            if suites and all("tests" in suite.attrib for suite in suites):
                summary = {key: str(sum(int(suite.get(key, "0"))
                                        for suite in suites))
                           for key in counts}
        for key, computed in counts.items():
            if key in summary:
                declared = int(summary[key])
                if declared < 0 or declared != computed:
                    raise ValueError(f"JUnit {key} counter mismatch: {file} "
                                     f"declared={declared} observed={computed}")
            totals[key] += computed
        totals["reports"] += 1
        totals["files"].append({"path": str(file.relative_to(path)) if path.is_dir() else file.name,
                                 "sha256": hashlib.sha256(file.read_bytes()).hexdigest()})
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
    locations = [str(path.resolve()) for path in junit.values()]
    if len(set(locations)) != len(locations):
        issues.append("duplicate backend JUnit report location")
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
    if not (math.isfinite(args.min_line) and math.isfinite(args.min_branch)
            and 0 <= args.min_line <= 1 and 0 <= args.min_branch <= 1):
        parser.error("coverage minimum must be finite and between zero and one")
    if args.release and (args.min_line < .90 or args.min_branch < .90):
        parser.error("release minimum cannot be lowered below 90% line and branch")
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
