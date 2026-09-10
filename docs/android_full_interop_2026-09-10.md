# Android ALPR: wdrożenie interoperacyjności — 10.09.2026

Podstawa: `HANDOFF_ANDROID_ALPR_FULL_INTEROP_2026-09-10.md`, lokalny HEAD
`95a11ec`. Użytkownik wyłączył migrację wcześniejszych sesji z zakresu zadania.
Zmiany dotyczą nowych zapisów. Nie usuwamy ani nie przepisujemy istniejących paczek.

## Rozbieżności wykryte przed zmianą

- Galeria i `CropSessionStore` grupowały po dokładnym raw, podczas gdy Desktop
  porównuje `uppercase_alphanumeric.v1`.
- Paczka cropów nie miała jawnego klucza kanonicznego, polityki normalizacji
  ani identyfikacji buildu producenta.
- Manifest wejścia nie kontrolował deklaracji kwantyzacji względem tensora;
  parametry batch/letterbox/padding były domyślną polityką wykonania.
- `ResearchAttemptBatch.finish` przypisywał ogólny błąd późniejszego
  przetwarzania do `execution_error` wszystkich wcześniejszych wywołań MT.
- Brakowało małego, powtarzalnego zestawu przepuszczonego przez kod obu aplikacji.

Importer schematów i ról, kontrola hashy i ścieżek, zapis 0/1/N, osobne wejście MT,
anulowanie, `PARTIAL`, STOP/drain i zamrożona konfiguracja istniały już przed zadaniem.
Zachowano je i objęto regresją.

## Jedno API normalizacji

`RegistrationTextNormalizer.registrationKey(raw)`:

- uppercase przez `Locale.ROOT`, następnie litery i liczby Unicode;
- obsługa code pointów, także spoza BMP i kategorii Letter Number / Other Number
  uwzględnianych przez Python `isalnum()`;
- brak korekt O/0, I/1; null i tekst bez znaków alfanumerycznych dają pusty klucz;
- raw nie jest nadpisywany.

Do API odwołują się galeria, crop session, wyszukiwanie, finalizacja odczytu,
konsensus/stabilizacja, porównania AutoZoom i normalizacja tekstu w metrykach.
Wywołania backendów i surowe wyniki MZ nie są zastępowane kluczem.

```text
raw: aaa123 / P4     → key: AAA123
raw: AAA123 / P9     → key: AAA123
raw: AA A-123 / P9   → key: AAA123
```

Jedna grupa, osobne wystąpienia, osobne ID encji. Bazowy crop zachowuje swój raw,
znaki, pewność i czas. Szczegóły pokazują raw danej obserwacji oraz klucz porównawczy,
jeżeli zapisy się różnią. Cache zachowuje deduplikację po tożsamości obserwacji.
Pusty MZ i sam przeniesiony konsensus nadal nie tworzą zwykłego odczytu.

Wspólne wektory testowe: `app/src/androidTest/assets/interop/registration_cases.json`.
Zawierają ASCII, spacje, separatory, ekspansję ß, liczby Unicode, znaki łączące
i znak spoza BMP. Zgodność tych przypadków sprawdzono na Androidzie. Nie jest to
wyczerpujący dowód zgodności tabel Unicode wszystkich wersji Java/Python.

## Zmiany kontraktów są addytywne

### Zwykła paczka cropów

Schema pozostaje `alpr_crop_session_v1`. Dotychczasowy `text` nadal opisuje surowy
odczyt odpowiadający obrazowi/obserwacji. Dodano:

- manifest: `normalization_policy`, `app_build`, `capabilities`;
- grupa i każda obserwacja: `raw_prediction`, `registration_key`;
- capabilities jawnie wykluczają pełny rejestr MT i jakość pełnego pipeline'u.

`registration_key` jest kluczem nowych grup; raw może się różnić między ich
obserwacjami. To jawna zmiana polityki grupowania, oznaczona polityką w manifeście,
bez zmiany znaczenia starego pola `text`. Konsument zakładający identyczny raw
we wszystkich wystąpieniach musi przejść na opisany kontrakt.

Nie dodano `entry_sha256` do zwykłych cropów. Nie dodano fikcyjnych prób MT.
Stare sesje pozostają poza zakresem migracji.

### Paczka badawcza

Zachowano `alpr.mobile_research_bundle.v1` oraz `alpr.mobile_research_samples.v2`.
Do prób dopisano kolumny, zachowując poprzednie nazwy i znaczenia:

```text
raw_prediction, registration_key, normalization_policy, source_timestamp_domain,
mt_backend, mz_backend, mz_execution_error, processing_error
```

`prediction` nadal jest świeżym wynikiem MZ, również pustym. Klucz nie zastępuje
`subject_key`, `attempt_id` ani `mt_invocation_id`. Adnotacje cropów badania
zawierają również raw i klucz.

`mt_backend` / `mz_backend` powstają przy wejściu do rzeczywistego backendu.
`execution_error` dotyczy błędu wywołania MT; błąd MZ ma `mz_execution_error`,
a ogólny błąd przetwarzania `processing_error`. Poprawne wcześniejsze MT nie
stają się błędne tylko dlatego, że późniejszy etap zawiódł. Anulowanie zachowuje
osobne znaczniki i pierwszeństwo wynikające z istniejącego kontraktu.

Descriptor `samples/schema.json` publikuje `normalization_policy` i capabilities
obsługiwanych pól. Capabilities oznaczają możliwości formatu, a nie gwarancję
kompletności każdej obserwacji. Nadal trzeba sprawdzać `collection_complete`,
powody braku dowodów i pola konkretnej próby. Brak backendu w rekordzie `NOT_RUN`
nie jest zgadywany z konfiguracji GUI.

`environment/software.json` przenosi teraz także `app_build` z raportu. Istniejące
model refs, hashe artefaktów, zamrożone profile i konfiguracja ROI pozostają źródłem
pochodzenia modelu. Generator paczek kontrolnych jawnie oznacza materiał jako fixture.

### Modele i preprocessing

Zachowano `alpr.model.v1` oraz `alpr.package.v1`. Istniejąca polityka mobilna to
batch=1, trzy kanały RGB/BGR, NCHW/NHWC, letterbox z tłem 114 i interpolacja
biliniowa. Opcjonalne deklaracje `batch`, `resize_mode`, `padding_value`,
`interpolation` są sprawdzane; niezgodne wartości powodują odmowę zamiast
cichego wykonania innej transformacji. Brak tych pól zachowuje politykę v1.

`input.quantization.scale/zero_point`, już emitowane przez Desktop dla niektórych
wariantów, są walidowane i porównywane z tensorem. Kwantyzacja dotyczy rzeczywistego
wejścia INT8/UINT8; wariant ONNX INT8 QDQ nadal ma publiczne wejście FLOAT32.
Niepoprawny kolor, typ danych i niefinitywna normalizacja są odrzucane.

Opcjonalny `output.keypoint_order` jest dopuszczany dla czterech punktów
`top_left, top_right, bottom_right, bottom_left`. Nie dokonujemy cichej permutacji
punktów niezgodnego kontraktu. Brak pola zachowuje obecne zachowanie dekodera.

## Testy i granice potwierdzenia

- 647 testów JVM przeszło.
- 58 unikalnych testów instrumentacyjnych przeszło: import modeli, ONNX FP32/QDQ,
  normalizacja, galeria, crop session, pełny magazyn badania, zamrożona konfiguracja,
  manifesty i archiwum.
- Test UI potwierdził import cache po włączeniu Akwizycji: `AAA/P4` i `aaa/P9`
  trafiają do jednej grupy, zachowują raw i nie dublują się po wznowieniu.
- Desktop otworzył rzeczywiste artefakty serializerów Androida: sesję kompletną
  z 8 rekordami, częściową i sesję z błędem późniejszego przetwarzania.
- Desktop potwierdził liczbę wywołań 0/1/3, świeży sukces i pusty MZ, NOT_RUN,
  błąd backendu i anulowanie. Odrzucił niezgodny hash i sprzeczny detection count.
- Paczka cropów przeszła niezależną walidację z użyciem normalizatora Desktopu.
  Aktualny desktopowy czytnik raportów poprawnie odrzuca ją jako niebędącą badaniem.

Modele kontrolne są opakowane rzeczywistym eksporterem Desktopu. ONNX FP32 i QDQ
są wykonywalnymi małymi grafami, które wykonano także na Androidzie i zdekodowano.
Konwersja checkpointu treningowego jest zastąpiona kontrolowanym dostawcą artefaktów.
TFLite i NCNN w tych paczkach to stuby kontroli importu, nie modele do inferencji.
Nie deklarujemy nowego pełnego parytetu wszystkich wytrenowanych modeli/runtime'ów.

Osobny adapter `alpr_crop_session_v1` pozostaje po stronie agenta Desktop.
Nie zmieniano desktopowego rankingu, metryk ani repozytorium aplikacji Python.
Import human review do Androida pozostaje poza zakresem zgodnie z handoffem.

## Pliki i odtwarzanie

- API i reguły: `domain/RegistrationTextNormalizer`, wywołania w capture,
  acquisition, camera, consensus i metrics.
- Kontrakty: `ModelInputSpec`, `ModelOutputSpec`, `ModelTensorContractValidator`.
- Serializacja: `CropSessionStore`, `AcquisitionAttemptRecord`,
  `ResearchAttemptBatch`, `ResearchSessionStore`, `ResearchArchive`, `BuildProvenance`.
- Testy: `DesktopInteropInstrumentedTest`, `InteropArtifactInstrumentedTest`,
  `RegistrationTextNormalizerTest`, zaktualizowane testy galerii i cache/UI.
- Generatory, paczki i raport odczytu Desktopu: [tools/interop](../tools/interop/README.md).
- Formalny profil nowych cropów: [JSON Schema](alpr-crop-session-v1.schema.json).

Zmiany pozostają w working tree na bazie `95a11ec`; nie wykonano w tym etapie
nowego commitu ani push. Opis z 9.09 o ścisłym kluczu raw ma charakter historyczny.

## Lista zmienionych i dodanych plików

- `app/src/androidTest/assets/interop/models/README.txt`
- `app/src/androidTest/assets/interop/models/bad-hash.alprmodel`
- `app/src/androidTest/assets/interop/models/character.alprmodel`
- `app/src/androidTest/assets/interop/models/duplicate.alprmodel`
- `app/src/androidTest/assets/interop/models/missing-character.alprmodel`
- `app/src/androidTest/assets/interop/models/missing-file.alprmodel`
- `app/src/androidTest/assets/interop/models/missing-plate.alprmodel`
- `app/src/androidTest/assets/interop/models/mp-mt-mz.alprmodel`
- `app/src/androidTest/assets/interop/models/mt-mz.alprmodel`
- `app/src/androidTest/assets/interop/models/plate.alprmodel`
- `app/src/androidTest/assets/interop/models/traversal.alprmodel`
- `app/src/androidTest/assets/interop/models/vehicle.alprmodel`
- `app/src/androidTest/assets/interop/models/wrong-role.alprmodel`
- `app/src/androidTest/assets/interop/registration_cases.json`
- `app/src/androidTest/java/com/example/alpr_v1/capture/DynamicRecognitionHistoryInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/experiment/InteropArtifactInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/model/DesktopInteropInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/ui/PreSessionAndCropExportInstrumentedTest.java`
- `app/src/main/java/com/example/alpr_v1/MainActivity.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/RegistrationSearchPolicy.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/ScanAcquisitionFinalizer.java`
- `app/src/main/java/com/example/alpr_v1/camera/AutoZoomController.java`
- `app/src/main/java/com/example/alpr_v1/camera/AutoZoomRecognitionMemory.java`
- `app/src/main/java/com/example/alpr_v1/capture/CropSessionStore.java`
- `app/src/main/java/com/example/alpr_v1/capture/RecentReadCache.java`
- `app/src/main/java/com/example/alpr_v1/capture/RecognitionHistoryItem.java`
- `app/src/main/java/com/example/alpr_v1/capture/RecognitionHistoryObservation.java`
- `app/src/main/java/com/example/alpr_v1/capture/RecognitionHistoryStore.java`
- `app/src/main/java/com/example/alpr_v1/domain/PlateTextConsensus.java`
- `app/src/main/java/com/example/alpr_v1/domain/RegistrationTextNormalizer.java`
- `app/src/main/java/com/example/alpr_v1/experiment/AcquisitionAttemptRecord.java`
- `app/src/main/java/com/example/alpr_v1/experiment/ResearchAttemptBatch.java`
- `app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionStore.java`
- `app/src/main/java/com/example/alpr_v1/inference/ModelTensorContractValidator.java`
- `app/src/main/java/com/example/alpr_v1/metrics/BuildProvenance.java`
- `app/src/main/java/com/example/alpr_v1/metrics/MetricsCollector.java`
- `app/src/main/java/com/example/alpr_v1/metrics/ResearchArchive.java`
- `app/src/main/java/com/example/alpr_v1/model/ModelInputSpec.java`
- `app/src/main/java/com/example/alpr_v1/model/ModelOutputSpec.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/MobileAlprEngine.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/MultiRecognitionStabilizer.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/RecognitionStabilizer.java`
- `app/src/main/java/com/example/alpr_v1/ui/RecognitionEntityPager.java`
- `app/src/test/java/com/example/alpr_v1/domain/RegistrationTextNormalizerTest.java`
- `docs/alpr-crop-session-v1.schema.json`
- `docs/alpr-mobile-research-samples-v2.schema.json`
- `docs/alpr-model-v1.schema.json`
- `docs/android_full_interop_2026-09-10.md`
- `docs/gallery_exact_read_identity.md`
- `docs/mobile_architecture.md`
- `docs/mobile_research_export.md`
- `docs/model_package_v1.md`
- `tools/interop/README.md`
- `tools/interop/fixtures/android/bad-count.alprsession`
- `tools/interop/fixtures/android/bad-hash.alprsession`
- `tools/interop/fixtures/android/crop-session.zip`
- `tools/interop/fixtures/android/desktop-verification.json`
- `tools/interop/fixtures/android/processing-error.alprsession`
- `tools/interop/fixtures/android/research-complete.alprsession`
- `tools/interop/fixtures/android/research-partial.alprsession`
- `tools/interop/generate_desktop_fixtures.py`
- `tools/interop/requirements.txt`
- `tools/interop/verify_desktop_exchange.py`
