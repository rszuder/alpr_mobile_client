# Raport: Android experiment candidate hardening v1

Data: 2026-09-08. Handoff: `handoff_android_experiment_candidate_hardening_v1.md`.

## 1. Baza

- HEAD przed zmianą: `636d0bcdb407310546a23813f83637c9c609dc01`.
- Gałąź: `feature/research-session-auto-collection-v1`.
- Początkowe drzewo czyste, zgodne z audytem. Bez resetu, merge, push i tagowania.

## 2. Zmienione pliki

- `app/src/main/java/com/example/alpr_v1/experiment/ResearchAttemptBatch.java`
- `app/src/main/java/com/example/alpr_v1/experiment/AcquisitionAttemptRecord.java`
- `app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionStore.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/MobileAlprEngine.java`
- `app/src/main/java/com/example/alpr_v1/MainActivity.java`
- `app/src/androidTest/java/com/example/alpr_v1/experiment/ResearchSessionStoreInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/experiment/ResearchExecutionConfigInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/pipeline/ResearchAttemptAuditInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/ui/ResearchAutoCollectionInstrumentedTest.java`
- `docs/alpr-mobile-research-samples-v2.schema.json`
- `docs/mobile_research_export.md`
- `tools/validate_research_session.py`
- Niniejszy raport.

## 3. Invocation i subject

`mt_invocation_id` identyfikuje jedno wejście do backendu MT na jednym wejściu.
Nadanie ID i `mt_executed=true` następuje bezpośrednio przed `plateBackend.run`.
Przygotowanie wejścia bez uruchomienia backendu pozostawia NOT_RUN i puste ID.
Child rows dziedziczą ID, geometrię wejścia, znacznik źródła i generacje.
Indeks liczy kolejność dekodera, również detekcje z niepoprawnym quad.
`subject_key` i jego istniejący fallback pozostają bez zmian.

Przy STOP rozpoczęty batch zachowuje także późniejsze child rows. Limit obrazów
nie usuwa metadanych wywołania: powstają rekordy z jawnym powodem braku dowodu,
a sesja jest PARTIAL. Istniejący ograniczony writer/failure journal nie gwarantuje
bezstratności przy dowolnym przeciążeniu; liczniki ujawniają takie straty.

## 4. Kolumny i dowody

Na końcu attempts.csv dopisano `mt_detection_index`, `mt_detection_count`,
`mt_executed`, `roi_left/top/right/bottom`, `input_width/height`,
`plate_left/top/right/bottom`, `input_scale`, `input_pad_x/y`,
`mt_input_evidence_entry`, `mt_input_missing_evidence_reason`, `execution_error`.

Dowód MT jest kopią rzeczywistego letterbox przed normalizacją tensora.
Zapis cropa MZ zachowuje teraz osobno wejście MT; wcześniejsza implementacja
zastępowała ten obraz cropem. `evidence_entry` nadal wskazuje crop, gdy istnieje,
więc dotychczasowy reader zachowuje swoją semantykę. Każdy child ma własny JPEG
wejścia. Budżet RAM nadal obejmuje wszystkie kopie.

## 5. Przypadki 0/1/N

| Wynik jednego invocation | Wiersze | Count | Index |
| --- | --- | --- | --- |
| Zero detekcji | 1, NO_DETECTION | 0 | puste |
| Jedna detekcja | 1 | 1 | 0 |
| Trzy detekcje | 3, wspólne ID | 3 w każdym | 0,1,2 |
| MT nie wykonano | 1, NOT_RUN | puste | puste |

Dwa wywołania z 2 i 1 detekcją mają dwa ID i trzy wiersze. Anulowanie zachowuje
ID i jawny powód. `execution_error` odróżnia nieudane wykonanie/dekodowanie od
normalnego negatywnego wyniku; takich wierszy nie należy liczyć jako MT miss.
Liczba prawdziwych tablic pozostaje informacją GT ocenianą na desktopie.

## 6. Zgodność i integralność

Zachowano samples v2 i bundle v1, kolejność starych kolumn i pola wymagane
schematu. Descriptor addytywnie opisuje invocation i kolejność dekodera.
Android oraz niezależny walidator Python sprawdzają wszystkie `entry_sha256`.
Nie zmieniono `ResearchArchive`: istniejący writer obejmuje nowe pliki hashami.

Stary walidator z bazowego commita otwiera nowe paczki. Nowy walidator otwiera
także obie stare paczki QA. Negatywna próba ze zmienionym detection_count i
ponownie przeliczonymi hashami jest odrzucana przez walidację kontraktu.

## 7. Provenance i kompletność

`session.json` zamraża przed START `app_version` i `app_build` z `git_commit`,
`git_dirty`, `git_dirty_available`, `built_at_utc`, `source_state` oraz wersją
aplikacji. Finalny raport korzysta z tego snapshotu również podczas recovery
pod innym APK. `built_at_utc` już istniało w MetricsCollector; brakowało trwałego
snapshotu dla odzyskiwanej sesji. Brak `.git` oznacza unavailable/unknown,
nie clean (weryfikacja kodu Gradle; nie wykonywano pełnego buildu archiwum bez Git).

`storage_prepared=true` i `collection_mode=automatic` istnieją przed START.
Finalizacja raportuje complete, dropped sample/telemetry, integrity, attempt i
crop counts. Brak wejścia MT przy obecnym cropie, ucięta telemetria oraz straty
pisania uniemożliwiają COMPLETED. Powtórny eksport nie nalicza ponownie wykrytej
utraty telemetrii.

## 8. Regresja

- Build `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: sukces;
  543 testy JVM, 0 błędów, 0 pominięć.
- Pełna regresja Androida po osłonie lifecycle: 151/152 PASS; ostatni test
  kamery wykazał zbyt wczesne sprawdzanie ZIP przy stanie FINALIZING.
  Po poprawce oczekiwania oba testy ResearchAutoCollection przeszły (26,549 s).
  Łącznie wszystkie 152 przypadki mają wynik PASS; końcowy przebieg na APK
  z czystego commita jest utrwalany w lokalnych logach wskazanych niżej.
- Rzeczywiste wykonanie pięciu wariantów MT: PASS, 36,982 s.
- Rzeczywiste MT/MZ z porównaniem predykcji bez kolektora, dowodami wejścia,
  zachowaniem cropów i anulowaniem: PASS.
- Testy M1–M4: 0/1/3 oraz 2+1, geometria i identyczne metadane invocation.
- M5–M6: scena/STOP, brak dowodu, oddzielny input i crop, limit pamięci.
- M7–M9: liczba kolumn, przecinek/cudzysłów/nowa linia, stary reader, SHA.
- M10: freeze model/variant/runtime/precision/profile i istniejąca regresja
  konfiguracji STATIC/DYNAMIC, lock, AZ oraz rzeczywistych backendów.

Paczki zweryfikowane przed commitem:

| Paczka | Stan | Invocations | Próby | Cropy | SHA |
| --- | --- | --- | --- | --- | --- |
| research-qa-auto.alprsession | COMPLETED | 10 | 10 NO_DETECTION | 0 | 35 |
| research-qa-mz.alprsession | COMPLETED | 4 | 4 VALID_QUAD | 4 | 31 |

Obie bez ostrzeżeń, każda zawiera jeden wiersz anulowany. Logi, paczki i kopia
starego walidatora: `app/build/reports/experiment-candidate/` (nie wersjonowane).

Przebieg urządzenia ujawnił wcześniejszy wyścig: callback kamery lub ponowne
planowanie pracy luminancji po zniszczeniu Activity wywoływały executor po
shutdown. MainActivity odrzuca teraz tę spóźnioną pracę i obsługuje wyścig z
shutdown. Osobny test zamkniętego Activity odtwarza obie drogi. Nie zmieniono
algorytmów STATIC/DYNAMIC, lock/search, AZ, kolejki akwizycji ani wyboru modeli.

Do powtórzenia używać instalacji zachowującej dane i bezpośredniej instrumentacji:

```text
gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e liveResearch true -e installedVariants true com.example.alpr_v1.test/androidx.test.runner.AndroidJUnitRunner
python tools/validate_research_session.py <paczka.alprsession>
```

Testy opt-in wymagają zainstalowanego pakietu pięciu wariantów i pliku
`research-qa-source.png` w external files aplikacji.

## 9. Kandydat

Kandydatem jest pojedynczy commit zawierający ten raport. Pełny SHA i wynik
`git status` są podane w końcowej odpowiedzi agenta. APK do pilotażu należy
budować z tego czystego commita; APK z etapu implementacji ma prawidłowo dirty=true.
Nie utworzono taga ani nie wykonywano merge/push.

## 10. Ograniczenia i incydent środowiska

- To regresja i kandydat kodu, nie zakończony pilotaż terenowy ani pomiar jakości GT.
- Nie uruchamiano GUI Desktop; zgodność sprawdzono parserami ZIP/CSV/JSON.
- JPEG jest dowodem wizualnym, nie bezstratnym zrzutem tensora. Dodatkowy zapis
  wejść zwiększa koszt I/O i zajętość pamięci; nie zmierzono długiej kampanii.
- Pierwsze uruchomienie `connectedDebugAndroidTest` przez Gradle odinstalowało
  aplikację z lokalnymi danymi. Agent nie wykonał wcześniej pełnego backupu.
  APK i modele przywrócono przez importer z eksportu
  `ALPR_20260907_2059_MP-MT-MZ_razem-16p0MB_experyment.alprmodel`; SHA pakietów
  MT i MZ odpowiadają poprzedniemu raportowi QA. Dawnych lokalnych sesji i
  ustawień nie odtworzono — brak pełnej kopii. Dotychczasowe eksporty QA na
  komputerze pozostały zachowane. Pomocniczy test przywracania usunięto z kodu.
  Dalsze instalacje i testy korzystały z `adb install -r` i `am instrument`.
