# AAPT resource linking fix

Виправлено помилку Android resource linking у `activity_native_core.xml`:

```xml
android:prompt="Профіль"
```

замінено на посилання на рядковий ресурс:

```xml
android:prompt="@string/profile_prompt"
```

До `res/values/strings.xml` додано:

```xml
<string name="profile_prompt">Профіль</string>
```

Причина: атрибут `android:prompt` типу `reference` не приймає текстовий літерал.
