"""Fail-closed R8 -> BLA DEX -> ZIP alignment -> local signing pipeline.

BLA 3.6.1 stealth adds inert trailer text, not method obfuscation or encryption.
R8 supplies code optimization/obfuscation. Never publish private comparisons.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]
VERSION = '3.6.1'
CERT = 'c03c51075a49a049387185c775626074368aa9f4220eb5ba0e74cc4a4f88087d'
PACKAGE = 'com.example.mimic'
DEX_NAME = re.compile(r'classes(?:[1-9][0-9]*)?\.dex\Z')
SIGNATURE = re.compile(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))\Z', re.I)
MANIFEST = 'assets/mimic-bla-verification.json'
# Independently pinned output for the reviewed version/stealth/seed policy.
# Changing this requires an explicit BLA upgrade/revalidation, not trusting a receipt.
TRAILER_BYTES = 3801
TRAILER_SHA256 = '1f4bbdc3876cdec500da73ca7f45401361aa8f312553716927421b94639d1709'


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_digest() -> str:
    digest = hashlib.sha256()
    files = sorted(p for p in (ROOT / 'app/src').rglob('*') if p.is_file())
    files += [ROOT / 'app/build.gradle.kts', ROOT / 'gradle/libs.versions.toml', ROOT / 'settings.gradle.kts']
    for path in files:
        digest.update(path.relative_to(ROOT).as_posix().encode() + b'\0' + path.read_bytes())
    return digest.hexdigest()


def run(args, **kwargs) -> str:
    result = subprocess.run([str(a) for a in args], check=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    # ADB/Node/JDK emit UTF-8. Decode in this thread, rather than Windows' cp1252
    # reader threads silently leaving stdout=None on music titles containing Unicode.
    return result.stdout.decode('utf-8')


def tools():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        prop = ROOT / 'local.properties'
        if prop.exists():
            sdk = next((line.split('=', 1)[1].strip().replace('\\:', ':').replace('\\\\', '\\')
                        for line in prop.read_text().splitlines() if line.strip().startswith('sdk.dir')), None)
    if not sdk:
        raise RuntimeError('Android SDK is required')
    bt = Path(sdk) / 'build-tools' / '36.0.0'
    suffix = '.exe' if os.name == 'nt' else ''
    java_home = os.environ.get('JAVA_HOME', '')
    if os.name == 'nt' and java_home.startswith('/c/'):
        java_home = 'C:/' + java_home[3:]
    java = str(Path(java_home) / 'bin' / ('java' + suffix)) if java_home else shutil.which('java')
    required = [bt / ('zipalign' + suffix), bt / ('dexdump' + suffix), bt / 'lib/apksigner.jar', bt / ('aapt2' + suffix)]
    if not java or any(not path.is_file() for path in required):
        raise RuntimeError('JDK and Android SDK build-tools 36.0.0 are required')
    return java, *required


def dex_index(name: str) -> int:
    return 1 if name == 'classes.dex' else int(name[7:-4])


def dex_members(archive: zipfile.ZipFile) -> list[str]:
    names = archive.namelist()
    if len(names) != len(set(names)):
        raise RuntimeError('Duplicate ZIP members')
    dex = sorted((name for name in names if DEX_NAME.fullmatch(name)), key=dex_index)
    if not dex or [dex_index(name) for name in dex] != list(range(1, len(dex) + 1)):
        raise RuntimeError('Missing, aliased or non-contiguous root multidex entries')
    if any(name.endswith('.dex') and name not in dex for name in names):
        raise RuntimeError('Unaccounted nested or non-canonical DEX member')
    return dex


def validate_dex(data: bytes):
    if len(data) < 112 or data[:4] != b'dex\n' or data[7] != 0 or data[4:7] not in (b'035', b'037', b'038', b'039', b'040'):
        raise RuntimeError('Unsupported DEX version (including unsupported v041)')
    if struct.unpack_from('<III', data, 32) != (len(data), 112, 0x12345678):
        raise RuntimeError('Invalid DEX header')
    if data[12:32] != hashlib.sha1(data[32:]).digest() or struct.unpack_from('<I', data, 8)[0] != zlib.adler32(data[12:]):
        raise RuntimeError('DEX signature or checksum mismatch')


def validate_embedded(apk: Path):
    with zipfile.ZipFile(apk) as z:
        if z.testzip():
            raise RuntimeError('ZIP CRC error')
        dex = dex_members(z)
        receipt = json.loads(z.read(MANIFEST))
        if receipt['blaVersion'] != VERSION or receipt['intensity'] != 'stealth' or set(receipt['dex']) != set(dex):
            raise RuntimeError('Missing or incomplete BLA receipt')
        for name in dex:
            data = z.read(name)
            validate_dex(data)
            if sha(data) != receipt['dex'][name]['protectedSha256']:
                raise RuntimeError(f'Protected DEX changed: {name}')
            record = receipt['dex'][name]
            original_size = record.get('originalBytes', 0)
            if not 112 <= original_size < len(data) or len(data) - original_size != TRAILER_BYTES:
                raise RuntimeError('Invalid original DEX extent or missing BLA trailer')
            if sha(data[original_size:]) != TRAILER_SHA256:
                raise RuntimeError('DEX does not contain the exact validated BLA trailer')
            original = bytearray(data[:original_size])
            struct.pack_into('<I', original, 32, original_size)
            original[12:32] = hashlib.sha1(original[32:]).digest()
            struct.pack_into('<I', original, 8, zlib.adler32(original[12:]))
            if sha(original) != record.get('originalSha256'):
                raise RuntimeError('Original DEX program preservation proof failed')
        return receipt


def protect_directory(input_dir: Path, output_dir: Path):
    input_dir, output_dir = input_dir.resolve(), output_dir.resolve()
    if input_dir == output_dir or input_dir in output_dir.parents or output_dir in input_dir.parents:
        raise RuntimeError('Protection needs separate sibling input/output directories')
    _, align, dexdump, _, _ = tools()
    if output_dir.exists() and any(output_dir.iterdir()):
        # Only this task's fixed generated directory may be replaced on repeated builds.
        allowed = (ROOT / 'app/build/outputs/bla').resolve()
        if ROOT not in allowed.parents or allowed not in output_dir.parents:
            raise RuntimeError('Refusing to replace non-task output')
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    apks = sorted(input_dir.glob('*.apk'))
    if not apks:
        raise RuntimeError('No unsigned APKs to protect')
    variant = output_dir.name
    mapping = ROOT / 'app/build/outputs/mapping' / variant / 'mapping.txt'
    if not mapping.is_file() or mapping.stat().st_size == 0:
        raise RuntimeError('Missing R8 mapping evidence; R8 must precede BLA')
    comparison = ROOT / 'build/bla/private-comparison' / variant
    comparison.mkdir(parents=True, exist_ok=True)
    dex_cache = {}  # ABI splits share code: SDK-verify each unique DEX once per invocation.
    for apk in apks:
        shutil.copy2(apk, comparison / apk.name)
        if 'unsigned' not in apk.name:
            raise RuntimeError('AGP release input must be unsigned')
        with tempfile.TemporaryDirectory(prefix='mimic-bla-') as temp:
            temp = Path(temp)
            with zipfile.ZipFile(apk) as src:
                if MANIFEST in src.namelist():
                    raise RuntimeError('Refusing to harden an already protected APK')
                dex = dex_members(src)
                protected, evidence = {}, {}
                for name in dex:
                    before = src.read(name)
                    validate_dex(before)
                    original, after = temp / name, temp / ('protected-' + name)
                    before_sha = sha(before)
                    if before_sha in dex_cache:
                        data, details = dex_cache[before_sha]
                    else:
                        original.write_bytes(before)
                        details = json.loads(run(['node', ROOT / 'tools/bla/protect-dex.mjs', original, after]))
                        data = after.read_bytes()
                        validate_dex(data)
                        if sha(data[len(before):]) != TRAILER_SHA256 or len(data) - len(before) != TRAILER_BYTES:
                            raise RuntimeError('BLA output differs from the independently validated policy')
                        run([dexdump, '-c', after])
                        # SDK decoder also walks all classes and code; do not rely on BLA's marker alone.
                        with open(os.devnull, 'w') as null:
                            subprocess.run([str(dexdump), '-f', str(after)], check=True, stdout=null, stderr=subprocess.PIPE)
                        dex_cache[before_sha] = data, details
                    protected[name] = data
                    evidence[name] = dict(details, originalSha256=before_sha, protectedSha256=sha(data))
                receipt = dict(schema='mimic-bla-apk-v1', blaVersion=VERSION, intensity='stealth',
                               r8=True, originalApkSha256=sha(apk.read_bytes()), dex=evidence,
                               limitations=['Removable inert text trailers; no new runtime protection.',
                                            'No demonstrated JADX resistance beyond R8.'])
                repacked = temp / 'protected-unsigned.apk'
                with zipfile.ZipFile(repacked, 'w') as dst:
                    for info in src.infolist():
                        if SIGNATURE.fullmatch(info.filename):
                            continue
                        dst.writestr(info, protected.get(info.filename, src.read(info)))
                    info = zipfile.ZipInfo(MANIFEST, date_time=(1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    dst.writestr(info, json.dumps(receipt, sort_keys=True).encode())
            final = output_dir / apk.name
            run([align, '-P', '16', '-f', '4', repacked, final])
            validate_embedded(final)
            # All original non-DEX/resources/native member contents must survive byte-for-byte.
            with zipfile.ZipFile(apk) as before, zipfile.ZipFile(final) as after:
                expected = {n for n in before.namelist() if not SIGNATURE.fullmatch(n)} | {MANIFEST}
                if set(after.namelist()) != expected:
                    raise RuntimeError('Unexpected APK member addition/removal')
                for info in before.infolist():
                    if info.filename in dex or SIGNATURE.fullmatch(info.filename):
                        continue
                    if before.read(info) != after.read(info.filename):
                        raise RuntimeError(f'Non-DEX entry altered: {info.filename}')
            (output_dir / (apk.name + '.bla.json')).write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    for item in input_dir.iterdir():
        if item.is_file() and item.suffix != '.apk':
            shutil.copy2(item, output_dir / item.name)


def sign_apk(source: Path, target: Path):
    java, align, _, signer, _ = tools()
    validate_embedded(source)
    props = dict(line.split('=', 1) for line in (ROOT / 'keystore.properties').read_text().splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    env = dict(os.environ, MIMIC_STORE_PASS=props['storePassword'], MIMIC_KEY_PASS=props['keyPassword'])
    run([java, '-jar', signer, 'sign', '--ks', props['storeFile'], '--ks-key-alias', props['keyAlias'],
         '--ks-pass', 'env:MIMIC_STORE_PASS', '--key-pass', 'env:MIMIC_KEY_PASS', '--out', target, source], env=env)
    certs = run([java, '-jar', signer, 'verify', '--print-certs', target])
    found = re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]{64})', certs)
    if found != [CERT]:
        raise RuntimeError('Release signing identity mismatch')
    run([align, '-c', '-P', '16', '4', target])
    validate_embedded(target)


def apk_min_sdk(badging: str) -> int:
    # aapt1 uses sdkVersion; current aapt2 uses minSdkVersion.
    match = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", badging, re.M)
    if not match or int(match[1]) < 1:
        raise RuntimeError('Missing APK minimum SDK metadata')
    return int(match[1])


def prepare_dist(dist: Path):
    expected = ROOT.resolve() / 'dist'
    if dist.resolve() != expected:
        raise RuntimeError('Refusing to clear a directory outside the project dist folder')
    if dist.exists():
        shutil.rmtree(dist)
    dist.mkdir()


def stage_release(version: str, dist: Path):
    if dist.resolve() != (ROOT / 'dist').resolve():
        raise RuntimeError('Release staging must use the project dist directory')
    dist.mkdir(exist_ok=True)
    if any(dist.iterdir()):
        raise RuntimeError('dist must be empty before staging')
    _, _, _, _, aapt = tools()
    outputs = [('full', suffix) for suffix in ('universal', 'arm64-v8a', 'armeabi-v7a', 'x86_64')] + [('standard', 'universal')]
    evidence = {}
    for edition, abi in outputs:
        folder = ROOT / 'app/build/outputs/bla' / f'{edition}Release'
        candidates = list(folder.glob(f'app-{edition}-{abi}-release-unsigned.apk'))
        if len(candidates) != 1:
            raise RuntimeError(f'Missing/ambiguous protected output: {edition} {abi}')
        name = f'MImic-v{version}-{edition}' + ('' if abi == 'universal' else f'-{abi}') + '.apk'
        target = dist / name
        receipt = validate_embedded(candidates[0])
        sign_apk(candidates[0], target)
        all_badging = run([aapt, 'dump', 'badging', target])
        badging = all_badging.splitlines()[0]
        if f"name='{PACKAGE}'" not in badging or f"versionName='{version}'" not in badging:
            raise RuntimeError('Unexpected signed APK package/version')
        min_sdk = apk_min_sdk(all_badging)
        evidence[name] = dict(sha256=sha(target.read_bytes()), bytes=target.stat().st_size, minSdk=min_sdk,
                              certificateSha256=CERT, dexCount=len(receipt['dex']), blaVersion=VERSION,
                              intensity='stealth', r8=True, nativeAlignmentKiB=16,
                              limitations=receipt['limitations'])
    (dist / 'protection-report.json').write_text(json.dumps(dict(schema='mimic-bla-release-v1',
        version=version, artifacts=evidence), indent=2) + '\n', encoding='utf8')


def verify_release(dist: Path, emulator: bool = False):
    report = json.loads((dist / 'protection-report.json').read_text(encoding='utf8'))
    apks = {path.name: path for path in dist.glob('*.apk')}
    if not apks or set(apks) != set(report['artifacts']):
        raise RuntimeError('Release report does not cover exactly the staged APKs')
    java, align, _, signer, _ = tools()
    for name, path in apks.items():
        evidence = report['artifacts'][name]
        if sha(path.read_bytes()) != evidence['sha256'] or evidence['certificateSha256'] != CERT:
            raise RuntimeError(f'Stale/tampered release artifact: {name}')
        validate_embedded(path)
        certs = run([java, '-jar', signer, 'verify', '--print-certs', path])
        if re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]{64})', certs) != [CERT]:
            raise RuntimeError('Unexpected signing certificate')
        run([align, '-c', '-P', '16', '4', path])
    if emulator:
        tested = json.loads((dist / 'emulator-verification.json').read_text(encoding='utf8'))
        required = {n for n in apks if n.endswith(('-full.apk', '-full-x86_64.apk', '-standard.apk'))}
        if tested.get('passed') is not True or tested.get('upgradeVerified') is not True or len(required) != 3:
            raise RuntimeError('Missing successful signed release upgrade test')
        if tested.get('sourceDigest') != source_digest():
            raise RuntimeError('Emulator test receipt belongs to different source')
        if tested.get('testedApks') != {name: report['artifacts'][name]['sha256'] for name in sorted(required)}:
            raise RuntimeError('Emulator receipt does not cover these exact final APKs')
        for name in (n for n in required if n.endswith(('-full.apk', '-standard.apk'))):
            playback = tested.get('playbackTests', {}).get(name, {})
            if not all(playback.get(field) is True for field in ('localAudio', 'backgroundPlayback',
                        'lockScreenPlayback', 'mediaNotification', 'activeAudioTrack')):
                raise RuntimeError('Missing full/standard playback verification')
        recovery = tested.get('recoveryTests', {})
        full = next(name for name in required if name.endswith('-full.apk'))
        if recovery.get('apkSha256') != report['artifacts'][full]['sha256'] or not all(
            recovery.get(field) is True for field in ('activeThemeFallback', 'removalPersistsAfterRestart',
                'removalPersistsAfterReboot', 'sameVersionReinstall', 'lastThemeProtected',
                'offlineThemeRestoration', 'restorePreservesSelection', 'settingsResetConfirmed',
                'originalInstallRecordPreserved', 'removalPersistsAfterUpgrade', 'visibleDownloadsAndFavoritesPreserved')):
            raise RuntimeError('Missing final APK theme recovery/repair regression evidence')
    print('BLA release verification passed' + (' including emulator upgrade evidence' if emulator else ''))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    protect = sub.add_parser('protect-directory')
    protect.add_argument('input', type=Path)
    protect.add_argument('output', type=Path)
    stage = sub.add_parser('stage-release')
    stage.add_argument('version')
    stage.add_argument('dist', type=Path)
    prepare = sub.add_parser('prepare-dist')
    prepare.add_argument('dist', type=Path)
    verify = sub.add_parser('verify-release')
    verify.add_argument('dist', type=Path)
    verify.add_argument('--require-emulator', action='store_true')
    args = parser.parse_args()
    if args.command == 'protect-directory':
        protect_directory(args.input, args.output)
    elif args.command == 'prepare-dist':
        prepare_dist(args.dist)
    elif args.command == 'stage-release':
        stage_release(args.version, args.dist)
    else:
        verify_release(args.dist, args.require_emulator)
