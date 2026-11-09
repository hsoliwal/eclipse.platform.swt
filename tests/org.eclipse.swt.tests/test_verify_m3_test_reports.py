# Copyright (c) 2026 Synexia and contributors.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Synthetic report fixtures; these tests do not execute SWT or Java."""
import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import verify_m3_test_reports as gate
from verify_viewport_screenshots import qualified_suite


class M3ReportFixtures(unittest.TestCase):
    COMMIT = 'a' * 40
    RUN = 'synthetic-parser-fixture-attempt-1'
    PLATFORM = 'gtk3-x11-x86_64'
    RECORDINGS = '/synthetic/qualified-official-recordings'
    ORACLE = gate.PACKAGE + 'Test_M3BulkUpdatesDifferential'

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='swt-m3-report-fixture-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.reports = self.root / 'reports'
        self.reports.mkdir()
        self.receipt = self.root / 'receipt.json'
        self.contract = json.loads(gate.CONTRACT.read_text(encoding='utf-8'))
        # Synthetic source bytes keep fixtures independent of a native checkout.
        # The reviewed names remain the real inventory; only fixture hashes vary.
        for entry in self.contract['classes']:
            source = self.root / entry['source']
            source.parent.mkdir(parents=True, exist_ok=True)
            data = ('// synthetic fixture, not compiled\nclass ' + entry['class'].split('.')[-1] + ' {\n' +
                    ''.join('  @Test void ' + name + '() {}\n' for name in entry['methods']) + '}\n').encode()
            source.write_bytes(data)
            entry['sourceSha256'] = hashlib.sha256(data).hexdigest()
        self.contract_path = self.root / 'contract.json'
        self.write_contract()
        self.write_reports()

    def write_contract(self):
        self.contract_path.write_text(json.dumps(self.contract), encoding='utf-8')

    def path(self, name):
        return self.reports / ('TEST-' + name + '.xml')

    def write_reports(self, mode='viewport'):
        properties = {
            'test': 'AllTests', 'm3.release.commit': self.COMMIT, 'm3.release.run': self.RUN,
            'm3.release.platform': self.PLATFORM, 'swt.widget.mode': mode,
            'swt.viewport.record': 'false', 'swt.viewport.survey': 'false',
            'swt.viewport.recording': self.RECORDINGS,
            'java.version': '25', 'os.name': 'Linux', 'os.arch': 'amd64',
        }
        for entry in self.contract['classes']:
            skip = mode == 'original' and entry['class'] == gate.ORIGINAL_SKIP_CLASS
            suite = ET.Element('testsuite', name=entry['class'], tests=str(len(entry['methods'])),
                               failures='0', errors='0', skipped='1' if skip else '0')
            props = ET.SubElement(suite, 'properties')
            for name, value in properties.items():
                ET.SubElement(props, 'property', name=name, value=value)
            for method in entry['methods']:
                case = ET.SubElement(suite, 'testcase', classname=entry['class'], name=method + '()')
                if skip and method == gate.ORIGINAL_SKIP_METHOD:
                    ET.SubElement(case, 'skipped', message='lanes exist only for the viewport')
            self.save(entry['class'], suite)

    def read(self, name=ORACLE):
        return ET.parse(self.path(name)).getroot()

    def save(self, name, suite):
        self.path(name).write_bytes(ET.tostring(suite, encoding='utf-8', xml_declaration=True))

    def verify(self, mode='viewport'):
        return gate.verify(self.root, self.reports, self.receipt, commit=self.COMMIT,
                           run_id=self.RUN, platform=self.PLATFORM, mode=mode,
                           recording_directory=self.RECORDINGS, contract_path=self.contract_path)

    def reject(self, mode='viewport', message=None):
        self.receipt.write_text('{"status":"PASS","stale":true}', encoding='utf-8')
        errors = (AssertionError, ValueError, KeyError, TypeError, OSError, ET.ParseError)
        with self.assertRaises(errors) as caught:
            self.verify(mode)
        if message:
            self.assertIn(message, str(caught.exception))
        self.assertFalse(self.receipt.exists(), 'A failed validation left a PASS receipt')

    def set_property(self, key, value, name=ORACLE):
        suite = self.read(name)
        props = suite.find('properties')
        match = next((prop for prop in props if prop.get('name') == key), None)
        if match is None:
            ET.SubElement(props, 'property', name=key, value=value)
        else:
            match.set('value', value)
        self.save(name, suite)

    def test_complete_viewport_reports_and_receipt_hashes(self):
        result = self.verify()
        self.assertEqual(result['discoveredClasses'], 34)
        self.assertEqual(result['passingMethods'], 245)
        self.assertEqual(result['recordingComparisonMethods'], 11)
        self.assertEqual(result['junit'], {'tests': 245, 'failures': 0, 'errors': 0, 'skipped': 0})
        for entry in result['classes']:
            self.assertEqual(entry['reportSha256'], hashlib.sha256(self.path(entry['class']).read_bytes()).hexdigest())
            self.assertTrue(all(method['status'] == 'PASS' for method in entry['methods']))

    def test_original_reports_require_only_the_exact_existing_skip(self):
        self.write_reports('original')
        result = self.verify('original')
        skipped = [(entry['class'], method['name']) for entry in result['classes']
                   for method in entry['methods'] if method['status'] == 'SKIP']
        self.assertEqual(skipped, [(gate.ORIGINAL_SKIP_CLASS, gate.ORIGINAL_SKIP_METHOD)])
        self.assertEqual(result['passingMethods'], 244)
        self.assertEqual(result['recordingComparisonMethods'], 11)

    def test_new_boundary_methods_cannot_be_omitted_failed_or_skipped(self):
        required = (
            ('Test_M3ColumnFilter', 'test_invalidUpdatedParentFailsAndResetRecovers'),
            ('Test_M3ColumnFilter', 'test_ancestorCycleFallbackKeepsOnlyReachableRows'),
            ('Test_M3ColumnFilter', 'test_synchronousGenerationAndCriterionCopies'),
            ('Test_M3ColumnSort', 'test_duplicateKeysStayStableAcrossFilterAndSourceEdits'),
            ('Test_M3ColumnSort', 'test_rejectedSortKeysPreserveTheViewAndCopyInputs'),
            ('Test_M3ItemRange', 'test_duplicateAtMaximumIntCoalescesOnce'),
            ('Test_M3ItemRange', 'test_upperIntBoundaryAndNonAdjacentRuns'),
            ('Test_M3RowExtents', 'test_negativeEndpointBranchesAndZeroLengthEdits'),
            ('Test_M3RowExtents', 'test_anchoringDistinguishesFirstMeasurementAndExactBoundaries'),
            ('Test_M3RowExtents', 'test_smartAlignmentKeepsRevealedOffsetsAndCentersOthers'),
            ('Test_M3RowExtents', 'test_sparseMaximumRowCountKeepsLongOffsetsThroughEdits'),
            ('Test_M3RowExtents', 'test_autoAlignmentMovesOnlyToNearestRevealBoundary'),
        )
        for short_name, method in required:
            name = gate.PACKAGE + short_name
            for outcome in ('missing', 'failure', 'skipped'):
                with self.subTest(name=name, method=method, outcome=outcome):
                    self.write_reports()
                    suite = self.read(name)
                    case = next(case for case in suite.findall('testcase')
                                if case.get('name') == method + '()')
                    if outcome == 'missing':
                        suite.remove(case)
                        suite.set('tests', str(int(suite.get('tests')) - 1))
                    else:
                        ET.SubElement(case, outcome)
                        suite.set('failures' if outcome == 'failure' else 'skipped', '1')
                    self.save(name, suite)
                    self.reject(message='required method')

    def test_repeated_validation_is_byte_identical(self):
        self.verify()
        before = self.receipt.read_bytes()
        self.verify()
        self.assertEqual(before, self.receipt.read_bytes())

    def test_missing_class_cannot_pass_from_source_inventory(self):
        self.path(self.ORACLE).unlink()
        self.reject(message='Missing M3 report classes')

    def test_missing_method_with_consistent_reduced_counters_fails(self):
        suite = self.read()
        suite.remove(suite.find('testcase'))
        suite.set('tests', '1')
        self.save(self.ORACLE, suite)
        self.reject(message='required recording comparison')

    def test_duplicate_method_cannot_replace_a_missing_method(self):
        suite = self.read()
        cases = suite.findall('testcase')
        cases[0].set('name', cases[1].get('name'))
        self.save(self.ORACLE, suite)
        self.reject(message='duplicate or unexpected JUnit method')

    def test_unexpected_method_fails(self):
        suite = self.read()
        suite.find('testcase').set('name', 'notAReviewedMethod()')
        self.save(self.ORACLE, suite)
        self.reject(message='unexpected JUnit method')

    def test_wrong_testcase_class_fails(self):
        suite = self.read()
        suite.find('testcase').set('classname', gate.PACKAGE + 'SomeOtherTest')
        self.save(self.ORACLE, suite)
        self.reject(message='Unexpected JUnit class')

    def test_all_six_recording_classes_reject_skips_in_both_modes(self):
        classes = [entry['class'] for entry in self.contract['classes'] if entry['recordingRequired']]
        self.assertEqual(len(classes), 6)
        for mode in ('viewport', 'original'):
            for name in classes:
                with self.subTest(mode=mode, name=name):
                    self.write_reports(mode)
                    suite = self.read(name)
                    suite.set('skipped', '1')
                    ET.SubElement(suite.find('testcase'), 'skipped', message='no reference recording')
                    self.save(name, suite)
                    self.reject(mode, 'required recording comparison')

    def test_viewport_rejects_even_the_original_mode_exception(self):
        suite = self.read(gate.ORIGINAL_SKIP_CLASS)
        suite.set('skipped', '1')
        case = next(case for case in suite.findall('testcase')
                    if case.get('name') == gate.ORIGINAL_SKIP_METHOD + '()')
        ET.SubElement(case, 'skipped')
        self.save(gate.ORIGINAL_SKIP_CLASS, suite)
        self.reject()

    def test_original_requires_its_named_skip_to_be_present(self):
        self.write_reports('original')
        suite = self.read(gate.ORIGINAL_SKIP_CLASS)
        suite.set('skipped', '0')
        for case in suite.findall('testcase'):
            for skipped in case.findall('skipped'):
                case.remove(skipped)
        self.save(gate.ORIGINAL_SKIP_CLASS, suite)
        self.reject('original')

    def test_spoofed_counters_cannot_hide_failure_error_or_skip_nodes(self):
        for tag in ('failure', 'error', 'skipped'):
            for placement in ('method', 'suite'):
                with self.subTest(tag=tag, placement=placement):
                    self.write_reports()
                    suite = self.read()
                    ET.SubElement(suite.find('testcase') if placement == 'method' else suite, tag)
                    self.save(self.ORACLE, suite)
                    self.reject()

    def test_counter_disagrees_with_real_testcases(self):
        suite = self.read()
        suite.set('tests', '999')
        self.save(self.ORACLE, suite)
        self.reject(message='JUnit counters')

    def test_duplicate_report_class_fails(self):
        (self.reports / 'TEST-zz-duplicate.xml').write_bytes(self.path(self.ORACLE).read_bytes())
        self.reject(message='Duplicate M3 report class')

    def test_unreviewed_m3_report_class_fails(self):
        name = gate.PACKAGE + 'Test_M3Unreviewed'
        suite = ET.Element('testsuite', name=name, tests='1', failures='0', errors='0', skipped='0')
        ET.SubElement(suite, 'testcase', classname=name, name='unreviewed')
        self.save(name, suite)
        self.reject(message='Unexpected M3 report class')

    def test_report_filename_and_suite_identity_must_match(self):
        path = self.path(self.ORACLE)
        data = path.read_bytes()
        path.unlink()
        (self.reports / 'TEST-incorrect-name.xml').write_bytes(data)
        self.reject(message='Unexpected Surefire report identity')

    def test_missing_or_empty_report_directory_fails(self):
        self.reports = self.root / 'missing'
        self.reject(message='Missing Surefire report directory')
        self.reports.mkdir()
        self.reject(message='Missing M3 report classes')

    def test_malformed_xml_is_rejected(self):
        self.path(self.ORACLE).write_bytes(b'<testsuite')
        self.reject()

    def test_declaration_guard_rejects_otherwise_complete_reports(self):
        body = ET.tostring(self.read(), encoding='unicode')
        doctype = '<!DOCTYPE testsuite [<!ENTITY unused "fixture">]>'
        variants = [
            ((doctype + body).encode('utf-8'), 'DTD/entity declarations'),
            (('<?xml version="1.0" encoding="UTF-16"?>' + doctype + body).encode('utf-16'), 'Only UTF-8'),
            (('<?xml version="1.0" encoding="UTF-16"?>' + doctype + body).encode('utf-16-le'), 'Only UTF-8'),
            (('<?xml version="1.0" encoding="ISO-8859-1"?>' + body).encode('utf-8'), 'Only UTF-8'),
        ]
        for data, message in variants:
            with self.subTest(message=message, prefix=data[:30]):
                self.path(self.ORACLE).write_bytes(data)
                self.reject(message=message)

    def test_required_properties_cannot_be_missing_stale_or_narrowed(self):
        for key in ('test', 'm3.release.commit', 'm3.release.run', 'm3.release.platform',
                    'swt.widget.mode', 'swt.viewport.recording'):
            for operation in ('missing', 'wrong'):
                with self.subTest(key=key, operation=operation):
                    self.write_reports()
                    suite = self.read()
                    props = suite.find('properties')
                    prop = next(prop for prop in props if prop.get('name') == key)
                    if operation == 'missing':
                        props.remove(prop)
                    else:
                        prop.set('value', 'wrong-or-stale')
                    self.save(self.ORACLE, suite)
                    self.reject(message=key)

    def test_recording_survey_seed_filter_and_widget_override_cannot_pass(self):
        values = [('swt.viewport.record', 'true'), ('swt.viewport.survey', 'true'),
                  ('swt.viewport.seeds', ''), ('swt.viewport.seeds', '999'),
                  ('swt.widget.mode.Table', 'original'), ('swt.widget.mode.Tree', 'viewport'),
                  ('swt.viewport.themeSwitch', '1:dark'), ('swt.viewport.lafSwitch', '')]
        for key, value in values:
            with self.subTest(key=key, value=value):
                self.write_reports()
                self.set_property(key, value)
                self.reject()

    def test_duplicate_property_fails_even_when_values_agree(self):
        suite = self.read()
        props = suite.find('properties')
        props.append(copy.deepcopy(props[0]))
        self.save(self.ORACLE, suite)
        self.reject(message='duplicate JUnit property')

    def test_reports_from_mixed_jvm_or_platform_are_rejected(self):
        for key in ('java.version', 'os.name', 'os.arch'):
            with self.subTest(key=key):
                self.write_reports()
                self.set_property(key, 'different')
                self.reject(message='mix JVM/platform')

    def test_changed_java_source_requires_reviewed_expectations(self):
        source = self.root / self.contract['classes'][0]['source']
        source.write_bytes(source.read_bytes() + b'// changed\n')
        self.reject(message='Java source hash')

    def test_added_or_missing_java_class_cannot_escape_the_inventory(self):
        extra = self.root / gate.SOURCE_DIRECTORY / 'Test_M3Unreviewed.java'
        extra.write_text('// unreviewed source', encoding='utf-8')
        self.reject(message='source inventory differs')
        extra.unlink()
        (self.root / self.contract['classes'][0]['source']).unlink()
        self.reject(message='source inventory differs')

    def test_narrowed_contract_does_not_hide_an_existing_source_class(self):
        self.contract['classes'].pop(0)
        self.write_contract()
        self.reject(message='source inventory differs')

    def test_old_pass_receipt_is_removed_after_a_later_failure(self):
        self.verify()
        self.assertTrue(self.receipt.exists())
        self.path(self.ORACLE).unlink()
        with self.assertRaises(AssertionError):
            self.verify()
        self.assertFalse(self.receipt.exists())


class ExistingOwnerCompatibility(unittest.TestCase):
    def report(self, *, skip=False, extra_skip=False):
        suite = ET.Element('testsuite', tests='1', failures='0', errors='0', skipped='1' if skip else '0')
        case = ET.SubElement(suite, 'testcase', classname='ExistingTest', name='method()')
        if skip:
            ET.SubElement(case, 'skipped')
        if extra_skip:
            ET.SubElement(suite, 'skipped')
        return io.BytesIO(ET.tostring(suite))

    def test_existing_three_argument_call_still_requires_zero_skips(self):
        self.assertEqual(qualified_suite(self.report(), 'ExistingTest', {'method'})['skipped'], 0)
        with self.assertRaises(AssertionError):
            qualified_suite(self.report(skip=True), 'ExistingTest', {'method'})

    def test_opt_in_skip_requires_exact_identity_and_an_actual_skip(self):
        result = qualified_suite(self.report(skip=True), 'ExistingTest', {'method'}, expected_skips={'method'})
        self.assertEqual(result['skipped'], 1)
        with self.assertRaises(AssertionError):
            qualified_suite(self.report(), 'ExistingTest', {'method'}, expected_skips={'method'})
        with self.assertRaises(AssertionError):
            qualified_suite(self.report(skip=True), 'ExistingTest', {'method'}, expected_skips={'other'})

    def test_an_unattached_skip_cannot_be_accepted(self):
        with self.assertRaises(AssertionError):
            qualified_suite(self.report(skip=True, extra_skip=True), 'ExistingTest', {'method'},
                            expected_skips={'method'})


if __name__ == '__main__':
    unittest.main()
