# Contact Pro – pakiet startowy do Android Studio

To jest pierwszy prototyp źródłowy aplikacji Contact Pro. Zawiera kod ekranu, odczyt kontaktów, zdjęcia, wyszukiwarkę i szybkie wybieranie numeru.

## Co działa w tej wersji
- ciemny interfejs Premium,
- prośba o dostęp do kontaktów,
- odczyt kontaktów widocznych w systemowym Android Contacts Provider (w tym kontaktów kont Google zsynchronizowanych z telefonem),
- zdjęcia kontaktów lub inicjały,
- wyszukiwarka nazw i numerów,
- lista ulubionych oparta o gwiazdkę kontaktu w Androidzie,
- otwarcie systemowego dialera z numerem,
- otwarcie systemowego formularza dodawania kontaktu.

## Wymagania
- Android Studio (stabilna wersja),
- JDK 17,
- Android SDK Platform 36,
- telefon z Androidem 8.0 lub nowszym albo emulator.

## Najprostsze uruchomienie dla początkującego
Ten pakiet zawiera pliki źródłowe i konfigurację aplikacji, ale nie zawiera binarnego pliku Gradle Wrapper. Najpewniejsza droga to utworzenie szkieletu projektu w Android Studio:

1. Rozpakuj archiwum ZIP.
2. Otwórz Android Studio i wybierz **New Project**.
3. Wybierz szablon **Empty Activity** (nie Empty Views Activity).
4. Ustaw:
   - Name: `Contact Pro`
   - Package name: `pl.contactpro.app`
   - Language: `Kotlin`
   - Minimum SDK: `API 26`
   - zaznacz użycie Jetpack Compose, jeśli kreator pokazuje tę opcję.
5. Kliknij **Finish** i poczekaj, aż Android Studio zakończy synchronizację.
6. W rozpakowanym pakiecie otwórz `app/src/main/java/pl/contactpro/app/MainActivity.kt`. Skopiuj cały plik i wklej go w miejsce `MainActivity.kt` utworzonego przez Android Studio.
7. W pliku `app/build.gradle.kts` projektu Android Studio dopisz w bloku `dependencies`:
   `implementation("androidx.compose.material:material-icons-extended")`
   Jeśli projekt nie ma jeszcze Compose Material 3, dodaj również:
   `implementation("androidx.compose.material3:material3")`
   Nie usuwaj zależności Compose wygenerowanych przez kreator.
8. Otwórz plik `app/src/main/AndroidManifest.xml` i dodaj bezpośrednio pod znacznikiem `<manifest ...>`:
   `<uses-permission android:name="android.permission.READ_CONTACTS" />`
   Zachowaj istniejący wpis `<application>` i aktywność z kreatora.
9. Sprawdź, czy w `MainActivity.kt` pierwsza linia `package` to `package pl.contactpro.app`. Jeśli wybrałeś inny pakiet, zmień tę linię na pakiet swojego projektu.
10. Podłącz telefon przez USB z włączonym Debugowaniem USB albo uruchom emulator.
11. Naciśnij zielony przycisk **Run**.
12. Przy pierwszym uruchomieniu aplikacji wybierz **Zezwól na dostęp do kontaktów**.

## Budowanie APK
W Android Studio wybierz **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Po zakończeniu kliknij **locate** w komunikacie. Plik debug APK będzie zwykle w:
`app/build/outputs/apk/debug/app-debug.apk`

## Ważne ograniczenia prototypu
- To pierwszy prototyp: nie edytuje ani nie usuwa istniejących kontaktów. Dodawanie otwiera systemowy formularz Androida.
- Kontakty Google pojawią się, jeśli konto Google jest dodane do telefonu i synchronizacja kontaktów jest włączona.
- Zakładki Historia i Więcej są na razie wizualnymi miejscami na kolejne funkcje.
- Połączenie otwiera systemowy dialer; użytkownik sam zatwierdza połączenie.
- Aplikacja nie wysyła książki adresowej do internetu. Nie ma backendu ani własnej synchronizacji z chmurą.

## Pakiet aplikacji
`pl.contactpro.app`
