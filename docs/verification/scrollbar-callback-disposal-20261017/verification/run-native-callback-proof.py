#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
# SPDX-License-Identifier: Apache-2.0
"""Compile exact SWT owners and run real GTK callback regressions under a supplied X display."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--before-root', required=True, type=Path)
parser.add_argument('--after-root', required=True, type=Path)
parser.add_argument('--base-classes', required=True, type=Path)
parser.add_argument('--native-root', required=True, type=Path)
parser.add_argument('--java-home', required=True, type=Path)
parser.add_argument('--junit-console', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
if not os.environ.get('DISPLAY'):
    raise SystemExit('REFUSE: run the driver inside an isolated Xvfb display')
args.output.mkdir(parents=True, exist_ok=False)
java = args.java_home / 'bin'
owner_path = Path('bundles/org.eclipse.swt/Eclipse SWT/gtk/org/eclipse/swt/widgets/ScrollBar.java')
test_path = Path('tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/'
                 'Test_org_eclipse_swt_widgets_ScrollBar_CallbackDisposalGtk.java')
test_class = 'org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_ScrollBar_CallbackDisposalGtk'
expected_source = {
    'before': '966855c88fbe0fa13c25b5df89ec62ddbead1850bc94ef935d27f9d2c83f15ca',
    'after': 'b2cc90ce5b7f3f706a758099eacdca8199cae5ac53f7b44cdb9beb779aa09966',
}
expected_test = 'cf94333fc5329b963bade2d842fbcb0352a05342d9c0f2bee2b1b8715898488a'
negative_cases = {
    'showListenerMayDisposeOwnerBeforeFirstPaint()',
    'hideListenerMayDisposeOwner()',
    'showListenerMayDisposeOnlyScrollBarAndOwnerStillReceivesResize()',
    'hideListenerMayDisposeOnlyScrollBarAndOwnerStillReceivesResize()',
    'nestedVisibilityDisposalStopsEveryOutstandingOwnerResize()',
    'nestedResizeDisposalStopsTheOuterOwnerResize()',
    'nestedScrollBarDisposalRetainsBothLiveOwnerResizeNotifications()',
    'displayFilterMayDisposeOwnerBeforeTheVisibilityListener()',
}
commands = []


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command, log, expected_exit=0):
    command = [str(value) for value in command]
    result = subprocess.run(command, capture_output=True, text=True)
    commands.append({'argv': command, 'exit_code': result.returncode})
    log.write_text(result.stdout + result.stderr)
    (args.output / 'commands.json').write_text(json.dumps(commands, indent=2) + '\n')
    if result.returncode != expected_exit:
        raise SystemExit(f'REFUSE unexpected exit {result.returncode}: {log}')
    return result.stdout + result.stderr


test = args.after_root / test_path
if sha(test) != expected_test:
    raise SystemExit('REFUSE changed regression source')
run([java / 'java', '-version'], args.output / 'java-version.txt')
summary = {}
diagnostics = {}
for generation, source_root in (('before', args.before_root), ('after', args.after_root)):
    source = source_root / owner_path
    if sha(source) != expected_source[generation]:
        raise SystemExit('REFUSE changed ' + generation + ' ScrollBar source')
    lane = args.output / generation
    classes = lane / 'classes'
    classes.mkdir(parents=True)
    diagnostics[generation] = run([
        java / 'javac', '--release', '21', '-proc:none', '-Xlint:all',
        '-cp', args.base_classes, '-d', classes, source,
    ], lane / 'owner-compile.log').replace(str(source), '<ScrollBar.java>')
    run([
        java / 'javac', '--release', '21', '-proc:none', '-Xlint:all', '-Werror',
        '-cp', os.pathsep.join(map(str, (classes, args.base_classes, args.junit_console))),
        '-d', classes, test,
    ], lane / 'test-compile.log')
    run([
        java / 'javap', '-classpath', os.pathsep.join(map(str, (classes, args.base_classes))),
        '-package', '-s', 'org.eclipse.swt.widgets.ScrollBar',
    ], lane / 'api.txt')
    run([
        java / 'java', '-Xmx512m', '-Djava.library.path=' + str(args.native_root),
        '-jar', args.junit_console,
        '--class-path', os.pathsep.join(map(str, (classes, args.base_classes))),
        '--select-class', test_class, '--reports-dir', lane / 'junit-reports',
        '--details', 'tree', '--disable-ansi-colors',
    ], lane / 'junit.log', expected_exit=1 if generation == 'before' else 0)
    suite = ET.parse(lane / 'junit-reports/TEST-junit-jupiter.xml').getroot()
    counts = {key: int(suite.attrib[key]) for key in ('tests', 'skipped', 'failures', 'errors')}
    if counts['tests'] != 12 or counts['skipped'] or counts['errors']:
        raise SystemExit('REFUSE unexpected real JUnit discovery/result: ' + str(counts))
    if generation == 'before':
        failed = {case.attrib['name']: case.find('failure') for case in suite.findall('testcase')
                  if case.find('failure') is not None}
        if counts['failures'] != 8 or set(failed) != negative_cases:
            raise SystemExit('REFUSE changed baseline failure set')
        for name, failure in failed.items():
            detail = failure.attrib.get('message', '') + (failure.text or '')
            if ('NullPointerException' not in detail or 'this.parent' not in detail
                    or 'ScrollBar.setVisible' not in detail):
                raise SystemExit('REFUSE non-regression baseline failure: ' + name)
    elif counts['failures']:
        raise SystemExit('REFUSE remaining callback regression')
    counts['passed'] = counts['tests'] - counts['failures'] - counts['errors'] - counts['skipped']
    summary[generation] = counts
    print(generation + ': ' + json.dumps(counts), flush=True)

if diagnostics['before'] != diagnostics['after'] or diagnostics['before'].count('warning:') != 9:
    raise SystemExit('REFUSE changed pre-existing owner compiler diagnostics')
if (args.output / 'before/api.txt').read_bytes() != (args.output / 'after/api.txt').read_bytes():
    raise SystemExit('REFUSE changed declared ScrollBar API')

dependencies = {
    'junit_console': {'path': str(args.junit_console), 'sha256': sha(args.junit_console)},
    'native_libraries': [{'path': str(path), 'sha256': sha(path)}
                         for path in sorted(args.native_root.glob('libswt-*.so'))],
    'swt_base_classes': [{'path': str(path.relative_to(args.base_classes)), 'sha256': sha(path)}
                         for path in sorted(args.base_classes.rglob('*.class'))],
    'swt_base_resources': [{'path': str(path.relative_to(args.base_classes)), 'sha256': sha(path)}
                           for path in sorted(args.base_classes.rglob('*'))
                           if path.is_file() and path.suffix != '.class'],
}
if not dependencies['native_libraries'] or not dependencies['swt_base_classes']:
    raise SystemExit('REFUSE absent actual SWT native/classes input inventory')
(args.output / 'dependency-inputs.json').write_text(json.dumps(dependencies, indent=2) + '\n')
(args.output / 'result.json').write_text(json.dumps({
    'driver_sha256': sha(Path(__file__)),
    'source_sha256': expected_source,
    'test_sha256': expected_test,
    'native_platform': 'GTK3/X11 under caller-provided isolated Xvfb',
    'junit': summary,
    'api': 'PASS identical declared ScrollBar descriptors',
    'compiler': 'PASS Java21 production source; nine exact pre-existing warnings, zero new diagnostics; strict regression-test -Werror',
    'scope': 'Native GTK callback lifetime/event behavior. GTK4, Cocoa, Win32, whole-reactor and viewport performance/pixel acceptance not executed.',
}, indent=2) + '\n')
print('GTK_CALLBACK_DISPOSAL_SOURCE_BOUND_PROOF_PASS', flush=True)
