#!/usr/bin/env python3
# Copyright (c) 2026 Hitesh Soliwal and contributors.
# SPDX-License-Identifier: EPL-2.0
"""Compile real shared SWT sources and run the Java contract suite; no native execution claim."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

JUNIT_SHA256 = "a1de557821293ce903c213c694165fff532cf92081bac4238b9e05b35f04f43f"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--junit", required=True, type=Path,
                        help="Official junit-platform-console-standalone 1.10.2 JAR")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    output = (args.output or root / "target/mobile-java").resolve()
    output.mkdir(parents=True, exist_ok=True)
    junit = args.junit.resolve()
    if hashlib.sha256(junit.read_bytes()).hexdigest() != JUNIT_SHA256:
        parser.error("The JUnit JAR does not match the pinned official 1.10.2 artifact")
    common = (root / "m3/mobile/common-sources.txt").read_text().splitlines()
    if len(common) != len(set(common)):
        parser.error("Duplicate common-source manifest entry")
    sources = []
    for name in common:
        p = Path(name)
        if p.is_absolute() or ".." in p.parts or p.suffix != ".java":
            parser.error(f"Noncanonical Java source path: {name}")
        sources.append(root / p)
    for name in ["bundles/org.eclipse.swt/Eclipse SWT/mobile",
                 "bundles/org.eclipse.swt/Eclipse SWT/ios",
                 "bundles/org.eclipse.swt/Eclipse SWT PI/ios",
                 "tests/org.eclipse.swt.tests/mobile"]:
        sources.extend(sorted((root / name).rglob("*.java")))
    classes = output / "classes"
    headers = output / "jni-headers"
    reports = output / "reports"
    for p in [classes, headers, reports]:
        p.mkdir(parents=True, exist_ok=True)
    commands = [
        [str(args.jdk / "bin/javac"), "--release", "21", "-Xlint:all", "-cp", str(junit),
         "-d", str(classes), "-h", str(headers), *[str(p) for p in sources]],
        [str(args.jdk / "bin/java"), "-jar", str(junit), "execute", "--disable-ansi-colors",
         "--class-path", str(classes), "--select-class", "org.eclipse.swt.tests.mobile.MobileDisplayTest",
         "--reports-dir", str(reports), "--details", "summary"]]
    with (output / "verification.log").open("w") as log:
        for command in commands:
            result = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, timeout=120)
            log.write(result.stdout)
            print(result.stdout, end="")
            if result.returncode:
                raise SystemExit(result.returncode)
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for report in reports.glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, "0"))
    if totals["tests"] < 28 or any(totals[k] for k in ["failures", "errors", "skipped"]):
        raise SystemExit(f"Incomplete or failing JUnit execution: {totals}")
    native = root / "bundles/org.eclipse.swt/Eclipse SWT PI/ios/library/org_eclipse_swt_internal_ios_OS.h"
    generated = headers / native.name
    if generated.read_bytes() != native.read_bytes():
        raise SystemExit("Committed iOS JNI header differs from real javac -h output")
    receipt = {"status": "PASS", "scope": "Java SWT mobile contracts and JNI declaration identity",
               "native_android_execution": "NOT_RUN_BY_THIS_SUITE",
               "native_ios_execution": "NOT_RUN_BY_THIS_SUITE", "junit": totals,
               "source_sha256": {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in sorted(sources)}}
    (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(f"SWT_MOBILE_JAVA_PASS tests={totals['tests']} sources={len(sources)}")

if __name__ == "__main__":
    main()
