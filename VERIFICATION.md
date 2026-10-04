# Проверка RelayBridge 2.0 (код 17)

04.10.2026. Проект Android Studio / Java на основе 1.13. JDK 17, Gradle 8.11.1, compileSdk/targetSdk 36, minSdk 35.

Исправлена устаревшая строка «1.13» в GUI. Шапка использует BuildConfig.VERSION_NAME из Gradle. В созданном BuildConfig VERSION_NAME=2.0, VERSION_CODE=17; в конечном APK aapt подтвердил versionName=2.0/versionCode=17. В instrumentation добавлена проверка совпадения версии шапки и установленного пакета; она скомпилирована, но не выполнена на устройстве.

Выполнена чистая сборка (clean). Успешно выполнены testDebugUnitTest, assembleDebug, lintDebug, assembleDebugAndroidTest. 57 JVM-тестов: 0 ошибок, падений и пропусков. Проверены фильтрация по ID подписки, независимый выбор SIM для двух источников, отказ от неоднозначного определения подписки, SIM перед номером в стандартном шаблоне, отдельные действия разрешений. Существующие SMTP TLS-тесты также прошли.

Lint: 0 ошибок, 20 предупреждений. Instrumentation APK с проверкой шифрованного сохранения двух фильтров и сохранения их через GUI собран, но не запускался: подключённых Android-устройств нет. Реальные входящие звонки, одновременные звонки на две SIM и OEM-особенности определения подписки не проверены на физическом устройстве.

Новых разрешений нет. SIM определяется из extras события, либо для RINGING по единственной звонящей подписке через публичный getCallStateForSubscription. Для IDLE без ID сбрасывается состояние только карт, подтверждённых системой как IDLE. SIM по умолчанию не используется для угадывания. В режиме выбранных SIM неизвестная подписка пропускается; в режиме всех SIM подписывается «SIM не определена».

Сохранён пакет ru.eyeone.relaybridge и development-сертификат SHA-256 c25541c2f392863dffd06ea75717799174e8c0d2ef1019dc5cf05c5a28085fd7. apksigner verify пройден. Поведение обновления и миграция на устройстве не проверены; новые callAllSims=true/callSubscriptions=[] сохраняют захват всех SIM при старой конфигурации.

APK SHA-256: a2d8e10d3463c61d5212fee91793266da3e3b9fe49f612e2d390d23e700e0fc2

Play Protect, одобрение Play и выдача чувствительных разрешений этой сборкой не проверены. Старые runtime-отчёты не подтверждают 2.0. Старый release AAB не пересобирался и не относится к этой версии.

## Подготовка GitHub Actions ARMv8

Локальный запуск с -PrelayAbi=arm64-v8a: testDebugUnitTest, lintDebug, assembleDebug — BUILD SUCCESSFUL. 57 JVM-тестов без ошибок. Проверены Gradle Wrapper SHA-256, YAML и синтаксис bash шагов workflow, APK версия 2.0 (17), подпись и допустимые ABI. Негативная проверка подтверждает отказ verify-apk.py для тестового APK с lib/x86_64/*.so. В реальном APK нет нативных .so, поэтому результат универсальный Java APK, поддерживающий ARMv8.

GitHub-репозиторий пока не определён; публикация исходников и запуск Actions не выполнены. Локальный успех не объявляется успешным GitHub run.

## Release APK и репозиторий GuardConnect/RelayBridge

Подготовлено дерево для основной ветки main на базе commit 664da711a743ab82b62697ab78b5bd7eb242bdd2; сохранён существующий LICENSE. Добавлены исходники Android Studio в корне, CI и workflow публикации Releases.

Локально testDebugUnitTest/lintRelease/assembleRelease с -PrelayAbi=arm64-v8a -PrelayDevelopmentSigning=true завершились BUILD SUCCESSFUL. 57 тестов, 0 failures/errors. apksigner подтвердил прежнюю подпись; aapt подтвердил версию 2.0/код 17 и отсутствие application-debuggable. verify-apk.py/package-release.py успешно проверили и подготовили APK/контрольную сумму/описание Release. YAML и bash синтаксис обоих workflows проверены.

Публикация на GitHub и запуск Actions ещё не выполнены. Проверка доступа git push --dry-run отказала из-за отсутствия авторизации. Это не ошибка сборки и не подтверждение прав записи установленного плагина. Реальные OEM звонки/определение SIM/Play Protect не проверялись.
