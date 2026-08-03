# Постійний release-підпис APK

Постійний ключ потрібен, щоб наступні версії APK встановлювалися поверх попередньої без видалення застосунку та втрати налаштувань.

## 1. Створи ключ один раз

У PowerShell або CMD із встановленою Java:

```powershell
keytool -genkeypair -v `
  -keystore pocketbridge-release.jks `
  -alias pocketbridge `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

Збережи файл `.jks`, alias і паролі у надійному місці. Втрата ключа означає, що новий APK не зможе оновити вже встановлений release APK.

## 2. Перетвори ключ у Base64

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes(".\pocketbridge-release.jks")
) | Set-Content -NoNewline ".\pocketbridge-release.base64.txt"
```

## 3. Додай GitHub Actions secrets

У репозиторії відкрий:

```text
Settings → Secrets and variables → Actions → New repository secret
```

Створи:

- `PB_KEYSTORE_BASE64` — вміст `pocketbridge-release.base64.txt`;
- `PB_KEYSTORE_PASSWORD` — пароль сховища;
- `PB_KEY_ALIAS` — наприклад `pocketbridge`;
- `PB_KEY_PASSWORD` — пароль ключа.

Після цього workflow автоматично збере:

```text
PocketBridge-Remote-MultiDevice-v2.3.1-release.apk
```

Не додавай `.jks`, паролі або Base64-файл безпосередньо до репозиторію.
