#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Install and exercise the SDK-built SWT/Nebula demo on an explicitly selected Android device.

Requires a successful build-apk.py receipt and matching APK hashes. Source/build
success is distinct from this native run; every run starts a fresh native receipt.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-output', type=Path, required=True)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True, help='Explicit adb serial of the device/emulator to test')
    args = parser.parse_args()
    output = args.build_output.resolve()
    if not (output / '.swt-mobile-sdk-build').is_file():
        parser.error('The selected folder is not an owned SDK build output')
    evidence = output / 'native-evidence'
    if evidence.exists():
        shutil.rmtree(evidence)
    evidence.mkdir()
    receipt_path = output / 'native-receipt.json'
    receipt = {'status': 'RUNNING', 'serial': args.serial, 'stages': [], 'screenshots': []}

    def save():
        receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')

    def adb(stage, *command, timeout=180):
        receipt['stages'].append({'name': stage, 'status': 'RUNNING'})
        save()
        process = subprocess.run([str(args.adb), '-s', args.serial, *map(str, command)],
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout)
        with (evidence / 'adb.log').open('a', encoding='utf-8') as log:
            log.write(json.dumps(list(map(str, command))) + '\n' + process.stdout + '\n')
        receipt['stages'][-1]['status'] = 'PASSED' if process.returncode == 0 else 'FAILED'
        save()
        if process.returncode:
            raise RuntimeError(f'{stage} failed: {process.stdout}')
        return process.stdout

    save()
    try:
        build_path = output / 'build-receipt.json'
        build = json.loads(build_path.read_text(encoding='utf-8'))
        if build.get('status') != 'PASSED':
            raise RuntimeError('Build did not pass')
        receipt['build_receipt_sha256'] = sha256(build_path)
        for artifact in build['artifacts']:
            path = output / artifact['filename']
            if path.parent != output or sha256(path) != artifact['sha256']:
                raise RuntimeError('APK hash does not match the tested build receipt')
        level = int(adb('Device API', 'shell', 'getprop', 'ro.build.version.sdk').strip())
        if level < 35:
            raise RuntimeError('The initial mobile slice requires Android API 35 or newer')
        receipt['device_api'] = level
        receipt['application_id'] = build['application_id']
        adb('Install application', 'install', '-r', output / 'app.apk')
        adb('Install instrumentation', 'install', '-r', '-t', output / 'instrumentation.apk')
        result = adb('Native instrumentation', 'shell', 'am', 'instrument', '-w', build['instrumentation'])
        nebula = build['application_id'] == 'org.eclipse.nebula.examples.mobile'
        sentinel = 'NEBULA_ANDROID_NATIVE_PASS:' if nebula else 'SWT_ANDROID_NATIVE_PASS:'
        if sentinel not in result or 'INSTRUMENTATION_CODE: -1' not in result or '_NATIVE_FAIL:' in result:
            raise RuntimeError('Native test did not report its explicit passing result\n' + result)
        folder = re.search(r'INSTRUMENTATION_RESULT: screenshotDirectory=(.+)', result)
        if folder is None:
            raise RuntimeError('Missing native screenshot directory')
        remote = folder.group(1).strip()
        if not remote.endswith('/Android/data/' + build['application_id'] + '/files'):
            raise RuntimeError('Unexpected native screenshot directory')
        prefix = 'nebula' if nebula else 'swt'
        for phase in ['before', 'after']:
            filename = prefix + '-android-' + phase + '.png'
            local = evidence / filename
            adb('Pull ' + phase + ' screen', 'pull', remote + '/' + filename, local)
            data = local.read_bytes()
            if len(data) < 33 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
                raise RuntimeError('Missing or invalid native PNG')
            width, height = struct.unpack('>II', data[16:24])
            if width < 100 or height < 100:
                raise RuntimeError('Native capture is not a full-size device surface')
            receipt['screenshots'].append({'filename': filename, 'width': width, 'height': height, 'sha256': sha256(local)})
        receipt['status'] = 'PASSED'
        save()
        print(json.dumps(receipt, indent=2))
        return 0
    except Exception as failure:
        receipt['status'] = 'FAILED'
        receipt['failure'] = str(failure)
        save()
        print(str(failure), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
