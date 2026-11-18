#!/usr/bin/env python3
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""build_maya.py --out DIR [--skia DIR] [--jdk DIR] [--vcvars BAT] [--target windows-x64|android-x64|android-arm64 --ndk DIR]

Builds maya.dll (win32 x86_64): the Skija JNI sources in cc/ that SWT reaches (sources.txt), compiled with MSVC against
the prebuilt Skia release Skija 0.116.3 was made with, linked to export exactly the functions of maya.def.

--skia   the extracted Skia release; when omitted it is downloaded into <out>/skia and its sha256 checked
--jdk    the JDK whose include folder has jni.h (default: JAVA_HOME)
--vcvars vcvars64.bat of a Visual Studio C++ toolchain (default: the first found under Program Files)
Each source folder compiles into its own object folder (five sources are named interop.cc); /Brepro makes the
build reproducible (the same inputs give the same bytes); the object folders start empty, so no object of an
earlier build is linked. Prints BUILD-OK or the failing step; logs are in <out>.
"""
import argparse
import collections
import hashlib
import os
import pathlib
import shutil
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
        "zlib.lib", "expat.lib", "skcms.lib", "d3d12allocator.lib", "spirv_cross.lib", "d3d12.lib", "d3d11.lib", "dcomp.lib", "dxgi.lib", "d3dcompiler.lib",
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


# Android: JetBrains skia-pack m116-47d3027-1 (Skia m116, the milestone of Skija 0.116.3), whose ICU entry points carry the
# library suffix _skiko and no version suffix; built with the NDK's clang into libmaya.so
ANDROID = {"android-x64": ("x86_64-linux-android", "Release-android-x64"),
           "android-arm64": ("aarch64-linux-android", "Release-android-arm64")}
ANDROID_API = 26
ANDROID_DEFINES = ["NDEBUG", "SK_GL", "SK_GANESH", "SK_SHAPER_HARFBUZZ_AVAILABLE", "SK_UNICODE_AVAILABLE",
                   "SK_USING_THIRD_PARTY_ICU", "U_LIB_SUFFIX_C_NAME=_skiko", "U_DISABLE_VERSION_SUFFIX=1",
                   "U_USING_ICU_NAMESPACE=0", "U_COMMON_IMPLEMENTATION", "U_STATIC_IMPLEMENTATION", "U_ENABLE_DYLOAD=0",
                   "U_I18N_IMPLEMENTATION", "U_NOEXCEPT=",
                   # ART implements JNI 1.6; the sources ask for 1.8, the JDK's
                   "JNI_VERSION_1_8=JNI_VERSION_1_6"]
ANDROID_LIBS = ["skottie", "sksg", "skshaper", "skparagraph", "skunicode", "skresources", "svg", "skia", "harfbuzz",
                "icu", "freetype2", "png", "jpeg", "webp", "wuffs", "zlib", "expat", "skcms", "dng_sdk", "piex",
                "cpu-features"]


def build_android(args, out):
    """libmaya.so for one Android ABI: every source compiled with the NDK's clang, linked against the skia-pack
    archives, exporting only JNI_OnLoad and the functions of maya.def (a version script made from it)"""
    import concurrent.futures
    triple, libdir = ANDROID[args.target]
    if not args.ndk or not args.skia:
        sys.exit("--target %s needs --ndk and --skia (the extracted skia-pack release)" % args.target)
    skia = pathlib.Path(args.skia).resolve()
    bin_dir = pathlib.Path(args.ndk) / "toolchains" / "llvm" / "prebuilt" / "windows-x86_64" / "bin"
    clang = [str(bin_dir / "clang++.exe"), "--target=%s%d" % (triple, ANDROID_API)]
    shutil.rmtree(out / "obj", ignore_errors=True)
    sources = [l.strip() for l in (HERE / "sources.txt").read_text().splitlines() if l.strip()]
    includes = [skia / p for p in SKIA_INCLUDES] + [HERE / "cc"]
    # skia-pack builds Skia without RTTI: a subclass of a Skia type (Drawable.cc) must not ask for its typeinfo
    flags = (["-c", "-std=c++17", "-O2", "-fPIC", "-fno-rtti", "-ffunction-sections", "-fdata-sections", "-w",
              "-ffile-prefix-map=%s=." % HERE, "-ffile-prefix-map=%s=skia" % skia]
             + ["-D" + d for d in ANDROID_DEFINES] + ["-I%s" % i for i in includes])

    def compile_one(source):
        obj = out / "obj" / (source.replace("/", "_")[:-3] + ".o")
        obj.parent.mkdir(parents=True, exist_ok=True)
        p = subprocess.run(clang + flags + [str(HERE / "cc" / source), "-o", str(obj)], capture_output=True)
        return source, obj, p.returncode, p.stderr.decode("utf-8", "replace")

    with concurrent.futures.ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
        results = list(pool.map(compile_one, sources))
    failed = [r for r in results if r[2] != 0]
    (out / "cl.log").write_text("".join("== %s\n%s" % (r[0], r[3]) for r in results), encoding="utf-8")
    if failed:
        print("COMPILE-FAILED", " ".join(r[0] for r in failed))
        sys.exit(1)
    exports = [l.strip() for l in (HERE / "maya.def").read_text().splitlines()[2:] if l.strip()]
    (out / "maya.map").write_text("{\n  global:\n" + "".join("    %s;\n" % e for e in exports) + "  local: *;\n};\n",
                                  encoding="ascii")
    link = clang + ["-shared", "-o", str(out / "libmaya.so"), "-Wl,--version-script=%s" % (out / "maya.map"),
                    "-Wl,--gc-sections", "-Wl,--no-undefined", "-Wl,--build-id=none", "-Wl,-z,max-page-size=16384", "-static-libstdc++", "-s"]
    link += [str(r[1]) for r in results] + ["-L%s" % (skia / "out" / libdir), "-Wl,--start-group"]
    link += ["-l" + l for l in ANDROID_LIBS if (skia / "out" / libdir / ("lib%s.a" % l)).exists()]
    link += ["-Wl,--end-group", "-llog", "-landroid", "-lEGL", "-lGLESv2", "-ljnigraphics"]
    p = subprocess.run(link, capture_output=True)
    (out / "link.log").write_text(p.stderr.decode("utf-8", "replace"), encoding="utf-8")
    if p.returncode != 0:
        print("LINK-FAILED")
        sys.exit(1)
    print("BUILD-OK")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--skia")
    parser.add_argument("--jdk", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--vcvars")
    parser.add_argument("--target", default="windows-x64", choices=["windows-x64"] + sorted(ANDROID))
    parser.add_argument("--ndk", help="the Android NDK (r30) for the android targets")
    args = parser.parse_args()
    out = pathlib.Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    if args.target in ANDROID:
        build_android(args, out)
        return
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
    # objects of an earlier build would be linked too (the link takes obj\<folder>\*.obj): start empty
    shutil.rmtree(out / "obj", ignore_errors=True)
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
