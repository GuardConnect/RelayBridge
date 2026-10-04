#!/usr/bin/env python3
"""Package a verified APK and notes without publishing credentials."""
import hashlib
import os
import re
import shutil
from pathlib import Path

text = Path('app/build/release-badging.txt').read_text()
version = re.search(r"versionName='([^']+)'", text).group(1)
if not re.fullmatch(r'\d+(?:\.\d+){1,2}', version):
    raise SystemExit('Invalid release version')
out = Path('artifacts')
out.mkdir(exist_ok=True)
apk = out / f'RelayBridge-{version}-arm64-v8a.apk'
shutil.copyfile('app/build/outputs/apk/release/app-release.apk', apk)
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
(out / 'SHA256SUMS.txt').write_text(f'{sha}  {apk.name}\n')
signing = os.environ.get('RELEASE_SIGNING', 'temporary CI test key')
(out / 'RELEASE-NOTES.md').write_text(
    f'RelayBridge {version} — Android 15 и новее.\n\n'
    'Добавлены SIM входящего звонка и независимый выбор SIM для захвата звонков. '
    'Версия в интерфейсе совпадает с APK. Telegram, обычный SMTP, шаблоны и журнал сохранены.\n\n'
    'Сборка Release, debuggable=false, профиль arm64-v8a. Текущий Java APK универсальный, '
    'без нативных .so библиотек; совместим с ARMv8.\n\n'
    f'Подпись: {signing}.\n'
    + ('Используется новый временный тестовый ключ CI. Обновление поверх ранее установленных APK с другой подписью невозможно; '
       'для официального выпуска необходим собственный защищённый ключ.\n' if signing == 'temporary CI test key' else '')
    + '\nАвтотесты и lint выполнены при сборке. Реальная пересылка и определение SIM '
      'на физическом Android с двумя SIM ещё требуют проверки.\n', encoding='utf-8')
if path := os.environ.get('GITHUB_OUTPUT'):
    with open(path, 'a') as stream:
        stream.write(f'version={version}\n')
print(f'Prepared {apk.name}; SHA-256: {sha}')
