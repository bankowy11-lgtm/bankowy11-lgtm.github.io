# bankowy11-lgtm.github.io

Główna strona domeny. Najważniejszy plik: `.well-known/assetlinks.json` — potwierdza Androidowi, że aplikacje z Google Play (TWA) należą do tej strony, więc otwierają się bez paska adresu.

Każda nowa aplikacja dopisuje tu swój wpis: nazwa pakietu + odciski SHA-256 (klucz wysyłania ORAZ klucz Play App Signing z Play Console).
`.nojekyll` jest potrzebny, żeby GitHub Pages nie ukrywał folderu `.well-known`.
