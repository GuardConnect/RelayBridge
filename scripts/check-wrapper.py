#!/usr/bin/env python3
"""Validate the committed Gradle wrapper before executing it in CI."""
import hashlib
from pathlib import Path

expected = "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"
actual = hashlib.sha256(Path('gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest()
if actual != expected:
    raise SystemExit('Gradle wrapper SHA-256 mismatch')
print('PASS: Gradle wrapper SHA-256')
