# F6 Android — pełna ramka i korekta narożnika, 14.11.2026, v1

Naprawiono pusty szkic po odrzuceniu niezgodnego zapisu F6, aktywne pozornie strzałki oraz pozostawianie w lupie obrazu poprzedniego rogu. **Dwa celowane testy hostowe: 2/2 PASS; kompilacja instrumentacji i debug APK offline: PASS. Nowej APK nie instalowano. UI A12 / geometria TV i F6 / odbiór pięciu zdjęć: NOT_VERIFIED; FINAL176: NO_GO.**

Repo: `C:\Users\48572\AndroidStudioProjects\ALPR_v1`; gałąź `main`; HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`. Zastany katalog był zmodyfikowany. Nie wykonywano commit/push, reset/clean/stash, instalacji zależności ani operacji w Desktopie.

## Przyczyna potwierdzona odczytem i regresją

Odczyt ADB był wyłącznie diagnostyczny: bez sterowania UI, START/STOP, uruchamiania kamery, force-stop, instalacji lub zmiany danych. Zachowano preferencje, oba rotowane logi aplikacji, logcat i screenshot operatora w `device_readonly/` nowej dostawy.

| Objaw | Potwierdzona przyczyna | Poprawka i sprawdzenie |
|---|---|---|
| Jeden róg zamiast ramki | `seedCorners(false)` po wyjątku z odczytu starego F6 wykonywało `setCorners(emptyList())`, a następnie dopuszczało edycję. | Po braku zgodnego zapisu powstają cztery punkty szkicu wewnątrz aktualnego, zatwierdzonego TV. Test przed poprawką: oczekiwane 4, rzeczywiste 0. Po poprawce test przechodzi. |
| Fine tuning nie przesuwa punktu | PG na screenie miał `X — / Y —`: punkt nie istniał. `canNudge()` nie sprawdzało tego warunku, choć sam sterownik ruchu słusznie odmawiał przesunięcia nieistniejącego punktu. | Brak punktu wyłącza cztery strzałki i krok; pojawia się instrukcja wskazania punktu. Test używa rzeczywistych przycisków i sterownika aplikacji. |
| Lupa pokazuje poprzedni róg | `refreshMagnifier()` dla pustego punktu pomijało aktualizację, bez usunięcia wcześniejszego obrazu. | Pusty punkt kończy kopiowanie, usuwa obraz poprzedniego punktu i pokazuje neutralne tło. Test sprawdza też brak późniejszych kopii/ruchu. |

W zabezpieczonych danych A12 zapis TV ma źródło **1080×1440**, a zachowane F6 **960×1280**, bbox `[151,461,773,845]`. TV digest: `d2a88fc5b6e96a66f9ac23fc83667658fd420f712a21f030ac6fa7aed82b7f70`; ROI digest: `a40bf9cb1b7065ddcca9661fa1a193d169cf7fc27dd98fe8e7aa48477fd51a50`; zapisany w ROI TV-crop digest: `59988d176b155bd33c85c4338617c8bf8673b0b5a974f0ebfbd5f38dcb6f7eb2`.

Log `2026-11-14T03:16:57.704+01:00` zawiera `photo_approval_rejected` i przyczynę `TV_APPROVAL_CHANGED`; o `03:16:57.718` następuje `photo_draft_unavailable`, `F6_PHOTO_ROI_REQUIRED_OR_STALE`, `draft_corners_norm=[]`. Odrzucenie starego zapisu było prawidłowe; obsługa szkicu i kontrolek była błędna. Nie ma podstaw, aby przedstawiać stare pole jako aktualne. Jego rekord pozostał w preferencjach. Pojedynczy, niezapisany punkt z ekranu operatora nie jest zapisem trwałym; zabezpieczono jego screenshot, nie deklaruje się odzyskania szkicu z RAM.

## Zakres zmiany

- `setup/TvCornerDraft.java`: szkic F6 w istniejącym układzie źródłowym. Każdy punkt to `centroid TV + 0,6 × (punkt TV − centroid TV)`, z zachowaniem kolejności i istniejącej walidacji. To pomoc do ręcznej edycji, nie rozpoznanie pola fotografii ani zatwierdzona kalibracja.
- `ui/TvSetupController.java`: zgodny zapis nadal odtwarza dokładne punkty. Przy braku/niezgodności powstaje jeden kompletny szkic z komunikatem **„Dopasuj ramkę i zapisz”**. Odświeżanie, wybór punktu i lupa go nie inicjalizują ponownie. Jeśli nie można utworzyć poprawnego, widocznego szkicu, pozostaje jawny błąd `DRAFT_NOT_VISIBLE`. Dodano warunek istnienia punktu do dostępności strzałek.
- `ui/TvCalibrationOverlayView.java`: usunięcie nieaktualnej lupy dla pustego punktu; brak zasłaniania wtedy podglądu pustym buforem. Wykorzystano istniejące rysowanie zamkniętego czworokąta i osobnej żółtej obwiedni MT.
- `res/values/tv_calibration_precision_strings.xml`: dwa komunikaty powyższych stanów.
- `test/.../ui/TvSetupUiTest.java`: dwie celowane regresje, opisane niżej.

Ścieżki powyżej są względem `app/src/main/java/com/example/alpr_v1`, poza zasobami i testem. Pełne ścieżki i SHA są w `audit/changed-files.json`, a diff wyłącznie tego zadania w `audit/task-only.patch`. Ten raport jest jedynym nowym plikiem dokumentacji zadania.

Zapis TV/F6 nadal jest osobny. Szkic nie zmienia preferencji do świadomego „Zapisz pole fotografii”; anulowanie zachowuje poprzednie rekordy. Nie poluzowano `F6_PHOTO_ROI_REQUIRED_OR_STALE`, digestów ani walidacji źródła. Nie zmieniono mapowania kamery, AF, modeli, liczby wątków, wejść MT 512×512 / MZ 320×320, R0/MP OFF, schedulera, formatu eksportu ani reguły ocenianej klatki. Nie tworzono nowych sesji ani pakietu 4100 klatek.

## Wykonane kontrole

1. `stalePhotoStartsOneCompleteInnerDraftAndKeepsApprovalsUntilSave`: produkcyjny zapis TV/F6 w 960×1280, osobne zatwierdzenie TV w 1080×1440, zachowany stary rekord F6, cztery nowe punkty wewnętrzne, 60 aktualizacji bez dotyku, wybór PG/8×, brak zapisów do kluczy zatwierdzeń, anulowanie, jawny zapis i dokładne odtworzenie nowego pola. **PASS**.
2. `frameArrowsMoveSourcePointAndLoupePixelsAndMissingPointCannotNudge`: rzeczywisty XML/kontroler i Native Canvas; cztery boki ramki, rzeczywiste przyciski 1 i 5 px źródła, zmiana tylko aktywnego punktu, zmiana pikseli pod nieruchomym celownikiem z pustym środkiem, 8× bez przesunięcia, DOWN/UP bez skoku, zatrzymanie powtarzania przy zmianie rogu, pusty PG bez aktywnych strzałek i bez starego obrazu/kopii, brak zapisu F6 i zmian TV. **PASS**. Tylko pozyskanie klatki jest syntetyczne.

Przed poprawką pierwsza regresja zakończyła się rzeczywistym FAIL: `expected:<4> but was:<0>`, zachowanym w `audit/baseline-TvSetupUiTest.xml`. Pierwszy przebieg po poprawce miał 1 PASS / 1 FAIL: kontrola rastra wymagała koloru ramki dokładnie w połowie każdego boku, gdzie etykieta narożnika może zasłaniać krótki bok. Test poprawiono na kontrolę kilku odcinków każdego boku, pozostawiając wymóg wszystkich czterech boków. Końcowy render sprawdzono wizualnie. Produkcji między tymi dwoma przebiegami nie zmieniano. Wynik końcowy to **dwie unikalne regresje, 2 PASS / 0 FAIL / 0 SKIP**; nie sumuje się ich z przebiegami wcześniejszymi.

Końcowa komenda (JBR Android Studio, bez sieci):

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --offline -I app/build/f6_corner_frame_nudge_20261114_v1/audit/two-checks.gradle `
  :app:testDebugUnitTest `
  --tests '*TvSetupUiTest.stalePhotoStartsOneCompleteInnerDraftAndKeepsApprovalsUntilSave' `
  --tests '*TvSetupUiTest.frameArrowsMoveSourcePointAndLoupePixelsAndMissingPointCannotNudge' `
  :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebug --console=plain
```

Log: `audit/two-checks-and-build-2.log`; XML: `audit/final-results/`; podsumowanie: `audit/final-test-summary.json`. Instrumentację **skompilowano, nie uruchomiono**. Nie wykonywano pełnej regresji JVM ani prób dotykowych na A12. Obrazy `synthetic-host-ui/39-corner-frame-tv-synthetic.png` i `40-corner-frame-f6-synthetic.png` są renderami aplikacyjnego XML w Robolectric z syntetycznym/nieobecnym źródłem kamery, **nie screenshotami nowej APK na telefonie**.

Kontrola ochrony: **7100 plików wcześniejszych dostaw bez zmiany SHA, rozmiaru i mtime** (`audit/protected-deliveries-check.json`). Wszystkie 14 wpisów `assets/` i `lib/` nowej APK są identyczne bajtowo z poprzednią pełną dostawą camera_geometry_focus_v1. Porównanie całego odczytanego zestawu preferencji A12 przed/po: identyczne wartości, zero zmienionych kluczy. Nie wykonano zmian danych telefonu.

## Strumienie A12 i granica potwierdzenia

Żądanie zapisane na telefonie: **1440×1080**. Odczyt systemowy potwierdza ostatnie rzeczywiste strumienie aplikacji: **Preview 960×720 PRIVATE (0x22), ImageAnalysis 1440×1080 YUV (0x23)**, oba 4:3. Zapis TV i log edytora mają pełne źródło po obrocie **1080×1440**, crop `[0,0,1080,1440]`.

W chwili końcowego odczytu `Active Camera Clients: []`; podane rozmiary są z sekcji **„Dumpsys from previous open session”**, nie z nowo uruchomionej kamery. Nie włączano jej na potrzeby audytu. Dowody: `device_readonly/camera-service-current.txt`, `stream-identity.json`, `calibration-before.json` i właściwe `alpr.log-before` / `alpr.log.1-before`. Zgodne proporcje i znane wymiary nie stanowią odbioru geometrii na TV. Layout PC `final_f2_v3_marker_clearance` pozostaje informacją od operatora/Desktopu, bez nowej próby stanowiska w tym zadaniu.

## Nowa APK i przekazanie

**Dostawa:** `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_corner_frame_nudge_20261114_v1\`.

**APK:** `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_corner_frame_nudge_20261114_v1\alpr-f6-corner-frame-nudge-debug.apk`.

- Rozmiar: **86 624 972 B**.
- SHA-256: **`c72643eab04456a50ca5dfcc418c1d1c0a10cf59b886afa04162d86f1d06fc5f`**.
- `BUILT_AT_UTC=2026-11-14T02:42:51.352118200Z`; package `com.example.alpr_v1`; versionCode `1`, versionName `1.0`; `GIT_DIRTY=true`. Tożsamość buildu potwierdzono również w DEX dostarczonej APK.
- Podpis APK v2 PASS; certyfikat SHA-256 `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`, zgodny z poprzednią dostawą i zachowaną identyfikacją APK A12.
- Na A12 nadal odczytano starszą APK o SHA `e5d513edcc3a496ffbb89917794375f337516f34eca06b9e4f5e78461284cb26`. **Nie zawiera ona tej poprawki. Nowej nie instalowano.**

Pełna dokumentacja zabezpieczenia i diff są w dostawie. `SHA256SUMS.json`, `review_package.zip` i `READY.json` powstają po zakończeniu kontroli; gotowość paczki nie oznacza GO eksperymentu. Producenta danych nie zmieniano, więc nie dostarcza się nowego archiwum sesji udającego materiał z urządzenia.

Po aktualizacji przez operatora bez odinstalowywania i czyszczenia danych: plansza TEST_ONLY na PC → sprawdzenie kalibracji całego TV dla rzeczywistego źródła → Pole fotografii F6 → dopasowanie **czterech narożników** nowej ramki → strzałki 1/5 px, lupa 4×/8× → „Zapisz pole fotografii” → ponowne otwarcie. Nowa ramka jest punktem wyjścia; trzeba objąć całe pole zdjęć i sprawdzić żółty prostokąt względem kodów. Nie należy zatwierdzać punktów leżących poza TV ani zakładać wykluczenia kodów po samym zapisie.

| Status | Wynik |
|---|---|
| ANDROID_HOST_TESTS | PASS — 2/2 celowane regresje |
| OFFLINE_DEBUG_BUILD / INSTRUMENTATION_COMPILE | PASS / PASS |
| PREVIOUS_DELIVERIES / PHONE_PREFERENCES | PRESERVED |
| UI_A12_LIFECYCLE — nowa ramka, strzałki, lupa | NOT_VERIFIED |
| A12_TV_TECHNICAL — geometria, brak kodów w MT, AF, pięć zdjęć | NOT_VERIFIED |
| EXPERIMENT_START / FINAL176 | NO_GO — wymaga wspólnego rzeczywistego odbioru z operatorem |

Nie uruchomiono kolejnych testów urządzeniowych, pokazu pięciu zdjęć ani FINAL176. Nie oceniano skuteczności i nie zmieniano historii sesji, eksportów, GT ani pracy.
