# Copyright (c) 2026 Synexia and contributors.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Qualify the reviewed M3 inventory from actual AllTests Surefire reports."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

from verify_viewport_screenshots import qualified_suite, require


HERE = Path(__file__).resolve().parent
CONTRACT = HERE / 'm3-test-report-contract.json'
PACKAGE = 'org.eclipse.swt.tests.junit.'
SOURCE_DIRECTORY = Path('JUnit Tests/org/eclipse/swt/tests/junit')
RETENTION = PACKAGE + 'Test_org_eclipse_swt_internal_render_RowPaintRetention'
ORIGINAL_SKIP_CLASS = PACKAGE + 'Test_M3RowMath'
ORIGINAL_SKIP_METHOD = 'test_millionRowVirtualTreeAnswersFromTheLanes'
FORBIDDEN_PROPERTIES = {
    'swt.viewport.seeds', 'swt.viewport.themeSwitch', 'swt.viewport.lafSwitch',
}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def load_contract(test_root, contract_path):
    """Bind the reviewed method inventory to the exact Java test source set."""
    data = contract_path.read_bytes()
    contract = json.loads(data)
    require(contract['schemaVersion'] == 1, 'Unsupported M3 contract version')
    entries = contract['classes']
    require(isinstance(entries, list) and entries, 'Empty M3 class contract')
    names = [entry['class'] for entry in entries]
    require(names == sorted(set(names)), 'Duplicate or unordered M3 classes')
    observed_sources = {
        path.relative_to(test_root).as_posix()
        for path in (test_root / SOURCE_DIRECTORY).glob('Test_M3*.java')
    }
    retention_source = SOURCE_DIRECTORY / (RETENTION.removeprefix(PACKAGE) + '.java')
    require((test_root / retention_source).is_file(), 'Missing RowPaintRetention source')
    observed_sources.add(retention_source.as_posix())
    require(observed_sources == {entry['source'] for entry in entries},
            'M3 source inventory differs from the reviewed contract')
    for entry in entries:
        name, methods = entry['class'], entry['methods']
        require(name.startswith(PACKAGE + 'Test_M3') or name == RETENTION,
                'Unexpected class in M3 contract: ' + name)
        source = SOURCE_DIRECTORY / (name.removeprefix(PACKAGE) + '.java')
        require(entry['source'] == source.as_posix(), 'Class/source mismatch: ' + name)
        require(isinstance(methods, list) and methods and methods == sorted(set(methods)),
                'Empty, duplicate or unordered methods: ' + name)
        require(all(re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*', method) for method in methods),
                'Unexpected method name: ' + name)
        require(type(entry['recordingRequired']) is bool, 'Invalid recording flag: ' + name)
        require(re.fullmatch(r'[0-9a-f]{64}', entry['sourceSha256']) is not None,
                'Invalid Java source hash: ' + name)
        require(sha256((test_root / source).read_bytes()) == entry['sourceSha256'],
                'Java source hash differs from the reviewed contract: ' + name)
    require(ORIGINAL_SKIP_CLASS in names and
            ORIGINAL_SKIP_METHOD in next(entry['methods'] for entry in entries
                                         if entry['class'] == ORIGINAL_SKIP_CLASS),
            'Original-mode skip is absent from the reviewed inventory')
    return contract, sha256(data)


def report_properties(suite):
    properties = {}
    for prop in suite.findall('properties/property'):
        name, value = prop.get('name'), prop.get('value')
        require(name and value is not None and name not in properties,
                'Missing or duplicate JUnit property: ' + str(name))
        properties[name] = value
    return properties


def in_scope(class_name):
    return class_name.startswith(PACKAGE + 'Test_M3') or class_name == RETENTION


def verify(test_root, reports, receipt_path, *, commit, run_id, platform, mode,
           recording_directory, contract_path=CONTRACT):
    """Write a receipt only for complete, current, nonfiltered M3 report evidence."""
    receipt_path.unlink(missing_ok=True)
    require(re.fullmatch(r'[0-9a-f]{40}', commit) is not None, 'A full candidate commit is required')
    require(run_id.strip() and platform.strip(), 'Run and platform identities are required')
    require(mode in {'viewport', 'original'}, 'Unsupported release mode')
    require(recording_directory.strip(), 'A qualified recording directory is required')
    contract, contract_hash = load_contract(test_root, contract_path)
    expected = {entry['class']: entry for entry in contract['classes']}
    require(reports.is_dir(), 'Missing Surefire report directory: ' + str(reports))
    required_properties = {
        'test': 'AllTests',
        'm3.release.commit': commit,
        'm3.release.run': run_id,
        'm3.release.platform': platform,
        'swt.widget.mode': mode,
        'swt.viewport.record': 'false',
        'swt.viewport.survey': 'false',
        'swt.viewport.recording': recording_directory,
    }
    qualified = {}
    environment = None
    for report in sorted(reports.glob('TEST-*.xml')):
        data = report.read_bytes()
        try:
            xml = data.decode('utf-8-sig')
        except UnicodeDecodeError as error:
            raise AssertionError('Only UTF-8 Surefire XML is supported: ' + report.name) from error
        require('\x00' not in xml, 'Only UTF-8 Surefire XML is supported: ' + report.name)
        declaration = re.match(r'<\?xml\s+[^?]*\?>', xml)
        if declaration:
            encoding = re.search(r'''\bencoding\s*=\s*['"]([^'"]+)['"]''', declaration.group())
            require(encoding is None or encoding.group(1).lower() in {'utf-8', 'utf8'},
                    'Only UTF-8 Surefire XML is supported: ' + report.name)
        require('<!DOCTYPE' not in xml.upper() and '<!ENTITY' not in xml.upper(),
                'DTD/entity declarations are not Surefire evidence: ' + report.name)
        suite = ET.fromstring(data)
        observed = {case.get('classname', '') for case in suite.iter('testcase')}
        scoped = {name for name in observed | {suite.get('name', '')} if in_scope(name)}
        if not scoped:
            continue
        require(len(scoped) == 1 and suite.tag == 'testsuite',
                'Expected one scoped class per Surefire testsuite: ' + report.name)
        name = next(iter(scoped))
        require(name in expected, 'Unexpected M3 report class: ' + name)
        require(name not in qualified, 'Duplicate M3 report class: ' + name)
        require(suite.get('name') == name and report.name == 'TEST-' + name + '.xml',
                'Unexpected Surefire report identity: ' + report.name)
        props = report_properties(suite)
        for key, value in required_properties.items():
            require(props.get(key) == value, name + ': missing or mismatched ' + key)
        require(not FORBIDDEN_PROPERTIES.intersection(props),
                name + ': seed filtering or theme/LAF switching is not release evidence')
        require(not any(key.startswith('swt.widget.mode.') for key in props),
                name + ': per-widget mode overrides are not release evidence')
        current_environment = {key: props.get(key, '') for key in ('java.version', 'os.name', 'os.arch')}
        require(all(current_environment.values()), name + ': incomplete JVM/platform properties')
        require(environment is None or current_environment == environment,
                name + ': reports mix JVM/platform environments')
        environment = current_environment
        entry = expected[name]
        skips = {ORIGINAL_SKIP_METHOD} if mode == 'original' and name == ORIGINAL_SKIP_CLASS else set()
        try:
            counts = qualified_suite(io.BytesIO(data), name, set(entry['methods']), expected_skips=skips)
        except (AssertionError, ValueError) as error:
            label = 'required recording comparison' if entry['recordingRequired'] else 'required method'
            raise AssertionError(name + ': ' + label + ' did not qualify: ' + str(error)) from error
        methods = []
        for case in suite.iter('testcase'):
            methods.append({'name': case.get('name').removesuffix('()'),
                            'status': 'SKIP' if case.find('skipped') is not None else 'PASS'})
        qualified[name] = {
            'class': name, 'report': report.name, 'reportSha256': sha256(data),
            'source': entry['source'], 'sourceSha256': entry['sourceSha256'],
            'recordingRequired': entry['recordingRequired'], 'junit': counts,
            'methods': sorted(methods, key=lambda method: method['name']),
        }
    require(set(qualified) == set(expected),
            'Missing M3 report classes: ' + ', '.join(sorted(set(expected) - set(qualified))))
    classes = [qualified[name] for name in sorted(qualified)]
    total = {key: sum(entry['junit'][key] for entry in classes)
             for key in ('tests', 'failures', 'errors', 'skipped')}
    receipt = {
        'status': 'PASS',
        'scope': 'M3 AllTests report completeness; not JaCoCo coverage or binary/reference provenance',
        'candidateCommit': commit, 'runId': run_id, 'platform': platform, 'mode': mode,
        'recordingDirectory': recording_directory, 'environment': environment,
        'reviewedSource': contract['reviewedSource'], 'contractSha256': contract_hash,
        'validatorSha256': sha256(Path(__file__).read_bytes()),
        'sharedValidatorSha256': sha256((HERE / 'verify_viewport_screenshots.py').read_bytes()),
        'discoveredClasses': len(classes), 'junit': total,
        'passingMethods': total['tests'] - total['skipped'],
        'recordingComparisonMethods': sum(entry['junit']['tests'] for entry in classes
                                          if entry['recordingRequired']),
        'classes': classes,
    }
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    receipt_path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return receipt


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--test-root', type=Path, default=HERE)
    parser.add_argument('--reports', type=Path)
    parser.add_argument('--receipt', type=Path)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--platform', required=True)
    parser.add_argument('--mode', choices=('viewport', 'original'), required=True)
    parser.add_argument('--recording-directory', required=True)
    args = parser.parse_args(argv)
    reports = args.reports or args.test_root / 'target/surefire-reports'
    receipt = args.receipt or args.test_root / ('target/m3-test-report-validation-' + args.mode + '.json')
    try:
        result = verify(args.test_root, reports, receipt, commit=args.commit, run_id=args.run_id,
                        platform=args.platform, mode=args.mode, recording_directory=args.recording_directory)
    except (AssertionError, OSError, ValueError, KeyError, TypeError, ET.ParseError) as error:
        print('M3 report gate failed: ' + str(error), file=sys.stderr)
        return 1
    print(json.dumps({key: result[key] for key in
                      ('status', 'runId', 'mode', 'discoveredClasses', 'passingMethods', 'junit')},
                     sort_keys=True))
    return 0


if __name__ == '__main__':
    sys.exit(main())
