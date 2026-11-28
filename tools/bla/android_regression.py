"""Run existing Android tests directly, restoring the exact emulator baseline.

Gradle connected tests uninstall their target; this runner deliberately never
uses them. Its own freshly saved snapshot protects the owner's app and data.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid

from apk_pipeline import ROOT, CERT, run, tools, sha, source_digest
from verify_emulator import Emulator


def main():
    inputs_digest = source_digest()
    env = dict(os.environ)
    if os.name == 'nt' and env.get('JAVA_HOME', '').startswith('/c/'):
        env['JAVA_HOME'] = 'C:/' + env['JAVA_HOME'][3:]
    command = ['cmd.exe', '/d', '/c', str(ROOT / 'gradlew.bat')] if os.name == 'nt' else [str(ROOT / 'gradlew')]
    subprocess.run(command + ['--no-configuration-cache', '--max-workers=2',
        '-Dorg.gradle.jvmargs=-Xmx5g -Dfile.encoding=UTF-8', '-Pkotlin.daemon.jvmargs=-Xmx4g',
        ':app:assembleFullDebug', ':app:assembleFullDebugAndroidTest'], cwd=ROOT, env=env, check=True)
    apk = ROOT / 'app/build/outputs/apk/full/debug/app-full-debug.apk'
    test = ROOT / 'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk'
    java, _, _, signer, _ = tools()
    for path in (apk, test):
        certs = run([java, '-jar', signer, 'verify', '--print-certs', path])
        if re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]{64})', certs) != [CERT]:
            raise RuntimeError('Debug/test APK signing key does not match the existing app')
    phone = Emulator(os.environ.get('ANDROID_SERIAL', 'emulator-5554'))
    before = phone.installed()
    snapshot = 'mimic_regression_' + uuid.uuid4().hex[:12]
    saved = phone.command('emu', 'avd', 'snapshot', 'save', snapshot)
    if 'KO:' in saved or 'OK' not in saved:
        raise RuntimeError('Cannot save emulator baseline; refusing to change the app')
    result = None
    try:
        phone.command('install', '-r', str(apk))
        phone.command('install', '-r', str(test))
        output = phone.shell('am', 'instrument', '-w', '-e', 'notClass',
            'com.example.mimic.HomeDiscoveryLiveTest',
            'com.example.mimic.test/androidx.test.runner.AndroidJUnitRunner')
        (ROOT / 'build/bla/android-instrumentation.log').write_text(output, encoding='utf8')
        passed = re.search(r'OK\s*\((\d+) tests?\)', output)
        if not passed or re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_ABORTED', output):
            raise RuntimeError('Android instrumentation failed; see build/bla/android-instrumentation.log')
        if source_digest() != inputs_digest:
            raise RuntimeError('Android source changed while regression tests were running')
        result = dict(schema='mimic-android-regression-v1', passed=True, tests=int(passed[1]),
                      sourceDigest=inputs_digest, debugApkSha256=sha(apk.read_bytes()),
                      testApkSha256=sha(test.read_bytes()), excludedLiveTest='HomeDiscoveryLiveTest',
                      emulator=phone.device, cleanup='restored own saved snapshot; no uninstall/clear-data')
    finally:
        restored = phone.command('emu', 'avd', 'snapshot', 'load', snapshot)
        (ROOT / 'build/bla/android-snapshot-restore.txt').write_text(restored, encoding='utf8')
        if 'KO:' in restored or 'OK' not in restored:
            raise RuntimeError(f'Emulator restore failed; retain snapshot {snapshot} for recovery: {restored.strip()}')
        for _ in range(30):
            try:
                if phone.installed() == before:
                    break
            except (RuntimeError, subprocess.CalledProcessError):
                pass
            time.sleep(1)
        else:
            raise RuntimeError('Restored emulator app does not match its baseline')
        # Keep the restored RAM backing file while QEMU may still page from it.
        # Delayed host crashes were observed after eager snapshot cleanup here.
        # Cleanup is safe only after this emulator has stopped.
        if result:
            result['retainedBaselineSnapshot'] = snapshot
    if result:
        (ROOT / 'build/bla/android-regression.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
        print(f"PASS: {result['tests']} direct Android regression tests; emulator baseline restored")


if __name__ == '__main__':
    main()
