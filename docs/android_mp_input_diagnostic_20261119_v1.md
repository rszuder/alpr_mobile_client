# Rzeczywiste wejście i wynik MP — v5.12 diagnostic

Zbudowano i zainstalowano kompilację z jawnym, jednorazowym zapisem MP. Dwie próbki z A12 mają pełne dane i zgodne SHA-256. W obu tensor wejściowy odpowiada zapisanemu obrazowi po przygotowaniu do modelu, a wszystkie wyniki klas są poniżej obowiązującego progu 0,25. Brak detekcji w tych próbkach wynika z niskich wartości już przed dekoderem. Przyczyna niskich wartości modelu pozostaje nieustalona; nie jest to naprawa ani pełny odbiór dynamicznego ALPR.

## Kompilacja i zakres

- APK: `alpr-dynamic-continuity-20261118-v5.12-diagnostic.apk`, SHA-256 `7e9999545517a1f14e7bfae66c7e8493e33461f44a875b9299724f9b12deb909`.
- `release-v5.12-diagnostic`: **62/62 PASS**, `assembleDebug` i `compileDebugAndroidTestJavaWithJavac` PASS. Kompilacja testów Android nie oznacza ich wykonania na A12.
- Dokładna baza v5.11 zabezpieczona. Zmieniono trzy istniejące pliki produkcyjne i jeden test; dodano klasę zapisu oraz jej test. Pozostałe 966 istniejących źródeł zachowano; aktualna pieczęć obejmuje 972 pliki.
- Modele, progi, konfiguracja i decyzje dekodera pozostają bez zmian. Zapis jest wyłączony przy zwykłym uruchomieniu; włącza go wyłącznie debugowy parametr `debug_mp_capture` z nowym identyfikatorem żądania.

## Co zapisano

Każdy zestaw zawiera `source.png` (bitmapa przekazana do przygotowania wejścia), `letterbox.png` (piksele obrazu po letterboxie), `input.bin` (dokładne bajty przed `backend.run`), `output-0.bin` (dokładne bajty przed dekodowaniem) oraz `capture.json` z identyfikatorem klatki, źródłowym czasem CameraX, modelem, kształtami, parametrami i SHA-256.

Użyto istniejącego obserwatora przygotowania tensora. Obrazy i bufory są zapisywane strumieniowo; buforów nie przewija się ani nie zmienia na potrzeby zapisu. Jedno żądanie może zapisać jedną klatkę, z limitem zestawu 20 MiB. Błędy zapisu oznaczają niekompletne dane diagnostyczne i nie zastępują wyniku modelu. Testy obejmują m.in. mutację bufora przez syntetyczny backend po wykonaniu kopii, zachowanie pozycji/limitów, limit bajtów oraz ochronę istniejących zestawów.

Czas I/O jest oznaczony osobno i usuwany z pomiarów etapów PRE/POST tam, gdzie występuje wewnątrz etapu. Cały przebieg z zapisem nie jest pomiarem wydajności ani płynności — kompresja PNG i przesyłanie danych wydłużają obsługę klatki.

## Wyniki rzeczywistych próbek

Model `vehicle-yolo26n`, fingerprint `ca73c8919b1b22d6`, NCNN/CPU, FLOAT32. Wejście `[1,3,640,640]`, wyjście `[1,84,8400]`, RGB/NCHW. Źródło ma 1080 × 1440 px, skala letterboxu 0,44444445 i marginesy poziome po 80 px. Obrazy obejrzano; pojazd jest obecny na rzeczywistym wejściu, z widocznymi pasami charakterystycznymi dla rejestracji ekranu. Wpływu tych pasów na wynik modelu nie ustalono.

| Próbka | Maksymalny wynik klasy car | Maksimum dowolnej klasy | Próg | Detekcje po dekodowaniu |
| --- | ---: | ---: | ---: | ---: |
| `mp-v512-20261119-01` | 0,0673383 | 0,0872954 | 0,25 | 0 |
| `mp-v512-20261119-02` | 0,1138135 | 0,1138135 | 0,25 | 0 |

W każdej próbce zweryfikowano wszystkie 1 228 800 elementów wejścia względem zapisanych pikseli, kolejności kanałów i skali; liczba rozbieżnych elementów i maksymalny błąd wynoszą zero. Wyjścia mają po 705 600 skończonych wartości, bez NaN/Inf. Żadna z 8400 propozycji nie przekracza progu dla żadnej klasy, więc NMS ani filtr klas pojazdów nie mogły przywrócić detekcji. Jest to ustalenie dla tych dwóch próbek, nie dowód poprawności modelu względem referencyjnego backendu czy danych drogowych.

Dowody znajdują się w `app/build/android_dynamic_continuity_20261118_v5/device/mp-input-v5.12-diagnostic-01/` i `mp-input-v5.12-diagnostic-02/`. Każdy folder ma `capture/`, archiwum, analizę `tensor_analysis.json` oraz zapis sterowania. Szczególnie użyteczne są drugi `capture/letterbox.png`, `capture/input.bin`, `capture/output-0.bin` i `capture/capture.json`.

## Błąd pierwszego kontrolera i sprawdzona poprawka

Pierwszy kontroler uznał pustą odpowiedź ADB przed powstaniem pliku za błąd JSON. Następnie próbował zatrzymać aplikację jeszcze podczas przygotowania; wygląd przycisku był UNKNOWN i nie wysłano STOP. Kontroler zakończył pracę, a analiza pozostała aktywna. Jej późniejszy STOP potwierdzono o 12:54:57. Ten przebieg ma **NO_GO dla procedury sterowania**, mimo prawidłowej pierwszej próbki MP: zapis został zakończony około 12:48:04 i później odzyskany z identycznymi sumami plików. Oryginalny błędny zapis procedury zachowano, a odzyskanie oznaczono oddzielnie w `capture_recovery.json`; nie przedstawia się go jako udanego testu czasowego.

Poprawka traktuje pustą lub niegotową odpowiedź jako oczekiwanie. Przy zatrzymywaniu czeka na rozpoznawalny stan przycisku; nie uznaje białego START za koniec rozpoczętego przygotowania bez potwierdzonego wykonania. Zabezpieczenie ma granicę czasu i awaryjnie zatrzymuje pierwotną aplikację, zapisując to jako błąd sterowania. Nie dotyka innego procesu uruchomionego w międzyczasie. Kontroler przeszedł **5/5 testów syntetycznych sterowania**, odrębnych od testów modeli.

Drugi rzeczywisty przebieg: START 13:02:30, potwierdzony automatyczny STOP 13:02:49, około 19 s. Stan `CAPTURE_COMPLETE`, przycisk STOP rozpoznany, jedno normalne żądanie STOP, bez awaryjnego zakończenia. Mirroring nieaktywny; dwa pomiary RAM przed START: 1 796 176 i 1 806 636 KiB.

## Zachowanie danych i stan końcowy

Aktualizacja zachowała wszystkie 436 plików objętych kontrolą przed instalacją, w tym 5 plików preferencji i 335 pierwotnych plików cropów. Po zapisach potwierdzono zgodność 38 plików modeli. Archiwa przed/po obu kontrolach zawierają te same **839 identyfikowalnych obserwacji**, bez usuniętych lub zmienionych wcześniejszych identyfikatorów; nowych odczytów MZ nie było.

Próby nie są oceną fizycznej płynności, GT OCR ani odbiorem drogowym. Wcześniejsza blokada MT/MZ po utracie świeżego MP oraz problem niskich wyników MP pozostają osobnymi otwartymi zagadnieniami. Po diagnozie analiza jest zatrzymana; aplikację przywrócono do zwykłego uruchomienia bez parametru zapisu.
