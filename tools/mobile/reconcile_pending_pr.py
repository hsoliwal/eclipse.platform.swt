#!/usr/bin/env python3
"""Verify pinned Git blob identities across two existing mobile source worktrees.

This is an identity audit, never a semantic equivalence or merge operation.
The manifest is a strict, sorted TSV containing candidate and baseline Git SHA-1
blob identities. No source file is created, modified, removed, or moved.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath

HEADER = "path\tcandidate_git_blob\tbaseline_git_blob"
SHA1 = re.compile(r"[0-9a-f]{40}\Z")


class AuditError(ValueError):
    """A source, manifest, or report contract is violated."""


def git_blob_sha(data: bytes) -> str:
    """Calculate the Git SHA-1 blob ID, including size and object type."""
    header = b"blob " + str(len(data)).encode("ascii") + b"\0"
    return hashlib.sha1(header + data).hexdigest()


def _file_at(root: Path, relative: str) -> Path:
    """Disallow absolute, noncanonical and symlink-traversing paths."""
    logical = PurePosixPath(relative)
    if (
        not relative
        or logical.is_absolute()
        or "\\" in relative
        or any(part in ("", ".", "..") for part in relative.split("/"))
        or logical.as_posix() != relative
    ):
        raise AuditError(f"noncanonical manifest path: {relative!r}")
    base = root.resolve(strict=True)
    file = base.joinpath(*logical.parts)
    current = base
    for part in logical.parts:
        current = current / part
        if current.is_symlink():
            raise AuditError(f"symlink in manifest path: {relative}")
    if not file.is_file() or not file.resolve(strict=True).is_relative_to(base):
        raise AuditError(f"missing or escaping input: {relative}")
    return file


def read_manifest(path: Path) -> list[tuple[str, str, str]]:
    """Require sorted, unique, three-column TSV with exact Git object IDs."""
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != HEADER:
        raise AuditError("manifest header differs from the documented schema")
    entries: list[tuple[str, str, str]] = []
    previous = ""
    for number, line in enumerate(lines[1:], 2):
        columns = line.split("\t")
        if len(columns) != 3:
            raise AuditError(f"TSV line {number} requires exactly three columns")
        relative, candidate_sha, baseline_sha = columns
        if relative <= previous:
            raise AuditError(f"TSV line {number} not strictly sorted or duplicated")
        if not SHA1.fullmatch(candidate_sha) or not SHA1.fullmatch(baseline_sha):
            raise AuditError(f"TSV line {number} has malformed Git blob identity")
        entries.append((relative, candidate_sha, baseline_sha))
        previous = relative
    if not entries:
        raise AuditError("empty manifest is not an accepted audit")
    return entries


def audit(candidate: Path, baseline: Path, manifest: Path) -> dict:
    """Refuse drift; return deterministic data, not a semantic decision."""
    entries = read_manifest(manifest)
    records: list[dict] = []
    for relative, candidate_expected, baseline_expected in entries:
        left = _file_at(candidate, relative).read_bytes()
        right = _file_at(baseline, relative).read_bytes()
        left_sha, right_sha = git_blob_sha(left), git_blob_sha(right)
        if left_sha != candidate_expected:
            raise AuditError(f"candidate content drift at {relative}: {left_sha}")
        if right_sha != baseline_expected:
            raise AuditError(f"baseline content drift at {relative}: {right_sha}")
        records.append(
            {
                "path": relative,
                "candidate_git_blob": left_sha,
                "baseline_git_blob": right_sha,
                "identity": "identical" if left_sha == right_sha else "different",
                "candidate_bytes": len(left),
                "baseline_bytes": len(right),
            }
        )
    return {
        "schema": 1,
        "manifest_sha256": hashlib.sha256(manifest.read_bytes()).hexdigest(),
        "scope": "Git blob identity only; not semantic equivalence, build, or OS test",
        "files": records,
        "summary": {
            "inspected": len(records),
            "identical": sum(r["identity"] == "identical" for r in records),
            "different": sum(r["identity"] == "different" for r in records),
        },
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    try:
        report = audit(args.candidate, args.baseline, args.manifest)
        output = (json.dumps(report, indent=2, sort_keys=True) + "\n").encode("utf-8")
        if args.report is None:
            sys.stdout.buffer.write(output)
        elif args.report.is_symlink():
            raise AuditError("report path must not be a symlink")
        elif args.report.exists():
            if args.report.read_bytes() != output:
                raise AuditError("existing report differs; no implicit overwrite")
        else:
            args.report.write_bytes(output)
    except (AuditError, OSError, UnicodeError) as exc:
        print(f"AUDIT_FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
