# SPDX-License-Identifier: EPL-2.0
"""Synthetic parser/source guard tests. Never interpret fixture success as SWT CI."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

PATH = Path(__file__).with_name("readiness.py")
spec = importlib.util.spec_from_file_location("swt_release_readiness", PATH)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ReadinessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for item in gate.REQUIRED:
            target = self.root / item
            if item.startswith("tests/"):
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("fixture\n", encoding="utf-8")
        (self.root / "bundles/org.eclipse.swt/META-INF/MANIFEST.MF").write_text(
            "Bundle-SymbolicName: org.eclipse.swt; singleton:=true\n"
            "Automatic-Module-Name: org.eclipse.swt\n"
            "Bundle-RequiredExecutionEnvironment: JavaSE-21\n"
            "Export-Package: org.eclipse.swt.widgets,\n"
            " org.eclipse.swt.graphics\n", encoding="utf-8")

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def junit(self, name, failure=0):
        return self.write(name, f'<testsuite tests="3" failures="{failure}" '
                          'errors="0" skipped="1"><testcase name="one" /></testsuite>')

    def jacoco(self, line_covered=99, branch_covered=99):
        return self.write("jacoco.xml", '<report name="fixture">'
                          f'<counter type="LINE" covered="{line_covered}" missed="1"/>'
                          f'<counter type="BRANCH" covered="{branch_covered}" missed="1"/>'
                          '</report>')

    def test_manifest_naming_and_source_anchors(self):
        self.assertEqual([], gate.inspect_source(self.root))
        manifest = self.root / "bundles/org.eclipse.swt/META-INF/MANIFEST.MF"
        manifest.write_text("Bundle-SymbolicName: wrong.toolkit\n")
        self.assertTrue(gate.inspect_source(self.root))

    def test_junit_counts_separate_skips_and_failures(self):
        counts = gate.junit_report(self.junit("TEST-demo.xml"))
        self.assertEqual((3, 1, 0), (counts["tests"], counts["skipped"], counts["failures"]))
        self.assertEqual(1, gate.junit_report(self.junit("TEST-bad.xml", 1))["failures"])

    def test_invalid_or_empty_junit_fails_closed(self):
        with self.assertRaises(ValueError):
            gate.junit_report(self.write("TEST-empty.xml", '<testsuite tests="0"/>'))
        with self.assertRaises(ValueError):
            gate.junit_report(self.write("TEST-invalid.xml", '<other/>'))

    def test_jacoco_counts_and_missing_branch(self):
        metrics = gate.jacoco_report(self.jacoco())
        self.assertEqual(.99, metrics["line"]["ratio"])
        self.assertEqual(.99, metrics["branch"]["ratio"])
        with self.assertRaises(ValueError):
            gate.jacoco_report(self.write("nocounters.xml", '<report><counter type="LINE" covered="1" missed="1"/></report>'))

    def test_source_only_never_becomes_release_ready(self):
        result = gate.evaluate(self.root, {}, None, False, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertFalse(result["source_issues"])
        self.assertEqual(len(gate.BACKENDS), len(result["pending_backends"]))

    def test_missing_backend_and_coverage_block_release(self):
        result = gate.evaluate(self.root, {"win32": self.junit("TEST-win32.xml")}, None, True, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertTrue(any("missing platform JUnit" in x for x in result["source_issues"]))

    def test_failed_junit_and_insufficient_branch_coverage_block(self):
        files = {backend: self.junit("TEST-" + backend + ".xml") for backend in gate.BACKENDS}
        files["win32"] = self.junit("TEST-win32-fail.xml", 1)
        result = gate.evaluate(self.root, files, self.jacoco(branch_covered=8), True, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertTrue(any("failed JUnit: win32" in x for x in result["source_issues"]))
        self.assertTrue(any("branch coverage" in x for x in result["source_issues"]))

    def test_complete_synthetic_surface_is_not_product_evidence(self):
        files = {backend: self.junit("TEST-" + backend + ".xml") for backend in gate.BACKENDS}
        result = gate.evaluate(self.root, files, self.jacoco(), True, .9, .9)
        self.assertEqual([], result["source_issues"])
        self.assertTrue(result["surface_ready"])
        self.assertIn("not a native/UI", result["scope"])


if __name__ == "__main__":
    unittest.main()
