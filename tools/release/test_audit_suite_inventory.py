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
        # A repaired suite must also remove its no-longer-valid omission waiver.
        self.baseline.write_text("# no active omissions\\n", encoding="utf-8")
        report = self.check()
        self.assertTrue(report["nonregression_pass"])
        self.assertTrue(report["suite_complete"])
        self.assertEqual(0, report["unreferenced_candidate_classes"])
        self.assertEqual([], report["stale_baseline_entries"])

    def test_stale_baseline_cannot_silently_claim_nonregression(self):
        self.put("AllWidgets", "@SelectClasses({Test_Nested.class, Test_Unreferenced.class})")
        stale = self.check()
        self.assertFalse(stale["nonregression_pass"])
        self.assertFalse(stale["suite_complete"])
        self.assertEqual(0, stale["unreferenced_candidate_classes"])
        self.assertEqual(["Test_Unreferenced"], stale["stale_baseline_entries"])
        self.baseline.write_text("# historical Test_Unreferenced now registered\\n", encoding="utf-8")
        repaired = self.check()
        self.assertTrue(repaired["nonregression_pass"])
        self.assertTrue(repaired["suite_complete"])
        self.assertEqual([], repaired["stale_baseline_entries"])

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
        # Registration without baseline reconciliation intentionally remains a failure.
        self.assertFalse(after["suite_complete"])
        self.assertEqual(["Test_Unreferenced"], after["stale_baseline_entries"])

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

    def test_noncode_annotations_cannot_complete_a_suite(self):
        annotation = "@SelectClasses({AllWidgets.class, Test_Direct.class})"
        for source in (
                "/* " + annotation + " */ class AllTests {}",
                "// " + annotation + "\nclass AllTests {}",
                'class AllTests { String note = "' + annotation + '"; }',
                'class AllTests { String note = """\n' + annotation + '\n"""; }'):
            with self.subTest(source=source):
                self.put("AllTests", source)
                report = self.check()
                self.assertFalse(report["nonregression_pass"])
                self.assertFalse(report["suite_complete"])
                self.assertEqual(["AllTests"], report["missing_suite_references"])
                self.assertEqual(0, report["reachable_candidate_classes"])

    def test_noncode_annotation_before_real_suite_does_not_select_omitted_test(self):
        self.put("AllTests", '/* @SelectClasses({Test_Unreferenced.class}) */\n'
                 '@SelectClasses({AllWidgets.class, Test_Direct.class})\n'
                 'class AllTests { String note = "@SelectClasses({Test_Unreferenced.class})"; }')
        report = self.check()
        self.assertTrue(report["nonregression_pass"])
        self.assertEqual(2, report["reachable_candidate_classes"])
        self.assertEqual(["Test_Unreferenced"], report["unreferenced"])

    def test_nested_suite_annotation_in_literal_is_not_a_suite(self):
        self.put("Test_Nested", 'class Test_Nested {\n'
                 'String note = "@SelectClasses({Test_Gone.class})";\n'
                 '@Test void test() {} }')
        report = self.check()
        self.assertTrue(report["nonregression_pass"])
        self.assertNotIn("Test_Nested", report["suites_seen"])
        self.assertEqual([], report["missing_suite_references"])

    def test_block_comments_inside_real_annotation_do_not_add_references(self):
        self.put("AllWidgets", "@SelectClasses({Test_Nested.class, "
                 "/* Test_Unreferenced.class */})")
        self.assertEqual(["Test_Unreferenced"], self.check()["unreferenced"])

    def test_java_unicode_comment_delimiters_are_masked_before_annotation_scan(self):
        self.put("AllTests", r"\u002f\u002a @SelectClasses({AllWidgets.class, "
                 r"Test_Direct.class}) \u002a\u002f class AllTests {}")
        report = self.check()
        self.assertFalse(report["nonregression_pass"])
        self.assertEqual(["AllTests"], report["missing_suite_references"])

    def test_eligible_unicode_annotation_and_doubled_backslash_literal(self):
        self.put("AllTests", r"\uuuu0040SelectClasses({AllWidgets.class, Test_Direct.class})"
                 + '\nclass AllTests { String note = "\\\\u0022'
                 + '@SelectClasses({Test_Gone.class})"; }')
        report = self.check()
        self.assertTrue(report["nonregression_pass"])
        self.assertEqual([], report["missing_suite_references"])

    def test_qualified_value_annotation_and_single_class_shorthand(self):
        self.put("AllTests", "@org.junit.platform.suite.api.SelectClasses(value = AllWidgets.class)")
        self.put("AllWidgets", "@SelectClasses(value = {Test_Nested.class, Test_Direct.class})")
        report = self.check()
        self.assertTrue(report["nonregression_pass"])
        self.assertEqual(2, report["reachable_candidate_classes"])

    def test_literal_escape_sequences_cannot_expose_annotation_cues(self):
        source = ('class Holder { char quote = \'\\\'\'; char slash = \'\\\\\'; '
                  'String text = "escaped \\\" @SelectClasses({Test_Gone.class})"; }')
        self.assertEqual([], inventory.selections(source))

    def test_incomplete_java_lexical_input_is_refused(self):
        for source in ('/* @SelectClasses({Test_Gone.class})',
                       '"@SelectClasses({Test_Gone.class})',
                       '"""\n@SelectClasses({Test_Gone.class})',
                       r'\u00GG @SelectClasses({Test_Gone.class})'):
            with self.subTest(source=source):
                with self.assertRaises(ValueError):
                    inventory.selections(source)


if __name__ == "__main__":
    unittest.main()
