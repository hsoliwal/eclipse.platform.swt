#!/usr/bin/env python3
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
#
# This program and the accompanying materials are made available under the
# terms of the Apache License, Version 2.0, or (at your option) the Eclipse
# Public License 2.0, which are available at
# https://www.apache.org/licenses/LICENSE-2.0 and
# https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Builds the native library of the Android port (libswtpixels.so) with the Android NDK and checks it.

  python mobile/android/build_native.py --ndk <ndk dir> --output <dir> [--abi <abi> ...] [--api 26]

For every ABI (default: arm64-v8a, armeabi-v7a, x86_64, x86) it configures the CMake project in
bundles/org.eclipse.swt/Eclipse SWT PI/android/library with the NDK's toolchain file, builds it with Ninja, and
then reads the library back with the NDK's llvm-readelf:

  - every JNI function defined in the C sources is an exported symbol;
  - the library needs libjnigraphics.so;
  - 64-bit libraries have load segments aligned to 16 KB pages.

Output: <output>/jniLibs/<abi>/libswtpixels.so (the layout an Android application's src/main/jniLibs has) and
<output>/native-build.tsv, one row per ABI. The exit code is 1 if a build or a check fails. Nothing is installed
and nothing outside <output> is written. Needs CMake 3.22 or later and Ninja on the PATH (or --cmake / --ninja).
Without --ndk the environment variables ANDROID_NDK_HOME and ANDROID_NDK_ROOT are read.
"""
import argparse
import hashlib
import os
import pathlib
import re
import shutil
import subprocess
import sys

ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
WIDE = {"arm64-v8a", "x86_64"}
JNI = re.compile(r"JNICALL\s+(Java_\w+)\s*\(")
PAGE_16K = 0x4000


def run(command, log):
    done = subprocess.run(command, capture_output=True, text=True, errors="replace")
    log.write("$ " + " ".join(str(c) for c in command) + "\n" + done.stdout + done.stderr + "\n")
    return done


def host_tools(ndk):
    prebuilt = ndk / "toolchains" / "llvm" / "prebuilt"
    hosts = sorted(p for p in prebuilt.iterdir() if p.is_dir()) if prebuilt.is_dir() else []
    if not hosts:
        sys.exit(f"not an NDK: {ndk} has no toolchains/llvm/prebuilt/<host>")
    return hosts[0] / "bin"


def check(readelf, library, expected, abi, log):
    """Returns the list of failed checks of one library."""
    failed = []
    symbols = run([readelf, "--dyn-syms", "-W", library], log).stdout
    exported = set(re.findall(r"\sFUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+(Java_\w+)", symbols))
    for name in sorted(expected - exported):
        failed.append("not exported: " + name)
    needed = set(re.findall(r"\(NEEDED\).*\[(.+?)\]", run([readelf, "-d", "-W", library], log).stdout))
    if "libjnigraphics.so" not in needed:
        failed.append("does not need libjnigraphics.so")
    if abi in WIDE:
        loads = re.findall(r"^\s*LOAD\s.*\s(0x[0-9a-fA-F]+)\s*$", run([readelf, "-l", "-W", library], log).stdout, re.M)
        if not loads or any(int(align, 16) < PAGE_16K for align in loads):
            failed.append("load segments are not aligned to 16 KB: " + " ".join(loads))
    return failed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ndk", default=os.environ.get("ANDROID_NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT"))
    parser.add_argument("--output", required=True)
    parser.add_argument("--abi", action="append", choices=ABIS)
    parser.add_argument("--api", type=int, default=26, help="the minimum Android API level (minSdk of mobile/android)")
    parser.add_argument("--swt", default=str(pathlib.Path(__file__).resolve().parents[2]), help="the SWT checkout")
    parser.add_argument("--cmake", default="cmake")
    parser.add_argument("--ninja", default=shutil.which("ninja"))
    args = parser.parse_args()
    if not args.ndk:
        sys.exit("no NDK: pass --ndk or set ANDROID_NDK_HOME")
    ndk, output = pathlib.Path(args.ndk).resolve(), pathlib.Path(args.output).resolve()
    source = pathlib.Path(args.swt).resolve() / "bundles" / "org.eclipse.swt" / "Eclipse SWT PI" / "android" / "library"
    toolchain = ndk / "build" / "cmake" / "android.toolchain.cmake"
    if not toolchain.is_file():
        sys.exit(f"not an NDK: {toolchain} is missing")
    if not args.ninja:
        sys.exit("Ninja is not on the PATH: pass --ninja")
    tools = host_tools(ndk)
    readelf = next((str(p) for p in (tools / "llvm-readelf.exe", tools / "llvm-readelf") if p.exists()), None)
    if readelf is None:
        sys.exit(f"llvm-readelf is missing in {tools}")
    expected = set()
    for file in sorted(source.glob("*.c")):
        expected |= set(JNI.findall(file.read_text(encoding="utf-8")))
    if not expected:
        sys.exit(f"no JNI function found in {source}")
    revision = next((line.split("=", 1)[1].strip() for line in (ndk / "source.properties").read_text().splitlines()
                     if line.startswith("Pkg.Revision")), "unknown")

    output.mkdir(parents=True, exist_ok=True)
    rows, failures = [], 0
    with open(output / "native-build.log", "w", encoding="utf-8", newline="\n") as log:
        for abi in args.abi or ABIS:
            build, libs = output / "build" / abi, output / "jniLibs" / abi
            shutil.rmtree(build, ignore_errors=True)
            configure = run([args.cmake, "-G", "Ninja", "-S", str(source), "-B", str(build),
                             "-DCMAKE_MAKE_PROGRAM=" + str(args.ninja), "-DCMAKE_TOOLCHAIN_FILE=" + str(toolchain),
                             "-DANDROID_ABI=" + abi, "-DANDROID_PLATFORM=android-" + str(args.api),
                             "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=" + str(libs)], log)
            built = configure.returncode == 0 and run([args.cmake, "--build", str(build)], log).returncode == 0
            library = libs / "libswtpixels.so"
            if not built or not library.is_file():
                failed, size, digest = ["build failed (see native-build.log)"], 0, ""
            else:
                failed = check(readelf, str(library), expected, abi, log)
                data = library.read_bytes()
                size, digest = len(data), hashlib.sha256(data).hexdigest()
            failures += bool(failed)
            state = "FAIL" if failed else "PASS"
            rows.append((abi, state, str(size), digest, str(len(expected)), "; ".join(failed)))
            print(f"{state} {abi} {size} bytes" + ("" if not failed else " : " + "; ".join(failed)))
    with open(output / "native-build.tsv", "w", encoding="utf-8", newline="\n") as table:
        table.write(f"# ndk {revision} api {args.api} sources {source.as_posix()}\n")
        table.write("abi\tstate\tbytes\tsha256\tjni_functions\tfailed_checks\n")
        table.writelines("\t".join(row) + "\n" for row in rows)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
