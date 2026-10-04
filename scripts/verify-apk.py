#!/usr/bin/env python3
"""Fail CI if the APK has an unexpected version or a non-ARM64 native library."""
import re
import sys
import zipfile
from pathlib import Path

apk, badging = map(Path, sys.argv[1:])
text = badging.read_text()
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text)
if not package or package.groups() != ('ru.eyeone.relaybridge', '24', '2.7'):
    raise SystemExit('Unexpected package or version; expected ru.eyeone.relaybridge 2.7 (24)')
with zipfile.ZipFile(apk) as archive:
    bad_entry = archive.testzip()
    if bad_entry:
        raise SystemExit(f'Corrupt APK entry: {bad_entry}')
    libraries = [n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
    if any(n.split('/')[1] != 'arm64-v8a' for n in libraries):
        raise SystemExit('APK contains native libraries outside arm64-v8a')
print('PASS: version 2.7 (24); ARM64 native libraries only' if libraries
      else 'PASS: version 2.7 (24); Java-only universal APK, compatible with ARMv8')
