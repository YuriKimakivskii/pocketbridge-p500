# PocketBridge Remote 1.7.1 для LG P500

Нативний Android-клієнт із підтримкою API 10.

## Екрани Native Core

- **Панель** — профілі та програмовані кнопки.
- **Тач** — нативний тачпад, кліки й прокручування.
- **Клав.** — комбінації клавіш і Unicode-текст.
- **Ще** — WebView-інструменти, Wake-on-LAN, резервний пульт, налаштування й діагностика.

## Режими

- Native Core увімкнений за замовчуванням.
- Ultra Lite Web UI лишається запасним режимом.
- Повний WebView запускається лише вручну.

## Збірка

Проєкт використовує Gradle, compileSdk 30, Build Tools 30.0.3, Java 7 і minSdk 10.

Артефакт GitHub Actions:

```text
PocketBridge-Remote-LG-P500-v1.7.1
```

Встановлення:

```powershell
adb install -r PocketBridge-Remote-LG-P500-v1.7.1-debug.apk
```
