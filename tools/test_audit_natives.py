import io
import json
import unittest
import zipfile
from pathlib import Path
from audit_natives import audit


def jar(files):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as archive:
        for name, content in files.items():
            archive.writestr(name, content)
    return stream.getvalue()


class AuditTests(unittest.TestCase):
    def test_recursive_and_extensions(self):
        data = jar({'top.DLL': b'win', 'README': b'not native',
                    'dep.jar': jar({'x/lib.so.1.2': b'linux', 'x/lib.a': b'aix',
                                    'mac.jnilib': b'mac', 'app.exe': b'exe'})})
        report = audit(data)
        self.assertEqual(len(report), 5)
        self.assertIn('dep.jar!/x/lib.a', [item['path'] for item in report])
        self.assertTrue(all(len(item['sha256']) == 64 for item in report))

    def test_no_natives(self):
        self.assertEqual(audit(jar({'Example.class': b'java'})), [])

    def test_depth_limit(self):
        data = jar({'lib.dll': b'x'})
        for _ in range(12):
            data = jar({'nested.jar': data})
        with self.assertRaises(ValueError):
            audit(data)

    def test_submitted_artifacts(self):
        root = Path(__file__).resolve().parents[1]
        report = json.loads((root / 'docs/native-audit/submitted-jars.json').read_text())
        for item in report:
            self.assertEqual(audit((root / item['artifact']).read_bytes()), item['natives'])
            self.assertEqual(len(item['natives']), 29)


if __name__ == '__main__':
    unittest.main()
