# SPDX-License-Identifier: EPL-2.0
"""Synthetic Surefire XML acceptance and missing-execution refusals."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SOURCE = Path(__file__).with_name("check_junit_coverage.py")
SPEC = importlib.util.spec_from_file_location("swt_junit_evidence", SOURCE)
assert SPEC is not None and SPEC.loader is not None
evidence = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evidence)


class JunitEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def write_case(self, name, result=""):
        file = self.root / ("TEST-" + name + ".xml")
        file.write_text(
            '<testsuite><testcase classname="org.eclipse.swt.tests.junit.'
            + name + '" name="testContract">'
            + result + '</testcase></testsuite>', encoding="utf-8"
        )

    def test_both_classes_have_executed(self):
        for name in evidence.REQUIRED:
            self.write_case(name)
        report = evidence.audit(self.root)
        self.assertTrue(report["passed"])
        self.assertEqual(1, report["testcase_counts"][evidence.REQUIRED[0]]["passed"])

    def test_missing_class_is_not_coverage(self):
        self.write_case(evidence.REQUIRED[0])
        report = evidence.audit(self.root)
        self.assertFalse(report["passed"])
        self.assertEqual(0, report["testcase_counts"][evidence.REQUIRED[1]]["passed"])

    def test_skip_is_not_pass(self):
        self.write_case(evidence.REQUIRED[0], "<skipped/>")
        self.write_case(evidence.REQUIRED[1])
        self.assertFalse(evidence.audit(self.root)["passed"])

    def test_failure_is_not_pass(self):
        self.write_case(evidence.REQUIRED[0], "<failure message='regression'/>")
        self.write_case(evidence.REQUIRED[1])
        self.assertFalse(evidence.audit(self.root)["passed"])

    def test_no_reports_is_not_pass(self):
        self.assertFalse(evidence.audit(self.root)["passed"])


if __name__ == "__main__":
    unittest.main()
