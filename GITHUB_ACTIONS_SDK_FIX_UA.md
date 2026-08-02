# Виправлення GitHub Actions: `sdkmanager: command not found`

Причина: runner `ubuntu-22.04` більше не гарантує наявність `sdkmanager` у `PATH` до окремого налаштування Android SDK.

Workflow тепер використовує:

```yaml
- name: Set up Android SDK
  uses: android-actions/setup-android@v4
  with:
    cmdline-tools-version: "8512546"
    accept-android-sdk-licenses: true
    log-accepted-android-sdk-licenses: false
    packages: >-
      platform-tools platforms;android-30 build-tools;30.0.3
```

Версію Android command-line tools зафіксовано на `8512546` (7.0), тому `sdkmanager` сумісний з Java 11. Java 11 потрібна через стару сумісну зв'язку Gradle 6.7.1 + Android Gradle Plugin 4.2.2.

## Застосування у вже створеному репозиторії

Замініть файл:

```text
.github/workflows/build-apk.yml
```

на файл із цього архіву, потім виконайте commit і push. У GitHub відкрийте `Actions → Build PocketBridge APK → Run workflow`.
