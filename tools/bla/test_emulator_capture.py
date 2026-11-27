import unittest
from unittest.mock import patch

from verify_emulator import Emulator


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


if __name__ == '__main__':
    unittest.main()
