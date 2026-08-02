# PocketBridge 1.4.0 — WebView Token Bridge Hotfix

Виправлено відкриття повного та Lite UI з Android 2.3 WebView:

- токен передається через query-параметр як сумісний fallback;
- Android після завантаження сторінки безпечно передає збережений device token у JavaScript;
- повний UI і P500 Lite UI мають `PocketBridgeSetToken` для автоматичної авторизації;
- токен зберігається у localStorage та одразу запускає завантаження конфігурації;
- ручне введення токена більше не повинно бути потрібне після відкриття UI з APK.
