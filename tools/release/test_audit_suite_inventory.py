# SPDX-License-Identifier: EPL-2.0
"""Synthetic regression fixtures for default SWT suite reachability."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("audit_suite_inventory.py")
SPEC = importlib.util.spec_from_file_location("swt_suite_inventory", MODULE)
assert SPEC is not None and SPEC.loader is not None
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class SuiteInventoryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.dir = self.root / inventory.TEST_DIR
        self.dir.mkdir(parents=True)
        self.baseline = self.root / inventory.DEFAULT_BASELINE
        self.baseline.parent.mkdir(parents=True)
        self.baseline.write_text("# reviewed baseline\nTest_Unreferenced\n", encoding="utf-8")
        self.put("AllTests", "@SelectClasses({AllWidgets.class, Test_Direct.class})")
        self.put("AllWidgets", "@SelectClasses({Test_Nested.class})")
        for name in ("Test_Direct", "Test_Nested", "Test_Unreferenced"):
            self.put(name, "class " + name + " { @Test void test() {} }")

    def put(self, name, content):
        (self.dir / (name + ".java")).write_text(content, encoding="utf-8")

    def check(self):
        return inventory.audit(self.root)

    def test_known_baseline_debt_is_visible_without_false_completion(self):
        report = self.check()
        self.assertTrue(report["nonregression_pass"])
        self.assertFalse(report["suite_complete"])
        self.assertEqual(3, report["candidate_classes"])
        self.assertEqual(2, report["reachable_candidate_classes"])
        self.assertEqual(["Test_Unreferenced"], report["unreferenced"])
        self.assertEqual(2, len(report["suites_seen"]))

    def test_all_registered_after_repair(self):
        self.put("AllWidgets", "@SelectClasses({Test_Nested.class, Test_Unreferenced.class})")
        report = self.check()
        self.assertTrue(report["nonregression_pass"])
        self.assertTrue(report["suite_complete"])
        self.assertEqual(0, report["unreferenced_candidate_classes"])

    def test_new_test_without_default_suite_entry_fails(self):
        self.put("Test_New", "class Test_New { @Test void test() {} }")
        report = self.check()
        self.assertFalse(report["nonregression_pass"])
        self.assertEqual(["Test_New"], report["new_omissions"])

    def test_removed_reviewed_source_fails(self):
        (self.dir / "Test_Unreferenced.java").unlink()
        report = self.check()
        self.assertFalse(report["nonregression_pass"])
        self.assertEqual(["Test_Unreferenced"], report["baseline_source_disappearances"])

    def test_exact_candidate_content_changes_source_digest(self):
        before = self.check()
        self.put("Test_Direct", "class Test_Direct { @Test void changed() {} }\n")
        after = self.check()
        self.assertNotEqual(before["candidate_sources_sha256"],
                            after["candidate_sources_sha256"])
        self.assertEqual(before["suite_sources_sha256"],
                         after["suite_sources_sha256"])
        self.assertTrue(after["nonregression_pass"])

    def test_suite_content_changes_independent_suite_digest(self):
        before = self.check()
        self.put("AllWidgets", "@SelectClasses({Test_Nested.class, Test_Unreferenced.class})")
        after = self.check()
        self.assertEqual(before["candidate_sources_sha256"],
                         after["candidate_sources_sha256"])
        self.assertNotEqual(before["suite_sources_sha256"],
                            after["suite_sources_sha256"])
        self.assertTrue(after["suite_complete"])

    def test_missing_selected_class_fails(self):
        self.put("AllTests", "@SelectClasses({AllWidgets.class, Test_Gone.class})")
        report = self.check()
        self.assertFalse(report["nonregression_pass"])
        self.assertIn("Test_Gone", report["missing_suite_references"])

    def test_commented_entries_do_not_count_as_registration(self):
        self.put("AllWidgets",
                 "@SelectClasses({Test_Nested.class, // Test_Unreferenced.class\n})")
        report = self.check()
        self.assertEqual(["Test_Unreferenced"], report["unreferenced"])


if __name__ == "__main__":
    unittest.main()
