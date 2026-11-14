#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0
"""Build real SWT/Nebula Android APKs using JDK 21 and the official SDK 35 tools.

The Android core-for-system-modules.jar is converted into a javac system image;
host JDK APIs cannot accidentally satisfy unavailable Android library symbols.
The image is a compiler API image, not a deployable VM. No device run is inferred.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile, ZIP_DEFLATED

ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)
NEBULA_SOURCES = (
    'widgets/roundedcheckbox/org.eclipse.nebula.widgets.roundedcheckbox/src/org/eclipse/nebula/widgets/roundedcheckbox/RoundedCheckbox.java',
    'widgets/opal/commons/org.eclipse.nebula.widgets.opal.commons/src/org/eclipse/nebula/widgets/opal/commons/SelectionListenerUtil.java',
    'widgets/opal/commons/org.eclipse.nebula.widgets.opal.commons/src/org/eclipse/nebula/widgets/opal/commons/ReflectionUtils.java',
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Build:
    def __init__(self, args):
        self.args = args
        self.out = args.output.resolve()
        marker = self.out / '.swt-mobile-sdk-build'
        if self.out.exists() and any(self.out.iterdir()) and not marker.is_file():
            raise RuntimeError(f'Refusing to use nonempty unowned output directory: {self.out}')
        self.out.mkdir(parents=True, exist_ok=True)
        marker.write_text('SWT mobile Android SDK build outputs\n', encoding='utf-8')
        self.work = self.out / 'work'
        if self.work.exists():
            shutil.rmtree(self.work)
        self.work.mkdir()
        self.log = self.out / 'build.log'
        self.log.write_text('', encoding='utf-8')
        self.receipt = {'status': 'RUNNING', 'android_api': 35, 'min_api': 35,
                        'java_source': 21, 'classfile_major': 65,
                        'native_execution': 'NOT_RUN', 'stages': [], 'sources': []}
        self.save_receipt()
        self.jdk = args.jdk.resolve()
        self.sdk = args.android_sdk.resolve()
        self.tools = self.sdk / 'build-tools' / '35.0.0'
        self.android = self.sdk / 'platforms' / 'android-35' / 'android.jar'
        self.core = self.android.with_name('core-for-system-modules.jar')
        self.suffix = '.exe' if os.name == 'nt' else ''

    def require_tools(self):
        for path in [self.android, self.core, self.jdk / 'bin' / ('javac' + self.suffix),
                     self.tools / ('aapt2' + self.suffix), self.tools / ('zipalign' + self.suffix),
                     self.tools / 'lib' / 'd8.jar', self.tools / 'lib' / 'apksigner.jar']:
            if not path.is_file():
                raise FileNotFoundError(f'Required official SDK/JDK component is missing: {path}')

    def save_receipt(self):
        (self.out / 'build-receipt.json').write_text(json.dumps(self.receipt, indent=2) + '\n', encoding='utf-8')

    def jdk_tool(self, name):
        return self.jdk / 'bin' / (name + self.suffix)

    def run(self, stage, arguments):
        command = [str(x) for x in arguments]
        self.receipt['stages'].append({'name': stage, 'status': 'RUNNING'})
        self.save_receipt()
        process = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        with self.log.open('a', encoding='utf-8') as log:
            log.write(json.dumps(command) + '\n' + process.stdout + '\n')
        self.receipt['stages'][-1]['status'] = 'PASSED' if process.returncode == 0 else 'FAILED'
        self.save_receipt()
        if process.returncode:
            raise RuntimeError(f'{stage} failed ({process.returncode}); inspect {self.log}\n{process.stdout[-7000:]}')
        return process.stdout

    def android_system_image(self):
        version = self.run('JDK version', [self.jdk_tool('javac'), '-version'])
        if not re.search(r'javac 21(?:\.|\s|$)', version):
            raise RuntimeError('This build requires JDK 21')
        root = self.work / 'android-core-api'
        root.mkdir()
        with ZipFile(self.core) as jar:
            packages = sorted({str(Path(n).parent).replace(os.sep, '.') for n in jar.namelist()
                               if n.endswith('.class') and '/' in n and not n.startswith('META-INF/')})
        descriptor = root / 'module-info.java'
        descriptor.write_text('module java.base {\n' + ''.join(f' exports {p};\n' for p in packages) + '}\n', encoding='utf-8')
        self.run('Android core module descriptor', [self.jdk_tool('javac'), '--system', 'none',
                 '--patch-module', f'java.base={self.core}', '-d', root, descriptor])
        modular = root / 'java.base.jar'
        with ZipFile(self.core) as source, ZipFile(modular, 'w', ZIP_DEFLATED) as target:
            for entry in source.infolist():
                if entry.filename != 'module-info.class':
                    target.writestr(entry, source.read(entry.filename))
            target.writestr('module-info.class', (root / 'module-info.class').read_bytes())
        description = self.run('JDK compiler host target', [self.jdk_tool('jmod'), 'describe', self.jdk / 'jmods' / 'java.base.jmod'])
        platform = re.search(r'^platform (\S+)', description, re.MULTILINE)
        if platform is None:
            raise RuntimeError('Cannot resolve JDK jlink host platform from java.base.jmod')
        jmod = root / 'jmod'
        jmod.mkdir()
        self.run('Android core compiler module', [self.jdk_tool('jmod'), 'create', '--module-version', '21',
                 '--target-platform', platform.group(1), '--class-path', modular, jmod / 'java.base.jmod'])
        image = root / 'image'
        self.run('Android core compiler system image', [self.jdk_tool('jlink'), '--module-path', jmod,
                 '--add-modules', 'java.base', '--output', image, '--disable-plugin', 'system-modules'])
        shutil.copy2(self.jdk / 'lib' / 'jrt-fs.jar', image / 'lib' / 'jrt-fs.jar')
        self.receipt['android_jar_sha256'] = sha256(self.android)
        self.receipt['android_core_jar_sha256'] = sha256(self.core)
        self.receipt['compiler_system'] = 'official Android core-for-system-modules.jar'
        return image

    def source_files(self):
        swt = self.args.swt_checkout.resolve()
        source_root = swt / 'bundles' / 'org.eclipse.swt' / 'Eclipse SWT'
        manifest = swt / 'm3' / 'mobile' / 'common-sources.txt'
        sources = []
        for line in manifest.read_text(encoding='utf-8').splitlines():
            if not line.endswith('.java'):
                continue
            path = (swt / line).resolve()
            if not path.is_relative_to(swt):
                raise RuntimeError('Common source manifest escapes SWT checkout')
            sources.append(path)
        for folder in ['mobile', 'android']:
            sources.extend(sorted((source_root / folder).rglob('*.java')))
        if self.args.nebula_checkout:
            nebula = self.args.nebula_checkout.resolve()
            sources.extend(nebula / path for path in NEBULA_SOURCES)
            sources.extend(sorted((nebula / 'm3/mobile/common/src/main/java').rglob('*.java')))
            app = nebula / 'm3/mobile/android/app'
            package = 'org.eclipse.nebula.examples.mobile'
            runner = package + '.NebulaMobileInstrumentation'
        else:
            app = swt / 'm3/mobile/android/demo'
            package = 'org.eclipse.swt.examples.mobile'
            runner = package + '.SWTMobileInstrumentation'
        sources.extend(sorted((app / 'src/main/java').rglob('*.java')))
        tests = sorted((app / 'src/androidTest/java').rglob('*.java'))
        if not tests:
            raise RuntimeError('Native Android instrumentation sources are missing')
        for path in sources + tests:
            if not path.is_file():
                raise FileNotFoundError(f'Missing source: {path}')
            if path.is_relative_to(swt):
                owner, relative = 'swt', path.relative_to(swt)
            else:
                owner, relative = 'nebula', path.relative_to(self.args.nebula_checkout.resolve())
            self.receipt['sources'].append({'owner': owner, 'path': str(relative), 'sha256': sha256(path)})
        if len(sources) != len(set(sources)):
            raise RuntimeError('Duplicate compilation source path')
        return sources, tests, app, package, runner

    def compile(self, name, sources, system, classpath=()):
        classes = self.work / (name + '-classes')
        classes.mkdir()
        args_file = self.work / (name + '-sources.args')
        args_file.write_text(''.join('"' + str(p).replace('\\', '\\\\').replace('"', '\\"') + '"\n' for p in sources), encoding='utf-8')
        self.run(name + ' Java 21 Android API compilation', [self.jdk_tool('javac'), '--system', system,
                 '-source', '21', '-target', '21', '-XDstringConcat=inline', '-encoding', 'UTF-8',
                 '-classpath', os.pathsep.join(str(p) for p in [self.android, *classpath]), '-d', classes, '@' + str(args_file)])
        archive = self.work / (name + '.jar')
        with ZipFile(archive, 'w', ZIP_DEFLATED) as jar:
            for path in sorted(classes.rglob('*.class')):
                jar.write(path, path.relative_to(classes).as_posix())
        return archive

    def dex(self, name, archive, classpath=()):
        output = self.work / (name + '-dex')
        output.mkdir()
        command = [self.jdk_tool('java'), '-cp', self.tools / 'lib/d8.jar', 'com.android.tools.r8.D8',
                   '--debug', '--min-api', '35', '--lib', self.android, '--output', output]
        for dependency in classpath:
            command += ['--classpath', dependency]
        self.run(name + ' D8', command + [archive])
        if not list(output.glob('classes*.dex')):
            raise RuntimeError('D8 emitted no DEX files')
        return output

    def package(self, name, manifest, dex, key):
        base = self.work / (name + '-base.apk')
        self.run(name + ' Android manifest link', [self.tools / ('aapt2' + self.suffix), 'link',
                 '-I', self.android, '--manifest', manifest, '--min-sdk-version', '35', '--target-sdk-version', '35',
                 '--version-code', '1', '--version-name', '0.1', '-o', base])
        with ZipFile(base, 'a', ZIP_DEFLATED) as apk:
            for path in sorted(dex.glob('classes*.dex')):
                apk.write(path, path.name)
        aligned = self.work / (name + '-aligned.apk')
        self.run(name + ' ZIP alignment', [self.tools / ('zipalign' + self.suffix), '-f', '4', base, aligned])
        result = self.out / (name + '.apk')
        self.run(name + ' debug signing', [self.jdk_tool('java'), '-jar', self.tools / 'lib/apksigner.jar',
                 'sign', '--ks', key, '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android',
                 '--key-pass', 'pass:android', '--out', result, aligned])
        self.run(name + ' signature verification', [self.jdk_tool('java'), '-jar', self.tools / 'lib/apksigner.jar',
                 'verify', '--verbose', result])
        return {'filename': result.name, 'sha256': sha256(result), 'bytes': result.stat().st_size}

    def execute(self):
        self.require_tools()
        system = self.android_system_image()
        sources, tests, app, package, runner = self.source_files()
        main_archive = self.compile('app', sources, system)
        test_archive = self.compile('instrumentation', tests, system, [main_archive])
        app_dex = self.dex('app', main_archive)
        test_dex = self.dex('instrumentation', test_archive, [main_archive])
        manifest = ET.parse(app / 'src/main/AndroidManifest.xml')
        manifest.getroot().set('package', package)
        manifest.getroot().find('application').set('{' + ANDROID + '}debuggable', 'true')
        app_manifest = self.work / 'AndroidManifest.xml'
        manifest.write(app_manifest, encoding='utf-8', xml_declaration=True)
        test_manifest = ET.Element('manifest', {'package': package + '.test'})
        ET.SubElement(test_manifest, 'application', {'{' + ANDROID + '}label': 'SWT native instrumentation'})
        ET.SubElement(test_manifest, 'instrumentation', {'{' + ANDROID + '}name': runner,
                      '{' + ANDROID + '}targetPackage': package, '{' + ANDROID + '}functionalTest': 'true'})
        instrumentation_manifest = self.work / 'InstrumentationManifest.xml'
        ET.ElementTree(test_manifest).write(instrumentation_manifest, encoding='utf-8', xml_declaration=True)
        key = self.out / 'debug.keystore'
        if not key.is_file():
            self.run('Create local debug key', [self.jdk_tool('keytool'), '-genkeypair', '-noprompt',
                     '-keystore', key, '-alias', 'androiddebugkey', '-storepass', 'android', '-keypass', 'android',
                     '-dname', 'CN=Android Debug,O=SWT Mobile,C=US', '-validity', '10000', '-keyalg', 'RSA', '-keysize', '2048'])
        self.receipt['artifacts'] = [self.package('app', app_manifest, app_dex, key),
                                    self.package('instrumentation', instrumentation_manifest, test_dex, key)]
        self.receipt['application_id'] = package
        self.receipt['instrumentation'] = package + '.test/' + runner
        self.receipt['status'] = 'PASSED'
        self.save_receipt()
        print(json.dumps({'status': 'PASSED', 'output': str(self.out), 'native_execution': 'NOT_RUN',
                          'sources': len(sources), 'instrumentation_sources': len(tests)}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--swt-checkout', type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument('--nebula-checkout', type=Path)
    parser.add_argument('--android-sdk', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    builder = None
    try:
        builder = Build(args)
        builder.execute()
    except Exception as failure:
        if builder is not None:
            builder.receipt['status'] = 'FAILED'
            builder.receipt['failure'] = str(failure)
            builder.save_receipt()
        print(str(failure), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
