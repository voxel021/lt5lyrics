#!/usr/bin/env python3
"""Inventory native files, including those in nested JARs, without extracting them."""
import argparse
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path

NATIVE = re.compile(r'\.(dll|so(?:\.\d+)*|dylib|exe|jnilib|a)$', re.I)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def audit(data, prefix='', depth=0):
    if depth > 10:
        raise ValueError('Nested JAR depth exceeds safety limit')
    results = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            if NATIVE.search(name):
                payload = archive.read(entry)
                results.append({'path': prefix + name, 'size': len(payload),
                                'sha256': sha256(payload)})
            elif name.lower().endswith('.jar'):
                results.extend(audit(archive.read(entry), prefix + name + '!/', depth + 1))
    return sorted(results, key=lambda item: item['path'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jars', nargs='+', type=Path)
    args = parser.parse_args()
    reports = []
    for path in args.jars:
        data = path.read_bytes()
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            metadata = json.loads(archive.read('fabric.mod.json')) if 'fabric.mod.json' in archive.namelist() else {}
        reports.append({'artifact': path.name, 'sha256': sha256(data),
                        'mod_version': metadata.get('version'),
                        'minecraft': metadata.get('depends', {}).get('minecraft'),
                        'natives': audit(data)})
    print(json.dumps(reports, indent=2))


if __name__ == '__main__':
    main()
