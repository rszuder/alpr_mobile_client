# AKT54 — nowe cechy sceny po rozpoznaniu tablicy, FIX06

Następna poprawka: [FIX07 — obrys ramek bez przypisania](AKT54_PLATE_OUTLINE_FIX07_20261022.md). Po jej zastosowaniu bieżący stan sprawdza patch FIX07; historyczny patch FIX06 zachowuje własne dokładne bramki SHA.

Zastosowano zatwierdzony plan na lokalnym stanie po FIX05. **Reprodukcja i regresje JVM: PASS. Kompilacja testów Androida: PASS. Wykonanie na A12: NOT_RUN.** START i timer z FIX05 zachowano. Nie instalowano APK, nie uruchamiano pomiarów ani F4; brak commitu/pushu.

## Przyczyna i dowody

Potwierdzono lukę w obserwacji cech: dotychczasowe kanały śledzą punkty wybrane w **pierwotnej scenie**. Nie wykrywają pojawienia się nowych struktur, jeżeli stare punkty pozostają na miejscu, a luminancja nie przekracza progów. To potwierdzony mechanizm w kodzie, ale bez logów dokładnie z incydentu operatora nie stanowi rozstrzygnięcia przyczyny jego konkretnego zacięcia A12.

| Przypadek | Przed FIX06 | Po FIX06 | Weryfikacja |
|---|---|---|---|
| Niezmieniona tablica, nowe delikatne cechy tła | Brak granicy przez 12 obserwacji co 100 ms; `local=0`, `global=0` | Granica przy trzeciej zgodnej obserwacji, `static_scene_features_added` | Czerwony test uruchomiony przed zmianą, następnie zielony; dodatkowo wykonano sondę na źródłach z backupu |
| Cechy całej klatki | W pierwotnym obrazie 4 punkty, które pozostają poprawnie śledzone | 16 bieżących punktów, 12 bez odpowiednika w pierwotnej scenie, udział 0,75 | Deterministyczna klatka 200×200, tło 100, nowe narożniki 90/110, tablica 50/170 bez zmian |
| Brak cech w scenie początkowej | Kanał forward nie dostarcza dowodu zmiany | Nowy kanał może potwierdzić trwałe pojawienie się struktury | JVM PASS |
| Powrót starej sceny, naprzemienne tekstury, szum | Nie dodano do tych przypadków nowego powodu resetu | Kandydat jest zerowany lub rozpoczynany ponownie | JVM PASS |
| Wyjście z `STATIC_IDLE` i gotowość następnej klatki | Istniejąca ścieżka `HARD_RESET` | Ta sama ścieżka z nowym powodem i diagnostyką | Rozszerzony test Androida skompilowany; wykonanie NOT_RUN |

## Mechanizm

- Niezależny selektor całej klatki działa przy `watch=plates`, `vehicles` i `global`. Używa istniejącego wyboru narożników w siatce 4×4, maksymalnie 16 punktów zweryfikowanych przez tracker na własnej klatce. Nie tworzy obiektów ALPR ani nowych ROI.
- Bieżące punkty porównywane są z **niezmienną pierwotną sceną**. Dopasowanie używa korelacji ZNCC fragmentów 9×9, wyszukiwania ±2 px, progu 0,85 i minimalnego odchylenia luminancji 1 w skali 8-bitowej. Normalizacja ogranicza reakcję na samą ekspozycję lub kontrast.
- Nowość wymaga co najmniej 4 punktów, co najmniej 3 bez dopasowania i udziału niedopasowanych ≥65%.
- Pierwsza nowa struktura tworzy kandydata z **własną kopią klatki**. Kolejne obserwacje muszą mieć ≥65% punktów i co najmniej 3 punkty zgodne z tą pierwszą klatką. Kandydat nie zastępuje pierwotnej referencji ani nie przesuwa się wraz z kolejnymi klatkami.
- Wybudzenie wymaga 3 kolejnych kwalifikujących się obserwacji obejmujących co najmniej 120 ms. Zachowano kadencję kontroli cech co najmniej 100 ms i brak zerowania po samym odstępie >500 ms.
- Powrót starej sceny zeruje kandydata. Inna nowa struktura lub cofnięcie czasu rozpoczyna potwierdzanie od nowa. Reset i zmiana rozmiaru klatki czyszczą stan; zoom ma oddzielny watcher i referencję.
- Dotychczasowe kanały luminancji, ruchu i utraty punktów oraz ich progi i priorytety pozostają bez zmian. `HARD_RESET`, inkrementacja generacji, odrzucanie starego stampu i otwarcie następnej klatki nie zostały przebudowane.

`Result` oraz ograniczone częstotliwościowo logi DEBUG `ALPR_STATIC` zawierają `added_points`, `added_unmatched`, `added_persistence`, `added_confirmations`. Zdarzenie `static_scene_boundary` otrzymało odpowiadające im opcjonalne pola `added_feature_*`; format v1 i istniejące archiwa pozostają bez zmian. Pierwsza próbka kandydata ma `added_persistence=0`: nie jest jeszcze potwierdzeniem czasowym.

## Walidacja i granice

Wykonano:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --console=plain
```

**PASS: 809 testów JVM, 161 zestawów, 0 failures, 0 errors, 0 skipped.** Nowy zestaw ma 22 testy. Obejmuje reprodukcję, wszystkie trzy rodzaje watch, referencję bez cech, nieregularne odstępy, szybkie callbacki, cofnięcie czasu, powrót starej sceny, naprzemienne tekstury, ponowne użycie bufora, późne odkrycie ROI bez przyspieszania potwierdzeń, reset oraz izolację zoomu. Przypadki negatywne obejmują nieruchomy obraz, ekspozycję +30, zmianę kontrastu tła 95/105 → 90/110, jitter poniżej istniejącego progu ruchu, zmienny szum oraz niewielką lokalną zmianę tła. Dopasowanie ZNCC sprawdzono także dla czterech kolejnych rozmyć filtrem 5×5 i wyszukiwania przesunięcia 2 px.

Testy ujawniły i zachowały dwie wcześniejsze granice forward flow: przesunięcie 2 px w obu osiach (około 2,83 px, powyżej progu ruchu 2,5 px) oraz czterokrotne silne rozmycie syntetycznego tła powodują reset starymi powodami. Potwierdzono to również na źródłach **sprzed FIX06**. Nowy kanał ma w tych przypadkach zero potwierdzeń. Nie obiecuje się braku wszystkich wybudzeń przy dowolnym rozmyciu lub drganiach; end-to-end testy bez wybudzeń obejmują trzy kolejne rozmycia/wyostrzenia i jitter poniżej dotychczasowego progu. Fragmenty o odchyleniu poniżej 1 nie stanowią dowodu zgodności struktury.

**Kompilacja wszystkich testów Androida: PASS. Wykonanie: NOT_RUN.** Dodany przypadek sprawdza nowy powód granicy, pojedynczy wzrost generacji, opuszczenie IDLE, gotowość następnej klatki, odrzucenie starego wyniku/stampu i brak pętli resetów. Nie wykonuje inferencji modeli. Kompilacja nie potwierdza rzeczywistego opóźnienia MT ani niezawodności na A12.

`git diff --check`: PASS. Patch zweryfikowano pod względem dokładnego zastosowania, backupów, idempotencji oraz odrzucenia obcego źródła i naruszonego warunku wstępnego bez zapisu. Wyniki i SHA zawiera plik poniżej. Nie zmieniano modeli, conf/IoU, GT, masek/ROI, zabezpieczeń MZ INT8 ani danych badawczych.

## Artefakty i kontynuacja

- [Patch FIX06](patches/patch_android_static_feature_addition_fix06_20261022.py), wejście: dokładny lokalny stan po FIX05.
- [Diff](patches/STATIC_FEATURE_ADDITION_FIX06_20261022.diff), [SHA przed/po, warunki wstępne, backup i wyniki kontroli](patches/STATIC_FEATURE_ADDITION_FIX06_20261022_sha256.json).
- [Poprzedni raport FIX05](AKT54_TIMER_STABILITY_FIX05_20261022.md).

Poprawka jest już zastosowana w tym checkoutcie. Kontrola wyłącznie do odczytu:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_static_feature_addition_fix06_20261022.py" --check
```

Zastosowanie w zgodnej kopii stanu wejściowego tworzy backup:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_static_feature_addition_fix06_20261022.py" --apply
```

Następny krok operatora: po kompilacji i instalacji w Android Studio krótki techniczny test zmiany 2–3 obrazów po rozpoznaniu tablicy oraz pozostawienia jednego obrazu nieruchomo, z logami `ALPR_STATIC`. Pomiar liczyć od rzeczywistej zmiany obrazu. Nie uruchamiano ani nie zleca się ponownej kampanii F4.
