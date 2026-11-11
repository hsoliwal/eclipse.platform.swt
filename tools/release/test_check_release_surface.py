# SPDX-License-Identifier: EPL-2.0
"""Synthetic fail-closed fixtures for SWT release naming/registration preflight."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("check_release_surface.py")
SPEC = importlib.util.spec_from_file_location("swt_release_surface", MODULE)
assert SPEC is not None and SPEC.loader is not None
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleaseSurfaceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.put("bundles/org.eclipse.swt/META-INF/MANIFEST.MF",
                 "Bundle-SymbolicName: org.eclipse.swt; singleton:=true\n"
                 "Automatic-Module-Name: org.eclipse.swt\n"
                 "Bundle-RequiredExecutionEnvironment: JavaSE-21\n")
        self.put("tests/org.eclipse.swt.tests/pom.xml", "<test>AllTests</test>\n")
        self.put(".github/workflows/maven.yml",
                 "jobs:\n"
                 "  build-linux:\n    strategy:\n      matrix:\n        java: ['21', '25']\n"
                 "  build-windows:\n    strategy:\n      matrix:\n        java: ['21', '25']\n"
                 "  build-macos:\n    strategy:\n      matrix:\n        java: ['21', '25']\n")
        self.put(release.TEST_DIR / "AllTests.java",
                 "@SelectClasses({Test_ItemBitSetDifferential.class, "
                 "Test_ItemBitSetMaximumPosition.class})\n")
        for name in release.REQUIRED:
            self.put(release.TEST_DIR / (name + ".java"),
                     "class " + name + " { @Test void contract() {} }\n")

    def put(self, path, content):
        destination = self.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")

    def test_valid_naming_and_suite_registration(self):
        report = release.audit(self.root)
        self.assertTrue(report["passed"])
        self.assertTrue(all(report["checks"].values()))

    def test_wrong_module_name_is_a_blocker(self):
        self.put("bundles/org.eclipse.swt/META-INF/MANIFEST.MF",
                 "Bundle-SymbolicName: org.eclipse.swt; singleton:=true\n"
                 "Automatic-Module-Name: com.m3.swt\n"
                 "Bundle-RequiredExecutionEnvironment: JavaSE-21\n")
        report = release.audit(self.root)
        self.assertFalse(report["passed"])
        self.assertFalse(report["checks"]["java_module_name_unchanged"])

    def test_existing_unregistered_test_is_a_blocker(self):
        self.put(release.TEST_DIR / "AllTests.java",
                 "@SelectClasses({Test_ItemBitSetDifferential.class})\n")
        report = release.audit(self.root)
        self.assertFalse(report["passed"])
        self.assertFalse(report["checks"]["Test_ItemBitSetMaximumPosition_registered"])

    def test_missing_java21_in_one_platform_matrix_is_a_blocker(self):
        matrix = self.root / ".github/workflows/maven.yml"
        matrix.write_text(matrix.read_text(encoding="utf-8").replace(
            "  build-windows:\n    strategy:\n      matrix:\n        java: ['21', '25']",
            "  build-windows:\n    strategy:\n      matrix:\n        java: ['25']"),
            encoding="utf-8")
        report = release.audit(self.root)
        self.assertFalse(report["passed"])
        self.assertFalse(report["checks"]["build-windows_java21_java25"])
        self.assertTrue(report["checks"]["build-linux_java21_java25"])

    def test_source_without_a_junit_test_is_a_blocker(self):
        self.put(release.TEST_DIR / "Test_ItemBitSetDifferential.java",
                 "class Test_ItemBitSetDifferential {}\n")
        report = release.audit(self.root)
        self.assertFalse(report["passed"])
        self.assertFalse(report["checks"]["Test_ItemBitSetDifferential_source_has_test"])


if __name__ == "__main__":
    unittest.main()
