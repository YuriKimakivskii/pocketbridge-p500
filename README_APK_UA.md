# APK PocketBridge Remote 2.3.1

Один APK підтримує LG Optimus One P500 (Android 2.3/API 10) і Redmi Note 9 Pro.

## Виправлено у 2.3.1

- порядок останнього руху та завершення перетягування у HTTP fallback;
- повторне надсилання `drag_end`, коли черга тимчасово переповнена;
- об’єднання realtime-рухів без втрати кліків і меж drag;
- клік по кадру завжди надсилається на монітор, з якого отриманий кадр;
- захист LG P500 від аварійного завершення при нестачі RAM для JPEG;
- безпечне завантаження через `.part` із перевіркою повного розміру;
- обмеження JSON-відповіді до 512 КіБ;
- автоматичне виправлення зарезервованих Windows-імен файлів;
- обробка непередбаченої помилки фонової команди без зависання UI;
- підтримка до 12 профілів панелей.

## Збірка

```text
GitHub → Actions → Build PocketBridge APK → Run workflow
```

Артефакт:

```text
PocketBridge-Remote-MultiDevice-v2.3.1
```

Установлення:

```powershell
adb install -r PocketBridge-Remote-MultiDevice-v2.3.1-debug.apk
```
