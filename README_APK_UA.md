# APK-клієнт PocketBridge Remote 1.7.0

Android-клієнт створений для LG Optimus One P500, Android 2.3.3 / API 10 і CyanogenMod 7.2.

## Native Core

Версія 1.7.0 за замовчуванням не завантажує WebView. Головна панель, профілі, тачпад, клавіатура, статус ПК, Wake-on-LAN і фізичні кнопки реалізовані нативно на Java.

Переваги для P500:

- менше використання RAM;
- швидший перший екран;
- менше пауз збирача сміття;
- відсутність великого HTML DOM у фоновому режимі;
- кеш профілів у SharedPreferences;
- обмежені черги мережевих команд;
- адаптивне опитування статусу;
- тачпад надсилає об’єднані рухи, а не кожну подію.

## Сумісність

```text
minSdkVersion 10
versionCode 15
versionName 1.7.0
сервер 1.3.0 / API 6
Java 7
без AndroidX, Kotlin і Google Play Services
```

## Збірка

Workflow: `.github/workflows/build-apk.yml`.

```text
Actions → Build PocketBridge APK → Run workflow
```

## Встановлення

```powershell
adb install -r PocketBridge-Remote-LG-P500-v1.7.0-debug.apk
```

Для оновлень без видалення APK використовуй один постійний release-ключ.
