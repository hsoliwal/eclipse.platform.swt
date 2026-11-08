# SPDX-License-Identifier: EPL-2.0
"""Exercise the actual native dependency checker with explicit tool fixtures.

The readelf/objdump responses are controlled fixtures, not native load evidence.
mktemp is the real host command; its creations are observed without replacement.
"""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / "bundles/org.eclipse.swt/Eclipse SWT PI/gtk/library/check_dependencies.sh"

TOOL_FIXTURE = r'''
from pathlib import Path
import json, os, stat, subprocess, sys, time

root = Path(os.environ["SWT_CHECK_FIXTURE"])
tool = Path(sys.argv[0]).name
arguments = sys.argv[1:]
if tool == "mktemp":
    result = subprocess.run([os.environ["SWT_REAL_MKTEMP"], *arguments],
                            capture_output=True, text=True)
    if result.returncode == 0:
        created = Path(result.stdout.strip())
        record = {"path": str(created), "mode": stat.S_IMODE(created.stat().st_mode),
                  "arguments": arguments}
        with (root / "temporary-files.jsonl").open("a", encoding="utf-8") as log:
            log.write(json.dumps(record) + "\n")
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
    sys.exit(result.returncode)
expected = ["-d" if tool == "readelf" else "-R", os.environ["SWT_CHECK_LIBRARY"]]
if arguments != expected:
    sys.stderr.write("fixture received split or unexpected arguments: " + repr(arguments))
    sys.exit(64)
if tool == "objdump" and (root / "overlap-enabled").exists():
    (root / ("entered-" + str(os.getpid()))).write_text("entered\n")
    deadline = time.monotonic() + 10
    while not (root / "release-overlap").exists():
        if time.monotonic() >= deadline:
            sys.exit(70)
        time.sleep(0.01)
sys.stdout.write((root / (tool + ".txt")).read_text(encoding="utf-8"))
sys.exit(int(os.environ.get("SWT_" + tool.upper() + "_STATUS", "0")))
'''


class NativeDependencyCheck(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="swt-native-check-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.private_tmp = self.root / "private temp with spaces"
        self.private_tmp.mkdir()
        self.bin = self.root / "fixture-bin"
        self.bin.mkdir()
        self.library = self.root / "library.so"
        self.library.write_text("explicit non-ELF tool fixture\n", encoding="utf-8")
        real_mktemp = shutil.which("mktemp")
        if real_mktemp is None:
            self.fail("The native checker requires the real mktemp command")
        for name in ("readelf", "objdump", "mktemp"):
            executable = self.bin / name
            executable.write_text("#!" + sys.executable + "\n" + TOOL_FIXTURE, encoding="utf-8")
            executable.chmod(0o700)
        self.env = os.environ.copy()
        self.env.update({"PATH": str(self.bin) + os.pathsep + self.env.get("PATH", ""),
                         "TMPDIR": str(self.private_tmp), "LC_ALL": "C", "PYTHONDONTWRITEBYTECODE": "1",
                         "SWT_CHECK_FIXTURE": str(self.root), "SWT_REAL_MKTEMP": real_mktemp,
                         "SWT_CHECK_LIBRARY": str(self.library)})
        self.responses("libc.so.6", ("2.9", "2.14", "2.28"))

    def responses(self, dependency, versions):
        dynamic = (" 0x0000000000000001 (NEEDED) Shared library: [" + dependency + "]\n"
                   if dependency else "Dynamic section contains no NEEDED entries\n")
        (self.root / "readelf.txt").write_text(dynamic, encoding="utf-8")
        relocations = "".join("0000000000001000 R_X86_64_JUMP_SLOT symbol@GLIBC_" + version + "\n"
                              for version in versions)
        (self.root / "objdump.txt").write_text(relocations, encoding="utf-8")

    def command(self):
        return ["bash", str(CHECKER), str(self.library), "2.28", "libc.so.6", "libpthread.so.0"]

    def check(self):
        return subprocess.run(self.command(), env=self.env, cwd=self.root,
                              capture_output=True, text=True, timeout=15)

    def assert_private_cleanup(self, count):
        log = self.root / "temporary-files.jsonl"
        records = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
        self.assertEqual(count, len(records))
        self.assertEqual(count, len({record["path"] for record in records}))
        for record in records:
            created = Path(record["path"])
            self.assertEqual(self.private_tmp, created.parent)
            self.assertEqual(0o600, record["mode"])
            self.assertEqual([str(self.private_tmp / "swt-version-check.XXXXXX")], record["arguments"])
            self.assertFalse(created.exists(), "The checker leaked its private version file")
        self.assertEqual([], list(self.private_tmp.iterdir()))

    def test_allowed_dependencies_and_glibc_version_order_pass(self):
        result = self.check()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assert_private_cleanup(1)

    def test_no_dependencies_or_glibc_symbols_pass(self):
        self.responses(None, ())
        result = self.check()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_private_cleanup(1)

    def test_disallowed_dependency_fails_before_version_check(self):
        self.responses("libunexpected.so.1", ("2.14",))
        result = self.check()
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("illegal dependency of libunexpected.so.1", result.stdout)
        self.assert_private_cleanup(0)

    def test_glibc_overflow_fails_and_reports_greatest_version(self):
        self.responses("libc.so.6", ("2.9", "2.28", "2.29", "2.100"))
        result = self.check()
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("greater than allowed version of 2.28", result.stdout)
        self.assertIn("symbol@GLIBC_2.100", result.stdout)
        self.assertNotIn("symbol@GLIBC_2.29", result.stdout)
        self.assert_private_cleanup(1)

    def test_space_containing_library_filename_is_one_argument(self):
        self.library = self.root / "native library with spaces.so"
        self.library.write_text("explicit non-ELF tool fixture\n", encoding="utf-8")
        self.env["SWT_CHECK_LIBRARY"] = str(self.library)
        result = self.check()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_private_cleanup(1)

    def test_objdump_failure_preserves_status_and_cleans_private_file(self):
        self.env["SWT_OBJDUMP_STATUS"] = "7"
        result = self.check()
        self.assertEqual(7, result.returncode, result.stdout + result.stderr)
        self.assert_private_cleanup(1)

    def test_readelf_failure_preserves_status_without_creating_tempfile(self):
        self.env["SWT_READELF_STATUS"] = "6"
        result = self.check()
        self.assertEqual(6, result.returncode, result.stdout + result.stderr)
        self.assert_private_cleanup(0)

    def test_overlapping_invocations_keep_distinct_private_version_files(self):
        (self.root / "overlap-enabled").write_text("enabled\n")
        processes = [subprocess.Popen(self.command(), env=self.env, cwd=self.root,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                     for _ in range(2)]
        try:
            deadline = time.monotonic() + 10
            while len(list(self.root.glob("entered-*"))) != 2:
                self.assertTrue(all(process.poll() is None for process in processes),
                                "A checker exited before both invocations reached objdump")
                self.assertLess(time.monotonic(), deadline, "The overlapping invocations did not start")
                time.sleep(0.01)
            self.assertEqual(2, len(list(self.private_tmp.iterdir())))
            (self.root / "release-overlap").write_text("release\n")
            for process in processes:
                stdout, stderr = process.communicate(timeout=10)
                self.assertEqual(0, process.returncode, stdout + stderr)
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.communicate()
        self.assert_private_cleanup(2)


if __name__ == "__main__":
    unittest.main()
