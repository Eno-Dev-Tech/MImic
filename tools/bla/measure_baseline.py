"""Private R8-only performance measurement; always restore the owned emulator snapshot."""
import json
import time
import uuid
from pathlib import Path

from apk_pipeline import ROOT, CERT, run, tools
import re
from verify_emulator import Emulator


def main():
    source = ROOT / 'build/bla/private-comparison/fullRelease/app-full-universal-release-unsigned.apk'
    apk = ROOT / 'build/bla/private-comparison/r8-only-signed.apk'
    if not source.is_file():
        raise RuntimeError('Build the protected release first to obtain its private R8 input')
    # Intentionally not a stage-release asset. This APK must never be published.
    java, align, _, signer, _ = tools()
    aligned = apk.with_name('r8-only-aligned.apk')
    run([align, '-P', '16', '-f', '4', source, aligned])
    # sign_apk intentionally requires BLA; ordinary comparison signing is explicitly private here.
    props = dict(line.split('=', 1) for line in (ROOT / 'keystore.properties').read_text().splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    import os
    env = dict(os.environ, MIMIC_STORE_PASS=props['storePassword'], MIMIC_KEY_PASS=props['keyPassword'])
    run([java, '-jar', signer, 'sign', '--ks', props['storeFile'], '--ks-key-alias', props['keyAlias'],
         '--ks-pass', 'env:MIMIC_STORE_PASS', '--key-pass', 'env:MIMIC_KEY_PASS', '--out', apk, aligned], env=env)
    certs = run([java, '-jar', signer, 'verify', '--print-certs', apk])
    if re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]{64})', certs) != [CERT]:
        raise RuntimeError('Private comparison signing identity mismatch')
    phone = Emulator('emulator-5554')
    before = phone.installed()
    snapshot = 'mimic_comparison_' + uuid.uuid4().hex[:12]
    saved = phone.command('emu', 'avd', 'snapshot', 'save', snapshot)
    if 'KO:' in saved or 'OK' not in saved:
        raise RuntimeError('Cannot preserve emulator for comparison')
    try:
        phone.command('install', '-r', str(apk))
        phone.shell('cmd', 'package', 'compile', '-m', 'speed', '-f', 'com.example.mimic')
        result = phone.performance()
        result['theme'] = phone.current_theme()
        (apk.parent / 'r8-performance.json').write_text(json.dumps(result, indent=2), encoding='utf8')
        print(json.dumps(result))
    finally:
        restored = phone.command('emu', 'avd', 'snapshot', 'load', snapshot)
        if 'KO:' in restored or 'OK' not in restored:
            raise RuntimeError('Restore failed; retain snapshot ' + snapshot)
        for _ in range(30):
            try:
                if phone.installed() == before:
                    break
            except Exception:
                pass
            time.sleep(1)
        else:
            raise RuntimeError('Private comparison did not restore original installation')
        phone.command('emu', 'avd', 'snapshot', 'delete', snapshot)


if __name__ == '__main__':
    main()
