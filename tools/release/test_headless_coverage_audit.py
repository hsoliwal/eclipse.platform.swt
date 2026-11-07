# SPDX-License-Identifier: EPL-2.0
"""Synthetic refusal tests for the focused headless coverage gap audit."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("headless_coverage_audit.py")
SPEC = importlib.util.spec_from_file_location("swt_headless_coverage", SCRIPT)
audit = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(audit)


class HeadlessCoverageAuditTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source_name = ("bundles/org.eclipse.swt/Eclipse SWT/common/"
                            "org/eclipse/swt/m3/Synthetic.java")
        source = self.root / self.source_name
        source.parent.mkdir(parents=True)
        source.write_text("package org.eclipse.swt.m3; final class Synthetic {}\n")
        self.test_name = (
            "tests/org.eclipse.swt.tests/JUnit Tests/"
            "org/eclipse/swt/tests/junit/Test_M3Synthetic.java")
        test_source = self.root / self.test_name
        test_source.parent.mkdir(parents=True)
        test_source.write_text("class Test_M3Synthetic {}\n", encoding="utf-8")
        self.revision = "a" * 40
        self.receipt = {
            "schema_version": 1, "product": "SWT M3",
            "status": "PASS_FOCUSED_HEADLESS_ONLY", "release_status": "HOLD",
            "source_commit": self.revision,
            "execution": {"tests_found": 3, "tests_started": 3,
                          "tests_succeeded": 3, "tests_failed": 0,
                          "tests_skipped": 0, "tests_aborted": 0,
                          "native_display_created": False},
            "java_compile": {"native_build": "NOT_RUN"},
            "test_inputs": [{
                "path": self.test_name,
                "sha256": hashlib.sha256(test_source.read_bytes()).hexdigest(),
            }],
            "source_counters": [{
                "source": self.source_name,
                "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "line": {"covered": 9, "missed": 1},
                "branch": {"covered": 8, "missed": 2},
            }],
            "scoped_totals": {
                "line": {"covered": 9, "missed": 1, "total": 10, "percent": 90.0},
                "branch": {"covered": 8, "missed": 2, "total": 10, "percent": 80.0},
            },
        }

    def test_real_counts_report_deficit_without_qualifying_release(self):
        result = audit.evaluate(self.root, self.receipt, candidate_sha=self.revision)
        self.assertEqual(1, result["measured_files"])
        self.assertEqual(1, result["files"][0]["branch"]["additional_needed"])
        self.assertEqual(0, result["files"][0]["line"]["additional_needed"])
        self.assertFalse(result["release_ready"])
        self.assertTrue(any("per-file" in x for x in result["release_blockers"]))
        self.assertTrue(any("native" in x for x in result["release_blockers"]))
        self.assertEqual(1, result["test_inputs_recorded"])
        self.assertEqual(1, result["test_inputs_matching"])
        self.assertEqual([], result["test_input_drift"])

    def test_forged_totals_and_sha_are_refused(self):
        for key, value, reason in (
            ("covered", 99, "totals"),
            ("total", 11, "totals"),
            ("percent", 100.0, "totals"),
        ):
            receipt = copy.deepcopy(self.receipt)
            receipt["scoped_totals"]["line"][key] = value
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, reason):
                audit.evaluate(self.root, receipt)
        receipt = copy.deepcopy(self.receipt)
        receipt["source_counters"][0]["source_sha256"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "identity drift"):
            audit.evaluate(self.root, receipt)

    def test_malformed_counts_and_duplicate_rows_are_refused(self):
        for value in (-1, None, True, "8"):
            receipt = copy.deepcopy(self.receipt)
            receipt["source_counters"][0]["branch"]["covered"] = value
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "counts"):
                audit.evaluate(self.root, receipt)
        receipt = copy.deepcopy(self.receipt)
        receipt["source_counters"].append(copy.deepcopy(receipt["source_counters"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            audit.evaluate(self.root, receipt)

    def test_recorded_test_mutation_is_reported_as_stale(self):
        recorded = self.root / self.test_name
        recorded.write_text("class Test_M3Synthetic { /* changed */ }\n", encoding="utf-8")
        result = audit.evaluate(self.root, self.receipt, candidate_sha=self.revision)
        self.assertEqual(1, result["test_inputs_recorded"])
        self.assertEqual(0, result["test_inputs_matching"])
        self.assertEqual([self.test_name], [x["path"] for x in result["test_input_drift"]])
        self.assertNotEqual(result["test_input_drift"][0]["receipt_sha256"],
                            result["test_input_drift"][0]["current_sha256"])
        self.assertTrue(any("recorded test inputs changed" in x
                            for x in result["release_blockers"]))
        self.assertFalse(result["release_ready"])

    def test_recorded_test_manifest_rejects_missing_or_duplicate_entries(self):
        receipt = copy.deepcopy(self.receipt)
        receipt.pop("test_inputs")
        with self.assertRaisesRegex(ValueError, "no recorded test source"):
            audit.evaluate(self.root, receipt)
        receipt = copy.deepcopy(self.receipt)
        receipt["test_inputs"].append(copy.deepcopy(receipt["test_inputs"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate or missing"):
            audit.evaluate(self.root, receipt)
        receipt = copy.deepcopy(self.receipt)
        receipt["test_inputs"][0]["sha256"] = "not-a-sha256"
        with self.assertRaisesRegex(ValueError, "SHA-256 missing"):
            audit.evaluate(self.root, receipt)

    def test_recorded_test_path_refuses_escape_and_symlink(self):
        for value in ("/tmp/elsewhere/Test_M3X.java",
                      "tests/org.eclipse.swt.tests/JUnit Tests/../../Bad.java",
                      "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/../Bad.java",
                      "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/Bad.txt"):
            with self.subTest(path=value), self.assertRaises(ValueError):
                audit.safe_test_source(self.root, value)
        source = self.root / self.test_name
        alias = source.parent / "Test_M3Alias.java"
        try:
            alias.symlink_to(source)
        except (OSError, NotImplementedError):
            return
        with self.assertRaisesRegex(ValueError, "symlink refused"):
            audit.safe_test_source(self.root, self.test_name.replace("Test_M3Synthetic", "Test_M3Alias"))

    def test_untrusted_paths_and_symlinks_are_refused(self):
        for value in ("/etc/passwd", "../Synthetic.java", "bundles/../../Synthetic.java",
                      "bundles/org.eclipse.swt\\x.java"):
            with self.subTest(path=value), self.assertRaises(ValueError):
                audit.safe_source(self.root, value)
        link = self.root / "bundles/org.eclipse.swt/alias.java"
        try:
            link.symlink_to(self.root / self.source_name)
        except (OSError, NotImplementedError):
            return
        with self.assertRaisesRegex(ValueError, "symlink"):
            audit.safe_source(self.root, "bundles/org.eclipse.swt/alias.java")

    def test_invalid_status_test_counts_and_limits_are_refused(self):
        for field, value in (("status", "RELEASE_READY"), ("release_status", "PASS"),
                             ("product", "other")):
            receipt = copy.deepcopy(self.receipt)
            receipt[field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "receipt"):
                audit.evaluate(self.root, receipt)
        receipt = copy.deepcopy(self.receipt)
        receipt["execution"]["tests_succeeded"] = 2
        with self.assertRaisesRegex(ValueError, "executed-test"):
            audit.evaluate(self.root, receipt)
        for minimum in (0, 0.89, float("nan"), 2):
            with self.subTest(minimum=minimum), self.assertRaises(ValueError):
                audit.evaluate(self.root, self.receipt, minimum=minimum)

    def test_historical_head_and_missing_platform_never_pass(self):
        result = audit.evaluate(self.root, self.receipt, candidate_sha="b" * 40)
        self.assertTrue(any("historical receipt" in x for x in result["release_blockers"]))
        result = audit.evaluate(self.root, self.receipt)
        self.assertFalse(result["release_ready"])

    def test_cli_writes_honest_json_and_release_mode_refuses(self):
        import contextlib
        import io
        self.receipt_path = self.root / "receipt.json"
        self.receipt_path.write_text(json.dumps(self.receipt))
        output = self.root / "audit.json"
        args = ["--root", str(self.root), "--receipt", str(self.receipt_path),
                "--candidate-sha", self.revision, "--json-out", str(output)]
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, audit.main(args))
            self.assertEqual(1, audit.main(args + ["--release"]))
        self.assertFalse(json.loads(output.read_text())["release_ready"])

    def test_duplicate_json_keys_are_refused(self):
        with self.assertRaisesRegex(ValueError, "duplicate receipt key"):
            json.loads('{"a":1,"a":2}', object_pairs_hook=audit.unique_keys)


if __name__ == "__main__":
    unittest.main()
