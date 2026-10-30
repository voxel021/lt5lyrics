#!/usr/bin/env python3
"""Extract a hash-checked uploaded source archive for one Minecraft version."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path


def extract(version, destination):
    root = Path(__file__).resolve().parents[1]
    source = json.loads((root / 'tools/version-sources.json').read_text())[version]
    archive = root / source['archive']
    if hashlib.sha256(archive.read_bytes()).hexdigest() != source['sha256']:
        raise ValueError('Source ZIP hash differs from the reviewed manifest')
    destination = Path(destination)
    if destination.exists():
        raise ValueError('Destination must not exist')
    with zipfile.ZipFile(archive) as z:
        members = []
        roots = set()
        for entry in z.infolist():
            path = Path(entry.filename)
            if path.is_absolute() or '..' in path.parts or '.git' in path.parts or '\\' in entry.filename:
                raise ValueError('Unsafe ZIP member: ' + entry.filename)
            roots.add(path.parts[0])
            if not entry.is_dir() and len(path.parts) > 1:
                members.append((entry, Path(*path.parts[1:])))
        if len(roots) != 1:
            raise ValueError('Expected one top-level project folder')
        for entry, relative in members:
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(z.read(entry))
    properties = (destination / 'gradle.properties').read_text()
    if f'minecraft_version={version}' not in properties.splitlines():
        raise ValueError('Minecraft version does not match archive')
    (destination / 'gradlew').chmod(0o755)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', choices=['1.21.11', '26.2'])
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    extract(args.version, args.destination)
