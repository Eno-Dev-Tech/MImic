"""ADB-only recovery UI checks. Preserve the owner's emulator with an owned snapshot."""
import json
import time
import uuid
from pathlib import Path

from apk_pipeline import ROOT, PACKAGE, sha
from verify_emulator import Emulator


def labels(phone):
    return {n.get('text') or n.get('content-desc') for n in phone.tree().iter('node')}


def scroll(phone, upward=False):
    phone.shell('input', 'swipe', '400', '450' if upward else '1500', '400', '1500' if upward else '450', '350')


def find(phone, label, attempts=25):
    for _ in range(attempts):
        if label in labels(phone):
            return
        scroll(phone)
    raise RuntimeError('Missing recovery UI: ' + label)


def gallery(phone):
    phone.launch()
    phone.tap('Settings')
    phone.tap('Theme Studio')


def updates(phone):
    phone.launch()
    phone.tap('Settings')
    find(phone, 'App Updates')
    phone.tap('App Updates')
    find(phone, 'Recovery & Repair')
    find(phone, 'Restore Removed Themes')


def screenshot(phone, name):
    folder = ROOT / 'build/bla/screenshots'
    folder.mkdir(parents=True, exist_ok=True)
    remote = '/sdcard/mimic-recovery-screen.png'
    phone.shell('screencap', '-p', remote)
    phone.command('pull', remote, str(folder / name))


def remove(phone, name):
    print('Removing theme through its confirmation:', name, flush=True)
    phone.tap('Remove ' + name)
    if 'Remove theme?' not in labels(phone) or 'You can restore this theme later from Settings > Updates.' not in labels(phone):
        raise RuntimeError('Theme removal confirmation contract changed')
    phone.tap('Remove')


def download_signature(phone):
    phone.launch()
    phone.tap('Downloads')
    find(phone, 'Offline music')
    tree = phone.tree()
    navigation = {'Home', 'Search', 'Library', 'Radio', 'Downloads', 'Settings'}
    text = sorted({n.get('text') for n in tree.iter('node') if n.get('text') and
                   n.get('text') not in navigation and 'used by MImic downloads' not in n.get('text')})
    favorites = [n.get('content-desc') for n in tree.iter('node') if n.get('content-desc') in
                 ('Add to favorites', 'Remove from favorites')]
    if not any('songs available offline' in value for value in text):
        raise RuntimeError('Missing personal download fixture')
    return text, sorted(favorites)


def all_removal_controls(phone):
    """Scan the entire gallery to prove absence/last-theme behavior, not only one viewport."""
    prior = None
    for _ in range(25):
        current = tuple((n.get('text'), n.get('content-desc'), n.get('bounds')) for n in phone.tree().iter('node'))
        if current == prior:
            break
        prior = current
        scroll(phone, upward=True)
    controls = {}
    previous = None
    for _ in range(50):
        tree = phone.tree()
        view = tuple((n.get('text'), n.get('content-desc'), n.get('bounds')) for n in tree.iter('node'))
        for node in tree.iter('node'):
            description = node.get('content-desc', '')
            if description.startswith('Remove '):
                # Compose may put enabled on the clickable parent, not the icon node.
                parent = next((p for p in tree.iter('node') if node in list(p)), node)
                controls[description] = node.get('enabled') != 'false' and parent.get('enabled') != 'false'
        if view == previous:
            return controls
        previous = view
        scroll(phone)
    raise RuntimeError('Gallery scan did not reach its end')


def prepare_upgrade(phone):
    """Use only on the internal beta.2 fixture, before building the final higher version."""
    gallery(phone)
    find(phone, 'Felt Orange')
    phone.tap('Felt Orange')
    remove(phone, 'Felt Orange')
    phone.launch()
    if phone.current_theme() == 'Current: Felt Orange':
        raise RuntimeError('Active-theme removal did not select a fallback')
    proof = dict(installed=phone.installed(), hiddenTheme='Felt Orange', activeTheme=phone.current_theme())
    (ROOT / 'build/bla/recovery-upgrade-baseline.json').write_text(json.dumps(proof, indent=2), encoding='utf8')
    print('Prepared internal upgrade fixture with Felt Orange removed; no uninstall or data clear', flush=True)


def verify(phone, apk: Path):
    before = phone.installed()
    snapshot = 'mimic_recovery_' + uuid.uuid4().hex[:12]
    answer = phone.command('emu', 'avd', 'snapshot', 'save', snapshot)
    if 'KO:' in answer or 'OK' not in answer:
        raise RuntimeError('Cannot preserve emulator before recovery checks')
    result = {}
    try:
        personal = download_signature(phone)
        baseline_path = ROOT / 'build/bla/recovery-upgrade-baseline.json'
        if baseline_path.exists():
            baseline = json.loads(baseline_path.read_text(encoding='utf8'))
            if before['versionCode'] <= baseline['installed']['versionCode'] or before['firstInstallTime'] != baseline['installed']['firstInstallTime']:
                raise RuntimeError('Recovery upgrade fixture does not precede this installed version')
            gallery(phone)
            if 'Remove ' + baseline['hiddenTheme'] in all_removal_controls(phone):
                raise RuntimeError('Removed theme preferences did not survive the APK upgrade')
            result['removalPersistsAfterUpgrade'] = True
            updates(phone)
            phone.tap('Restore Removed Themes')
        gallery(phone)
        find(phone, 'Felt Orange')
        phone.tap('Felt Orange')
        screenshot(phone, 'theme-gallery.png')
        remove(phone, 'Felt Orange')
        phone.launch()
        if phone.current_theme() == 'Current: Felt Orange':
            raise RuntimeError('Active removal retained the removed theme')
        result['activeThemeFallback'] = True
        # Reinstall the exact signed APK without uninstalling or changing its version.
        phone.command('install', '-r', str(apk))
        if phone.installed() != before:
            raise RuntimeError('Same-version reinstall changed the install record')
        phone.command('reboot')
        phone.command('wait-for-device')
        for _ in range(60):
            if phone.shell('getprop', 'sys.boot_completed').strip() == '1':
                break
            time.sleep(1)
        else:
            raise RuntimeError('Emulator did not finish rebooting')
        phone.shell('input', 'keyevent', '82')
        gallery(phone)
        if 'Remove Felt Orange' in all_removal_controls(phone):
            raise RuntimeError('Removed theme reappeared after restart/reinstall/reboot')
        result.update(removalPersistsAfterRestart=True, removalPersistsAfterReboot=True, sameVersionReinstall=True)
        updates(phone)
        find(phone, 'Restore Removed Themes')
        phone.tap('Restore Removed Themes')
        screenshot(phone, 'recovery-repair.png')
        gallery(phone)
        find(phone, 'Remove Felt Orange')
        # Remove every theme via the actual confirmation until the last control is disabled.
        count = 0
        while True:
            tree = phone.tree()
            nodes = [n for n in tree.iter('node') if n.get('content-desc', '').startswith('Remove ')]
            enabled = []
            for node in nodes:
                parent = next((p for p in tree.iter('node') if node in list(p)), node)
                if node.get('enabled') != 'false' and parent.get('enabled') != 'false':
                    enabled.append(node)
            if enabled:
                remove(phone, enabled[0].get('content-desc').removeprefix('Remove '))
                count += 1
                if count > 200:
                    raise RuntimeError('Theme removal did not converge')
            else:
                controls = all_removal_controls(phone)
                if len(controls) == 1 and not any(controls.values()):
                    break
                # The scan ended at the bottom; scroll to first available control.
                for _ in range(25):
                    scroll(phone, upward=True)
                if not any(controls.values()):
                    raise RuntimeError('No accessible last-theme control')
        result.update(lastThemeProtected=True, individuallyRemoved=count)
        # Offline restoration, preserving the selected last theme.
        phone.launch()
        selected = phone.current_theme()
        airplane = phone.shell('settings', 'get', 'global', 'airplane_mode_on').strip()
        try:
            phone.shell('cmd', 'connectivity', 'airplane-mode', 'enable')
            updates(phone)
            phone.tap('Restore Removed Themes')
            phone.launch()
            if phone.current_theme() != selected:
                raise RuntimeError('Restoration unnecessarily changed the selected theme')
        finally:
            if airplane != '1':
                phone.shell('cmd', 'connectivity', 'airplane-mode', 'disable')
        gallery(phone)
        find(phone, 'Remove Felt Orange')
        result.update(restorePreservesSelection=True, offlineThemeRestoration=True)
        updates(phone)
        find(phone, 'Reset App Settings')
        phone.tap('Reset App Settings')
        if 'Reset app settings?' not in labels(phone):
            raise RuntimeError('Missing settings-reset confirmation')
        screenshot(phone, 'reset-confirmation.png')
        phone.tap('Cancel')
        phone.tap('Reset App Settings')
        phone.tap('Reset settings')
        find(phone, 'Default settings restored. Your personal data has been kept.')
        phone.launch()
        if phone.current_theme() != 'Current: Classic Music Dark':
            raise RuntimeError('Settings reset did not restore the default appearance')
        gallery(phone)
        find(phone, 'Remove Felt Orange')
        if download_signature(phone) != personal:
            raise RuntimeError('Settings reset changed the visible downloads/favorites fixture')
        result['visibleDownloadsAndFavoritesPreserved'] = True
        # The isolated Android test verifies retained personal keys; this checks final UI behavior.
        result['settingsResetConfirmed'] = True
        if phone.installed() != before:
            raise RuntimeError('Recovery changed the original install identity')
        result['originalInstallRecordPreserved'] = True
        return dict(apkSha256=sha(apk.read_bytes()), **result)
    finally:
        restored = phone.command('emu', 'avd', 'snapshot', 'load', snapshot)
        if 'KO:' in restored or 'OK' not in restored:
            raise RuntimeError('Restore failed; retain recovery snapshot ' + snapshot)
        for _ in range(30):
            try:
                if phone.installed() == before:
                    break
            except Exception:
                pass
            time.sleep(1)
        else:
            raise RuntimeError('Recovery snapshot did not restore the original install record')
        phone.command('emu', 'avd', 'snapshot', 'delete', snapshot)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-upgrade', action='store_true')
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    emulator = Emulator('emulator-5554')
    if args.prepare_upgrade:
        prepare_upgrade(emulator)
    elif args.apk:
        print(json.dumps(verify(emulator, args.apk.resolve()), indent=2))
    else:
        parser.error('Choose --prepare-upgrade or --apk')
