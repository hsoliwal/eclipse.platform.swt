#!/usr/bin/env python3
# Copyright (c) 2026 Hitesh Soliwal and contributors.
# SPDX-License-Identifier: EPL-2.0
"""Stage the explicit SWT mobile source selection without changing source bytes.

This is product build wiring. It does not transform or generate SWT source and
contains no Synexia recipe implementation. Maven Compiler/Jar/Surefire remain the
Java build and test entrypoints. Android/iOS native qualification is separate.
"""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil


MANIFEST = Path(__file__).with_name("source-manifest.json")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_path(root, relative):
    relative = PurePosixPath(relative)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError(f"Source manifest path escapes its checkout: {relative}")
    root = Path(root).resolve()
    path = root.joinpath(*relative.parts).resolve()
    if not path.is_relative_to(root) or not path.is_file():
        raise ValueError(f"Required source is missing or outside its checkout: {path}")
    return path


def read_manifest(swt):
    manifest_path = checked_path(swt, "mobile/source-manifest.json")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schema") != 1 or manifest.get("javaRelease") != 21:
        raise ValueError("Unsupported SWT mobile source-manifest schema or Java release")
    return manifest


def checked_sources(root, entries, origin):
    result = []
    for entry in entries:
        path = checked_path(root, entry["path"])
        actual = digest(path)
        if entry.get("sha256") and actual != entry["sha256"]:
            raise ValueError(
                f"Unchanged {origin} source drift: {entry['path']}\n"
                f"expected {entry['sha256']}, observed {actual}. "
                "Review the new source closure before updating its manifest."
            )
        result.append({"origin": origin, "path": entry["path"], "file": path,
                       "sha256": actual})
    return result


def swt_sources(swt, platform="shared"):
    manifest = read_manifest(swt)
    if platform not in ("shared", "android", "ios"):
        raise ValueError(f"Unsupported mobile platform: {platform}")
    entries = manifest["shared"] + manifest["platforms"].get(platform, [])
    return checked_sources(swt, entries, "swt")


def nebula_sources(swt, nebula):
    return checked_sources(nebula, read_manifest(swt)["nebulaOriginalSources"], "nebula")


def source_target(relative):
    parts = PurePosixPath(relative).parts
    for index, part in enumerate(parts[:-1]):
        if part == "org" and parts[index + 1] == "eclipse":
            return Path(*parts[index:])
    raise ValueError(f"Source has no org/eclipse package root: {relative}")


def reset_child(output, name):
    output = Path(output).resolve()
    path = output / name
    if path.is_symlink():
        raise ValueError(f"Refusing to replace a symlink build directory: {path}")
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
    return path


def stage(entries, destination):
    used = set()
    for entry in entries:
        target = source_target(entry["path"])
        if target in used:
            raise ValueError(f"Two selected source owners have the same package path: {target}")
        used.add(target)
        path = destination / target
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(entry["file"], path)
        if digest(path) != entry["sha256"]:
            raise RuntimeError(f"Source copy changed bytes: {entry['path']}")


def stage_resources(swt, destination):
    manifest = read_manifest(swt)
    result = []
    for entry in manifest["resources"]:
        source = checked_sources(swt, [entry], "swt-resource")[0]
        target = PurePosixPath(entry["target"])
        if target.is_absolute() or ".." in target.parts:
            raise ValueError(f"Invalid resource destination: {target}")
        path = destination.joinpath(*target.parts)
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source["file"], path)
        result.append(source)
    return result


def portable_entries(entries):
    return [{key: value for key, value in entry.items() if key != "file"}
            for entry in entries]


def boolean(value):
    if value.lower() not in ("true", "false"):
        raise argparse.ArgumentTypeError("expected true or false")
    return value.lower() == "true"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--swt", required=True, type=Path)
    parser.add_argument("--nebula", type=Path)
    parser.add_argument("--platform", choices=("shared", "android", "ios"), default="shared")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--contracts", type=boolean, default=False)
    args = parser.parse_args()
    swt = args.swt.resolve()
    output = args.output.resolve()
    if output == swt or output == args.swt.parent.resolve():
        parser.error("--output must be a dedicated build directory")
    output.mkdir(parents=True, exist_ok=True)
    (output / "selected-sources.json").unlink(missing_ok=True)
    manifest = read_manifest(swt)
    main_sources = swt_sources(swt, args.platform)
    test_sources = []
    if args.contracts:
        if args.nebula is None:
            parser.error("--nebula is required for the original Nebula contract tests")
        test_sources = checked_sources(swt, manifest["contractSources"], "swt-test")
        test_sources += nebula_sources(swt, args.nebula)
    # Switching source/platform selections cannot retain obsolete native classes.
    reset_child(output, "classes")
    reset_child(output, "test-classes")
    stage(main_sources, reset_child(output, "generated-sources/mobile"))
    stage(test_sources, reset_child(output, "generated-test-sources/mobile"))
    resources = stage_resources(swt, reset_child(output, "generated-resources/mobile"))
    report = {"schema": 1, "platform": args.platform, "javaRelease": 21,
              "swtBaseCommit": manifest["swtBaseCommit"],
              "nebulaBaseCommit": manifest["nebulaBaseCommit"],
              "nativeQualification": "NOT_RUN_BY_SOURCE_STAGING",
              "mainSources": portable_entries(main_sources),
              "testSources": portable_entries(test_sources),
              "resources": portable_entries(resources)}
    (output / "selected-sources.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Selected {len(main_sources)} SWT {args.platform} production sources and "
          f"{len(test_sources)} test-only sources; original source bytes preserved.")


if __name__ == "__main__":
    main()
