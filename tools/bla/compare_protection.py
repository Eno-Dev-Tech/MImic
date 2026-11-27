"""Measure local R8-only vs R8+BLA APKs without publishing the comparison.

Use an independently downloaded, digest-checked official JADX 1.5.7 CLI jar.
Decompiled sources stay private under build/bla/private-comparison.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from apk_pipeline import ROOT, tools


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('protected', type=Path)
    parser.add_argument('--jadx-jar', type=Path, required=True)
    args = parser.parse_args()
    java, *_ = tools()
    version = subprocess.run([str(java), '-cp', str(args.jadx_jar), 'jadx.cli.JadxCLI', '--version'],
                             capture_output=True, text=True, check=True).stdout.strip()
    if version != '1.5.7':
        raise RuntimeError('Unreviewed JADX version; expected 1.5.7')
    folder = ROOT / 'build/bla/private-comparison'
    folder.mkdir(parents=True, exist_ok=True)
    results = dict(jadxVersion=version, classes={})
    for cls in ['com.example.mimic.MainActivity', 'com.example.mimic.MimicApplication']:
        pair = {}
        for name, apk in [('r8', args.baseline), ('r8-bla', args.protected)]:
            output = folder / f'{name}-{cls.rsplit(".", 1)[-1]}.java'
            result = subprocess.run([str(java), '-Xmx3g', '-cp', str(args.jadx_jar), 'jadx.cli.JadxCLI',
                '--no-res', '--single-class', cls, '--single-class-output', str(output), str(apk)],
                text=True, capture_output=True)
            (folder / (output.stem + '.log')).write_text(result.stdout + result.stderr, encoding='utf8')
            if not output.is_file():
                raise RuntimeError(f'JADX did not emit {cls} for {name}')
            count = re.search(r'finished with errors, count: (\d+)', result.stdout + result.stderr)
            pair[name] = dict(exitCode=result.returncode, sourceBytes=output.stat().st_size,
                              decompilerErrors=int(count[1]) if count else 0,
                              sourceSha256=hashlib.sha256(output.read_bytes()).hexdigest())
        pair['identicalDecompilation'] = pair['r8']['sourceSha256'] == pair['r8-bla']['sourceSha256']
        results['classes'][cls] = pair
    results['baselineBytes'] = args.baseline.stat().st_size
    results['protectedBytes'] = args.protected.stat().st_size
    results['sizeDifferenceBytes'] = results['protectedBytes'] - results['baselineBytes']
    results['addedJadxResistanceDemonstrated'] = False
    results['limitations'] = ['Representative class comparison; not a full attacker benchmark.',
        'Partial decompilation errors are reported, including errors already present with R8 alone.',
        'BLA stealth preserves every original program byte outside the DEX checksum/file-size header.']
    (folder / 'security-comparison.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf8')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
