#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Static SWT naming and required test-registration preflight.

This cannot establish native UI rendering, binary compatibility or actual
JUnit execution; those need platform CI reports on the same commit.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

REQUIRED = ("Test_ItemBitSetDifferential", "Test_ItemBitSetMaximumPosition")
TEST_DIR = Path("tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit")


def manifest_property(body: str, key: str) -> str:
    match = re.search(r"(?m)^" + re.escape(key) + r": ([^\r\n]+)$", body)
    return match.group(1).strip() if match else ""


def audit(root: Path) -> dict:
    root = root.resolve()
    manifest = (root / "bundles/org.eclipse.swt/META-INF/MANIFEST.MF").read_text(encoding="utf-8")
    suite = (root / TEST_DIR / "AllTests.java").read_text(encoding="utf-8")
    pom = (root / "tests/org.eclipse.swt.tests/pom.xml").read_text(encoding="utf-8")
    matrix = (root / ".github/workflows/maven.yml").read_text(encoding="utf-8")
    selected = re.search(r"@SelectClasses\s*\(\s*\{(.*?)\}\s*\)", suite, re.DOTALL)
    entries = set(re.findall(r"\b([A-Za-z][A-Za-z0-9_]*)\.class\b",
                             selected.group(1) if selected else ""))
    present = {}
    for name in REQUIRED:
        path = root / TEST_DIR / (name + ".java")
        source = path.read_text(encoding="utf-8") if path.is_file() else ""
        present[name] = bool(
            re.search(r"\bclass\s+" + re.escape(name) + r"\b", source)
            and "@Test" in source
        )
    checks = {
        "osgi_symbolic_name_unchanged": (
            manifest_property(manifest, "Bundle-SymbolicName").split(";")[0].strip()
            == "org.eclipse.swt"),
        "java_module_name_unchanged": (
            manifest_property(manifest, "Automatic-Module-Name") == "org.eclipse.swt"),
        "swt_api_minimum_java21_preserved": (
            manifest_property(manifest, "Bundle-RequiredExecutionEnvironment") == "JavaSE-21"),
        "main_junit_suite_configured": "<test>AllTests</test>" in pom,
        "platform_matrix_declared": all(
            re.search(r"(?m)^  " + lane + r":", matrix)
            for lane in ("build-linux", "build-windows", "build-macos")
        ),
        **{name + "_source_has_test": present[name] for name in REQUIRED},
        **{name + "_registered": name in entries for name in REQUIRED},
    }
    return {
        "schema": "SWT_RELEASE_SURFACE_PREFLIGHT_V1",
        "scope": "static API naming and two mandatory sparse-state JUnit class registrations",
        "checks": checks,
        "required_test_classes": list(REQUIRED),
        "passed": all(checks.values()),
        "disclaimer": "not a binary, native, JUnit execution or UI screenshot receipt",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    result = audit(args.root)
    output = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output, encoding="utf-8")
    print(output, end="")
    return 0 if result["passed"] or not args.strict else 1


if __name__ == "__main__":
    raise SystemExit(main())
