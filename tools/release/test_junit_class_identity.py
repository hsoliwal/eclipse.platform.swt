# SPDX-License-Identifier: EPL-2.0
"""Adversarial Surefire identity fixtures; not SWT product or GUI coverage."""
from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import runpy
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import xml.etree.ElementTree as ET

SOURCE = Path(__file__).with_name("check_junit_coverage.py")
SPEC = importlib.util.spec_from_file_location("swt_exact_class_evidence", SOURCE)
assert SPEC is not None and SPEC.loader is not None
evidence = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evidence)
PACKAGE = "org.eclipse.swt.tests.junit."


class ExactJUnitClassIdentityTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def report(self, rows, namespace=False):
        prefix = "{urn:fixture}" if namespace else ""
        suite = ET.Element(prefix + "testsuite")
        for classname, status in rows:
            attrs = {"name": "contract"}
            if classname is not None:
                attrs["classname"] = classname
            case = ET.SubElement(suite, prefix + "testcase", attrs)
            if status:
                ET.SubElement(case, prefix + status)
        ET.ElementTree(suite).write(self.root / "TEST-identity.xml", encoding="utf-8")

    def cases(self, package=PACKAGE, status=""):
        return [(package + name, status) for name in evidence.REQUIRED]

    def cli(self, *extra):
        argv = [str(SOURCE), "--reports-root", str(self.root), *extra]
        with patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()) as out:
            code = evidence.main()
        return code, json.loads(out.getvalue())

    def test_exact_fully_qualified_classes_preserve_receipt_schema(self):
        self.report(self.cases())
        result = evidence.audit(self.root)
        self.assertTrue(result["passed"])
        self.assertEqual(set(evidence.REQUIRED), set(result["testcase_counts"]))
        self.assertEqual("SWT_M3_JUNIT_EXECUTION_V1", result["schema"])
        self.assertEqual(2, result["all_junit_testcases"]["passed"])

    def test_unrelated_package_cannot_impersonate_required_classes(self):
        self.report(self.cases("unrelated.fixture."))
        result = evidence.audit(self.root)
        self.assertFalse(result["passed"])
        self.assertTrue(all(row["passed"] == 0 for row in result["testcase_counts"].values()))
        self.assertEqual(2, result["observed_junit_classes"])

    def test_default_package_is_not_the_required_package(self):
        self.report(self.cases(""))
        self.assertFalse(evidence.audit(self.root)["passed"])

    def test_prefix_suffix_and_nested_package_collisions_refuse(self):
        for prefix in ("x." + PACKAGE, PACKAGE + "nested.", "org.eclipse.swt.tests.junitx."):
            with self.subTest(prefix=prefix):
                self.report(self.cases(prefix))
                self.assertFalse(evidence.audit(self.root)["passed"])

    def test_inner_class_is_not_the_declared_required_owner(self):
        self.report([(PACKAGE + name + "$Nested", "") for name in evidence.REQUIRED])
        self.assertFalse(evidence.audit(self.root)["passed"])

    def test_failed_real_test_is_not_replaced_by_shadow_pass(self):
        self.report(self.cases(status="failure") + self.cases("other."))
        result = evidence.audit(self.root)
        self.assertFalse(result["passed"])
        self.assertEqual(sorted(PACKAGE + name for name in evidence.REQUIRED),
                         result["failed_class_names"])

    def test_skipped_real_test_is_not_replaced_by_shadow_pass(self):
        self.report(self.cases(status="skipped") + self.cases("other."))
        result = evidence.audit(self.root)
        self.assertFalse(result["passed"])
        self.assertEqual(2, len(result["skipped_class_names"]))

    def test_real_error_is_not_success(self):
        self.report(self.cases(status="error"))
        self.assertFalse(evidence.audit(self.root)["passed"])

    def test_unrelated_error_is_reported_without_changing_gate_scope(self):
        self.report(self.cases() + [("other.Helper", "error")])
        result = evidence.audit(self.root)
        self.assertTrue(result["passed"])
        self.assertEqual(["other.Helper"], result["failed_class_names"])
        self.assertEqual(1, result["all_junit_testcases"]["failed"])

    def test_missing_classname_is_not_required_execution(self):
        self.report([(None, ""), ("", "")])
        result = evidence.audit(self.root)
        self.assertFalse(result["passed"])
        self.assertEqual(0, result["observed_junit_classes"])

    def test_namespace_qualified_xml_with_exact_java_names_passes(self):
        self.report(self.cases(), namespace=True)
        self.assertTrue(evidence.audit(self.root)["passed"])

    def test_one_required_class_is_not_enough(self):
        self.report(self.cases()[:1])
        self.assertFalse(evidence.audit(self.root)["passed"])

    def test_no_reports_and_missing_directory_refuse(self):
        self.assertFalse(evidence.audit(self.root)["passed"])
        self.assertFalse(evidence.audit(self.root / "absent")["passed"])

    def test_malformed_xml_refuses_even_with_genuine_other_report(self):
        self.report(self.cases())
        (self.root / "TEST-broken.xml").write_text("<testsuite>", encoding="utf-8")
        result = evidence.audit(self.root)
        self.assertFalse(result["passed"])
        self.assertEqual(1, len(result["parse_errors"]))

    def test_non_testcase_nodes_are_not_execution(self):
        (self.root / "TEST-empty.xml").write_text("<testsuite><properties/></testsuite>")
        self.assertFalse(evidence.audit(self.root)["passed"])

    def test_strict_cli_saves_refusal_json(self):
        self.report(self.cases("shadow."))
        output = self.root / "nested" / "result.json"
        code, report = self.cli("--strict", "--json-out", str(output))
        self.assertEqual(1, code)
        self.assertFalse(report["passed"])
        self.assertEqual(report, json.loads(output.read_text()))

    def test_success_cli_returns_zero(self):
        self.report(self.cases())
        code, report = self.cli("--strict")
        self.assertEqual(0, code)
        self.assertTrue(report["passed"])

    def test_non_strict_diagnostic_does_not_assert_pass(self):
        code, report = self.cli()
        self.assertEqual(0, code)
        self.assertFalse(report["passed"])

    def test_main_entrypoint_propagates_refusal(self):
        self.report(self.cases("shadow."))
        with patch.object(sys, "argv", [str(SOURCE), "--reports-root", str(self.root), "--strict"]):
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit) as exit:
                runpy.run_path(str(SOURCE), run_name="__main__")
        self.assertEqual(1, exit.exception.code)


if __name__ == "__main__":
    unittest.main()
