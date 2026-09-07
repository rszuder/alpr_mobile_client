# Raport: wybór wariantów wykonawczych modeli v1

Data: 2026-09-07. Gałąź: `feature/model-variant-selection-v1`.
Baza: `5e164c8`, po wdrożeniu v4; przed rozpoczęciem drzewo było czyste.

## Wdrożenie

- `SettingsActivity.java`, `strings.xml`: bezpośrednie „Wybierz wariant wykonawczy”
  w każdym węźle MP/MT/MZ, komunikat o braku aktywnego modelu, czytelne
  runtime + precision, identyfikator jako dalsza część opisu, AUTO z aktualnym
  automatycznym wariantem i profilem sprzętowym. Niedostępne runtime’y mają
  opis przyczyny i wyłączone wiersze; dodatkowa kontrola pozostaje przed pinem.
- `AutoTuneManager.java`: wszystkie dostępne warianty uczestniczą w rankingu
  udanych median, także INT8 przy obecnym FP32. Błędne/niefinitywne pomiary
  nie wygrywają, także wyjątek bez komunikatu pozostaje błędem.
  Pin nadal wygrywa ze zapisanym AutoTune. Podgląd AUTO nie usuwa pinu.
  Nieaktualny wariant profilu nie narzuca swojego CPU/GPU fallbackowi.
- `AutoTuneResult.java`: zapis polityki
  `lowest_successful_median_all_executable_variants`.
- `ModelStatusFormatter.java`: wspólne etykiety wariantu i CPU/GPU oraz
  rozróżnienie ręcznego wyboru, AutoTune i AUTO bez profilu.
- `MobileAlprEngine.java`: istniejący log `ALPR_ENGINE_MODEL` uzupełniony
  o pliki oraz typ wejścia. Model, fingerprint, wariant, runtime, precyzja
  i rozmiar wejścia już były logowane z tych samych obiektów przekazywanych
  do `RuntimeBackendFactory.create()`.
- `AutoTuneSelectionTest.java`, `RuntimeCompositionInstrumentedTest.java`:
  ranking, piny, fingerprint, role, konfiguracja badawcza, dialog ustawień
  i opt-in test rzeczywistej paczki na urządzeniu.
- Architektura, dziennik budowy, kopia handoffu, plan i niniejszy raport.

Importer i schemat nie wymagały zmian. ONNX INT8 QDQ z FLOAT32 na wejściu
pozostaje obsługiwany. MP jest opcjonalny, MT/MZ pozostają wymagane.

## Przeładowanie i badanie

Pin/unpin używa istniejących kluczy rola + fingerprint. „Zastosuj” zwiększa
rewizję ustawień. Po powrocie `MainActivity.applySettingsRevision()` wywołuje
odświeżenie rejestru i `pipeline.invalidateModels()`. `ensureEngineLoaded()`
zamyka stary silnik przed inferencją i tworzy nowy. Nie dodano osobnego
mechanizmu przeładowania ani preferencji wariantów.

Pierwszeństwo: zamrożony `ResearchStageExecutionConfig.requireVariant()`
oraz profil badania → ręczny pin → poprawny wybór AutoTune → fallback.
Zmiana globalnego pinu nie zmienia snapshotu trwającego badania.

## Wyniki

Build `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest`:
**BUILD SUCCESSFUL**. JVM: **536 testów, 0 błędów, 0 pominiętych, 126 zestawów**.
Android na Samsung SM-A125F (R58R346GZYW): **OK (85 tests)**, 96,089 s.
APK aplikacji i testów zainstalowano przez `adb install -r`, z zachowaniem danych.

| Próba | Wynik i dowód |
| --- | --- |
| V1 | PASS: rzeczywisty MT zachowuje pięć wariantów po imporcie. |
| V2 | PASS: test Espresso otwiera wariant bez ponownego wyboru modelu, sprawdza AUTO + pięć wierszy i zapis pinu z rewizją. Dialog sprawdzony również wizualnie. |
| V3 | PASS: MT TFLite INT8 wykonuje inferencję z `variants/tflite_int8/model.tflite`; pin `variant_pin.plate.536c70fb81d68bb9=tflite-int8` przetrwał force-stop i nowy proces 17662. |
| V4 | PASS: przełączenie INT8 → FP32 i otwarcie `variants/tflite/model.tflite` w tym samym procesie 15033, po powrocie z ustawień. |
| V5 | PASS: deterministyczny test rankingu wybiera szybszy INT8 spośród pięciu kandydatów; Android respektuje zapisany zwycięski INT8 mimo FP32. Nie jest to twierdzenie, że INT8 wygrywa na każdym urządzeniu. |
| V6 | PASS: pin INT8 wygrywa ze starym FP32 i z kolejnym zapisanym profilem ONNX; AUTO przywraca wybór profilu. |
| V7 | PASS: fallback TFLite FP32 bez profilu/pinu; nieaktualny zwycięzca nie przenosi profilu sprzętowego na inny wariant. |
| V8 | Sprawdzono mapowanie dostępności wierszy na fabrykę oraz kontrolę przed pinem. Na tym telefonie wszystkie trzy runtime’y są dostępne; brak fizycznego testu urządzenia bez NCNN. |
| V9 | PASS: test JVM kontraktu ONNX INT8 QDQ FLOAT32 oraz import i rzeczywista inferencja ONNX INT8. |
| V10 | PASS: niezależne piny MP NCNN FP32, MT TFLite INT8, MZ ONNX INT8. |
| V11 | PASS: nowy fingerprint tego samego modelu nie dziedziczy pinu. |
| V12 | PASS: zamrożony MT ONNX INT8, runtime, precision i wejście FLOAT32 pozostają po zmianie globalnego pinu na TFLite FP32; regresja konfiguracji badawczej także przechodzi. |
| V13 | PASS: rzeczywisty `.alprmodel` wyjęty z paczki oraz pełny `alpr.package.v1` przechodzą istniejące importery; MT ma identyczny fingerprint i wszystkie pięć wariantów. |
| V14 | PASS: regresja kompozycji, opcjonalnego MP, wymaganego MT/MZ, badania, historii/galerii, akwizycji, overlay, recovery oraz NCNN. Rzeczywiste MT TFLite FP32/INT8, ONNX FP32/INT8 i NCNN FP32 wykonują inferencję. |

Rzeczywisty model MT: `plate-20260905_223437`, fingerprint `536c70fb81d68bb9`,
wejście 512×512. Test importuje dane do izolowanego katalogu i używa izolowanych
preferencji. Pięć backendów uruchamia się na buforze wejściowym, co sprawdza
wykonalność i kontrakt, a nie jakość rozpoznawania względem ground truth.
Test wymaga jawnego argumentu `-e installedVariants true`; zwykła regresja
bez paczki nie uruchamia tego dodatkowego scenariusza sprzętowego.

## Artefakty lokalne

W ignorowanym `app/build/reports/gallery-qa/`:

- `model-variants-v1-regression.log` — pełny wynik 85 testów z rzeczywistą paczką;
- `variants-dialog-ready.png` — dialog z AUTO i pięcioma wariantami;
- `variants-int8-running.png` — analiza obrazu kamery po wyborze INT8;
- `variants-fp32-persisted.log` — logi otwarcia INT8 o 22:46:13 i FP32 o 22:50:01;
- `variants-autotune-after-restart.xml` — utrwalony pin po restarcie procesu.

Po weryfikacji przywrócono wcześniejsze AUTO dla MT. Ręczne próby nie zmieniały
aktywnych modeli ani pinów MP/MZ. Gałąź jest lokalna; push nie był częścią handoffu.
