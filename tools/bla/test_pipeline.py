import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import warnings
import zipfile
import zlib

from apk_pipeline import dex_members, validate_dex, validate_embedded, MANIFEST, sha, run


def fixture():
    data = bytearray(112)
    data[:8] = b'dex\n037\0'
    struct.pack_into('<III', data, 32, len(data), 112, 0x12345678)
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into('<I', data, 8, zlib.adler32(data[12:]))
    return bytes(data)


class ProtectionAdmissionTest(unittest.TestCase):
    def test_utf8_tool_output_is_preserved_on_windows(self):
        value = run(['node', '-e', "process.stdout.write(Buffer.from('e2808d', 'hex'))"])
        self.assertEqual('\u200d', value)

    def archive(self, names):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as z:
            for name in names:
                z.writestr(name, b'fixture')
        stream.seek(0)
        return zipfile.ZipFile(stream)

    def test_multidex_includes_tenth_and_later_members(self):
        names = ['classes.dex'] + [f'classes{i}.dex' for i in range(2, 24)]
        with self.archive(reversed(names)) as z:
            self.assertEqual(names, dex_members(z))

    def test_missing_or_aliased_or_nested_dex_is_refused(self):
        for names in (['classes2.dex'], ['classes.dex', 'classes3.dex'],
                      ['classes.dex', 'classes1.dex'], ['classes.dex', 'assets/hidden.dex'],
                      ['classes.dex', 'classes02.dex']):
            with self.subTest(names=names), self.archive(names) as z:
                with self.assertRaises(RuntimeError):
                    dex_members(z)

    def test_duplicate_zip_members_are_refused(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with self.archive(['classes.dex', 'classes.dex']) as z:
                with self.assertRaises(RuntimeError):
                    dex_members(z)

    def test_sha1_is_checked_even_when_adler32_was_recomputed(self):
        data = bytearray(fixture())
        data[12] ^= 1
        struct.pack_into('<I', data, 8, zlib.adler32(data[12:]))
        with self.assertRaisesRegex(RuntimeError, 'signature or checksum'):
            validate_dex(data)

    def test_container_dex_041_is_rejected(self):
        data = bytearray(fixture())
        data[4:7] = b'041'
        with self.assertRaisesRegex(RuntimeError, 'Unsupported DEX'):
            validate_dex(data)

    def test_marker_alone_cannot_authorize_changed_dex(self):
        data = fixture()
        receipt = dict(blaVersion='3.6.1', intensity='stealth', dex={
            'classes.dex': {'protectedSha256': sha(data)}})
        data = bytearray(data)
        data[48] = 1
        data[12:32] = hashlib.sha1(data[32:]).digest()
        struct.pack_into('<I', data, 8, zlib.adler32(data[12:]))
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'changed.apk'
            with zipfile.ZipFile(apk, 'w') as z:
                z.writestr('classes.dex', data)
                z.writestr(MANIFEST, json.dumps(receipt))
            with self.assertRaisesRegex(RuntimeError, 'Protected DEX changed'):
                validate_embedded(apk)

    def test_self_reported_hash_does_not_authorize_an_unprotected_apk(self):
        data = fixture()
        receipt = dict(blaVersion='3.6.1', intensity='stealth', dex={
            'classes.dex': {'protectedSha256': sha(data), 'originalBytes': len(data)}})
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'ordinary.apk'
            with zipfile.ZipFile(apk, 'w') as z:
                z.writestr('classes.dex', data)
                z.writestr(MANIFEST, json.dumps(receipt))
            with self.assertRaisesRegex(RuntimeError, 'missing BLA trailer'):
                validate_embedded(apk)


if __name__ == '__main__':
    unittest.main()
