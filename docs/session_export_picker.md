# Czytelny wybór sesji eksperymentalnej do eksportu

Lista archiwów `.alprsession` pokazuje osobne karty zamiast technicznych nazw plików.
Każda karta zawiera lokalną datę i godzinę rozpoczęcia, czas trwania, typ i wariant
eksperymentu, tryb analizy, rozdzielczość kamery, profil, konfigurację MP/MT/MZ,
flagi wykonania, serię, scenariusz, numer powtórzenia i status kompletności.
Cała karta wybiera przypisany do niej plik do dotychczasowego eksportu przez SAF.

`ResearchSessionSummary` odczytuje zapisane metadane sesji na wątku roboczym.
Jeżeli plik towarzyszący jest niedostępny lub uszkodzony, odczytuje `session.json`
z archiwum. Ustawienia bieżącej aplikacji nie uczestniczą w budowaniu opisu.
Sesje są sortowane malejąco według daty startu, a nie daty modyfikacji pliku.
Brak startu pozwala pokazać wyraźnie opisaną datę utworzenia; brak obu dat daje
„Data nieznana”.

Czas trwania jest różnicą zapisanych zegarów monotonicznych. Dla kompletnej
starszej sesji bez tych pól dopuszczony jest czas ścienny oznaczony „≈”.
Sesja odzyskana po przerwaniu procesu bez czasu zakończenia ma „Czas nieustalony”:
data odzyskania pliku nie jest końcem pomiaru. Odczyt nie modyfikuje archiwów.

Walidacja na Samsung SM-A125F: 570 testów JVM oraz 28 testów Android obejmujących
picker, magazyn sesji i format archiwum. Sprawdzono daty, zegar monotoniczny,
konfigurację zamrożoną przy starcie, odzyskiwanie metadanych z ZIP, sortowanie,
zawijanie opisów i wybór właściwego pliku. Oceniono wygląd kart pięciu rzeczywistych
sesji, w tym przerwanej; ich zawartość pozostała identyczna z kopią przed aktualizacją.
Raporty i zrzuty: `app/build/reports/session-export-picker/` (ignorowane przez Git).
