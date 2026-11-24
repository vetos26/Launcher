# Постоянная подпись APK

Начиная с версии **1.5.1** APK собирается в `assembleRelease` и подписывается постоянным
ключом. Это значит: обновления ставятся поверх (`adb install -r`, или просто тап по APK)
без `adb uninstall`, потому что подпись больше не меняется от сборки к сборке.

## Как это устроено

- `app/build.gradle.kts` ищет ключ в таком порядке:
  1. `SIGNING_KEYSTORE_PATH` + `SIGNING_STORE_PASSWORD` (их выставляет CI из секретов);
  2. локально — `signing/utouch-launcher.p12` + `signing/password.txt` (папка в `.gitignore`).
- Если ключа нет, релизная сборка остаётся неподписанной, а workflow публикует debug APK —
  то есть форки и пул-реквесты ничего не ломают.
- CI дополнительно проверяет подпись через `apksigner verify --print-certs` и пишет тип APK
  в сводку сборки.

## Секреты репозитория (Settings → Secrets and variables → Actions)

| Имя | Значение |
|---|---|
| `SIGNING_KEYSTORE` | содержимое `signing/utouch-launcher.p12.base64.txt` (одна строка base64) |
| `SIGNING_STORE_PASSWORD` | пароль из `signing/password.txt` |
| `SIGNING_KEY_PASSWORD` | необязательно, по умолчанию совпадает со store-паролем |
| `SIGNING_KEY_ALIAS` | необязательно, по умолчанию `utouch` |

## Сертификат

- Subject: `CN = Ubuntu Touch Launcher, O = vetos26, C = DE`
- Тип: RSA 2048, PKCS12, alias `utouch`
- Годен до: 16.11.2056
- SHA-256: `A1:9C:79:52:0B:CA:BB:8F:21:60:F9:BD:87:05:B4:65:86:66:5A:80:6F:38:29:BC:D6:82:F1:3B:3D:FC:95:EC`

## Что важно помнить

- **Ключ нельзя терять.** Если потерять keystore, обновиться поверх установленного
  приложения будет невозможно — только удалить и поставить заново.
- Приватный ключ не должен попадать в публичный репозиторий: папка `signing/`
  в `.gitignore`, а CI берёт ключ из секретов.
- Один раз после перехода с debug на постоянную подпись приложение придётся переустановить
  (`adb uninstall dev.utouch.launcher`), потому что подпись меняется. Дальше — только обновления.
