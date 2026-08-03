# PocketBridge Remote 2.3.1

Нативний Android-клієнт для LG P500 і Redmi Note 9 Pro.

## Сумісність

```text
minSdkVersion 10
targetSdkVersion 28
compileSdkVersion 30
Java 7
без AndroidX, Kotlin і Google Play Services
```

## Основні модулі

- Native Core;
- WebSocket-тачпад;
- Remote Screen із monitor selector, auto refresh і кліками;
- Clipboard Hub для Windows ↔ Android;
- Native File Manager для upload/download і керування спільними папками;
- YouTube, VLC і PowerPoint;
- System Monitor;
- Android Share to PC;
- Wake-on-LAN;
- full/Lite WebView як додаткові інструменти.

## Збірка

Використовуйте `.github/workflows/build-apk.yml`. Артефакт має назву:

```text
PocketBridge-Remote-MultiDevice-v2.3.1
```
