#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Read-only SWT naming and reported-test surface gate; never a full release receipt."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import subprocess
import xml.etree.ElementTree as ET

BACKENDS = ("gtk3-x11", "gtk4-x11", "gtk4-wayland",
            "win32", "cocoa-x86_64", "cocoa-aarch64")
REPOSITORY = "hsoliwal/eclipse.platform.swt"
BINDING_SCHEMA = "SWT_REPORT_BINDING_V1"
REMAINING_RELEASE_GATES = (
    "producer authenticity and complete build-input provenance",
    "API/ABI and generated JNI compatibility",
    "native rebuilds and six-backend UI/visible-screen qualification",
    "per-atom coverage scope review",
    "CPU, heap, RSS and resource-lifetime qualification",
    "licensing, signed provenance and immutable release publication review",
)
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


def junit_report(path: Path, seen_files: set[tuple[int, int]] | None = None) -> dict:
    """Count executed JUnit testcases, refusing fabricated/inconsistent suite totals.

    Directory inputs must contain TEST-*.xml (Surefire/Tycho naming).
    Parse and hash the same bytes, so the binding manifest identifies the
    exact XML snapshot accepted by this read-only parser.
    """
    if path.is_symlink():
        raise ValueError("symlink JUnit report input: " + str(path))
    files = sorted(path.rglob("TEST-*.xml")) if path.is_dir() else [path]
    if not files:
        raise ValueError("no TEST-*.xml JUnit reports: " + str(path))
    totals = dict(tests=0, failures=0, errors=0, skipped=0, reports=0, files=[])
    if seen_files is None:
        seen_files = set()
    for file in files:
        if file.is_symlink() or not file.is_file():
            raise ValueError("JUnit reports must be regular files, not symlinks: " + str(file))
        info = file.stat()
        identity = (info.st_dev, info.st_ino)
        if identity in seen_files:
            raise ValueError("duplicate/aliased JUnit report file: " + str(file))
        seen_files.add(identity)
        data = file.read_bytes()
        root = ET.fromstring(data)
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
        totals["files"].append({"path": file.relative_to(path).as_posix() if path.is_dir() else file.name,
                                 "sha256": hashlib.sha256(data).hexdigest()})
    if totals["tests"] == totals["skipped"]:
        raise ValueError("zero executed tests (all skipped): " + str(path))
    return totals

def jacoco_report(path: Path) -> dict:
    data = path.read_bytes()
    root = ET.fromstring(data)
    if root.tag != "report":
        raise ValueError("not a JaCoCo report")
    items = root.findall("counter")
    counters = {x.get("type"): x for x in items}
    if len(counters) != len(items):
        raise ValueError("duplicate JaCoCo counter type")
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
    result["sha256"] = hashlib.sha256(data).hexdigest()
    return result


def unique_object(pairs: list[tuple]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate binding manifest key: " + key)
        result[key] = value
    return result


def require_keys(value, keys: set[str], label: str) -> None:
    if not isinstance(value, dict) or set(value) != keys:
        raise ValueError(label + " must contain exactly: " + ", ".join(sorted(keys)))


def report_inventory(files, label: str) -> dict[str, str]:
    if not isinstance(files, list) or not files:
        raise ValueError(label + " must contain a nonempty report inventory")
    result = {}
    for item in files:
        require_keys(item, {"path", "sha256"}, label + " file")
        path, digest = item["path"], item["sha256"]
        if not isinstance(path, str) or not path or path in result:
            raise ValueError(label + " has an invalid or duplicate report path")
        relative = PurePosixPath(path)
        if relative.is_absolute() or "\\" in path or ".." in relative.parts or relative.as_posix() != path:
            raise ValueError(label + " report paths must be canonical relative POSIX paths")
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError(label + " has an invalid report SHA-256")
        result[path] = digest
    return result


def verify_source(root: Path, source_sha: str) -> None:
    if not isinstance(source_sha, str) or not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("expected source SHA must be a full lowercase Git commit ID")
    def git(*args):
        return subprocess.run(["git", "-C", str(root), *args], check=True,
                              capture_output=True, text=True).stdout.strip()
    try:
        top = git("rev-parse", "--show-toplevel")
        head = git("rev-parse", "--verify", "HEAD")
        dirty = git("status", "--porcelain=v1", "--untracked-files=all",
                    "--ignore-submodules=none")
    except subprocess.CalledProcessError as exc:
        raise ValueError("release source must be an inspectable Git worktree") from exc
    if Path(top).resolve() != root.resolve():
        raise ValueError("--root must be the Git worktree root")
    if head != source_sha:
        raise ValueError("checked-out Git HEAD does not match expected source SHA")
    if dirty:
        raise ValueError("release source has tracked or untracked changes; require a clean Git worktree")


def verify_binding(root: Path, source_sha: str, path: Path,
                   junit: dict, jacoco: dict | None, jacoco_path: Path | None) -> dict:
    """Check declared source/report consistency, not producer authenticity."""
    verify_source(root, source_sha)
    data = path.read_bytes()
    binding = json.loads(data, object_pairs_hook=unique_object)
    require_keys(binding, {"schema", "repository", "source_sha", "run_id", "run_attempt",
                           "run_url", "junit", "jacoco"}, "binding manifest")
    if binding["schema"] != BINDING_SCHEMA or binding["repository"] != REPOSITORY:
        raise ValueError("unsupported binding schema or repository")
    if binding["source_sha"] != source_sha:
        raise ValueError("binding manifest source SHA does not match expected source SHA")
    for key in ("run_id", "run_attempt"):
        if type(binding[key]) is not int or binding[key] <= 0:
            raise ValueError("binding " + key + " must be a positive integer")
    run_url = (f"https://github.com/{REPOSITORY}/actions/runs/{binding['run_id']}"
               f"/attempts/{binding['run_attempt']}")
    if binding["run_url"] != run_url:
        raise ValueError("binding run URL does not match repository/run/attempt")
    require_keys(binding["junit"], set(BACKENDS), "binding JUnit backends")
    if set(junit) != set(BACKENDS) or jacoco is None or jacoco_path is None:
        raise ValueError("binding requires all six parsed backend reports and JaCoCo")
    identity = {key: binding[key] for key in ("source_sha", "run_id", "run_attempt")}
    for backend in BACKENDS:
        record = binding["junit"][backend]
        require_keys(record, set(identity) | {"backend", "files"}, backend)
        if record["backend"] != backend or any(type(record[key]) is not type(value) or record[key] != value
                                               for key, value in identity.items()):
            raise ValueError(backend + " source/backend/run/attempt mismatch")
        if report_inventory(record["files"], backend) != report_inventory(junit[backend]["files"], backend):
            raise ValueError(backend + " report inventory or SHA-256 mismatch")
    record = binding["jacoco"]
    require_keys(record, set(identity) | {"backends", "files"}, "JaCoCo binding")
    if record["backends"] != list(BACKENDS) or any(type(record[key]) is not type(value) or record[key] != value
                                                 for key, value in identity.items()):
        raise ValueError("JaCoCo source/backends/run/attempt mismatch")
    actual = [{"path": jacoco_path.name, "sha256": jacoco["sha256"]}]
    if report_inventory(record["files"], "JaCoCo") != report_inventory(actual, "JaCoCo"):
        raise ValueError("JaCoCo report inventory or SHA-256 mismatch")
    return {"schema": BINDING_SCHEMA, "manifest_sha256": hashlib.sha256(data).hexdigest(),
            "repository": REPOSITORY, **identity, "run_url": run_url,
            "source_clean": True, "cleanliness_scope": "tracked and nonignored untracked paths",
            "producer_authenticity": "not-verified"}


def evaluate(root: Path, junit: dict[str, Path], jacoco: Path | None,
             release: bool, min_line: float, min_branch: float,
             source_sha: str | None = None, evidence_manifest: Path | None = None) -> dict:
    issues = inspect_source(root)
    summary = {"schema": "SWT_RELEASE_SURFACE_V1", "mode": "release" if release else "source",
               "scope": "source naming and supplied XML content binding only; not a native/UI or full release admission",
               "source_issues": issues, "junit": {}, "jacoco": None,
               "pending_backends": [], "surface_ready": False,
               "report_binding": None, "release_admitted": False,
               "remaining_release_gates": list(REMAINING_RELEASE_GATES)}
    locations = [str(path.resolve()) for path in junit.values()]
    if len(set(locations)) != len(locations):
        issues.append("duplicate backend JUnit report location")
    seen_files, seen_digests = set(), set()
    for backend in sorted(junit):
        if backend not in BACKENDS:
            issues.append("unknown backend: " + backend)
            continue
        try:
            report = junit_report(junit[backend], seen_files)
            digests = {file["sha256"] for file in report["files"]}
            if digests & seen_digests:
                raise ValueError("reused JUnit report bytes across backends")
            seen_digests.update(digests)
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
    if release or source_sha is not None or evidence_manifest is not None:
        if source_sha is None or evidence_manifest is None:
            issues.append("source-bound reports require --source-sha and --evidence-manifest")
        else:
            try:
                summary["report_binding"] = verify_binding(
                    root, source_sha, evidence_manifest, summary["junit"], summary["jacoco"], jacoco)
            except (OSError, ValueError) as exc:
                issues.append("report binding: " + str(exc))
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
    parser.add_argument("--source-sha", help="expected full Git commit ID; required for --release")
    parser.add_argument("--evidence-manifest", type=Path,
                        help="source/report binding JSON; required for --release")
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
                      args.release, args.min_line, args.min_branch,
                      args.source_sha, args.evidence_manifest)
    data = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(data, encoding="utf-8")
    print(data, end="")
    return 1 if result["source_issues"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
