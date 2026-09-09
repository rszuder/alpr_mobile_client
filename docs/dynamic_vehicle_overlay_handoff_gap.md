# DYNAMIC: przerwa między lokalną a globalną geometrią

2026-09-09, logi z telefonu R58R346GZYW:

- 19:25:59.384–19:26:00.271: trzy pojazdy, źródło GLOBAL_FRAME_MOTION.
- 19:26:00.568: zero pojazdów, źródło LOCAL_KLT, mimo dostępnej transformacji
  diagnostycznej `dx=0.00297`.
- 19:26:01.012: ponownie trzy pojazdy z GLOBAL_FRAME_MOTION.

W tym fragmencie nie nastąpiła zmiana encji ani twardy reset sceny. Wersja na
telefonie miała czas instalacji 19:07:39, sprzed osobnej poprawki MT→MZ.

`dynamicCameraMotionOverlayItems` dopuszczał świeżą geometrię globalną tylko,
gdy tracker czekał na kotwicę MP lub pierwszą klatkę lokalną. Pusty wynik lokalny
albo callback bez takiego wyniku pomijał ją po zakończeniu tego stanu. Projector
odrzucał wtedy starsze kandydatury MP (limit podglądu w ruchu: do 1,5 s), choć
świeża geometria podglądu nadal pozwalała wyświetlić pojazdy. Inferencje telefonu
trwały około 4 s; logi potwierdziły także czekanie monitorów podglądu na pipeline.

Poprawka usuwa zależność dostępności globalnego ruchu od stanu kotwiczenia.
Każdy aktualny wynik lokalny zachowuje pierwszeństwo dla własnej encji;
geometrycznie skompensowany wynik globalny uzupełnia tylko brakujące encje.
Nadal wymagane są ważna transformacja, świeżość geometrii oraz aktualny kontekst
sceny i kamery. Nie zmieniono progów życia encji ani czasu wyświetlania starych
ramek. Blokad synchronizacji inferencji nie zmieniano w tej poprawce.

Testy regresji obejmują lokalny miss przy czterosekundowym wieku MP, brak jednej
ramki przy prawidłowym ruchu i zmianie rozmiaru pozostałych oraz wygaśnięcie bez
świeżego dowodu geometrii. Weryfikacja ruchu fizycznego telefonu wymaga nowego APK.

Wynik weryfikacji: 636/636 testów JVM oraz 52/52 testy instrumentacyjne
EntityOverlayMotionProjector i DetectionOverlayView na emulatorze Pixel 3.
APK debug zbudowane, `git diff --check` poprawne. Telefon nie był przeinstalowywany.
