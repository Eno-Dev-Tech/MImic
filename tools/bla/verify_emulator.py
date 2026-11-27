"""Test exact signed release artifacts on an existing emulator without uninstall.

Requires the previous release installed on the first run, an unlocked emulator,
and a local phone track (MIMIC_SMOKE_TRACK, default Artwork Red). A repeated run
may reuse upgrade evidence only for identical signed APK hashes. Never downgrade,
clear data, or invoke Gradle's connected-test cleanup against the owner's install.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET

from apk_pipeline import ROOT, PACKAGE, CERT, run, sha, tools, verify_release, source_digest


class Emulator:
    def __init__(self, serial):
        if not serial.startswith('emulator-'):
            raise RuntimeError('Only an Android emulator is allowed')
        self.serial = serial
        self.adb = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or
                        Path(os.environ.get('LOCALAPPDATA', '')) / 'Android/Sdk') / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')
        self.command('get-state')
        self.device = self.command('emu', 'avd', 'name').splitlines()[0].strip()
        self.screen = None

    def command(self, *args):
        return run([self.adb, '-s', self.serial, *args])

    def shell(self, *args):
        return self.command('shell', *args)

    def installed(self):
        text = self.shell('dumpsys', 'package', PACKAGE)
        code = re.search(r'versionCode=(\d+)', text)
        first = re.search(r'firstInstallTime=([^\r\n]+)', text)
        if not code or not first:
            raise RuntimeError('Previous MImic release must already be installed')
        return dict(versionCode=int(code[1]), firstInstallTime=first[1].strip())

    def launch(self):
        self.shell('am', 'force-stop', PACKAGE)
        text = self.shell('am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        if 'Status: ok' not in text:
            raise RuntimeError('App launch failed')
        match = re.search(r'TotalTime:\s*(\d+)', text)
        if not match:
            raise RuntimeError('Missing measured startup time')
        return int(match[1])

    def tree(self):
        # A failed dump may exit zero and leave yesterday's XML in place. Never
        # let an old dialog authorize a coordinate tap on the current screen.
        remote = '/sdcard/mimic-bla-validation.xml'
        for _ in range(6):
            try:
                self.shell('rm', '-f', remote)
                output = self.shell('uiautomator', 'dump', remote)
                if remote not in output or 'dumped to:' not in output:
                    time.sleep(.5)
                    continue
                raw = self.shell('cat', remote)
                root = ET.fromstring(raw)
                if any(n.get('text') or n.get('content-desc') for n in root.iter('node')):
                    self.screen = root
                    return self.screen
                time.sleep(.5)
            except (ET.ParseError, subprocess.CalledProcessError):
                time.sleep(.5)
        raise RuntimeError('No fresh stable accessibility tree')

    @staticmethod
    def bounds(node):
        values = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
        return values if len(values) == 4 else [0, 0, 0, 0]

    def tap(self, label, *, bottom=True, exclude_edit=False):
        for _ in range(4):
            tree = self.tree()
            matches = [n for n in tree.iter('node') if n.get('text') == label or n.get('content-desc') == label]
            if exclude_edit:
                matches = [n for n in matches if n.get('class') != 'android.widget.EditText']
            matches = [n for n in matches if self.bounds(n)[2] > self.bounds(n)[0] and self.bounds(n)[3] > self.bounds(n)[1]]
            if matches:
                break
            time.sleep(.4)
        else:
            raise RuntimeError(f'Missing visible UI target: {label}')
        chosen = sorted(matches, key=lambda n: self.bounds(n)[1], reverse=bottom)[0]
        x1, y1, x2, y2 = self.bounds(chosen)
        self.shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(.35)
        self.tree()

    def current_theme(self):
        self.tap('Settings')
        values = [n.get('text', '') for n in self.tree().iter('node')]
        themes = [v for v in values if v.startswith('Current: ')]
        if len(themes) != 1:
            raise RuntimeError('Could not read the existing theme preference')
        return themes[0]

    def navigation(self):
        for label in ['Home', 'Search', 'Library', 'Radio', 'Downloads', 'Settings']:
            self.tap(label)
            tree = self.tree()
            selected = [n for n in tree.iter('node') if n.get('selected') == 'true' and
                        any(c.get('content-desc') == label or c.get('text') == label for c in n.iter('node'))]
            if not selected:
                raise RuntimeError(f'Navigation destination was not selected: {label}')

    def playback_state(self):
        text = self.shell('dumpsys', 'media_session')
        start = text.find('package=' + PACKAGE)
        if start < 0:
            raise RuntimeError('MImic has no media session')
        block = text[start:]
        state = re.search(r'(?:playbackState|state)=PlaybackState\s*\{state=(?:[A-Z_]+\()?([0-9]+)', block)
        position = re.search(r'position=(\d+)', block)
        if not state or not position or state[1] != '3':
            raise RuntimeError('MImic is not playing local audio')
        updated = re.search(r'updated=(\d+)', block)
        speed = re.search(r'speed=([0-9.]+)', block)
        if not updated or not speed or float(speed[1]) <= 0:
            raise RuntimeError('Missing advancing MediaSession clock')
        pid = self.shell('pidof', PACKAGE).strip().split()[0]
        audio = self.shell('dumpsys', 'audio')
        if not re.search(r'u/pid:\d+/' + re.escape(pid) + r'\s+state:started\b', audio):
            raise RuntimeError('No active MImic AudioTrack while playback reports playing')
        # PlaybackState is a timestamped position, not a periodically updated counter.
        elapsed_ms = float(self.shell('cat', '/proc/uptime').split()[0]) * 1000
        return int(position[1]) + max(0, elapsed_ms - int(updated[1])) * float(speed[1])

    def playback(self):
        self.tap('Search')
        if any(n.get('text') == 'My library' for n in self.tree().iter('node')):
            self.tap('My library', bottom=False)
        if any(n.get('content-desc') == 'Clear search' for n in self.tree().iter('node')):
            self.tap('Clear search', bottom=False)
        nodes = [n for n in self.tree().iter('node') if n.get('class') == 'android.widget.EditText']
        if len(nodes) != 1:
            raise RuntimeError('Missing local search field')
        x1, y1, x2, y2 = self.bounds(nodes[0])
        self.shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        self.shell('settings', 'put', 'secure', 'stylus_handwriting_enabled', '0')
        self.shell('input', 'text', os.environ.get('MIMIC_SMOKE_TRACK', 'Artwork Red').replace(' ', '%s'))
        self.shell('input', 'keyevent', '4')
        self.tap(os.environ.get('MIMIC_SMOKE_TRACK', 'Artwork Red'), bottom=False, exclude_edit=True)
        time.sleep(2)
        first = self.playback_state()
        self.shell('input', 'keyevent', '3')
        time.sleep(2)
        background = self.playback_state()
        self.shell('input', 'keyevent', '223')
        time.sleep(2)
        locked = self.playback_state()
        self.shell('input', 'keyevent', '224')
        self.shell('input', 'keyevent', '82')
        if not first < background < locked:
            raise RuntimeError('Playback stopped advancing in background or while locked')
        notification = self.shell('dumpsys', 'notification')
        if PACKAGE not in notification:
            raise RuntimeError('Missing media notification')
        self.shell('cmd', 'media_session', 'dispatch', 'pause')
        return dict(localAudio=True, backgroundPlayback=True, lockScreenPlayback=True,
                    mediaNotification=True, activeAudioTrack=True,
                    playbackPositionsMs=[int(first), int(background), int(locked)])

    def performance(self):
        starts = [self.launch() for _ in range(3)]
        text = self.shell('dumpsys', 'meminfo', PACKAGE)
        pss = re.search(r'TOTAL PSS:\s*(\d+)', text)
        if not pss:
            pss = re.search(r'^\s*TOTAL\s+(\d+)', text, re.M)
        if not pss:
            raise RuntimeError('Missing app memory measurement')
        return dict(coldLaunchMs=starts, medianColdLaunchMs=statistics.median(starts), totalPssKiB=int(pss[1]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL', 'emulator-5554'))
    parser.add_argument('--dist', type=Path, default=ROOT / 'dist')
    args = parser.parse_args()
    dist = args.dist.resolve()
    verify_release(dist)
    release = json.loads((dist / 'protection-report.json').read_text())
    regression = json.loads((ROOT / 'build/bla/android-regression.json').read_text())
    if regression.get('passed') is not True or regression.get('sourceDigest') != source_digest():
        raise RuntimeError('Missing current-source Android regression test evidence')
    targets = sorted((name for name in release['artifacts'] if name.endswith(('-full.apk', '-full-x86_64.apk', '-standard.apk'))),
                     key=lambda n: 0 if n.endswith('-full-x86_64.apk') else 1 if n.endswith('-standard.apk') else 2)
    hashes = {name: release['artifacts'][name]['sha256'] for name in targets}
    previous = ROOT / 'build/bla/emulator-upgrade-receipt.json'
    cached = json.loads(previous.read_text()) if previous.exists() else None
    emulator = Emulator(args.serial)
    before = emulator.installed()
    emulator.launch()
    before_theme = emulator.current_theme()
    java, _, _, signer, aapt = tools()
    badging = run([aapt, 'dump', 'badging', dist / targets[0]]).splitlines()[0]
    target_code = int(re.search(r"versionCode='(\d+)'", badging)[1])
    fresh_upgrade = before['versionCode'] < target_code
    if before['versionCode'] > target_code:
        raise RuntimeError('Refusing downgrade of the emulator installation')
    if not fresh_upgrade and not (cached and cached.get('passed') and cached.get('testedApks') == hashes
                                  and cached.get('firstInstallTime') == before['firstInstallTime']):
        raise RuntimeError('No previous-version upgrade evidence for these APKs; restore the recorded baseline snapshot first')
    perf = {}
    playback_tests = {}
    for name in targets:
        print('Testing final signed APK:', name, flush=True)
        emulator.command('install', '-r', str(dist / name))
        if emulator.installed()['firstInstallTime'] != before['firstInstallTime']:
            raise RuntimeError('Installation replaced user data')
        # Ask ART to compile the actual final DEX, not only parse its checksum.
        emulator.shell('cmd', 'package', 'compile', '-m', 'speed', '-f', PACKAGE)
        perf[name] = emulator.performance()
        if emulator.current_theme() != before_theme:
            raise RuntimeError('Existing theme preference changed during update')
        emulator.navigation()
        print('PASS: install, ART compilation, preserved theme and six tabs:', name, flush=True)
        if name.endswith(('-full.apk', '-standard.apk')):
            print('Testing local/background/lock-screen playback:', name, flush=True)
            playback_tests[name] = emulator.playback()
    playback = next(value for name, value in playback_tests.items() if name.endswith('-full.apk'))
    from verify_recovery import verify as verify_recovery
    full = next(name for name in targets if name.endswith('-full.apk'))
    print('Testing final APK theme recovery, reinstall, reboot and reset', flush=True)
    recovery = verify_recovery(emulator, dist / full)
    pid = emulator.shell('pidof', PACKAGE).strip()
    errors = emulator.command('logcat', '-d', '--pid', pid, '-s', 'AndroidRuntime:E', 'ActivityManager:E')
    if 'FATAL EXCEPTION' in errors or 'ANR in ' + PACKAGE in errors or 'VerifyError' in errors:
        raise RuntimeError('Runtime crash/ANR/verification error in final APK')
    receipt = dict(schema='mimic-bla-emulator-v1', passed=True, upgradeVerified=True,
                   androidRegressionTests=regression['tests'], sourceDigest=regression['sourceDigest'],
                   previousVersionCode=before['versionCode'] if fresh_upgrade else cached['previousVersionCode'],
                   versionCode=target_code, firstInstallTime=before['firstInstallTime'],
                   preservedTheme=before_theme, emulator=emulator.device, serial=emulator.serial,
                   testedApks=hashes, performance=perf, navigationDestinations=6,
                   playbackTests=playback_tests, **playback,
                   recoveryTests=recovery,
                   limitations=['Physical ARM devices and live account/server flows are not tested by this gate.'])
    encoded = json.dumps(receipt, indent=2) + '\n'
    (dist / 'emulator-verification.json').write_text(encoded, encoding='utf8')
    previous.parent.mkdir(parents=True, exist_ok=True)
    previous.write_text(encoded, encoding='utf8')
    verify_release(dist, emulator=True)
    print('Final signed APK emulator and upgrade checks passed')


if __name__ == '__main__':
    main()
