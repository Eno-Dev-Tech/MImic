import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import hashlib
from pathlib import Path
import tempfile

from verify_emulator import Emulator
from verify_recovery import motion_chip


class FreshAccessibilityCaptureTest(unittest.TestCase):
    @patch('verify_emulator.time.sleep')
    def test_failed_zero_exit_dump_never_reads_old_xml(self, _):
        phone = object.__new__(Emulator)
        calls = []
        attempts = 0

        def shell(*args):
            nonlocal attempts
            calls.append(args)
            if args[0] == 'uiautomator':
                attempts += 1
                return 'ERROR: could not get idle state' if attempts == 1 else 'UI hierchary dumped to: /sdcard/mimic-bla-validation.xml'
            if args[0] == 'cat':
                return '<hierarchy><node text="Remove" content-desc="" /></hierarchy>'
            return ''

        phone.shell = shell
        self.assertEqual('Remove', next(phone.tree().iter('node')).get('text'))
        self.assertEqual(2, attempts)
        self.assertEqual(1, sum(args[0] == 'cat' for args in calls))
        self.assertEqual(2, sum(args[0] == 'rm' for args in calls))

    @patch('verify_emulator.time.sleep')
    def test_all_failed_dumps_refuse_any_coordinate_evidence(self, _):
        phone = object.__new__(Emulator)
        calls = []

        def shell(*args):
            calls.append(args)
            return 'ERROR: null root node returned by UiTestAutomationBridge'

        phone.shell = shell
        with self.assertRaisesRegex(RuntimeError, 'No fresh'):
            phone.tree()
        self.assertFalse(any(args[0] == 'cat' for args in calls))


class MotionSelectionEvidenceTest(unittest.TestCase):
    @patch('verify_recovery.find')
    def test_only_target_radio_or_tab_selection_authorizes_persistence(self, _):
        for attributes, expected in [
            ('checkable="true" checked="true" selected="false"', True),
            ('checkable="false" checked="false" selected="true"', True),
            ('checkable="true" checked="false" selected="false"', False),
            ('checkable="false" checked="true" selected="false"', False),
        ]:
            with self.subTest(attributes=attributes):
                tree = ET.fromstring('<hierarchy><node bounds="[0,0][1080,2400]">'
                    '<node text="Standard" bounds="[40,400][200,500]" />'
                    '<node ' + attributes + '><node text="Sliding Pill" /></node>'
                    '<node selected="true"><node text="Settings" /></node>'
                    '</node></hierarchy>')
                phone = object.__new__(Emulator)
                phone.tree = lambda: tree
                self.assertEqual(expected, motion_chip(phone, 'Sliding Pill'))


class InstalledArtifactEvidenceTest(unittest.TestCase):
    def test_matching_version_cannot_authorize_different_installed_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / 'candidate.apk'
            apk.write_bytes(b'exact signed candidate fixture')
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            phone = object.__new__(Emulator)
            for actual, accepted in [(digest, True), ('0' * 64, False), ('', False)]:
                phone.shell = lambda *args: ('package:/data/app/fixture/base.apk\n' if args[0] == 'pm'
                                            else actual + '  /data/app/fixture/base.apk\n')
                if accepted:
                    self.assertEqual(digest, phone.verify_installed_apk(apk))
                else:
                    with self.assertRaisesRegex(RuntimeError, 'Installed APK bytes'):
                        phone.verify_installed_apk(apk)

    def test_unexpected_installed_split_set_is_refused(self):
        phone = object.__new__(Emulator)
        phone.shell = lambda *args: 'package:/data/app/fixture/base.apk\npackage:/data/app/fixture/split.apk\n'
        with self.assertRaisesRegex(RuntimeError, 'one installed standalone'):
            phone.verify_installed_apk(Path('unused.apk'))


class ClickTargetEvidenceTest(unittest.TestCase):
    def test_clipped_row_tap_stays_above_the_mini_player(self):
        tree = ET.fromstring('<hierarchy><node bounds="[0,310][1080,1939]" scrollable="true">'
            '<node clickable="true" bounds="[0,1882][943,2008]"><node text="Felt Classic" />'
            '</node></node></hierarchy>')
        target = next(n for n in tree.iter('node') if n.get('clickable') == 'true')
        self.assertEqual((0, 1882, 943, 1939), Emulator.visible_bounds(tree, target))

    def test_negative_scrolled_bounds_are_clipped_to_the_viewport(self):
        tree = ET.fromstring('<hierarchy><node bounds="[42,100][1038,250]">'
            '<node clickable="true" bounds="[-100,100][80,250]" /></node></hierarchy>')
        target = next(n for n in tree.iter('node') if n.get('clickable') == 'true')
        self.assertEqual((42, 100, 80, 250), Emulator.visible_bounds(tree, target))

    def test_control_entirely_outside_viewport_cannot_authorize_tap(self):
        tree = ET.fromstring('<hierarchy><node bounds="[0,0][1080,1939]">'
            '<node clickable="true" bounds="[0,2000][943,2126]" /></node></hierarchy>')
        target = next(n for n in tree.iter('node') if n.get('clickable') == 'true')
        with self.assertRaisesRegex(RuntimeError, 'no visible touch area'):
            Emulator.visible_bounds(tree, target)

    def test_selected_focusable_tab_keeps_its_real_reselect_hit_area(self):
        tree = ET.fromstring('<hierarchy><node selected="true" focusable="true" clickable="false" enabled="true">'
            '<node content-desc="Settings" /></node></hierarchy>')
        label = next(n for n in tree.iter('node') if n.get('content-desc') == 'Settings')
        self.assertIs(tree.find('node'), Emulator.click_target(tree, label))

    def test_label_uses_its_actual_control_not_a_shifted_glyph(self):
        tree = ET.fromstring('<hierarchy><node clickable="true" bounds="[0,100][180,310]">'
            '<node text="Settings" clickable="false" bounds="[20,250][150,300]" />'
            '</node></hierarchy>')
        label = next(n for n in tree.iter('node') if n.get('text') == 'Settings')
        self.assertEqual('[0,100][180,310]', Emulator.click_target(tree, label).get('bounds'))

    def test_disabled_control_never_falls_through_to_an_outer_action(self):
        tree = ET.fromstring('<hierarchy><node clickable="true"><node clickable="true" enabled="false">'
            '<node text="Remove" /></node></node></hierarchy>')
        label = next(n for n in tree.iter('node') if n.get('text') == 'Remove')
        with self.assertRaisesRegex(RuntimeError, 'disabled'):
            Emulator.click_target(tree, label)

    def test_plain_text_cannot_authorize_a_coordinate_tap(self):
        tree = ET.fromstring('<hierarchy><node text="Remove" /></hierarchy>')
        with self.assertRaisesRegex(RuntimeError, 'no enabled clickable'):
            Emulator.click_target(tree, tree.find('node'))


class ForegroundEvidenceTest(unittest.TestCase):
    def test_system_shade_cannot_authorize_background_app_interactions(self):
        phone = object.__new__(Emulator)
        phone.shell = lambda *args: ('mCurrentFocus=Window{abc u0 NotificationShade}\n'
            'mFocusedApp=ActivityRecord{abc u0 com.example.mimic/.MainActivity t1}\n')
        with self.assertRaisesRegex(RuntimeError, 'not the focused UI'):
            phone.require_app_focus()


class RebootEvidenceTest(unittest.TestCase):
    before = '11111111-1111-1111-1111-111111111111'
    after = '22222222-2222-2222-2222-222222222222'

    def phone(self, changed=True, completed='1', timeout=False):
        phone = object.__new__(Emulator)
        reads = 0
        def shell(*args, **kwargs):
            nonlocal reads
            if args[0] == 'cat':
                reads += 1
                return self.after if changed and reads > 1 else self.before
            if args[0] == 'getprop':
                return completed
            if timeout:
                import subprocess
                raise subprocess.TimeoutExpired('adb', 15)
            return ''
        phone.shell = shell
        return phone

    @patch('verify_emulator.time.monotonic', side_effect=[0, 0])
    def test_changed_kernel_identity_and_completed_boot_are_required(self, _):
        proof = self.phone().reboot(wait_seconds=1)
        self.assertEqual(self.before, proof['previousBootId'])
        self.assertEqual(self.after, proof['bootId'])
        self.assertTrue(proof['bootCompleted'])

    @patch('verify_emulator.time.monotonic', side_effect=[0, 0])
    def test_transport_timeout_only_passes_with_independent_reboot_proof(self, _):
        proof = self.phone(timeout=True).reboot(wait_seconds=1)
        self.assertIn('timeout', proof['commandStatus'])
        self.assertNotEqual(proof['previousBootId'], proof['bootId'])

    @patch('verify_emulator.time.sleep')
    @patch('verify_emulator.time.monotonic', side_effect=[0, 0, 2])
    def test_old_completed_boot_cannot_fake_a_reboot(self, *_):
        with self.assertRaisesRegex(RuntimeError, 'Reboot was not proved'):
            self.phone(changed=False, timeout=True).reboot(wait_seconds=1)

    @patch('verify_emulator.time.sleep')
    @patch('verify_emulator.time.monotonic', side_effect=[0, 0, 2])
    def test_new_boot_that_is_not_complete_is_refused(self, *_):
        with self.assertRaisesRegex(RuntimeError, 'Reboot was not proved'):
            self.phone(completed='0').reboot(wait_seconds=1)

    def test_own_window_and_own_popup_are_accepted(self):
        phone = object.__new__(Emulator)
        for focus in ['com.example.mimic/com.example.mimic.MainActivity', 'PopupWindow:123', 'Pop-Up Window']:
            phone.shell = lambda *args: ('mCurrentFocus=Window{abc u0 ' + focus + '}\n'
                'mFocusedApp=ActivityRecord{abc u0 com.example.mimic/.MainActivity t1}\n')
            phone.require_app_focus()

    def test_external_popup_is_refused(self):
        phone = object.__new__(Emulator)
        phone.shell = lambda *args: ('mCurrentFocus=Window{abc u0 PopupWindow:123}\n'
            'mFocusedApp=ActivityRecord{abc u0 com.android.launcher/.Launcher t1}\n')
        with self.assertRaisesRegex(RuntimeError, 'not the focused UI'):
            phone.require_app_focus()


if __name__ == '__main__':
    unittest.main()
