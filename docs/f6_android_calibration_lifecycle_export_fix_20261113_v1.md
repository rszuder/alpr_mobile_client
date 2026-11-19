# F6 Android — trwałość kalibracji, finalizacja i odświeżanie edytora

Identyfikator: `F6_ANDROID_CALIBRATION_LIFECYCLE_EXPORT_FIX_20261113`. Data: 13.11.2026.

**Status zamknięcia dostawy wyznacza `READY.json` w jej katalogu, po sprawdzeniu testów, podpisu i paczki. Brak GO dla eksperymentu; UI nowej APK na A12/TV pozostaje NOT_VERIFIED.**

## Pochodzenie i zabezpieczenie

Repo: `C:\Users\48572\AndroidStudioProjects\ALPR_v1`, `main`, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`. Zachowano zastane zmiany F6 i edytora. Nie wykonano commit/push, checkout/reset/clean, instalacji zależności, instalacji/odinstalowania APK, czyszczenia danych, uruchomienia sesji na telefonie ani operacji na Desktopie.

Katalog tej dostawy: `app/build/f6_calibration_lifecycle_export_fix_20261113_v1/`. `audit/source-before.zip`, `worktree-before.diff`, `source-before-sha256.json` i `protected-before-sha256.json` zabezpieczają stan wejściowy. Poprzednie dostawy precision v1 i MP v1 nie są katalogami roboczymi tej poprawki.

Odczyt już autoryzowanego ADB potwierdził A12 `SM-A125F`, serial `R58R346GZYW`. Zainstalowany `com.example.alpr_v1`, versionCode 1/versionName 1.0, ma SHA-256 **`d68e11d5b14b1144316dbe78a48758d272cdd3ebf027a909a4406f313dd6f43e`**, build UTC `2026-11-13T02:11:36.434958900Z`. To inny plik niż wskazana dostawa precision v1 `ff6945…3695`; nie przypisujemy jej wyniku zainstalowanej aplikacji. Bieżący logcat nie zawierał szukanego błędu. Pełną przyczynę odczytano z autentycznych `session.json`.

Zabezpieczono kopię zainstalowanej APK, preferencji, trwałych logów, obu sesji wraz z obrazami, dziennikami i `.alprsession.tmp`: `audit/device_readonly/`. Telefon służył tylko do odczytu. SHA preferencji przed/po odczycie: `4088532cfddb4c13ef9abda564e5049b60c3bbd86d3bb444700f2e35d43a48c3`. Materiały te są lokalnymi dowodami diagnostycznymi, nie syntetyczną paczką dla Desktopu.

## Udowodnione przyczyny i zakres poprawki

1. **Finalizacja:** sesje `exp-1794536582894-0d770cc4` i `exp-1794537174642-a9ffe9cc` mają `last_write_error=finalization_failed: F6_EXPORT_INVALID: F6 requires actual R0 without MP`. Droga: `ResearchSessionViewModel.finish` → `ResearchSessionStore.finish/buildArchive` → `TvBboxInputValidator.validate` → `Final180ArchiveValidator.validate`. Ostatni sprawdzał dosłownie `execution.variant == R0`, podczas gdy pole jest etykietą operatora. Rzeczywiste wartości obu zapisów: `variant=R0_FINAL`, `roi_budget_policy=r0_full_frame`, `stages.mp.enabled=false`, `input_mode=TV_BBOX_CROP`; MT/MZ `tflite-fp32`, po 2 wątki. Test producenta najpierw odtworzył ten FAIL. Kontrola sprawdza teraz faktyczną politykę i aktywność etapów, także typ boolean; etykieta pozostaje bez zmian. R1/R2 ani MP ON nie mogą udawać R0 przez nazwę. Ten sam warunek sprawdzany jest przed utworzeniem trwałej sesji. Pozostałe kontrole eksportu pozostają czynne.

2. **Pozorny powrót punktów:** aktualny zapis `f6_photo_roi_v1` jest identyczny z ROI ostatniej sesji. BBOX wynosi `[141,519,771,789]` px w obrazie 960×1280; digest ROI `ed04ce4ad9396918f664bfd66cd52822893174b6ecc6c58ef169bcea5f491e1e`. Nie ma dowodu usunięcia tych punktów przez STOP. Porównanie sesji wykazało identyczne punkty TV, kamerę, zoom, rozmiary, crop i rotację; różniły się `binding_generation` 1→3 oraz `viewport_revision` 19→23. Zależny od nich digest TV zmieniał tożsamość wymaganą przez loader ROI. Loader po wyjątku pokazywał obrys TV jako fallback. Poprawka zachowuje pełne dotychczasowe digesty; nie usuwa z nich losowo pól.

3. **Zatwierdzenie a snapshot:** nowy wewnętrzny klucz `f6_photo_roi_approval_context_v1` w tych samych preferencjach przechowuje sealed kontekst: hash zatwierdzonego ROI, autentyczny snapshot TV oraz oryginalny zapis zatwierdzenia TV. Zapis jest atomowy z istniejącym ROI i potwierdzany po `commit`. Zgodność fizycznego źródła korzysta z istniejącego `TvEditDraft.sameCoordinates` i potwierdzonej kamery; bieżące generacje zadań nadal podlegają dotychczasowym kontrolom. Dla sesji tworzony jest niezmienny ROI v1 powiązany z jej rzeczywistym snapshotem TV, z identycznymi narożnikami i BBOX. Kontekst preferencji nie trafia do archiwum. START przechwytuje teksty ROI/kontekstu/zatwierdzenia na UI przed pracą asynchroniczną; finalizacja używa konfiguracji sesji.

4. **Starszy zapis:** bez nowego kontekstu wolno użyć dokładnie zgodnego bieżącego snapshotu albo autentycznego zachowanego snapshotu o wymaganym digescie. Wyszukiwanie starszych metadanych działa poza UI; nie dopisuje preferencji i nie zmienia starej sesji. Brak dowodu lub faktyczna zmiana kamery/cropu/rotacji/mirror/zoomu/rozmiarów pozostawia zapis i jawną blokadę. Edytor odtwarza raz integralne punkty jako szkic; nie przedstawia obrysu TV jako poprawnego zapisu F6. Nieudokumentowanego starego zapisu nie da się potwierdzić samym hashem bez autentycznego kontekstu. Niezmieniony zapis TV zachowuje pierwotną tożsamość zatwierdzenia.

   W gotowości START istniała jeszcze druga stara ścieżka `Final180PhotoRoi.fromJson(preferencje, snapshot_sesji)`. Zastąpiono ją kontrolą niezmienionego zatwierdzenia przechwyconego przy przygotowaniu, przy zachowaniu kontroli bieżącej kamery i geometrii. Zmiana ustawień przed START nadal wymaga ponownego przygotowania. Po START zmiana bieżących preferencji nie zmienia utrwalonego eksportu. Test rzeczywistej `MainActivity` obejmuje zgodne źródło z rewizją podglądu 3→103, zablokowany executor, zmianę preferencji, zatwierdzenie START i STOP bez klatek. Nie utożsamia to zatwierdzenia fizycznego pola z techniczną tożsamością konkretnej sesji.

5. **Odświeżanie:** nowy test 60 aktualizacji tej samej geometrii wykazał 120 naprzemiennych zmian instrukcji krótkiej/pełnej. Usunięto konkurujących właścicieli instrukcji i widoczności statusu. Aktualizacja celu lupy nie uruchamia ponownie pętli kopiowania. Nadal istnieje jedna pętla 100 ms, limit pary bitmap 32 MiB i kontrola źródła/epoki. Chwilowo nieudana kopia zgodnego źródła nie usuwa poprawnego bufora; po utracie świeżości widnieje „Przerwa — ostatnia klatka”. Rzeczywista utrata/zmiana źródła odrzuca obraz. Nie użyto dirty ani pierwszego dotknięcia do zamrażania obrazu. Fizyczne ustąpienie migania pozostaje do sprawdzenia na A12.

6. **STOP przed klatką:** dodatkowy test ujawnił brak fizycznego pliku pustego dziennika F6. Przygotowanie tworzy teraz pusty `samples/f6_execution.jsonl`. Nie tworzy rekordów MT ani fikcyjnej odpowiedzi „zero tablic”; brak rozpoczętego MT pozostaje odróżnialny od wykonania zakończonego zerem detekcji.

7. **Błąd i recovery:** wyjątek finalizacji zachowuje konfigurację, pliki i prawdziwy powód zakończenia; nie oznacza go ponownie jako błędu przygotowania. Pełny wyjątek z łańcuchem przyczyn zapisuje istniejący AppLog i pokazuje Diagnostyka, odrębnie od gotowości START. F6 recovery pracuje wyłącznie w kopii pod `recovery/f6-copy-<SHA>/`, z manifestem SHA i `recovery_provenance.json`. Oryginalny `session.json`, dzienniki i wadliwy `.tmp` pozostają. Walidowany eksport kopii ma stan PARTIAL; udane odzyskanie nie uzupełnia brakujących wykonań. Istniejącego workspace po nieudanym odzyskaniu nie nadpisuje się automatycznie. Historyczna ścieżka F5A pozostaje bez zmian.

Nie zmieniono schematów archiwum, nazw/jednostek dotychczasowych pól, identyfikatorów SourceStamp/frame/MT/FINAL/MZ, geometrii inferencji, `CROP_ONLY_TRANSLATION`, `H_TV=NOT_APPLIED`, modeli, liczby wątków, schedulera, NMS, konsensusu ani timera. Android pozostaje **Bez timera**; czas zdjęć jest ustawieniem Desktopu. Nie importowano GT i nie liczono skuteczności. Nowe logi są zdarzeniowe, bez bitmap i logowania każdego obrazu; narzut fizyczny i porównywalność z historycznymi czasami F5A nie zostały zmierzone.

Kontrakt dla Desktopu pozostaje `F6_FINAL180_SPLIT_V1`: sesja `alpr.mobile_research_session.v1`, próbki `alpr.mobile_research_samples.v2`, TV `alpr.tv_bbox_crop.v1`, ROI `alpr.f6_photo_roi.v1`, sceny `alpr.scene_evidence.v1`, lifecycle `alpr.f6_execution_event.v1`. `execution.variant` to zachowana etykieta operatora; faktyczną politykę określają `roi_budget_policy` i etapy. `execution.f6_photo_roi` w eksporcie jest związany z `execution.tv_bbox_crop` i `session.tv_bbox_crop` danej sesji. Sam rekord zatwierdzenia preferencji nie jest jej projekcją. Oryginalny pełny kontrakt i szczegóły SourceStamp, czasów, geometrii oraz braków: `docs/f6_final180_android_contract_20261112.md` (bez zmian).

Wewnętrzny kontekst preferencji ma schemat `alpr.f6_photo_roi_approval.v1` i pola `photo_roi_digest_sha256`, `tv_bbox_crop`, `tv_calibration_approval`, `provenance`, `digest_sha256`. Nie wymaga nowego parsera archiwum. Odczyt kontekstu ze starszych `session.json` ma limit 2 MiB na plik metadanych i czyta pojedyncze pliki; nie ogranicza strumieni scen, obrazów ani liczby klatek. Brak takiego dowodu nie daje ważności przez domysł. Pętle i szkice nie zachowują historii obrazów sesji w RAM. Limity strumieniowego F6 nie zostały zmienione.

Log `F6_CALIBRATION` zapisuje inicjalizację szkicu, zmianę geometrii, pierwszy rzeczywisty ruch, zatwierdzenie i zamknięcie: źródło poprzednie/aktualne, mapper, token callbacku, dirty, punkty szkicu, rzeczywisty BBOX i digesty. `F6_SESSION_CONFIG` zapisuje przygotowanie/STOP, a `F6_SESSION_FINALIZATION` pełny wyjątek. Pierwsza przetwarzana klatka ma istniejące powiązane rekordy `f6_execution.jsonl`/TV/MT. Domena BBOX to piksele pełnego źródła kamery, szkic to `source_frame_normalized_v1`, zegary sesji to `ANDROID_ELAPSED_REALTIME` w ns; obraz lupy używa czasu widoku w ms i nie jest dowodem badawczym.

## Testy, stany i dowody

Wykonane przebiegi są rozłącznie raportowane; nie dodaje się ich liczników:

| Przebieg | Wynik | Dowody |
|---|---|---|
| Szeroka regresja JVM | 1383/1383 PASS, 0 FAIL/ERROR/SKIP | `audit/final_run/`, `audit/full-regression-2.log` |
| Końcowa macierz START i odzyskiwania po korekcie gotowości MainActivity | 44/44 PASS, 0 FAIL/ERROR/SKIP | `audit/final_start_recovery_run/`, `final-recovery-and-main-regression-2.log` |
| Mocniejsza rewizja źródła + diagnostyka i UI | 3/3 PASS, 0 FAIL/ERROR/SKIP | `audit/final_diagnostic_run/`, `final-build-and-diagnostics.log` |
| Ostateczny eksport dwóch sesji i odpowiadający mu ślad stanów | 1/1 PASS, 0 FAIL/ERROR/SKIP | `audit/final-fixture-trace-and-apk.log`, `final-verified-lifecycle-state-trace.jsonl` |
| Instrumentacja | kompilacja PASS; wykonanie NOT_RUN | logi powyższych buildów |
| Debug APK offline | PASS; podpis i tożsamość poniżej | `audit/apksigner-new.txt` |

Główna komenda: `.\gradlew.bat --offline -I app/build/f6_calibration_lifecycle_export_fix_20261113_v1/audit/full-regression.gradle testDebugUnitTest compileDebugAndroidTestJavaWithJavac assembleDebug --console=plain`, z JAVA_HOME `C:\Program Files\Android\Android Studio\jbr`. Końcowe ograniczone przebiegi używają `--tests` z klas/metod wskazanych w XML i logach. JVM: 1024 MiB, jeden fork, odnowienie co 25 klas. Wyłączono wyłącznie ponowne generowanie `longSessionStreamsBeyond4096FramesAnd32MiBWithoutRetainingHistory`. To nie pomiar pamięci telefonu.

Ustawiono `F6_PREVIOUS_BAD_ARCHIVE` na zachowaną negatywną dostawę, `F6_ACCEPTED_MP_FIXTURES` na odebrany katalog MP v1 i `F6_DEVICE_COPY` na zabezpieczoną kopię. Nie ustawiano starych miejsc docelowych fixture. **20/20 wcześniejszych archiwów MP v1**, w tym 4100 klatek i scena >32 MiB, przeszło aktualny pełny gate producenta bez regeneracji; SHA długiego pozostaje `f4c9c9976e460ad85cb8e655311f45dbb090c9f8368dd0644fc70ec8ebff50d9`. Oryginalny wadliwy plik nadal jest odrzucany za niekompletne MP. PASS tego testu negatywnego nie jest akceptacją wadliwego pliku.

Zachowano wcześniejsze diagnostyczne FAIL i SKIP w `audit/`: odtworzenie błędu etykiety, 120 zmian instrukcji, brak pliku pustego dziennika i kontrole ukrytego statusu. Pierwszy szeroki przebieg miał 3 FAIL; poprawiono je i powtórzono pełną macierz. W jednym wcześniejszym wąskim przebiegu był SKIP archiwum negatywnego, gdy nie podano jego ścieżki; końcowe przebiegi nie mają SKIP. Przebiegów diagnostycznych i historycznych 1368 testów nie dolicza się do tabeli odbioru.

Pokrycie: T01–T05/T14 — prawdziwe preferencje, szkic, anulowanie, 60 odświeżeń, zmiana layoutu i niezgodność źródła; T06–T09 — kontroler + rzeczywisty silnik z syntetycznymi backendami + producent, dwa eksporty z MT `NO_DETECTION`, EMPTY, późny wynik/anulowanie, niezmienność konfiguracji; dodatkowo rzeczywista ścieżka capture/worker/START/STOP `MainActivity`; T10 — wyjątek telemetrii i pełny łańcuch przyczyn bez skasowania sesji; T11–T12 — stary PixelCopy, ukrycie/otwarcie, błędy bufora, rotacje i granice; T13 — niezmienione tensory/ROI oraz syntetyczny kolor znacznika poza MT; T15 — F5A i odebrane MP v1. Rzeczywistych ArUco/QR na TV nie odebrano.

Obejrzano rendery hostowe rzeczywistych widoków TV/F6 720×1600 przy 300 dpi, tekst 100%/200%: `renders/f6-precision-*.png`. Są syntetycznym uruchomieniem Robolectric Native, nie zrzutami A12 i nie dowodem ustąpienia migania na GPU telefonu.

Tabela poniżej opisuje wykonany syntetyczny cykl kontrolera i producenta, nie nieobserwowane kroki operatora na A12. Pełne 21 stanów, punkty, digesty, źródło i mapper: `audit/lifecycle-state-table.csv/.json` oraz ślad JSONL. Zatwierdzony BBOX tego przypadku to `[149,110,468,256]` px, przy źródle 640×400. Zapisany ROI pozostaje stały w obu sesjach; ID sesji/wywołań pozostają odrębne.

| Przejście przed → po | Wynik sprawdzenia |
|---|---|
| Brak TV → TV SAVE | powstał zapis TV; F6 jeszcze nie ustawiono |
| TV zapisany → F6 SAVE | osobny wewnętrzny ROI i kontekst; TV bez zmiany |
| F6 SAVE → główny podgląd | te same zatwierdzone punkty i BBOX |
| Otwarcie → 60 odświeżeń bez dotyku | te same punkty, zero zapisów preferencji, zero zmian instrukcji |
| Powrót → START i utrwalenie | rzeczywista konfiguracja R0_FINAL/r0_full_frame, MP OFF, źródło i snapshot ROI |
| START → pierwsza klatka | jeden rozpoczęty MT; zakończenie NO_DETECTION, rzeczywiste powiązania klatki |
| Klatka → STOP → finalizacja | kompletny eksport tego przebiegu; nie użyto późniejszych preferencji |
| Eksport → ponowne otwarcie | zapis i punkty zachowane; druga sesja bez rekalibracji |
| Ponowny niezmieniony TV SAVE | ten sam trwały rekord TV i zgodność F6 |
| Ponowne otwarcie → pierwszy ruch | zmienia się szkic wybranego rogu, nie zapis |
| Szkic → anulowanie | poprzednie TV/F6 zachowane |

Dodatkowo STOP przed klatką daje `PARTIAL`, `frames=0`, `mt_started_count=0`, bez rekordów MT. Awaria procesu zachowuje oryginalny RUNNING i tworzy kopię PARTIAL. Dwie autentyczne sesje A12 zostały odzyskane na hostowych kopiach jako PARTIAL, z niezmienioną konfiguracją i oryginalnym powodem `manual`; to nie zapis nowej APK na telefonie. Nie rekonstruowano brakujących obserwacji ani nie użyto GT.

Dla historycznego A12 nie istnieje pełny ślad wszystkich etapów otwarcia/bezczynności/pierwszego dotyku. Te komórki są NOT_CAPTURED, nie wypełnia się ich zgadywanymi wartościami. Dostępne rekordy przed/po i rzeczywiste różnice tożsamości są w `audit/diagnosis-a12.json`.

Zmiany tej poprawki: 15 plikow istniejacych i 6 nowych (lacznie 21). Minimalny diff wzgledem zabezpieczonego stanu roboczego: `scope.patch`, nie diff calosci dawnych zmian F6 wzgledem HEAD. Lista i SHA: `changed-source-files.json`. Ochrona: wszystkie 1396 plikow poprzednich dostaw zgodne z bazowymi SHA; 14 pozycji assets/lib nowej APK identycznych z precision v1. Modele, preferencje telefonu oraz obie surowe sesje pozostaly niezmienione; szczegoly w audytach.

```text
app/src/main/java/com/example/alpr_v1/DiagnosticsActivity.java
app/src/main/java/com/example/alpr_v1/MainActivity.java
app/src/main/java/com/example/alpr_v1/experiment/Final180ArchiveValidator.java
app/src/main/java/com/example/alpr_v1/experiment/Final180PhotoRoi.java
app/src/main/java/com/example/alpr_v1/experiment/Final180PhotoRoiApproval.java
app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionStore.java
app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionViewModel.java
app/src/main/java/com/example/alpr_v1/logging/AppLog.java
app/src/main/java/com/example/alpr_v1/ui/TvCalibrationMagnifier.java
app/src/main/java/com/example/alpr_v1/ui/TvSetupController.java
app/src/main/res/values/tv_calibration_precision_strings.xml
app/src/test/java/com/example/alpr_v1/experiment/F6LifecycleTrace.java
app/src/test/java/com/example/alpr_v1/experiment/Final180EvidenceTest.java
app/src/test/java/com/example/alpr_v1/experiment/Final180PhotoRoiApprovalTest.java
app/src/test/java/com/example/alpr_v1/experiment/Final180SavedDeviceRecoveryTest.java
app/src/test/java/com/example/alpr_v1/pipeline/TvBboxPipelineTest.java
app/src/test/java/com/example/alpr_v1/ui/F5a24CameraStartTest.java
app/src/test/java/com/example/alpr_v1/ui/TvCalibrationMagnifierTest.java
app/src/test/java/com/example/alpr_v1/ui/TvSetupUiTest.java
docs/f6_android_calibration_lifecycle_export_fix_20261113_v1.md
tools/package_f6_calibration_lifecycle.py
```

## APK, pakiet i instrukcja

Nowa APK (debug, com.example.alpr_v1, versionCode 1 / versionName 1.0):

```text
C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_calibration_lifecycle_export_fix_20261113_v1\alpr-f6-calibration-lifecycle-export-debug.apk
SHA-256: 1cd1350dd396168b3b8b8f8cdb051101ebdc34676d92c12ba126bd74c511e071
Rozmiar: 87222552 B
BUILT_AT_UTC: 2026-11-13T09:52:39.274664500Z
```

Podpis APK: PASS (v2), certyfikat SHA-256 `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`; zgodny zarowno z odczytana instalacja A12, jak i precision v1. Instalacji nie wykonano. Poprawka wymaga aktualizacji APK.

Pakiet dla Desktopu: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_calibration_lifecycle_export_fix_20261113_v1\desktop_package.zip`. Zawiera 27 syntetycznych eksportow oraz 2 hostowe odzyskania autentycznych kopii A12 (PARTIAL), ich pochodzenie, kontrakty, slady stanow i zrodla producenta. SHA kazdego archiwum: `fixtures-sha256.json` i `desktop_package/SHA256SUMS.txt`. SHA reportu, generatora, APK i paczki po spakowaniu: `READY.json`; pelny spis lokalnych dowodow: `SHA256SUMS.txt`. Generator: `tools/package_f6_calibration_lifecycle.py`, bez nowych zaleznosci. Nie regenerowano ani nie nadpisywano odebranej paczki MP v1.

1. Operator aktualizuje ten sam pakiet nową APK bez odinstalowania i bez usuwania danych. Agent nie instalował APK. Sprawdzić modele, R0, FP32, po 2 wątki, MP OFF, TV_BBOX_CROP, F6 ON i **Bez timera**.
2. PC: nieruchoma plansza TEST_ONLY/faza T. Android: sprawdzić zewnętrzną **Kalibrację ekranu TV**, następnie osobne **Pole fotografii F6** na wewnętrznym polu zdjęć. Zapisane dotąd punkty pozostają; przy braku autentycznego dowodu zgodności aplikacja musi wymagać jawnego potwierdzenia.
3. Zapisać F6, ponownie otworzyć bez dotykania i odczekać kilka sekund: punkty, status i obraz muszą pozostać stabilne. Sprawdzić 1/5 px, 4×/8×, brak skoku i zatrzymanie przytrzymania. Zamknąć bez zmiany.
4. START → klatki planszy (zero detekcji jest dopuszczalne) → STOP → zakończenie finalizacji → eksport. Ponownie otworzyć F6 i wykonać następny START bez rekalibracji. Oddzielnie sprawdzić STOP z zadaniem w locie.
5. Przy błędzie wejść w **Diagnostykę**: pełna finalizacja jest odrębna od komunikatu gotowości START. Zabezpieczać katalog sesji i dzienniki; nie kasować danych i nie wymuszać dodatniej detekcji. Stare uszkodzone sesje są zabezpieczone w `audit/device_readonly/`; obecność plików nie gwarantuje odzyskania danych nigdy niezapisanych.

| Status | Wynik |
|---|---|
| ANDROID_HOST_TESTS | PASS w opisanych osobnych przebiegach |
| SESSION_FINALIZATION_EXPORT | PASS_HOST_SYNTHETIC; kopie A12 odzyskane jako PARTIAL |
| UI_A12_LIFECYCLE | NOT_VERIFIED — nowej APK nie instalowano |
| A12_TV_TECHNICAL | NOT_VERIFIED |
| EXPERIMENT_START | NO_GO do rzeczywistego odbioru nowej APK i stanowiska |

Nie wykonano na nowej APK próby dotykowej A12, sprawdzenia migania na telefonie, odczytu kodów planszy, kontroli kodów poza rzeczywistym wejściem MT na TV, pokazu FINAL ani testu cieplnego. Hostowy eksport nie jest przyjęciem nowej paczki przez czytnik Desktopu; odbiór wykona osobny agent/operator.
