#!/usr/bin/env python3
"""Generate disposable build/test keys locally; never publish private key files."""
from pathlib import Path
import subprocess

def generate(path, alias, password, name, extra=()):
    path = Path(path)
    if path.exists():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['keytool', '-genkeypair', '-noprompt', '-keystore', str(path),
                    '-storetype', 'PKCS12', '-storepass', password, '-keypass', password,
                    '-alias', alias, '-keyalg', 'RSA', '-keysize', '2048',
                    '-validity', '3650', '-dname', name, *extra], check=True)

generate('app/src/test/resources/smtp-fixture.p12', 'smtp-fixture', 'fixture-only',
         'CN=localhost', ['-ext', 'SAN=dns:localhost'])
generate('development-signing.keystore', 'AndroidDebugKey', 'android',
         'CN=RelayBridge Disposable CI Key,O=RelayBridge,C=US')
