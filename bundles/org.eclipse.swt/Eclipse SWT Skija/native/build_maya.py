#!/usr/bin/env python3
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""build_maya.py --out DIR [--skia DIR] [--jdk DIR] [--vcvars BAT]

Builds maya.dll (win32 x86_64): the Skija JNI sources in cc/ that SWT reaches (sources.txt), compiled with MSVC against
the prebuilt Skia release Skija 0.116.3 was made with, linked to export exactly the functions of maya.def.

--skia   the extracted Skia release; when omitted it is downloaded into <out>/skia and its sha256 checked
--jdk    the JDK whose include folder has jni.h (default: JAVA_HOME)
--vcvars vcvars64.bat of a Visual Studio C++ toolchain (default: the first found under Program Files)
Each source folder compiles into its own object folder (five sources are named interop.cc); /Brepro makes the
build reproducible (the same inputs give the same bytes). Prints BUILD-OK or the
failing step; logs are in <out>.
"""
import argparse
import collections
import hashlib
import os
import pathlib
import subprocess
import sys
import urllib.request
import zipfile

SKIA_URL = ("https://github.com/HumbleUI/SkiaBuild/releases/download/m116-d2c211228d/"
            "Skia-m116-d2c211228d-windows-Release-x64.zip")
SKIA_SHA256 = "c4a9e5fb25a467ac302eced5865649f6c24be33d5f7594d082539d2ebf43872a"
HERE = pathlib.Path(__file__).resolve().parent
DEFINES = ["JNIEXPORT=", "NDEBUG", "FT2_BUILD_LIBRARY", "SK_GL", "SK_GANESH", "SK_SHAPER_HARFBUZZ_AVAILABLE",
           "SK_UNICODE_AVAILABLE", "U_DISABLE_RENAMING", "U_USING_ICU_NAMESPACE=0", "SK_USING_THIRD_PARTY_ICU",
           "U_COMMON_IMPLEMENTATION", "U_STATIC_IMPLEMENTATION", "U_ENABLE_DYLOAD=0", "U_I18N_IMPLEMENTATION",
           "_XOPEN_SOURCE=0", "NOMINMAX", "WIN32_LEAN_AND_MEAN", "SK_DIRECT3D", "U_NOEXCEPT="]
SKIA_INCLUDES = ["", "include/config", "include/core", "include/encode", "include/pathops", "include/svg", "include/utils",
                 "include/codec", "include/effects", "include/gpu", "src/gpu", "include/third_party/skcms",
                 "modules/skunicode/include", "modules/skshaper/include", "modules/skparagraph/include",
                 "third_party/externals/freetype/include", "third_party/externals/harfbuzz/src", "modules/svg/include",
                 "modules/skottie/include", "modules/sksg/include", "modules/skresources/include",
                 "third_party/externals/icu/source/common", "third_party/icu", "modules/skparagraph/src"]
LIBS = ["skottie.lib", "sksg.lib", "skshaper.lib", "skunicode.lib", "skresources.lib", "skparagraph.lib", "svg.lib", "skia.lib",
        "harfbuzz.lib", "icu.lib", "freetype2.lib", "libpng.lib", "libjpeg.lib", "libwebp.lib", "libwebp_sse41.lib", "wuffs.lib",
        "zlib.lib", "expat.lib", "skcms.lib", "d3d12allocator.lib", "spirv_cross.lib", "d3d12.lib", "dxgi.lib", "d3dcompiler.lib",
        "opengl32.lib", "user32.lib", "gdi32.lib", "ole32.lib", "OleAut32.lib", "usp10.lib", "dwrite.lib", "fontsub.lib",
        "advapi32.lib"]


def fetch_skia(out):
    target = out / "skia"
    if (target / "out" / "Release-x64" / "skia.lib").exists():
        return target
    archive = out / "skia.zip"
    urllib.request.urlretrieve(SKIA_URL, archive)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if digest != SKIA_SHA256:
        sys.exit("Skia release sha256 %s, expected %s" % (digest, SKIA_SHA256))
    with zipfile.ZipFile(archive) as z:
        z.extractall(target)
    archive.unlink()
    return target


def find_vcvars():
    for root in (os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("ProgramFiles(x86)", "")):
        for path in pathlib.Path(root or ".").glob("Microsoft Visual Studio/*/*/VC/Auxiliary/Build/vcvars64.bat"):
            return path
    sys.exit("no vcvars64.bat found; pass --vcvars")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--skia")
    parser.add_argument("--jdk", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--vcvars")
    args = parser.parse_args()
    out = pathlib.Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    skia = pathlib.Path(args.skia).resolve() if args.skia else fetch_skia(out)
    jdk = pathlib.Path(args.jdk)
    vcvars = pathlib.Path(args.vcvars) if args.vcvars else find_vcvars()
    sources = [l.strip() for l in (HERE / "sources.txt").read_text().splitlines() if l.strip()]
    groups = collections.defaultdict(list)
    for s in sources:
        folder = s.rsplit("/", 1)[0] if "/" in s else ""
        groups[folder.replace("/", "_") or "root"].append(s)
    includes = [skia / p for p in SKIA_INCLUDES] + [HERE / "cc", jdk / "include", jdk / "include" / "win32"]
    flags = ("cl /nologo /c /Brepro /MT /O2 /EHsc /std:c++17 /W0 " + " ".join("/D" + d for d in DEFINES) + " "
             + " ".join('/I"%s"' % i for i in includes))
    lines = ["@echo off", 'call "%s" >nul' % vcvars, 'cd /d "%s"' % out, "type nul > cl.log"]
    for g, files in sorted(groups.items()):
        (out / "obj" / g).mkdir(parents=True, exist_ok=True)
        (out / ("sources-%s.rsp" % g)).write_text("".join('"%s"\n' % (HERE / "cc" / s) for s in files), encoding="ascii")
        lines.append(flags + r" /Foobj\%s\ @sources-%s.rsp >> cl.log 2>&1" % (g, g))
        lines.append("if errorlevel 1 (echo COMPILE-FAILED %s & exit /b 1)" % g)
    objs = " ".join(r"obj\%s\*.obj" % g for g in sorted(groups))
    lines.append('link /nologo /Brepro /DLL /OUT:maya.dll /DEF:"%s" /OPT:REF /OPT:ICF /INCREMENTAL:NO /NODEFAULTLIB:MSVCRT /machine:X64 '
                 '/LIBPATH:"%s" %s %s > link.log 2>&1' % (HERE / "maya.def", skia / "out" / "Release-x64", objs, " ".join(LIBS)))
    lines += ["if errorlevel 1 (echo LINK-FAILED & exit /b 1)", "echo BUILD-OK", ""]
    script = out / "build.bat"
    script.write_text("\r\n".join(lines), encoding="ascii")
    sys.exit(subprocess.run(["cmd", "/c", str(script)]).returncode)


if __name__ == "__main__":
    main()
