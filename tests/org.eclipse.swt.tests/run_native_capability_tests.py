#!/usr/bin/env python3
# Copyright (c) 2026 Contributors to the Eclipse Foundation.
# SPDX-License-Identifier: EPL-2.0
"""Run the focused native bridge tests against a freshly built SWT fragment."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


TESTS = {
    "cocoa": (
        "org.eclipse.swt.tests.cocoa",
        "org.eclipse.swt.tests.cocoa.Test_cocoa_CoreAnimation",
        {
            "test_layerBackingPreservesCanvasAndNativeChild",
            "test_layerGeometryAndDecorationRoundTrip",
            "test_layerRetainsImageAndReleasesSublayerAndMask",
            "test_nestedTransactionsRestoreParentSettings",
        },
    ),
    "gtk4": (
        "org.eclipse.swt.tests.gtk",
        "org.eclipse.swt.tests.gtk.Test_GtkSnapshot",
        {
            "test_clipAndOpacityReachNativePixels",
            "test_appendNodeRetainsReleasedInput",
            "test_appendTextureRetainsPixelsAfterSourceRelease",
            "test_saveRestoreIsolatesTransform",
        },
    ),
}


def manifest(jar):
    with zipfile.ZipFile(jar) as archive:
        text = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    text = re.sub(r"\r?\n ", "", text)
    return dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)


def find_fragment(args):
    if args.swt_jar:
        candidates = [args.swt_jar.resolve()]
    else:
        candidates = sorted((args.fragment.resolve() / "target").glob("*.jar"))
    platform = "gtk" if args.platform == "gtk4" else "cocoa"
    matches = []
    for jar in candidates:
        with zipfile.ZipFile(jar) as archive:
            if "org/eclipse/swt/widgets/Display.class" not in archive.namelist():
                continue
        attributes = manifest(jar)
        symbol = attributes.get("Bundle-SymbolicName", "").split(";", 1)[0]
        if attributes.get("SWT-WS") != platform:
            continue
        if args.fragment and symbol != args.fragment.name:
            continue
        matches.append(jar)
    if len(matches) != 1:
        raise RuntimeError(f"Expected one built {platform} fragment JAR, found {matches}")
    return matches[0]


def extract_natives(jar, output, platform):
    directory = output / "native"
    directory.mkdir()
    names = set()
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            name = Path(entry.filename).name
            if not name.startswith("libswt-") or Path(name).suffix not in {".so", ".jnilib", ".dylib"}:
                continue
            if name in names:
                raise RuntimeError(f"Duplicate native library in SWT fragment: {name}")
            data = archive.read(entry)
            # Old binary filenames remain in the fragment after a native build.
            # With lfs:false these are placeholders, not libraries to load. SWT
            # selects its current version from the newly built native libraries.
            if data.startswith(b"version https://git-lfs.github.com/spec/v1\n"):
                print(f"Ignoring unbuilt native placeholder {name}")
                continue
            magic = {b"\x7fELF"} if platform == "gtk4" else {
                b"\xfe\xed\xfa\xcf", b"\xcf\xfa\xed\xfe",
                b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca",
                b"\xca\xfe\xba\xbf", b"\xbf\xba\xfe\xca",
            }
            if data[:4] not in magic:
                raise RuntimeError(f"Not a built native library: {name}")
            (directory / name).write_bytes(data)
            names.add(name)
    prefix = "libswt-pi4-" if platform == "gtk4" else "libswt-cocoa-"
    if not any(name.startswith(prefix) for name in names):
        raise RuntimeError(f"Built fragment has no {prefix} native library")
    print(f"Using SWT fragment {jar} with {len(names)} native libraries")
    return directory


def run_logged(command, logfile, env):
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, env=env, timeout=300, check=False)
    except subprocess.TimeoutExpired as error:
        output = error.stdout or b""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        logfile.write_text(output + "\nTimed out after 300 seconds.\n", encoding="utf-8")
        raise RuntimeError(f"Native capability command timed out; see {logfile}") from error
    logfile.write_text(result.stdout, encoding="utf-8")
    print(result.stdout, end="")
    if result.returncode:
        raise RuntimeError(f"Command exited {result.returncode}; see {logfile}")
    if re.search(r"(?:WARNING|FATAL ERROR) in native method|WARNING: JNI local refs", result.stdout):
        raise RuntimeError(f"JNI checking reported a diagnostic; see {logfile}")


def require_executed_tests(reports, class_name, expected):
    files = sorted(reports.glob("*.xml"))
    if not files:
        raise RuntimeError("JUnit produced no XML test reports")
    executed = []
    for report in files:
        root = ET.parse(report).getroot()
        for suite in root.iter("testsuite"):
            for attribute in ("errors", "failures", "skipped"):
                if int(suite.get(attribute, "0")):
                    raise RuntimeError(f"JUnit reported {attribute} in {report}")
        for case in root.iter("testcase"):
            if any(case.find(tag) is not None for tag in ("skipped", "failure", "error")):
                raise RuntimeError(f"Test did not pass in {report}: {case.get('name')}")
            if case.get("classname") == class_name:
                executed.append(case.get("name", "").split("(", 1)[0])
    if set(executed) != expected or len(executed) != len(expected):
        raise RuntimeError(f"Expected {sorted(expected)}; executed {executed}")
    print(f"Passed all {len(executed)} required native tests in {class_name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=TESTS, required=True)
    fragment = parser.add_mutually_exclusive_group(required=True)
    fragment.add_argument("--swt-jar", type=Path)
    fragment.add_argument("--fragment", type=Path, help="Built binaries/org.eclipse.swt.* directory")
    parser.add_argument("--junit", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if (args.platform == "cocoa" and sys.platform != "darwin"
            or args.platform == "gtk4" and not sys.platform.startswith("linux")):
        raise RuntimeError(f"Cannot execute {args.platform} native tests on {sys.platform}")
    junit = args.junit.resolve(strict=True)
    jar = find_fragment(args)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Do not let an old report or library turn a missing execution into a pass.
    for name in ("classes", "native", "surefire-reports"):
        if (output / name).exists():
            raise RuntimeError(f"Use a fresh output directory; {output / name} already exists")
    natives = extract_natives(jar, output, args.platform)
    classes = output / "classes"
    classes.mkdir()
    reports = output / "surefire-reports"
    bundle, class_name, expected = TESTS[args.platform]
    source = (Path(__file__).resolve().parents[2] / "tests" / bundle / "JUnit Tests"
              / (class_name.replace(".", "/") + ".java"))
    env = os.environ.copy()
    if args.platform == "gtk4":
        env["SWT_GTK4"] = "1"
    java_bin = args.java_home.resolve() / "bin"
    run_logged([
        str(java_bin / "javac"), "--release", "21", "-encoding", "UTF-8",
        "-Xlint:all", "-Werror", "-cp", os.pathsep.join((str(jar), str(junit))),
        "-d", str(classes), str(source),
    ], output / "compile.log", env)
    command = [str(java_bin / "java"), "-ea", "-Xcheck:jni"]
    if args.platform == "cocoa":
        command.append("-XstartOnFirstThread")
    command.extend([
        f"-Dswt.library.path={natives}", f"-Djava.library.path={natives}",
        "-jar", str(junit), "execute",
        "--class-path", os.pathsep.join((str(classes), str(jar))),
        "--select-class", class_name, "--reports-dir", str(reports),
        "--fail-if-no-tests", "--details", "tree", "--disable-ansi-colors",
        "--config", "junit.jupiter.execution.parallel.enabled=false",
    ])
    run_logged(command, output / "native-tests.log", env)
    require_executed_tests(reports, class_name, expected)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, KeyError, zipfile.BadZipFile, ET.ParseError) as error:
        sys.exit(str(error))
