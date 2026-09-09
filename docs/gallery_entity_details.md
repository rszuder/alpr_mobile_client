# Szczegóły odczytu: karty encji i filtr MZ

Widok szczegółów ma stały nagłówek z cropem tablicy (canvas 90 dp), ramkami znaków,
numerem oraz podsumowaniem. Przewijana jest wyłącznie dolna część, dzięki czemu
tablica pozostaje widoczna podczas przeglądania obserwacji.

`RecognitionEntityPager` grupuje obserwacje według sceny i encji. Wykrycia bez
encji rozdziela także według tracka tablicy. Poziomy RecyclerView z PagerSnapHelper
przełącza karty gestem, a badge P… pozwalają wybrać konkretną encję. Badge aktywnej
karty ma wypełnienie i stan selected. Obserwacje danej encji przewijają się pionowo.

Każda obserwacja ma odrębne pola: wynik i pewności, MP/MT/MZ i potok, FPS,
temperatura baterii, CPU aplikacji, rozdzielczość, źródło, warianty i backendy
modeli, track pojazdu i tablicy, scena, epoka, transformacja i numer klatki.
Starsze wpisy bez metadanych obserwacji otrzymują kartę archiwalną z czasami.
Akcje kopiowania, zapisu i usunięcia pozostają dostępne w stałej stopce.

`PlateObservation.hasFreshMzRead()` wymaga wykonanego MZ i niepustego
`freshPrediction`. Ten warunek jest sprawdzany przed kopiowaniem cropa przez
`collectCrops` oraz przed zmianą historii w `upsertObservation`. Pusty odczyt,
same białe znaki lub przeniesiony konsensus bez bieżącej inferencji nie tworzą
obrazu ani nowej obserwacji. Nie wymaga się potwierdzonego konsensusu, więc
niepusty odczyt wstępny nadal może trafić do galerii.

Testy sprawdzają wybór badge, gest poziomy, niezmienną pozycję cropa przy
przewijaniu pionowym, dostępność telemetrii oraz odmowę zapisu pustych odczytów
na obu ścieżkach zbierania. Istniejące wpisy galerii nie są usuwane.

Weryfikacja: 636/636 testów JVM, 15 testów instrumentacyjnych galerii, historii
i ustawień zakończonych powodzeniem (dwa dostosowane testy powtórzone osobno).
Ponadto ponowiony test przewijania potwierdził dodatnie scrollY zawartości oraz
niezmienną pozycję cropa. Sprawdzono zrzuty początku karty i przewiniętej części
z zasobami/modelami. APK debug zbudowane; telefon nie był przeinstalowywany.
